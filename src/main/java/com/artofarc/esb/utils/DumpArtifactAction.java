package com.artofarc.esb.utils;

import java.io.IOException;
import java.util.HashSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.artofarc.esb.action.Action;
import com.artofarc.esb.action.ExecutionException;
import com.artofarc.esb.artifact.Artifact;
import com.artofarc.esb.artifact.Directory;
import com.artofarc.esb.context.Context;
import com.artofarc.esb.context.ExecutionContext;
import com.artofarc.esb.http.HttpConstants;
import com.artofarc.esb.message.ESBConstants;
import com.artofarc.esb.message.ESBMessage;
import com.artofarc.util.IOUtils;

public class DumpArtifactAction extends Action {

	public DumpArtifactAction() {
		_streamingToSink = true;
	}

	@Override
	protected ExecutionContext prepare(Context context, ESBMessage message, boolean inPipeline) throws Exception {
		String appendHttpUrlPath = message.getVariable(ESBConstants.appendHttpUrlPath);
		Artifact artifact = context.getGlobalContext().getFileSystem().getArtifact(appendHttpUrlPath);
		if (artifact == null || artifact instanceof Directory) {
			throw new ExecutionException(this, "File not found or directory");
		}
		String filename = IOUtils.stripExt(artifact.getName()) + "-" + System.currentTimeMillis() + ".zip";
		message.clearHeaders();
		message.putHeader(HttpConstants.HTTP_HEADER_CONTENT_TYPE, "application/zip");
		message.putHeader(HttpConstants.HTTP_HEADER_CONTENT_DISPOSITION, "filename=\"" + filename + '"');
		return new ExecutionContext(artifact);
	}

	@Override
	protected void execute(Context context, ExecutionContext execContext, ESBMessage message, boolean nextActionIsPipelineStop) throws Exception {
		Artifact artifact = execContext.getResource();
		HashSet<Artifact> done = new HashSet<>();
		try (ZipOutputStream zos = new ZipOutputStream(message.getBody())) {
			dumpArtifact(zos, artifact, done);
		}
	}

	private static void dumpArtifact(ZipOutputStream zos, Artifact artifact, HashSet<Artifact> done) throws IOException {
		if (artifact.getModificationTime() > 0L && !done.contains(artifact)) {
			done.add(artifact);
			ZipEntry zipEntry = new ZipEntry(artifact.getURI().substring(1));
			zipEntry.setTime(artifact.getModificationTime());
			zos.putNextEntry(zipEntry);
			IOUtils.copy(artifact.getContentAsStream(), zos);
			for (String referenced : artifact.getReferenced()) {
				dumpArtifact(zos, artifact.getArtifact(referenced), done);
			}
		}
	}

}
