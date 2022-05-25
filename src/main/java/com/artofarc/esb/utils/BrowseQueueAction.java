/*
 * Copyright 2021 Andre Karalus
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.artofarc.esb.utils;

import java.util.Enumeration;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;

import javax.jms.BytesMessage;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.QueueBrowser;
import javax.jms.TextMessage;
import javax.json.stream.JsonGenerator;
import javax.servlet.http.HttpServletResponse;
import javax.xml.bind.DatatypeConverter;

import com.artofarc.esb.action.Action;
import com.artofarc.esb.action.ExecutionException;
import com.artofarc.esb.context.Context;
import com.artofarc.esb.context.ExecutionContext;
import com.artofarc.esb.http.HttpConstants;
import com.artofarc.esb.jms.JMSConnectionData;
import com.artofarc.esb.jms.JMSSession;
import com.artofarc.esb.message.BodyType;
import com.artofarc.esb.message.ESBConstants;
import com.artofarc.esb.message.ESBMessage;
import com.artofarc.esb.resource.JMSSessionFactory;
import com.artofarc.util.JsonValueGenerator;

public class BrowseQueueAction extends Action {

	private final String _jndiConnectionFactory, _userName, _password, _convertBytesMessage;
	private final HashSet<String> _convertTimestamps = new HashSet<>();
	private JMSConnectionData _jmsConnectionData;

	public BrowseQueueAction(ClassLoader classLoader, Properties properties) {
		_pipelineStop = true;
		_jndiConnectionFactory = properties.getProperty("jndiConnectionFactory");
		if (_jndiConnectionFactory == null) {
			throw new IllegalArgumentException("jndiConnectionFactory is mandatory");
		}
		_userName = properties.getProperty("userName");
		_password = properties.getProperty("password");
		_convertBytesMessage = properties.getProperty("convertBytesMessage");
		String convertTimestamps = properties.getProperty("convertTimestamps");
		if (convertTimestamps != null) {
			for (String key : convertTimestamps.split(",")) {
				_convertTimestamps.add(key);
			}
		}
	}

	@Override
	protected void execute(Context context, ExecutionContext execContext, ESBMessage message, boolean nextActionIsPipelineStop) throws Exception {
		message.clearHeaders();
		message.putHeader(HttpConstants.HTTP_HEADER_CONTENT_TYPE, HttpConstants.HTTP_HEADER_CONTENT_TYPE_JSON);
		if (_jmsConnectionData == null) {
			_jmsConnectionData = new JMSConnectionData(context.getGlobalContext(), _jndiConnectionFactory, _userName, _password);
		}
		JMSSessionFactory jmsSessionFactory = context.getResourceFactory(JMSSessionFactory.class);
		JMSSession jmsSession = jmsSessionFactory.getResource(_jmsConnectionData, false);
		String queueName = message.getVariable(ESBConstants.QueueName);
		if (queueName == null) {
			throw new ExecutionException(this, "QueueName not set");
		}
		String messageSelector = message.getVariable("messageSelector");
		String method = message.getVariable(ESBConstants.HttpMethod, "null");
		switch (method) {
		case "GET":
			String skipMessages = message.getVariable("skipMessages");
			String maxMessages = message.getVariable("maxMessages");
			try (QueueBrowser browser = jmsSession.getSession().createBrowser(jmsSession.createQueue(queueName), messageSelector)) {
				if (message.isSink()) {
					try (JsonGenerator jsonGenerator = message.getBodyAsJsonGenerator()) {
						new JsonFormatter(jsonGenerator, browser, skipMessages, maxMessages);
					}
				} else {
					JsonValueGenerator jsonValueGenerator = new JsonValueGenerator();
					new JsonFormatter(jsonValueGenerator, browser, skipMessages, maxMessages);
					message.reset(BodyType.JSON_VALUE, jsonValueGenerator.getJsonValue());
				}
			}
			break;
		case "PURGE":
			int count = 0;
			try (MessageConsumer consumer = jmsSession.getSession().createConsumer(jmsSession.createQueue(queueName), messageSelector)) {
				for (; consumer.receiveNoWait() != null; ++count);
			}
			if (message.isSink()) {
				message.getBodyAsJsonGenerator().write(count).close();
			} else {
				message.reset(BodyType.STRING, Integer.toString(count));
			}
			break;
		default:
			message.putVariable(ESBConstants.HttpResponseCode, HttpServletResponse.SC_METHOD_NOT_ALLOWED);
			throw new ExecutionException(this, method);
		}
	}

	class JsonFormatter {
		final GregorianCalendar timestamp = new GregorianCalendar();

		JsonFormatter(JsonGenerator jsonGenerator, QueueBrowser browser, String skipMessages, String maxMessages) throws Exception {
			jsonGenerator.writeStartArray();
			int skip = skipMessages != null ? Integer.parseInt(skipMessages) : 0 ;
			int max = maxMessages != null ? Integer.parseInt(maxMessages) : Integer.MAX_VALUE;
			@SuppressWarnings("unchecked")
			Enumeration<? extends Message> enumeration = browser.getEnumeration();
			while (enumeration.hasMoreElements()) {
				if (skip > 0) {
					enumeration.nextElement();
					--skip;
					continue;
				}
				if (--max < 0) {
					break;
				}
				writeJson(jsonGenerator, enumeration.nextElement());
			}
			jsonGenerator.writeEnd();
		}

		private void writeJson(JsonGenerator jsonGenerator, Message message) throws Exception {
			jsonGenerator.writeStartObject();
			Map.Entry<String, String> destinationName = JMSSession.getDestinationName(message.getJMSDestination());
			if (destinationName != null) {
				jsonGenerator.write(destinationName.getKey(), destinationName.getValue());
			}
			jsonGenerator.write(ESBConstants.JMSMessageID, message.getJMSMessageID());
			writeJson(jsonGenerator, ESBConstants.JMSTimestamp, message.getJMSTimestamp());
			writeJson(jsonGenerator, ESBConstants.JMSExpiration, message.getJMSExpiration());
			writeJson(jsonGenerator, ESBConstants.JMSType, message.getJMSType());
			writeJson(jsonGenerator, ESBConstants.JMSCorrelationID, message.getJMSCorrelationID());
			jsonGenerator.writeStartObject("properties");
			for (@SuppressWarnings("unchecked")
			Enumeration<String> propertyNames = message.getPropertyNames(); propertyNames.hasMoreElements();) {
				String propertyName = propertyNames.nextElement();
				writeJson(jsonGenerator, propertyName, message.getObjectProperty(propertyName));
			}
			jsonGenerator.writeEnd();
			if (message instanceof BytesMessage) {
				BytesMessage bytesMessage = (BytesMessage) message;
				byte[] bytes = new byte[Math.toIntExact(bytesMessage.getBodyLength())];
				bytesMessage.readBytes(bytes);
				if (_convertBytesMessage != null) {
					String charSet = message.getStringProperty(ESBConstants.Charset);
					jsonGenerator.write(_convertBytesMessage, new String(bytes, charSet != null ? charSet : "UTF-8"));
				} else {
					jsonGenerator.write("bytes", DatatypeConverter.printBase64Binary(bytes));
				}
			} else if (message instanceof TextMessage) {
				jsonGenerator.write("text", ((TextMessage) message).getText());
			}
			jsonGenerator.writeEnd();
		}

		private void writeJson(JsonGenerator jsonGenerator, String key, Object value) {
			if (value instanceof Number) {
				long longValue = ((Number) value).longValue();
				if (longValue > 0 && _convertTimestamps.contains(key)) {
					timestamp.setTimeInMillis(longValue);
					jsonGenerator.write(key, DatatypeConverter.printDateTime(timestamp));
				} else {
					jsonGenerator.write(key, longValue);
				}
			} else if (value != null) {
				jsonGenerator.write(key, value.toString());
			} else {
				jsonGenerator.writeNull(key);
			}
		}
	}

}
