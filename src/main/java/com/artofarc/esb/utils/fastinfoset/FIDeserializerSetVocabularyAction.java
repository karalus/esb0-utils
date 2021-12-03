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

import java.util.Collections;
import java.util.Map;
import java.util.Properties;

import com.artofarc.esb.action.ForwardAction;
import com.artofarc.esb.context.Context;
import com.artofarc.esb.context.ExecutionContext;
import com.artofarc.esb.message.ESBMessage;

public class FIDeserializerSetVocabularyAction extends ForwardAction {

	private final String schemaArtifactURI;

	public FIDeserializerSetVocabularyAction(ClassLoader classLoader, Properties properties) {
		schemaArtifactURI = properties.getProperty("schemaArtifactURI");
	}

	@SuppressWarnings("unchecked")
	@Override
	protected ExecutionContext prepare(Context context, ESBMessage message, boolean inPipeline) throws Exception {
		String vocabularyURI = resolve(message, FastInfosetVocabulary.VOCABULARY_URI, true);
		if (vocabularyURI == null) {
			vocabularyURI = schemaArtifactURI;
		}
		if (vocabularyURI != null) {
			@SuppressWarnings({ "rawtypes", "deprecation" })
			Map externalVocabularies = context.getFastInfosetDeserializer().getExternalVocabularies();
			if (externalVocabularies == null) {
				context.getFastInfosetDeserializer().setExternalVocabularies(Collections.singletonMap(vocabularyURI, getVocabulary(context, vocabularyURI)));
			} else if (!externalVocabularies.containsKey(vocabularyURI)) {
				externalVocabularies.put(vocabularyURI, getVocabulary(context, vocabularyURI));
			}
		}
		return super.prepare(context, message, inPipeline);
	}

	private static FastInfosetVocabulary getVocabulary(Context context, String vocabularyURI) throws Exception {
		FastInfosetVocabularyFactory resourceFactory = context.getGlobalContext().getResourceFactory(FastInfosetVocabularyFactory.class);
		return resourceFactory.getResource(vocabularyURI);
	}

}
