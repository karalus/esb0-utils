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

import javax.json.JsonReader;
import javax.json.JsonString;
import javax.servlet.http.HttpServletResponse;

import com.artofarc.esb.action.Action;
import com.artofarc.esb.artifact.DeployHelper;
import com.artofarc.esb.context.Context;
import com.artofarc.esb.context.ExecutionContext;
import com.artofarc.esb.context.GlobalContext;
import com.artofarc.esb.http.HttpConstants;
import com.artofarc.esb.message.BodyType;
import com.artofarc.esb.message.ESBConstants;
import com.artofarc.esb.message.ESBMessage;
import com.artofarc.util.JsonFactoryHelper;

public class GlobalPropertyAction extends Action {

	public GlobalPropertyAction() {
		_pipelineStop = true;
	}

	@Override
	protected void execute(Context context, ExecutionContext execContext, ESBMessage message, boolean nextActionIsPipelineStop) throws Exception {
		String contentType = message.getHeader(HttpConstants.HTTP_HEADER_CONTENT_TYPE);
		message.clearHeaders();
		GlobalContext globalContext = context.getGlobalContext();
		switch (message.<String> getVariable(ESBConstants.HttpMethod)) {
		case "POST":
			if (contentType != null && contentType.startsWith(HttpConstants.HTTP_HEADER_CONTENT_TYPE_JSON)) {
				try (JsonReader jsonReader = JsonFactoryHelper.JSON_READER_FACTORY.createReader(message.getBodyAsReader(context))) {
					jsonReader.readObject().forEach((k, v) -> {
						switch (v.getValueType()) {
						case NULL:
							globalContext.removeProperty(k);
							break;
						case STRING:
							globalContext.putProperty(k, ((JsonString) v).getString());
							break;
						default:
							break;
						}
					});
				}
				message.putVariable(ESBConstants.HttpResponseCode, HttpServletResponse.SC_NO_CONTENT);
			} else {
				message.putVariable(ESBConstants.HttpResponseCode, HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE);
			}
			message.reset(BodyType.INVALID, null);
			break;
		case "COPY":
			DeployHelper.deployChangeSet(globalContext, globalContext.getFileSystem().init(globalContext));
			message.reset(BodyType.INVALID, null);
			message.putVariable(ESBConstants.HttpResponseCode, HttpServletResponse.SC_NO_CONTENT);
			break;
		default:
			message.reset(BodyType.INVALID, null);
			message.putVariable(ESBConstants.HttpResponseCode, HttpServletResponse.SC_METHOD_NOT_ALLOWED);
			break;
		}
	}

}
