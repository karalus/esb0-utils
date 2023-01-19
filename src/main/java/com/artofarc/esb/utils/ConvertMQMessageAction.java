/*
 * Copyright 2023 Andre Karalus
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

import java.util.Map;

import com.artofarc.esb.action.Action;
import com.artofarc.esb.context.Context;
import com.artofarc.esb.context.ExecutionContext;
import com.artofarc.esb.message.BodyType;
import com.artofarc.esb.message.ESBConstants;
import com.artofarc.esb.message.ESBMessage;
import com.artofarc.util.MQMessageParser;

public class ConvertMQMessageAction extends Action {

	@Override
	protected ExecutionContext prepare(Context context, ESBMessage message, boolean inPipeline) throws Exception {
		if (message.getBodyType() == BodyType.INPUT_STREAM) {
			MQMessageParser mqMessageParser = new MQMessageParser(message.getBody());
			Map<String, Object> jmsProperties = mqMessageParser.getJMSProperties();
			if (jmsProperties != null) {
				for (Map.Entry<String, Object> entry : jmsProperties.entrySet()) {
					message.putHeader(entry.getKey(), entry.getValue());
				}
				// TODO: How to tell if this is a Topic?
				message.putVariableIfNotNull(ESBConstants.QueueName, mqMessageParser.getJMSDestination());
				message.prepareContent();
			}			
		}
		return null;
	}

}
