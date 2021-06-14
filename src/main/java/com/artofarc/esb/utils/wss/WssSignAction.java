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

import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;

import org.apache.wss4j.common.WSEncryptionPart;
import org.apache.wss4j.common.crypto.Crypto;
import org.apache.wss4j.common.crypto.CryptoFactory;
import org.apache.wss4j.dom.WSConstants;
import org.apache.wss4j.dom.engine.WSSConfig;
import org.apache.wss4j.dom.message.WSSecHeader;
import org.apache.wss4j.dom.message.WSSecSignature;
import org.w3c.dom.Document;

import com.artofarc.esb.action.Action;
import com.artofarc.esb.context.Context;
import com.artofarc.esb.context.ExecutionContext;
import com.artofarc.esb.message.BodyType;
import com.artofarc.esb.message.ESBMessage;

public class WssSignAction extends Action {

	private final String user, password;
	private final ArrayList<WSEncryptionPart> parts = new ArrayList<>();
	private final Crypto crypto;

	public WssSignAction(ClassLoader classLoader, Properties properties) throws Exception {
		WSSConfig.init();
		Properties cryptoProps = new Properties();
		cryptoProps.load(classLoader.getResourceAsStream(properties.getProperty("cryptoPropFile", "crypto.properties")));
		crypto = CryptoFactory.getInstance(cryptoProps, classLoader, null);
		user = properties.getProperty("privatekeyAlias", "${privatekeyAlias}");
		password = cryptoProps.getProperty("org.apache.ws.security.crypto.merlin.keystore.private.password");
		String[] signatureParts = properties.getProperty("signatureParts").split(";");
		for (String signaturePart : signatureParts) {
			String[] split = signaturePart.split(",");
			parts.add(new WSEncryptionPart(split[0], split[1], ""));
		}
	}

	@Override
	protected ExecutionContext prepare(Context context, ESBMessage message, boolean inPipeline) throws Exception {
		DOMResult domResult = new DOMResult();
		context.transformRaw(message.getBodyAsSource(context), domResult);
		WSSecHeader secHeader = new WSSecHeader((Document) domResult.getNode());
		secHeader.insertSecurityHeader();
		WSSecSignature builder = new WSSecSignature(secHeader);
		builder.setUserInfo((String) bindVariable(user, context, message), password);
		builder.setKeyIdentifierType(WSConstants.BST_DIRECT_REFERENCE);
		builder.setDigestAlgo(WSConstants.SHA256);
		builder.setAddInclusivePrefixes(false);
		builder.getParts().addAll(parts);
		Document document = builder.build(crypto);
		message.reset(BodyType.DOM, document);
		return new ExecutionContext(document);
	}

	@Override
	protected void execute(Context context, ExecutionContext execContext, ESBMessage message, boolean nextActionIsPipelineStop) throws Exception {
		if (nextActionIsPipelineStop) {
			Document document = execContext.getResource();
			if (message.isSink()) {
				context.transformRaw(new DOMSource(document), message.getBodyAsSinkResult(context));
			} else {
				message.reset(BodyType.DOM, document);
			}
		}
	}

}
