/*
 * Copyright 2025 Andre Karalus
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
package com.artofarc.esb.utils.wss;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Map;

import javax.mail.Header;
import javax.mail.MessagingException;
import javax.mail.internet.InternetHeaders;
import javax.mail.internet.MimeBodyPart;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.UnsupportedCallbackException;

import org.apache.wss4j.common.ext.Attachment;
import org.apache.wss4j.common.ext.AttachmentRequestCallback;
import org.apache.wss4j.common.ext.AttachmentResultCallback;

import com.artofarc.util.IOUtils;

class AttachmentCallbackHandler implements CallbackHandler {

	private final Map<String, MimeBodyPart> _attachments;

	AttachmentCallbackHandler(Map<String, MimeBodyPart> attachments) {
		_attachments = attachments;
	}

	@Override
	public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
		for (int i = 0; i < callbacks.length; ++i) {
			try {
				if (callbacks[i] instanceof AttachmentRequestCallback) {
					handleAttachmentRequestCallback((AttachmentRequestCallback) callbacks[i]);
				} else if (callbacks[i] instanceof AttachmentResultCallback) {
					handleAttachmentResultCallback((AttachmentResultCallback) callbacks[i]);
				} else {
					throw new UnsupportedCallbackException(callbacks[i], "Unrecognized Callback");
				}
			} catch (MessagingException e) {
				throw new RuntimeException(e);
			}
		}
	}

	private static Attachment createAttachment(String cid, MimeBodyPart mimeBodyPart) throws MessagingException, IOException {
		Attachment attachment = new Attachment();
		attachment.setId(cid);
		for (Enumeration<Header> allHeaders = mimeBodyPart.getAllHeaders(); allHeaders.hasMoreElements();) {
			Header header = allHeaders.nextElement();
			attachment.addHeader(header.getName(), header.getValue());
		}
		attachment.setMimeType(mimeBodyPart.getContentType());
		attachment.setSourceStream(mimeBodyPart.getInputStream());
		return attachment;
	}

	private void handleAttachmentRequestCallback(AttachmentRequestCallback attachmentRequestCallback) throws MessagingException, IOException {
		ArrayList<Attachment> attachments = new ArrayList<>();
		if ("Attachments".equals(attachmentRequestCallback.getAttachmentId())) {
			for (Map.Entry<String, MimeBodyPart> entry : _attachments.entrySet()) {
				attachments.add(createAttachment(entry.getKey(), entry.getValue()));
			}
		} else {
			MimeBodyPart mimeBodyPart = _attachments.get(attachmentRequestCallback.getAttachmentId());
			if (mimeBodyPart == null) {
				throw new RuntimeException("wrong attachment requested: '" + attachmentRequestCallback.getAttachmentId() + "'");
			}
			attachments.add(createAttachment(attachmentRequestCallback.getAttachmentId(), mimeBodyPart));
		}
		attachmentRequestCallback.setAttachments(attachments);
	}

	private void handleAttachmentResultCallback(AttachmentResultCallback attachmentResultCallback) throws MessagingException, IOException {
		Attachment attachment = attachmentResultCallback.getAttachment();
		InternetHeaders headers = new InternetHeaders();
		for (Map.Entry<String, String> header : attachment.getHeaders().entrySet()) {
			headers.setHeader(header.getKey(), header.getValue());
		}
		_attachments.replace(attachment.getId(), new MimeBodyPart(headers, IOUtils.toByteArray(attachment.getSourceStream())));
	}

}
