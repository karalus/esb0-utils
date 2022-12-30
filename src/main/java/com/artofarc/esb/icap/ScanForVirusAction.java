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

import java.util.Properties;

import com.artofarc.esb.action.Action;
import com.artofarc.esb.action.ExecutionException;
import com.artofarc.esb.context.Context;
import com.artofarc.esb.context.ExecutionContext;
import com.artofarc.esb.http.HttpConstants;
import com.artofarc.esb.message.ESBConstants;
import com.artofarc.esb.message.ESBMessage;

public class ScanForVirusAction extends Action {

	private final ICAPConnectionData icapConnectionData;
	private final Integer maxIdleTime;
	private final ICAP.ScanEngine scanEngine;

	public ScanForVirusAction(ClassLoader classLoader, Properties properties) throws ReflectiveOperationException {
		_pipelineStop = true;
		icapConnectionData = new ICAPConnectionData(properties.getProperty("ICAPRemoteHost"), properties.getProperty("ICAPRemotePort"), properties.getProperty("ICAPRemoteURI"));
		String iCAPMaxIdleTime = properties.getProperty("ICAPMaxIdleTime");
		maxIdleTime = iCAPMaxIdleTime != null ? Integer.valueOf(iCAPMaxIdleTime) : null;
		scanEngine = (ICAP.ScanEngine) classLoader.loadClass(properties.getProperty("ICAPScanEngine", "com.artofarc.esb.icap.ICAP$ScanEngine")).newInstance();
	}

	@Override
	protected void execute(Context context, ExecutionContext execContext, ESBMessage message, boolean nextActionIsPipelineStop) throws Exception {
		String filename = message.getVariable(ESBConstants.filename);
		if (filename == null) {
			throw new ExecutionException(this, "filename must be set");
		}
		String ISTag = message.getVariable("ISTag");
		ICAPConnectionFactory resourceFactory = context.getResourceFactory(ICAPConnectionFactory.class);
		ICAP icap = resourceFactory.getResource(icapConnectionData, scanEngine);
		if (maxIdleTime != null && icap.getIdleTime() > maxIdleTime) {
			resourceFactory.close(icapConnectionData);
			icap = resourceFactory.getResource(icapConnectionData, scanEngine);
		}
		try {
			if (ISTag != null && ISTag.equals(icap.getISTag())) {
				message.putVariable("scanResult", true);
				message.reset(null, null);
			} else {
				boolean result = icap.scanFile(filename, message.getBodyAsInputStream(context));
				message.reset(null, icap.getResponseText());
				message.putHeader(HttpConstants.HTTP_HEADER_CONTENT_TYPE, HttpConstants.HTTP_HEADER_CONTENT_TYPE_TEXT);
				message.putVariable("ISTag", icap.getISTag());
				message.putVariable("scanResult", result);
			}
		} catch (Exception e) {
			// Underlying socket connection might be corrupt
			resourceFactory.close(icapConnectionData);
			throw e;
		}
	}

}
