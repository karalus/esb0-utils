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
	
	public HeapDumpAction(ClassLoader classLoader, Properties properties) {
		_pipelineStop = true;
		inVM = Boolean.parseBoolean(properties.getProperty("inVM", "true"));
		doGC = Boolean.parseBoolean(properties.getProperty("doGC", "true"));
	}

	@Override
	protected void execute(Context context, ExecutionContext execContext, ESBMessage message, boolean nextActionIsPipelineStop) throws Exception {
		message.clearHeaders();
		File tempFileHprof = File.createTempFile("heap", ".hprof");
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
			File tempFileZIP = File.createTempFile("heap", ".zip");
			tempFileZIP.deleteOnExit();
			FileOutputStream fos = new FileOutputStream(tempFileZIP);
			zipHeapDump(tempFileHprof, fos);
			message.reset(BodyType.INPUT_STREAM, new FileInputStream(tempFileZIP));
		}
		message.putHeader(HttpConstants.HTTP_HEADER_CONTENT_TYPE, "application/zip");
		message.putHeader(HttpConstants.HTTP_HEADER_CONTENT_DISPOSITION, "filename=\"" + "heapdump.zip" + '"');
	}

	private final static void zipHeapDump(File tempFileHprof, OutputStream os) throws IOException {
		try (FileInputStream fis = new FileInputStream(tempFileHprof); ZipOutputStream zos = new ZipOutputStream(os)) {
			zos.putNextEntry(new ZipEntry("heap.hprof"));
			IOUtils.copy(fis, zos);
		} finally {
			tempFileHprof.delete();
		}
	}

}
