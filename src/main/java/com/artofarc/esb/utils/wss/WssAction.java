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

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Properties;

import javax.xml.transform.dom.DOMSource;

import org.apache.wss4j.common.crypto.Crypto;
import org.apache.wss4j.common.crypto.CryptoFactory;
import org.apache.wss4j.dom.engine.WSSConfig;
import org.w3c.dom.Document;

import com.artofarc.esb.action.Action;
import com.artofarc.esb.context.Context;
import com.artofarc.esb.context.ExecutionContext;
import com.artofarc.esb.message.BodyType;
import com.artofarc.esb.message.ESBMessage;

public abstract class WssAction extends Action {

	protected final Crypto crypto;
	protected final String user, password;

	public WssAction(ClassLoader classLoader, Properties properties) throws Exception {
		String cryptoPropFile = properties.getProperty("cryptoPropFile", "crypto.properties");
		InputStream is = classLoader.getResourceAsStream(cryptoPropFile);
		if (is == null) {
			throw new FileNotFoundException("cryptoPropFile not found: " + cryptoPropFile);
		}
		WSSConfig.init();
		Properties cryptoProps = new Properties();
		cryptoProps.load(is);
		crypto = CryptoFactory.getInstance(cryptoProps, classLoader, null);
		user = properties.getProperty("privatekeyAlias", "${privatekeyAlias}");
		password = cryptoProps.getProperty("org.apache.ws.security.crypto.merlin.keystore.private.password");
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
