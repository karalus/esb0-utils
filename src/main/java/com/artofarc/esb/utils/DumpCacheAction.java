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

import javax.json.stream.JsonGenerator;
import javax.servlet.http.HttpServletResponse;

import com.artofarc.esb.action.Action;
import com.artofarc.esb.context.Context;
import com.artofarc.esb.context.ExecutionContext;
import com.artofarc.esb.http.HttpConstants;
import com.artofarc.esb.message.BodyType;
import com.artofarc.esb.message.ESBConstants;
import com.artofarc.esb.message.ESBMessage;
import com.artofarc.esb.resource.LRUCacheWithExpirationFactory;
import com.artofarc.util.JsonValueGenerator;
import com.artofarc.util.ReflectionUtils;

public class DumpCacheAction extends Action {

	@Override
	protected ExecutionContext prepare(Context context, ESBMessage message, boolean inPipeline) throws Exception {
		message.clearHeaders();
		message.putHeader(HttpConstants.HTTP_HEADER_CONTENT_TYPE, HttpConstants.HTTP_HEADER_CONTENT_TYPE_JSON);
		String cacheName = message.getVariable("cacheName");
		@SuppressWarnings("unchecked")
		LRUCacheWithExpirationFactory<Object, Object[]> factory = context.getGlobalContext().getResourceFactory(LRUCacheWithExpirationFactory.class);
		if (factory.getResourceDescriptors().contains(cacheName)) {
			String method = message.getVariable(ESBConstants.HttpMethod);
			switch (method) {
			case "GET":
				return new ExecutionContext(factory.getResource(cacheName));
			case "DELETE":
				factory.removeResource(cacheName).clear();
				return new ExecutionContext("cache removed: " + cacheName);
			case "PURGE":
				factory.getResource(cacheName).clear();
				return new ExecutionContext("cache purged: " + cacheName);
			default:
				message.putVariable(ESBConstants.HttpResponseCode, HttpServletResponse.SC_METHOD_NOT_ALLOWED);
				return new ExecutionContext("method not allowed: " + method);
			}
		} else {
			message.putVariable(ESBConstants.HttpResponseCode, HttpServletResponse.SC_NOT_FOUND);
			return new ExecutionContext("cache not found: " + cacheName);
		}
	}

	@Override
	protected void execute(Context context, ExecutionContext execContext, ESBMessage message, boolean nextActionIsPipelineStop) throws Exception {
		if (execContext.getResource() instanceof String) {
			String msg = execContext.getResource();
			if (message.isSink()) {
				message.getBodyAsJsonGenerator().write(msg).close();
			} else {
				message.reset(BodyType.STRING, '"' + msg + '"');
			}
		} else {
			LRUCacheWithExpirationFactory<Object, Object[]>.Cache cache = execContext.getResource();
			if (message.isSink()) {
				try (JsonGenerator jsonGenerator = message.getBodyAsJsonGenerator()) {
					writeJson(jsonGenerator, cache);
				}
			} else {
				JsonValueGenerator jsonValueGenerator = new JsonValueGenerator();
				writeJson(jsonValueGenerator, cache);
				message.reset(BodyType.JSON_VALUE, jsonValueGenerator.getJsonValue());
			}
		}
	}

	public static void writeJson(JsonGenerator jsonGenerator, LRUCacheWithExpirationFactory<Object, Object[]>.Cache cache) {
		jsonGenerator.writeStartObject();
		for (Object expiration : cache.getExpirations()) {
			// TODO: Use better API
			String key = ReflectionUtils.getField(expiration, "_key");
			Object[] values = cache.get(key);
			if (values != null) {
				jsonGenerator.writeKey(key);
				if (values.length == 1) {
					writeJson(jsonGenerator, values[0]);
				} else {
					jsonGenerator.writeStartArray();
					for (Object value : values) {
						writeJson(jsonGenerator, value);
					}
					jsonGenerator.writeEnd();
				}
			}
		}
		jsonGenerator.writeEnd();
	}

	public static void writeJson(JsonGenerator jsonGenerator, Object value) {
		if (value instanceof Number) {
			jsonGenerator.write(((Number) value).longValue());
		} else {
			jsonGenerator.write(value.toString());
		}
	}

}
