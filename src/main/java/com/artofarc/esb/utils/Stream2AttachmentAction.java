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

import java.net.URLDecoder;

import com.artofarc.esb.action.Action;
import com.artofarc.esb.action.ExecutionException;
import com.artofarc.esb.context.Context;
import com.artofarc.esb.context.ExecutionContext;
import com.artofarc.esb.http.HttpConstants;
import com.artofarc.esb.message.BodyType;
import com.artofarc.esb.message.ESBMessage;

public class Stream2AttachmentAction extends Action {

	public Stream2AttachmentAction() {
		_pipelineStop = true;
	}

	@Override
	protected void execute(Context context, ExecutionContext execContext, ESBMessage message, boolean nextActionIsPipelineStop) throws Exception {
		if (!message.getBodyType().hasCharset()) {
			throw new ExecutionException(this, "Expected binary, got " + message.getBodyType());
		}
		String contentType = message.getHeader(HttpConstants.HTTP_HEADER_CONTENT_TYPE);
		String contentDisposition = message.getHeader(HttpConstants.HTTP_HEADER_CONTENT_DISPOSITION);
		// https://www.rfc-editor.org/rfc/rfc5987
		String filename = HttpConstants.getValueFromHttpHeader(contentDisposition, "filename*=");
		if (filename != null) {
			int i = filename.indexOf('\'');
			String enc = filename.substring(0, i);
			// skip locale
			i = filename.indexOf('\'', i + 1);
			filename = URLDecoder.decode(filename.substring(i + 1), enc);
		} else {
			filename = HttpConstants.getValueFromHttpHeader(contentDisposition, "filename=");
			if (filename != null) {
				filename = filename.substring(1, filename.length() - 1);
			}
		}
		message.addAttachment(null, contentType, message.getBodyAsByteArray(context), filename);
		message.clearHeaders();
		message.putHeader(HttpConstants.HTTP_HEADER_CONTENT_TYPE, HttpConstants.HTTP_HEADER_CONTENT_TYPE_JSON);
		message.reset(BodyType.STRING, "{\"contentType\":\"" + contentType + "\",\"filename\":\"" + filename + "\"}");
	}

}
