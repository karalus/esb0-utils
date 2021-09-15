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

import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;

import org.apache.wss4j.common.crypto.Crypto;
import org.apache.wss4j.common.crypto.CryptoFactory;
import org.apache.wss4j.common.ext.WSPasswordCallback;
import org.apache.wss4j.dom.WSConstants;
import org.apache.wss4j.dom.engine.WSSConfig;
import org.apache.wss4j.dom.engine.WSSecurityEngine;
import org.apache.wss4j.dom.engine.WSSecurityEngineResult;
import org.apache.wss4j.dom.handler.WSHandlerResult;
import org.w3c.dom.Document;

import com.artofarc.esb.action.Action;
import com.artofarc.esb.action.ExecutionException;
import com.artofarc.esb.context.Context;
import com.artofarc.esb.context.ExecutionContext;
import com.artofarc.esb.message.BodyType;
import com.artofarc.esb.message.ESBMessage;

public class WssVerifyAction extends Action implements CallbackHandler {

	private final Crypto crypto;
	private final WSSecurityEngine secEngine = new WSSecurityEngine();
	private final HashMap<String, String> users = new HashMap<>();

	public WssVerifyAction(ClassLoader classLoader, Properties properties) throws Exception {
		WSSConfig.init();
		Properties cryptoProps = new Properties();
		cryptoProps.load(classLoader.getResourceAsStream(properties.getProperty("cryptoPropFile", "crypto.properties")));
		crypto = CryptoFactory.getInstance(cryptoProps, classLoader, null);
		String user = properties.getProperty("privatekeyAlias", "${privatekeyAlias}");
		String password = cryptoProps.getProperty("org.apache.ws.security.crypto.merlin.keystore.private.password");
		users.put(user, password);
	}

	@Override
	public void handle(Callback[] callbacks) throws UnsupportedCallbackException {
		for (int i = 0; i < callbacks.length; i++) {
			if (callbacks[i] instanceof WSPasswordCallback) {
				WSPasswordCallback pc = (WSPasswordCallback) callbacks[i];
				if (users.containsKey(pc.getIdentifier())) {
					pc.setPassword(users.get(pc.getIdentifier()));
				}
			} else {
				throw new UnsupportedCallbackException(callbacks[i], "Unrecognized Callback");
			}
		}
	}

	@Override
	protected ExecutionContext prepare(Context context, ESBMessage message, boolean inPipeline) throws Exception {
		DOMResult domResult = new DOMResult();
		context.transformRaw(message.getBodyAsSource(context), domResult);
		WSHandlerResult result = secEngine.processSecurityHeader((Document) domResult.getNode(), null, this, crypto);
		List<WSSecurityEngineResult> results = result.getActionResults().get(WSConstants.SIGN);
		if (results.isEmpty()) {
			throw new ExecutionException(this, "Message not signed");
		} else {
			X509Certificate certificate = (X509Certificate) results.get(0).get(WSSecurityEngineResult.TAG_X509_CERTIFICATE);
			logger.info("Certificate DN: " + certificate.getSubjectX500Principal().getName());
		}
		message.reset(BodyType.DOM, domResult.getNode());
		return new ExecutionContext(domResult.getNode());
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
