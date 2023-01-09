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
package com.artofarc.esb.utils.wss;

import java.util.ArrayList;
import java.util.Properties;

import javax.xml.namespace.QName;
import javax.xml.transform.dom.DOMResult;

import org.apache.wss4j.common.WSEncryptionPart;
import org.apache.wss4j.dom.WSConstants;
import org.apache.wss4j.dom.message.WSSecHeader;
import org.apache.wss4j.dom.message.WSSecSignature;
import org.w3c.dom.Document;

import com.artofarc.esb.context.Context;
import com.artofarc.esb.context.ExecutionContext;
import com.artofarc.esb.message.BodyType;
import com.artofarc.esb.message.ESBMessage;

public class WssSignAction extends WssAction {

	private final ArrayList<WSEncryptionPart> parts = new ArrayList<>();

	public WssSignAction(ClassLoader classLoader, Properties properties) throws Exception {
		super(classLoader, properties);
		String[] signatureParts = properties.getProperty("signatureParts").split(",");
		for (String signaturePart : signatureParts) {
			QName qName = QName.valueOf(signaturePart);
			parts.add(new WSEncryptionPart(qName.getLocalPart(), qName.getNamespaceURI(), ""));
		}
	}

	@Override
	protected ExecutionContext prepare(Context context, ESBMessage message, boolean inPipeline) throws Exception {
		DOMResult domResult = new DOMResult();
		context.transformRaw(message.getBodyAsSource(context), domResult);
		WSSecHeader secHeader = new WSSecHeader((Document) domResult.getNode());
		secHeader.insertSecurityHeader();
		WSSecSignature builder = new WSSecSignature(secHeader);
		builder.setUserInfo((String) eval(user, context, message), password);
		builder.setKeyIdentifierType(WSConstants.BST_DIRECT_REFERENCE);
		builder.setDigestAlgo(WSConstants.SHA256);
		builder.setAddInclusivePrefixes(false);
		builder.getParts().addAll(parts);
		Document document = builder.build(crypto);
		message.reset(BodyType.DOM, document);
		return new ExecutionContext(document);
	}

}
