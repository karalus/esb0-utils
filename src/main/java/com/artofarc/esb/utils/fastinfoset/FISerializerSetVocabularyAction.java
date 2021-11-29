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
package com.artofarc.esb.utils.fastinfoset;

import java.util.Properties;

import javax.xml.transform.sax.SAXSource;
import javax.xml.xquery.XQItem;

import org.xml.sax.XMLReader;

import com.artofarc.esb.action.ExecutionException;
import com.artofarc.esb.action.SAXAction;
import com.artofarc.esb.context.Context;
import com.artofarc.esb.context.ExecutionContext;
import com.artofarc.esb.message.ESBMessage;
import com.artofarc.esb.resource.SchemaAwareFISerializerFactory;
import com.artofarc.util.SchemaAwareFastInfosetSerializer;
import com.artofarc.util.XMLFilterBase;

public class FISerializerSetVocabularyAction extends SAXAction {

	private final String schemaArtifactURI;
	private final Boolean ignoreWhitespace;

	public FISerializerSetVocabularyAction(ClassLoader classLoader, Properties properties) {
		schemaArtifactURI = properties.getProperty("schemaArtifactURI");
		if (schemaArtifactURI == null) {
			throw new IllegalArgumentException("Property schemaArtifactURI not found");
		}
		ignoreWhitespace = Boolean.valueOf(properties.getProperty("ignoreWhitespace"));
	}

	@Override
	protected ExecutionContext prepare(Context context, ESBMessage message, boolean inPipeline) throws Exception {
		if (message.getSchema() == null) {
			throw new ExecutionException(this, "Message has no schema assigned");
		}
		FastInfosetVocabularyFactory resourceFactory = context.getGlobalContext().getResourceFactory(FastInfosetVocabularyFactory.class);
		FastInfosetVocabulary vocabulary = resourceFactory.getResource(schemaArtifactURI);
		SchemaAwareFastInfosetSerializer serializer = context.getResourceFactory(SchemaAwareFISerializerFactory.class).getResource(message.getSchema(), ignoreWhitespace);
		serializer.getFastInfosetSerializer().setExternalVocabulary(vocabulary);
		return super.prepare(context, message, inPipeline);
	}

	@Override
	protected SAXSource createSAXSource(Context context, ESBMessage message, XQItem item) throws Exception {
		return new SAXSource(context.createNamespaceBeautifier(new SAXSource(new XQJFilter(item), null)), null);
	}

	@Override
	protected XMLFilterBase createXMLFilter(Context context, ESBMessage message, XMLReader parent) throws Exception {
		return context.createNamespaceBeautifier(new SAXSource(parent, null));
	}

}
