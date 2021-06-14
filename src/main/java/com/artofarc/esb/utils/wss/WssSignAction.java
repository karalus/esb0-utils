package com.artofarc.esb.utils.wss;

import java.util.ArrayList;
import java.util.Properties;

import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;

import org.apache.wss4j.common.WSEncryptionPart;
import org.apache.wss4j.common.crypto.Crypto;
import org.apache.wss4j.common.crypto.CryptoFactory;
import org.apache.wss4j.common.ext.WSSecurityException;
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

	public WssSignAction(Properties properties) throws WSSecurityException {
		user = properties.getProperty("com.artofarc.esb.utils.wss.privatekey.alias");
		password = properties.getProperty("org.apache.ws.security.crypto.merlin.keystore.private.password");
		String[] signatureParts = properties.getProperty("com.artofarc.esb.utils.wss.signatureparts").split(";");
		for (String signaturePart : signatureParts) {
			String[] split = signaturePart.split(",");
			parts.add(new WSEncryptionPart(split[0], split[1], ""));
		}
		WSSConfig.init();
		crypto = CryptoFactory.getInstance(properties);
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
