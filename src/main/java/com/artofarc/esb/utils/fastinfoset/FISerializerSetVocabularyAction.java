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

import com.artofarc.esb.action.ExecutionException;
import com.artofarc.esb.action.ForwardAction;
import com.artofarc.esb.context.Context;
import com.artofarc.esb.context.ExecutionContext;
import com.artofarc.esb.message.ESBMessage;
import com.artofarc.esb.resource.SchemaAwareFISerializerFactory;
import com.artofarc.util.SchemaAwareFastInfosetSerializer;

public class FISerializerSetVocabularyAction extends ForwardAction {

	private final String schemaArtifactURI;

	public FISerializerSetVocabularyAction(ClassLoader classLoader, Properties properties) {
		schemaArtifactURI = properties.getProperty("schemaArtifactURI");
		if (schemaArtifactURI == null) {
			throw new IllegalArgumentException("Property schemaArtifactURI not found");
		}
	}

	@Override
	protected ExecutionContext prepare(Context context, ESBMessage message, boolean inPipeline) throws Exception {
		if (message.getSchema() == null) {
			throw new ExecutionException(this, "Message has no schema assigned");
		}
		FastInfosetVocabularyFactory resourceFactory = context.getGlobalContext().getResourceFactory(FastInfosetVocabularyFactory.class);
		FastInfosetVocabulary vocabulary = resourceFactory.getResource(schemaArtifactURI);
		SchemaAwareFastInfosetSerializer serializer = context.getResourceFactory(SchemaAwareFISerializerFactory.class).getResource(message.getSchema());
		serializer.getFastInfosetSerializer().setExternalVocabulary(vocabulary);
		return super.prepare(context, message, inPipeline);
	}

}
