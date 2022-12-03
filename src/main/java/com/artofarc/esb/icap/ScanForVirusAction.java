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
package com.artofarc.esb.icap;

import java.util.Map;
import java.util.Properties;

import com.artofarc.esb.action.Action;
import com.artofarc.esb.action.ExecutionException;
import com.artofarc.esb.context.Context;
import com.artofarc.esb.context.ExecutionContext;
import com.artofarc.esb.message.ESBConstants;
import com.artofarc.esb.message.ESBMessage;

public class ScanForVirusAction extends Action {

	private final ICAPConnectionData icapConnectionData;
	private final Integer stdPreviewSize;

	public ScanForVirusAction(ClassLoader classLoader, Properties properties) {
		_pipelineStop = true;
		icapConnectionData = new ICAPConnectionData(properties.getProperty("ICAPRemoteHost"), properties.getProperty("ICAPRemotePort"), properties.getProperty("ICAPRemoteURI"));
		String iCAPPreviewSize = properties.getProperty("ICAPPreviewSize");
		stdPreviewSize = iCAPPreviewSize != null ? Integer.valueOf(iCAPPreviewSize) : null;
	}

	@Override
	protected void execute(Context context, ExecutionContext execContext, ESBMessage message, boolean nextActionIsPipelineStop) throws Exception {
		String filename = message.getVariable(ESBConstants.filename);
		if (filename == null) {
			throw new ExecutionException(this, "filename must be set");
		}
		Long length = message.getByteLength();
		if (length == null) {
			// This should be avoided cause it might need a lot of memory, but we need the size
			length = (long) message.getBodyAsByteArray(context).length;
		}
		ICAPConnectionFactory resourceFactory = context.getResourceFactory(ICAPConnectionFactory.class);
		ICAP icap = resourceFactory.getResource(icapConnectionData, stdPreviewSize);
		try {
			boolean result = icap.scanFile(filename, message.getBodyAsInputStream(context), length);
			message.putVariable("scanResult", result);
			for (Map.Entry<String, String> entry : icap.getResponseMap().entrySet()) {
				if (entry.getKey().startsWith("X-")) {
					// e.g. X-Violations-Found, X-Infection-Found
					message.putVariable("ICAP" + entry.getKey().substring(1), entry.getValue());
				}
			}
		} catch (Exception e) {
			// Underlying socket connection might be corrupt
			resourceFactory.close(icapConnectionData);
			throw e;
		}
	}

}
