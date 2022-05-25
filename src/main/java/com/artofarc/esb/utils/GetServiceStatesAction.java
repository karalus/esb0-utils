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

import javax.json.JsonArrayBuilder;
import javax.json.JsonObjectBuilder;

import com.artofarc.esb.action.Action;
import com.artofarc.esb.context.Context;
import com.artofarc.esb.context.ExecutionContext;
import com.artofarc.esb.http.HttpConstants;
import com.artofarc.esb.jms.JMSConsumer;
import com.artofarc.esb.message.BodyType;
import com.artofarc.esb.message.ESBMessage;
import com.artofarc.util.JsonFactoryHelper;

public class GetServiceStatesAction extends Action {

	public GetServiceStatesAction() {
		_pipelineStop = true;
	}

	@Override
	protected void execute(Context context, ExecutionContext execContext, ESBMessage message, boolean nextActionIsPipelineStop) throws Exception {
		message.clearHeaders();
		message.putHeader(HttpConstants.HTTP_HEADER_CONTENT_TYPE, HttpConstants.HTTP_HEADER_CONTENT_TYPE_JSON);
		JsonArrayBuilder result = JsonFactoryHelper.JSON_BUILDER_FACTORY.createArrayBuilder();
		for (JMSConsumer jmsConsumer : context.getGlobalContext().getJMSConsumers()) {
			JsonObjectBuilder builder = JsonFactoryHelper.JSON_BUILDER_FACTORY.createObjectBuilder();
			builder.add("uri", jmsConsumer.getUri());
			builder.add("key", jmsConsumer.getKey());
			builder.add("enabled", jmsConsumer.isEnabled());
			builder.add("lastChangeOfState", jmsConsumer.getLastChangeOfState().getTime());
			result.add(builder);
		}
		message.reset(BodyType.JSON_VALUE, result.build());
	}

}
