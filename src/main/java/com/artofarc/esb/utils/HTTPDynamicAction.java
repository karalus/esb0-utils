/*
 * Copyright 2024 Andre Karalus
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

import java.util.ArrayList;
import java.util.Set;

import com.artofarc.esb.action.Action;
import com.artofarc.esb.action.ExecutionException;
import com.artofarc.esb.action.HttpAction;
import com.artofarc.esb.action.HttpInboundAction;
import com.artofarc.esb.action.HttpOutboundAction;
import com.artofarc.esb.context.Context;
import com.artofarc.esb.context.ExecutionContext;
import com.artofarc.esb.http.HttpEndpoint;
import com.artofarc.esb.http.HttpUrl;
import static com.artofarc.esb.message.ESBConstants.*;
import com.artofarc.esb.message.ESBMessage;

public class HTTPDynamicAction extends Action {

	private static HttpEndpoint createHttpEndpoint(HttpEndpoint httpEndpoint, String path, String basicAuthCredential) throws Exception {
		ArrayList<HttpUrl> httpUrls = new ArrayList<>();
		for (HttpUrl httpUrl : httpEndpoint.getHttpUrls()) {
			httpUrls.add(new HttpUrl(httpUrl.getBaseUrl() + path, httpUrl.getWeight(), httpUrl.isActive()));
		}
		// TODO: Better constructor available in recent esb0
		String username = null, password = null;
		if (basicAuthCredential != null) {
			int i = basicAuthCredential.indexOf(':');
			username = basicAuthCredential.substring(0, i);
			password = basicAuthCredential.substring(i + 1);
		}
		return new HttpEndpoint(null, httpUrls, httpEndpoint.isMultiThreaded(), username, password, httpEndpoint.getConnectTimeout(), httpEndpoint.getRetries(),
				httpEndpoint.getCheckAliveInterval(), httpEndpoint.getHttpCheckAlive(), System.currentTimeMillis(), httpEndpoint.getProxy(), httpEndpoint.getSSLContext(),
				httpEndpoint.getVersion());
	}

	@Override
	protected ExecutionContext prepare(Context context, ESBMessage message, boolean inPipeline) throws Exception {
		String httpEndpointName = message.getVariable("httpEndpointName");
		HttpEndpoint httpEndpoint = context.getGlobalContext().getHttpEndpointRegistry().getHttpEndpoints().get(httpEndpointName);
		if (httpEndpoint == null) {
			throw new ExecutionException(this, "httpEndpoint not registered " + httpEndpointName);
		}
		message.clearHeadersExcept(Set.of("content-type", "soapaction"));
		message.putVariable(appendHttpUrlPath, "");
		message.putVariable(QueryString, null);
		String path = message.getVariable("path");
		HttpEndpoint checkAliveHttpEndpoint = path != null
				? createHttpEndpoint(httpEndpoint, path, httpEndpoint.getBasicAuthCredential() != null ? (String) eval(httpEndpoint.getBasicAuthCredential(), context, message) : null)
				: httpEndpoint;
		Action newAction;
		if (httpEndpoint.getVersion() != null) {
			newAction = new HttpAction(checkAliveHttpEndpoint, 60000, null, null, null);
			context.getExecutionStack().push(newAction);
		} else {
			newAction = new HttpOutboundAction(checkAliveHttpEndpoint, 60000, null, null, null);
			context.getExecutionStack().push(newAction);
			newAction = newAction.setNextAction(new HttpInboundAction());
		}
		if (_nextAction != null) {
			newAction.setNextAction(_nextAction);
		}
		return super.prepare(context, message, inPipeline);
	}

	@Override
	protected Action nextAction(ExecutionContext execContext) {
		return null;
	}

}
