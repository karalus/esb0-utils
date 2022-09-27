/*
 * Copyright 2022 Andre Karalus
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

import java.io.StringReader;
import java.lang.management.ManagementFactory;
import java.util.Arrays;

import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.json.JsonString;
import javax.json.JsonValue;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.json.bind.JsonbConfig;
import javax.json.bind.adapter.JsonbAdapter;
import javax.management.Attribute;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanParameterInfo;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import javax.servlet.http.HttpServletResponse;

import com.artofarc.esb.action.Action;
import com.artofarc.esb.action.ExecutionException;
import com.artofarc.esb.context.Context;
import com.artofarc.esb.context.ExecutionContext;
import com.artofarc.esb.http.HttpConstants;
import com.artofarc.esb.message.BodyType;
import com.artofarc.esb.message.ESBConstants;
import com.artofarc.esb.message.ESBMessage;
import static com.artofarc.util.JsonFactoryHelper.*;

public class MBeanAction extends Action {

	private final static Jsonb JSONB = JsonbBuilder.create(new JsonbConfig().withAdapters(new JsonbAdapter<CompositeData, JsonObject>() {

		@Override
		public JsonObject adaptToJson(CompositeData cd) {
			JsonObjectBuilder builder = JSON_BUILDER_FACTORY.createObjectBuilder();
			for (String key : cd.getCompositeType().keySet()) {
				String json = JSONB.toJson(cd.get(key));
				try (JsonReader jsonReader = JSON_READER_FACTORY.createReader(new StringReader(json))) {
					builder.add(key, jsonReader.readValue());
				}
			}
			return builder.build();
		}

		@Override
		public CompositeData adaptFromJson(JsonObject obj) {
			return null;
		}
	}));

	public MBeanAction() {
		_pipelineStop = true;
	}

	@Override
	protected void execute(Context context, ExecutionContext execContext, ESBMessage message, boolean nextActionIsPipelineStop) throws Exception {
		message.clearHeaders();
		String name = message.getVariable("objectName");
		if (name == null) {
			throw new ExecutionException(this, "objectName not set");
		}
		ObjectName objectName = new ObjectName(name);
		MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();
		MBeanInfo mBeanInfo;
		try {
			mBeanInfo = mbeanServer.getMBeanInfo(objectName);
		} catch (InstanceNotFoundException e) {
			message.putVariable(ESBConstants.HttpResponseCode, HttpServletResponse.SC_NOT_FOUND);
			throw new ExecutionException(this, name);
		}
		String attribute = message.getVariable("attribute");
		String method = message.getVariable(ESBConstants.HttpMethod, "null");
		switch (method) {
		case "GET":
			Object object;
			if (attribute != null) {
				object = mbeanServer.getAttribute(objectName, attribute);
			} else {
				String[] attributeNames = Arrays.asList(mBeanInfo.getAttributes()).stream().map(a -> a.getName()).toArray(String[]::new);
				object = mbeanServer.getAttributes(objectName, attributeNames);
			}
			returnResult(message, object, "");
			break;
		case "PUT":
			if (attribute == null) {
				throw new ExecutionException(this, "attribute not set");
			}
			try (JsonReader jsonReader = JSON_READER_FACTORY.createReader(message.getBodyAsReader(context))) {
				JsonValue jsonValue = jsonReader.readValue();
				mbeanServer.setAttribute(objectName, new Attribute(attribute, convert(jsonValue, "")));
			}
			returnResult(message, null, "void");
			break;
		case "POST":
			String operation = message.getVariable("operation");
			if (operation == null) {
				throw new ExecutionException(this, "operation not set");
			}
			try (JsonReader jsonReader = JSON_READER_FACTORY.createReader(message.getBodyAsReader(context))) {
				JsonObject jsonObject = jsonReader.readObject();
				for (MBeanOperationInfo operationInfo : mBeanInfo.getOperations()) {
					// TODO: No polymorphism, yet
					if (operation.equals(operationInfo.getName())) {
						MBeanParameterInfo[] signature = operationInfo.getSignature();
						Object params[] = new Object[signature.length];
						String types[] = new String[signature.length];
						for (int i = 0; i < signature.length; ++i) {
							params[i] = convert(jsonObject.get("p" + (i + 1)), types[i] = signature[i].getType());
						}
						Object result = mbeanServer.invoke(objectName, operation, params, types);
						returnResult(message, result, operationInfo.getReturnType());
						return;
					}
				}
				message.putVariable(ESBConstants.HttpResponseCode, HttpServletResponse.SC_NOT_FOUND);
				throw new ExecutionException(this, name + " has no operation " + operation);
			}
		default:
			message.putVariable(ESBConstants.HttpResponseCode, HttpServletResponse.SC_METHOD_NOT_ALLOWED);
			throw new ExecutionException(this, method);
		}
	}

	private Object convert(JsonValue jsonValue, String type) throws ExecutionException {
		if (jsonValue == null) {
			return null;
		}
		switch (jsonValue.getValueType()) {
		case TRUE:
			return Boolean.TRUE;
		case FALSE:
			return Boolean.FALSE;
		case NUMBER:
			JsonNumber jsonNumber = (JsonNumber) jsonValue;
			// TODO: short, BigInteger, BigDecimal, ...
			return type.endsWith("ong") ? jsonNumber.longValueExact() : jsonNumber.intValueExact();
		case STRING:
			return ((JsonString) jsonValue).getString();
		case NULL:
			return null;
		default:
			throw new ExecutionException(this, "Cannot convert JSON type " + jsonValue.getValueType());
		}
	}

	private void returnResult(ESBMessage message, Object result, String type) throws Exception {
		if (type.endsWith("oid")) {
			message.putVariable(ESBConstants.HttpResponseCode, HttpServletResponse.SC_NO_CONTENT);
			message.reset(BodyType.INVALID, null);
		} else {
			if (message.isSink()) {
				throw new ExecutionException(this, "Cannot write to sink");
			} else {
				message.reset(BodyType.STRING, JSONB.toJson(result));
			}
			message.putHeader(HttpConstants.HTTP_HEADER_CONTENT_TYPE, HttpConstants.HTTP_HEADER_CONTENT_TYPE_JSON);
		}
	}

}
