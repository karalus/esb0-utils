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

import java.io.FileNotFoundException;
import java.lang.reflect.Constructor;
import java.util.Collections;
import java.util.Iterator;

import org.jvnet.fastinfoset.Vocabulary;

import com.artofarc.esb.artifact.SchemaArtifact;
import com.artofarc.esb.context.GlobalContext;
import com.artofarc.esb.resource.ResourceFactory;
import com.artofarc.util.ReflectionUtils;
import com.sun.xml.analysis.frequency.SchemaProcessor;
import com.sun.xml.xsom.XSSchema;
import com.sun.xml.xsom.XSSchemaSet;
import com.sun.xml.xsom.visitor.XSVisitor;

public class FastInfosetVocabularyFactory extends ResourceFactory<FastInfosetVocabulary, String, Void, Exception> {

	private final GlobalContext _context;

	public FastInfosetVocabularyFactory(GlobalContext context) {
		_context = context;
	}

	@Override
	protected FastInfosetVocabulary createResource(String uri, Void param) throws Exception {
		SchemaArtifact schemaArtifact = _context.getFileSystem().getArtifact(uri);
		if (schemaArtifact == null) {
			throw new FileNotFoundException(uri);
		}
		return new FastInfosetVocabulary(schemaArtifact.getURI(), createVocabulary(schemaArtifact.getXSSchemaSet()));
	}

	@SuppressWarnings("unchecked")
	public static Vocabulary createVocabulary(XSSchemaSet schemaSet) throws ReflectiveOperationException {
		SchemaProcessor schemaProcessor = new SchemaProcessor(Collections.emptyList(), true, false);
		Constructor<XSVisitor> constructor = ReflectionUtils.findConstructor(schemaProcessor.getClass().getName() + "$InternalSchemaProcessor", schemaProcessor.getClass());
		constructor.setAccessible(true);
		XSVisitor visitor = constructor.newInstance(schemaProcessor);
		for (Iterator<XSSchema> iter = schemaSet.iterateSchema(); iter.hasNext();) {
			iter.next().visit(visitor);
		}
		Vocabulary v = new Vocabulary();
		v.prefixes.addAll(ReflectionUtils.getField(schemaProcessor, "prefixes"));
		v.namespaceNames.addAll(ReflectionUtils.getField(schemaProcessor, "namespaces"));
		v.localNames.addAll(ReflectionUtils.getField(schemaProcessor, "localNames"));
		v.elements.addAll(ReflectionUtils.getField(schemaProcessor, "elements"));
		v.attributes.addAll(ReflectionUtils.getField(schemaProcessor, "attributes"));
		v.characterContentChunks.addAll(ReflectionUtils.getField(schemaProcessor, "textContentValues"));
		v.attributeValues.addAll(ReflectionUtils.getField(schemaProcessor, "attributeValues"));
//		Vocabulary vocabulary = new com.sun.xml.analysis.frequency.FrequencyHandler(schemaProcessor).getVocabulary();
		return v;
	}

}
