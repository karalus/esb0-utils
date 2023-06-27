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

import java.util.Arrays;
import java.util.Optional;

import javax.json.JsonObject;
import javax.json.JsonValue;
import javax.json.bind.*;
import javax.json.bind.serializer.*;
import javax.json.stream.JsonGenerator;
import javax.management.*;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.TabularData;
import static javax.servlet.http.HttpServletResponse.*;

import com.artofarc.esb.action.Action;
import com.artofarc.esb.action.ExecutionException;
import com.artofarc.esb.context.Context;
import com.artofarc.esb.context.ExecutionContext;
import com.artofarc.esb.context.GlobalContext;
import com.artofarc.esb.http.HttpConstants;
import com.artofarc.esb.message.BodyType;
import com.artofarc.esb.message.ESBConstants;
import com.artofarc.esb.message.ESBMessage;
import com.artofarc.util.ReflectionUtils;

public class MBeanAction extends Action {

	private final static Jsonb JSONB = JsonbBuilder.create(new JsonbConfig().withSerializers(new JsonbSerializer<TabularData>() {

		@Override
		public void serialize(TabularData td, JsonGenerator generator, SerializationContext ctx) {
			generator.writeStartArray();
			td.values().forEach(cd -> ctx.serialize(cd, generator));
			generator.writeEnd();
		}
	}, new JsonbSerializer<CompositeData>() {

		@Override
		public void serialize(CompositeData cd, JsonGenerator generator, SerializationContext ctx) {
			generator.writeStartObject();
			cd.getCompositeType().keySet().forEach(key -> ctx.serialize(key, cd.get(key), generator));
			generator.writeEnd();
		}
	}, new JsonbSerializer<ObjectName>() {

		@Override
		public void serialize(ObjectName objectName, JsonGenerator generator, SerializationContext ctx) {
			generator.write(objectName.getCanonicalName());
		}
	}));

	static {
		GlobalContext.logVersion("JSON-B", Jsonb.class.getPackage(), JSONB.getClass());
	}

	public MBeanAction() {
		_pipelineStop = true;
	}

	@Override
	protected void execute(Context context, ExecutionContext execContext, ESBMessage message, boolean nextActionIsPipelineStop) throws Exception {
		message.clearHeaders();
		if (HttpConstants.isNotJSON(message.getContentType())) {
			throw httpError(message, SC_UNSUPPORTED_MEDIA_TYPE, message.getContentType(), null);
		}
		String name = message.getVariable("objectName");
		ObjectName objectName = name != null ? new ObjectName(name) : null;
		MBeanServer mbeanServer = context.getGlobalContext().getPlatformMBeanServer();
		String attribute = message.getVariable("attribute");
		String method = message.getVariable(ESBConstants.HttpMethod, "null");
		switch (method) {
		case "GET":
			Object object;
			if (objectName != null) {
				if (attribute != null) {
					if (attribute.equals("*")) {
						String[] attributeNames = Arrays.stream(getMBeanInfo(message, mbeanServer, objectName).getAttributes()).map(a -> a.getName()).toArray(String[]::new);
						object = mbeanServer.getAttributes(objectName, attributeNames);
					} else {
						object = mbeanServer.getAttribute(objectName, attribute);
					}
				} else {
					object = mbeanServer.queryNames(objectName, message.getVariable("query"));
				}
			} else {
				object = mbeanServer.getDomains();
			}
			returnResult(message, object, "");
			break;
		case "PUT":
			if (objectName == null) {
				throw httpError(message, SC_BAD_REQUEST, "objectName not set", null);
			}
			Optional<MBeanAttributeInfo> optional = Arrays.stream(getMBeanInfo(message, mbeanServer, objectName).getAttributes()).filter(a -> a.getName().equals(attribute)).findAny();
			if (!optional.isPresent()) {
				throw httpError(message, SC_BAD_REQUEST, name + " has no attribute " + attribute, null);
			}
			Object value = JSONB.fromJson(message.getBodyAsString(context), ReflectionUtils.classForName(optional.get().getType()));
			mbeanServer.setAttribute(objectName, new Attribute(attribute, value));
			returnResult(message, null, "void");
			break;
		case "POST":
			if (objectName == null) {
				throw httpError(message, SC_BAD_REQUEST, "objectName not set", null);
			}
			JsonObject jsonObject = message.getBodyAsJsonValue(context);
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
			for (MBeanOperationInfo operationInfo : getMBeanInfo(message, mbeanServer, objectName).getOperations()) {
				MBeanParameterInfo[] signature = operationInfo.getSignature();
				if (signature.length == paramCount && operationInfo.getName().equals(operation)) {
					try {
						for (int i = 0; i < paramCount; ++i) {
							params[i] = JSONB.fromJson(values[i], ReflectionUtils.classForName(types[i] = signature[i].getType()));
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
		default:
			throw httpError(message, SC_METHOD_NOT_ALLOWED, method, null);
		}
	}

	private MBeanInfo getMBeanInfo(ESBMessage message, MBeanServer mbeanServer, ObjectName objectName) throws Exception {
		try {
			return mbeanServer.getMBeanInfo(objectName);
		} catch (InstanceNotFoundException e) {
			throw httpError(message, SC_NOT_FOUND, objectName.getCanonicalName(), null);
		}
	}

	private ExecutionException httpError(ESBMessage esbMessage, int statusCode, String message, Throwable cause) {
		esbMessage.putVariable(ESBConstants.HttpResponseCode, statusCode);
		return new ExecutionException(this, message, cause);
	}

	private void returnResult(ESBMessage message, Object result, String type) {
		if (type.equals("void")) {
			message.putVariable(ESBConstants.HttpResponseCode, SC_NO_CONTENT);
			message.reset(BodyType.INVALID, null);
		} else {
			message.putHeader(HttpConstants.HTTP_HEADER_CONTENT_TYPE, HttpConstants.HTTP_HEADER_CONTENT_TYPE_JSON);
			message.reset(BodyType.STRING, JSONB.toJson(result));
		}
	}

}
