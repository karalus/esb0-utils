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

import javax.xml.crypto.Data;
import javax.xml.crypto.OctetStreamData;
import javax.xml.crypto.dsig.CanonicalizationMethod;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.spec.C14NMethodParameterSpec;
import javax.xml.transform.dom.DOMResult;
import javax.xml.xquery.XQItem;

import org.apache.jcp.xml.dsig.internal.dom.DOMSubTreeData;

import com.artofarc.esb.action.Action;
import com.artofarc.esb.context.Context;
import com.artofarc.esb.context.ExecutionContext;
import com.artofarc.esb.message.BodyType;
import com.artofarc.esb.message.ESBMessage;

public class XMLCanonicalizationAction extends Action {

	private final CanonicalizationMethod _canonicalizationMethod;

	public XMLCanonicalizationAction() throws Exception {
		_pipelineStop = true;
		_canonicalizationMethod = XMLSignatureFactory.getInstance().newCanonicalizationMethod(CanonicalizationMethod.INCLUSIVE, (C14NMethodParameterSpec) null);
	}

	@Override
	protected void execute(Context context, ExecutionContext execContext, ESBMessage message, boolean nextActionIsPipelineStop) throws Exception {
		Data data;
		switch (message.getBodyType()) {
		case SOURCE:
			DOMResult domResult = new DOMResult();
			context.transformRaw(message.getBodyAsSource(context), domResult);
			data = new DOMSubTreeData(domResult.getNode(), false);
			break;
		case XQ_ITEM:
			XQItem xqItem = message.getBody();
			data = new DOMSubTreeData(xqItem.getNode(), false);
			break;
		case DOM:
			data = new DOMSubTreeData(message.getBody(), false);
			break;
		default:
			data = new OctetStreamData(message.getBodyAsInputStream(context));
			break;
		}
		OctetStreamData transformedData = (OctetStreamData) _canonicalizationMethod.transform(data, null);
		message.reset(BodyType.INPUT_STREAM, transformedData.getOctetStream());
	}

}
