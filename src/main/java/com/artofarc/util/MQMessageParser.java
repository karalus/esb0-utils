/*
 * Copyright 2023 Andre Karalus
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
package com.artofarc.util;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.jms.JMSException;
import javax.jms.Message;

import com.ibm.mq.headers.MQRFH2;

public final class MQMessageParser {

	private static final int MQHRF2_SEARCH_WINDOW = 512;
	private static final KMPInputStream.Pattern MQHRF2 = new KMPInputStream.Pattern("MQHRF2".getBytes());
	private static final KMPInputStream.Pattern RFH = new KMPInputStream.Pattern("RFH ".getBytes());

	public static final boolean isMQMessage(Message message) throws JMSException {
		return message.propertyExists("JMS_IBM_Format");
	}

	private final MQRFH2 mqrfh2;

	public MQMessageParser(InputStream in) throws Exception {
		KMPInputStream kmpInputStream = new KMPInputStream(in);
		kmpInputStream.mark(MQHRF2_SEARCH_WINDOW);
		kmpInputStream.setPattern(MQHRF2);
		kmpInputStream.skip(MQHRF2_SEARCH_WINDOW);
		long i = kmpInputStream.indexOf();
		if (i >= 0) {
			kmpInputStream.setPattern(RFH);
			kmpInputStream.skip(MQHRF2_SEARCH_WINDOW - i);
			i = kmpInputStream.indexOf();
			kmpInputStream.reset();
			if (i >= 0) {
				in.skip(i);
				mqrfh2 = new MQRFH2(new DataInputStream(in));
			} else {
				mqrfh2 = null;
			}
		} else {
			mqrfh2 = null;
			kmpInputStream.reset();
		}
	}

	public Map<String, Map<String, Object>> getElements() throws IOException {
		if (mqrfh2 == null) {
			return Collections.emptyMap();
		}
		Map<String, Map<String, Object>> map = new LinkedHashMap<>();
		for (MQRFH2.Element element : mqrfh2.getFolders()) {
			map.put(element.getName(), convert(element));
		}
		return map;
	}

	private Map<String, Object> convert(MQRFH2.Element element) {
		Map<String, Object> map = new LinkedHashMap<>();
		for (MQRFH2.Element child : element.getChildren()) {
			map.put(child.getName(), child.getValue());
		}
		return map;
	}

	public Map<String, Object> getJMSProperties() throws IOException {
		MQRFH2.Element folder = mqrfh2 != null ? mqrfh2.getFolder("usr", false) : null;
		return folder != null ? convert(folder) : null;
	}

	public String getJMSDestination() throws IOException {
		return mqrfh2 != null ? mqrfh2.getStringFieldValue("jms", "Dst") : null;
	}

}
