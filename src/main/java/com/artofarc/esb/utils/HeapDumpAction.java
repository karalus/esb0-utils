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
package com.artofarc.esb.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import com.artofarc.esb.action.Action;
import com.artofarc.esb.context.Context;
import com.artofarc.esb.context.ExecutionContext;
import com.artofarc.esb.http.HttpConstants;
import com.artofarc.esb.message.BodyType;
import com.artofarc.esb.message.ESBMessage;
import com.artofarc.util.ByteArrayOutputStream;
import com.artofarc.util.IOUtils;

public class HeapDumpAction extends Action {

	private final boolean inVM, doGC;
	private final String fileName;

	public HeapDumpAction(ClassLoader classLoader, Properties properties) {
		_pipelineStop = true;
		inVM = Boolean.parseBoolean(properties.getProperty("inVM", "true"));
		doGC = Boolean.parseBoolean(properties.getProperty("doGC", "true"));
		fileName = properties.getProperty("fileName", "heap");
	}

	@Override
	protected void execute(Context context, ExecutionContext execContext, ESBMessage message, boolean nextActionIsPipelineStop) throws Exception {
		message.clearHeaders();
		File tempFileHprof = File.createTempFile(fileName, ".hprof");
		tempFileHprof.delete();
		
		if (doGC) {
			System.gc();
		}
		MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();
		mbeanServer.invoke(
			new ObjectName("com.sun.management:type=HotSpotDiagnostic"),
			"dumpHeap",
			new Object[] {tempFileHprof.getAbsolutePath(), true},
			new String[] {String.class.getName(), boolean.class.getName()});

		if (inVM) {
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			zipHeapDump(tempFileHprof, bos);
			message.reset(BodyType.INPUT_STREAM, bos.getByteArrayInputStream());
		} else {
			File tempFileZIP = File.createTempFile(fileName, ".zip");
			FileOutputStream fos = new FileOutputStream(tempFileZIP);
			try {
				zipHeapDump(tempFileHprof, fos);
			} catch (IOException e) {
				tempFileZIP.delete();
				throw e;
			}
			message.reset(BodyType.INPUT_STREAM, new FileInputStream(tempFileZIP) {

				@Override
				public void close() throws IOException {
					try {
						super.close();
					} finally {
						tempFileZIP.delete();
					}
				}
			});
		}
		message.putHeader(HttpConstants.HTTP_HEADER_CONTENT_TYPE, "application/zip");
		message.putHeader(HttpConstants.HTTP_HEADER_CONTENT_DISPOSITION, "filename=\"" + "heapdump.zip" + '"');
	}

	private void zipHeapDump(File tempFileHprof, OutputStream os) throws IOException {
		try (FileInputStream fis = new FileInputStream(tempFileHprof); ZipOutputStream zos = new ZipOutputStream(os)) {
			zos.putNextEntry(new ZipEntry(fileName + ".hprof"));
			IOUtils.copy(fis, zos);
		} finally {
			tempFileHprof.delete();
		}
	}

}
