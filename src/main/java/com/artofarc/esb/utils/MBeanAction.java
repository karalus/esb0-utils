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
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Optional;

import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.json.JsonValue;
import javax.json.bind.*;
import javax.json.bind.adapter.JsonbAdapter;
import javax.management.*;
import javax.management.openmbean.CompositeData;
import static javax.servlet.http.HttpServletResponse.*;

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
			builder.add("$type", cd.getCompositeType().getTypeName());
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
			throw httpError(message, SC_BAD_REQUEST, "objectName not set", null);
		}
		ObjectName objectName = new ObjectName(name);
		MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();
		MBeanInfo mBeanInfo;
		try {
			mBeanInfo = mbeanServer.getMBeanInfo(objectName);
		} catch (InstanceNotFoundException e) {
			throw httpError(message, SC_NOT_FOUND, name, null);
		}
		String attribute = message.getVariable("attribute");
		String method = message.getVariable(ESBConstants.HttpMethod, "null");
		switch (method) {
		case "GET":
			Object object;
			if (attribute != null) {
				object = mbeanServer.getAttribute(objectName, attribute);
			} else {
				String[] attributeNames = Arrays.stream(mBeanInfo.getAttributes()).map(a -> a.getName()).toArray(String[]::new);
				object = mbeanServer.getAttributes(objectName, attributeNames);
			}
			returnResult(message, object, "");
			break;
		case "PUT":
			Optional<MBeanAttributeInfo> optional = Arrays.stream(mBeanInfo.getAttributes()).filter(a -> a.getName().equals(attribute)).findAny();
			if (!optional.isPresent()) {
				throw httpError(message, SC_BAD_REQUEST, name + " has no attribute " + attribute, null);
			}
			Object value = JSONB.fromJson(message.getBodyAsString(context), classForName(optional.get().getType()));
			mbeanServer.setAttribute(objectName, new Attribute(attribute, value));
			returnResult(message, null, "void");
			break;
		case "POST":
			try (JsonReader jsonReader = JSON_READER_FACTORY.createReader(message.getBodyAsReader(context))) {
				JsonObject jsonObject = jsonReader.readObject();
				String operation = jsonObject.getString("operation", null);
				int paramCount = jsonObject.size() - 1;
				String values[] = new String[paramCount];
				for (int i = 0; i < paramCount; ++i) {
					JsonValue jsonValue = jsonObject.get("p" + i);
					if (jsonValue == null) {
						throw httpError(message, SC_BAD_REQUEST, "missing parameter p" + i, null);
					}
					values[i] = jsonValue.toString();
				}
				Object params[] = new Object[paramCount];
				String types[] = new String[paramCount];
				JsonbException lastException = null;
				for (MBeanOperationInfo operationInfo : mBeanInfo.getOperations()) {
					MBeanParameterInfo[] signature = operationInfo.getSignature();
					if (signature.length == paramCount && operationInfo.getName().equals(operation)) {
						try {
							for (int i = 0; i < paramCount; ++i) {
								params[i] = JSONB.fromJson(values[i], classForName(types[i] = signature[i].getType()));
							}
							Object result = mbeanServer.invoke(objectName, operation, params, types);
							returnResult(message, result, operationInfo.getReturnType());
							return;
						} catch (JsonbException e) {
							lastException = e;
						}
					}
				}
				if (lastException != null) {
					throw httpError(message, SC_BAD_REQUEST, "parameters do not match", lastException);
				} else {
					throw httpError(message, SC_NOT_FOUND, name + " has no operation " + operation, null);
				}
			}
		default:
			throw httpError(message, SC_METHOD_NOT_ALLOWED, method, null);
		}
	}

	private static Class<?> classForName(String name) throws Exception {
//		return com.artofarc.util.ReflectionUtils.classForName(name);
		try {
			Method method = Class.class.getDeclaredMethod("getPrimitiveClass", String.class);
			method.setAccessible(true);
			return (Class<?>) method.invoke(null, name);
		} catch (ReflectiveOperationException e) {
		}
		return Class.forName(name);
	}

	private ExecutionException httpError(ESBMessage esbMessage, int statusCode, String message, Throwable cause) {
		esbMessage.putVariable(ESBConstants.HttpResponseCode, statusCode);
		return new ExecutionException(this, message, cause);
	}

	private void returnResult(ESBMessage message, Object result, String type) throws Exception {
		if (type.endsWith("oid")) {
			message.putVariable(ESBConstants.HttpResponseCode, SC_NO_CONTENT);
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
