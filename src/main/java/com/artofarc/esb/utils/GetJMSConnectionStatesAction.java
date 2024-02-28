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

import java.util.Calendar;

import javax.json.JsonArrayBuilder;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;
import javax.xml.bind.DatatypeConverter;

import com.artofarc.esb.action.Action;
import com.artofarc.esb.action.ExecutionException;
import com.artofarc.esb.context.Context;
import com.artofarc.esb.context.ExecutionContext;
import com.artofarc.esb.context.WorkerPool;
import com.artofarc.esb.http.HttpConstants;
import com.artofarc.esb.jms.JMSConnectionProvider;
import com.artofarc.esb.mbean.JMSConnectionGuardMXBean;
import com.artofarc.esb.message.BodyType;
import com.artofarc.esb.message.ESBConstants;
import com.artofarc.esb.message.ESBMessage;
import com.artofarc.util.JsonFactoryHelper;

public class GetJMSConnectionStatesAction extends Action {

	public GetJMSConnectionStatesAction() {
		_pipelineStop = true;
	}

	@Override
	protected void execute(Context context, ExecutionContext execContext, ESBMessage message, boolean nextActionIsPipelineStop) throws Exception {
		message.clearHeaders();
		String jmsConnection = message.getVariable("jmsConnection");
		if ("POST".equals(message.getVariable(ESBConstants.HttpMethod))) {
			if (jmsConnection == null) {
				message.putVariable(ESBConstants.HttpResponseCode, 400);
				throw new ExecutionException(this, "jmsConnection not set");
			}
			if (HttpConstants.isNotJSON(message.getContentType())) {
				message.putVariable(ESBConstants.HttpResponseCode, 415);
				throw new ExecutionException(this, "Unexpected Content-Type: " + message.getContentType());
			}
			boolean disconnect = JsonValue.FALSE.equals(message.getBodyAsJsonValue(context));
			for (WorkerPool workerPool : context.getGlobalContext().getWorkerPools()) {
				JMSConnectionProvider jmsConnectionProvider = workerPool.getPoolContext().peekResourceFactory(JMSConnectionProvider.class);
				if (jmsConnectionProvider != null) {
					for (JMSConnectionGuardMXBean jmsConnectionGuard : jmsConnectionProvider.getResources()) {
						if (jmsConnectionGuard.getConnectionData().contains(jmsConnection)) {
							if (disconnect) {
								jmsConnectionGuard.disconnect();
							} else {
								jmsConnectionGuard.connect();
							}
						}
					}
				}
			}
		}
		message.putHeader(HttpConstants.HTTP_HEADER_CONTENT_TYPE, HttpConstants.HTTP_HEADER_CONTENT_TYPE_JSON);
		JsonArrayBuilder result = JsonFactoryHelper.JSON_BUILDER_FACTORY.createArrayBuilder();
		Calendar calendar = Calendar.getInstance();
		for (WorkerPool workerPool : context.getGlobalContext().getWorkerPools()) {
			JMSConnectionProvider jmsConnectionProvider = workerPool.getPoolContext().peekResourceFactory(JMSConnectionProvider.class);
			if (jmsConnectionProvider != null) {
				for (JMSConnectionGuardMXBean jmsConnectionGuard : jmsConnectionProvider.getResources()) {
					if (jmsConnection == null || jmsConnectionGuard.getConnectionData().contains(jmsConnection)) {
						JsonObjectBuilder builder = JsonFactoryHelper.JSON_BUILDER_FACTORY.createObjectBuilder();
						builder.add("workerPool", workerPool.getName());
						builder.add("jmsConnection", jmsConnectionGuard.getConnectionData());
						builder.add("connected", jmsConnectionGuard.isConnected());
						calendar.setTime(jmsConnectionGuard.getLastChangeOfState());
						builder.add("lastChangeOfState", DatatypeConverter.printDateTime(calendar));
						result.add(builder);
					}
				}
			}
		}
		message.reset(BodyType.JSON_VALUE, result.build());
	}

}
