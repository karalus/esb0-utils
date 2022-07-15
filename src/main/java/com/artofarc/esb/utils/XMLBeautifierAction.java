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

import javax.xml.transform.sax.SAXSource;
import javax.xml.xquery.XQItem;

import org.xml.sax.XMLReader;

import com.artofarc.esb.action.SAXAction;
import com.artofarc.esb.context.Context;
import com.artofarc.esb.message.ESBMessage;
import com.artofarc.util.XMLFilterBase;

public class XMLBeautifierAction extends SAXAction {

	@Override
	protected SAXSource createSAXSource(Context context, ESBMessage message, XQItem item) throws Exception {
		return new SAXSource(context.createNamespaceBeautifier(new SAXSource(new XQJFilter(item), null)), null);
	}

	@Override
	protected XMLFilterBase createXMLFilter(Context context, ESBMessage message, XMLReader parent) throws Exception {
		return context.createNamespaceBeautifier(new SAXSource(parent, null));
	}

}
