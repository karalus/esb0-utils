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

import java.io.IOException;
import java.net.HttpURLConnection;

import javax.servlet.http.HttpServletResponse;

import com.artofarc.esb.action.Action;
import com.artofarc.esb.context.Context;
import com.artofarc.esb.context.ExecutionContext;
import com.artofarc.esb.http.HttpCheckAlive;
import com.artofarc.esb.http.HttpEndpoint;
import com.artofarc.esb.message.BodyType;
import com.artofarc.esb.message.ESBConstants;
import com.artofarc.esb.message.ESBMessage;

public class HTTPConnectionTestAction extends Action {

	public HTTPConnectionTestAction() {
		_pipelineStop = true;
	}

	@Override
	protected void execute(Context context, ExecutionContext execContext, ESBMessage message, boolean nextActionIsPipelineStop) throws Exception {
		String httpEndpointName = message.getVariable("HttpEndpoint");
		HttpEndpoint httpEndpoint = context.getGlobalContext().getHttpEndpointRegistry().getHttpEndpoints().get(httpEndpointName);
		if (httpEndpoint != null) {
			HttpCheckAlive httpCheckAlive = httpEndpoint.getHttpCheckAlive() != null ? httpEndpoint.getHttpCheckAlive() : new HttpCheckAlive();
			IOException lastException = null;
			for (int i = 0; i < httpEndpoint.getHttpUrls().size(); ++i) {
				try {
					HttpURLConnection conn = httpCheckAlive.connect(httpEndpoint, i);
					int responseCode = conn.getResponseCode();
					if (httpCheckAlive.isAlive(conn, responseCode)) {
						message.putVariable(ESBConstants.HttpResponseCode, HttpServletResponse.SC_NO_CONTENT);
						return;
					}
					lastException = new HttpCheckAlive.ConnectException(httpEndpoint.getHttpUrls().get(i).getUrlStr() + " is not alive. Response code " + responseCode);
				} catch (IOException e) {
					lastException = e;
				}
			}
			message.putVariable(ESBConstants.HttpResponseCode, HttpServletResponse.SC_BAD_GATEWAY);
			message.reset(BodyType.EXCEPTION, lastException);
		} else {
			message.putVariable(ESBConstants.HttpResponseCode, HttpServletResponse.SC_NOT_FOUND);
		}
	}

}
