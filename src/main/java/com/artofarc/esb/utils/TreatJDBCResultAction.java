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

import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.util.ListIterator;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonValue;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;
import javax.xml.bind.DatatypeConverter;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamResult;

import org.xml.sax.InputSource;

import com.artofarc.esb.action.Action;
import com.artofarc.esb.action.ExecutionException;
import com.artofarc.esb.context.Context;
import com.artofarc.esb.context.ExecutionContext;
import static com.artofarc.esb.http.HttpConstants.*;
import com.artofarc.esb.message.BodyType;
import com.artofarc.esb.message.ESBMessage;
import com.artofarc.util.IOUtils;
import static com.artofarc.util.JsonFactoryHelper.*;

/**
 * Replace a BLOB in a JSON JDBC result with a CLOB taking code page and MIME type into account.
 */
public class TreatJDBCResultAction extends Action {

	public TreatJDBCResultAction() {
		_pipelineStop = true;
	}

	@Override
	protected void execute(Context context, ExecutionContext execContext, ESBMessage message, boolean nextActionIsPipelineStop) throws Exception {
		String contentType = message.getHeader(HTTP_HEADER_CONTENT_TYPE);
		if (contentType != null && !contentType.startsWith(HTTP_HEADER_CONTENT_TYPE_JSON)) {
			throw new ExecutionException(this, "Unexpected Content-Type: " + contentType);
		}
		if (message.getBodyType() != BodyType.JSON_VALUE) {
			try (JsonReader jsonReader = JSON_READER_FACTORY.createReader(message.getBodyAsReader(context))) {
				message.reset(BodyType.JSON_VALUE, jsonReader.readValue());
			}
		}
		JsonObject result = message.getBody();
		int posMessage = -1, posEncoding = -1, posMediaType = -1;
		JsonArray headers = result.getJsonArray("header");
		for (ListIterator<JsonValue> iterHeaders = headers.listIterator(); iterHeaders.hasNext();) {
			JsonObject header = iterHeaders.next().asJsonObject();
			if (header.containsKey("MESSAGE")) {
				if (header.getString("MESSAGE").equals("BLOB")) {
					posMessage = iterHeaders.previousIndex();
				}
			} else if (header.containsKey("ENCODING")) {
				posEncoding = iterHeaders.previousIndex();
			} else if (header.containsKey("MEDIATYPE")) {
				posMediaType = iterHeaders.previousIndex();
			}
		}
		if (posMessage >= 0) {
			JsonArrayBuilder rows = JSON_BUILDER_FACTORY.createArrayBuilder();
			for (ListIterator<JsonValue> iterRows = result.getJsonArray("rows").listIterator(); iterRows.hasNext();) {
				JsonArray row = iterRows.next().asJsonArray();
				String encoding = row.getString(posEncoding, "UTF-8");
				String mediaType = posMediaType < 0 ? SOAP_1_1_CONTENT_TYPE : row.getString(posMediaType, SOAP_1_1_CONTENT_TYPE);
				byte[] rawMessage = DatatypeConverter.parseBase64Binary(row.getString(posMessage));
				JsonValue msg = JsonValue.NULL;
				if (mediaType.startsWith("multipart/")) {
					MimeMultipart mmp = new MimeMultipart(new ByteArrayDataSource(rawMessage, contentType));
					String start = getValueFromHttpHeader(contentType, HTTP_HEADER_CONTENT_TYPE_PARAMETER_START);
					for (int i = 0; i < mmp.getCount(); ++i) {
						MimeBodyPart bodyPart = (MimeBodyPart) mmp.getBodyPart(i);
						String cid = bodyPart.getContentID();
						if (start == null && i == 0 || start != null && start.equals(cid)) {
							String charset = getValueFromHttpHeader(bodyPart.getContentType(), HTTP_HEADER_CONTENT_TYPE_PARAMETER_CHARSET);
							msg = Json.createValue(new String(IOUtils.toByteArray(bodyPart.getInputStream()), charset != null ? charset : "UTF-8"));
							break;
						}
					}
				} else if (isFastInfoset(mediaType)) {
					StringWriter sw = new StringWriter();
					context.transformRaw(new SAXSource(context.getFastInfosetDeserializer(), new InputSource(new ByteArrayInputStream(rawMessage))), new StreamResult(sw));
					msg = Json.createValue(sw.toString());
				} else {
					msg = Json.createValue(new String(rawMessage, encoding));
				}
				rows.add(JSON_BUILDER_FACTORY.createArrayBuilder(row).set(posMessage, msg));
			}
			message.reset(BodyType.JSON_VALUE, JSON_BUILDER_FACTORY.createObjectBuilder().add("header", headers).add("rows", rows).build());
		}
	}

}
