package com.artofarc.esb.sftp;

import static com.artofarc.esb.http.HttpConstants.HTTP_HEADER_CONTENT_TYPE_JSON;

import java.io.FileNotFoundException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import javax.json.JsonArrayBuilder;

import com.artofarc.esb.action.Action;
import com.artofarc.esb.action.ExecutionException;
import com.artofarc.esb.context.Context;
import com.artofarc.esb.context.ExecutionContext;
import com.artofarc.esb.http.HttpConstants;
import com.artofarc.esb.message.BodyType;
import com.artofarc.esb.message.ESBConstants;
import com.artofarc.esb.message.ESBMessage;
import com.artofarc.esb.message.MimeHelper;
import com.artofarc.util.JsonFactoryHelper;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.SftpException;

public class SFTPAction extends Action {

	private final SFTPConnectionData connectionData;
	private final String user, host, remoteDir;
	private final int port, connectTimeout, serverAliveInterval;

	private static String getRequiredProperty(Properties properties, String key) {
		String value = properties.getProperty(key);
		if (value == null) {
			throw new IllegalArgumentException(key + " must be set");
		}
		return value;
	}

	public SFTPAction(ClassLoader classLoader, Properties properties) {
		_pipelineStop = true;
		String identityPassword = properties.getProperty("identityPassword");
		connectionData = new SFTPConnectionData(getRequiredProperty(properties, "knownHostsFile"), getRequiredProperty(properties, "identityFile"), identityPassword != null ? identityPassword.getBytes(StandardCharsets.UTF_8) : null);
		user = getRequiredProperty(properties, "user");
		host = getRequiredProperty(properties, "host");
		port = Integer.parseInt(properties.getProperty("port", "22"));
		connectTimeout = Integer.parseInt(properties.getProperty("connectTimeout", "10000"));
		serverAliveInterval = Integer.parseInt(properties.getProperty("serverAliveInterval", "0"));
		remoteDir = properties.getProperty("remoteDir");
	}

	@Override
	protected void execute(Context context, ExecutionContext execContext, ESBMessage message, boolean nextActionIsPipelineStop) throws Exception {
		String sftpUser = (String) eval(user, context, message);
		String sftpRemoteDir = remoteDir != null ? (String) eval(remoteDir, context, message) : null;
		SFTPConnectionFactory connectionFactory = context.getGlobalContext().getResourceFactory(SFTPConnectionFactory.class);
		SFTPConnection connection = connectionFactory.getResource(connectionData);
		SFTPSessionFactory sessionFactory = context.getResourceFactory(SFTPSessionFactory.class);
		SFTPSessionData sessionData = new SFTPSessionData(sftpUser, host, port, connectTimeout, serverAliveInterval);
		SFTPSession session = sessionFactory.getResource(sessionData, connection);
		if (sftpRemoteDir != null && !session.channelSftp.pwd().equals(sftpRemoteDir)) {
			session.channelSftp.cd(sftpRemoteDir);
		}
		message.clearHeaders();
		String verb = message.getVariable(ESBConstants.HttpMethod);
		String filename = message.getVariable(ESBConstants.filename);
		if (filename != null && !filename.isEmpty()) {
			switch (verb) {
			case "GET":
				try {
					message.reset(BodyType.INPUT_STREAM, session.channelSftp.get(filename));
				} catch (SftpException e) {
					if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
						throw new FileNotFoundException(sessionData + (remoteDir != null ? remoteDir : "~") + "/" + filename);
					} 
					throw e;
				}
				message.setContentType(MimeHelper.guessContentTypeFromName(filename));
				message.putHeader(HttpConstants.HTTP_HEADER_CONTENT_DISPOSITION, "filename=\"" + filename + '"');
				break;
			case "POST":
				try (OutputStream os = session.channelSftp.put(filename)) {
					message.writeRawTo(os, context);
				}
				break;
			case "DELETE":
				session.channelSftp.rm(filename);
				break;
			default:
				message.putVariable(ESBConstants.HttpResponseCode, HttpConstants.SC_METHOD_NOT_ALLOWED);
				throw new ExecutionException(this, verb);
			}
		} else {
			switch (verb) {
			case "GET":
				JsonArrayBuilder builder = JsonFactoryHelper.JSON_BUILDER_FACTORY.createArrayBuilder();
				for (ChannelSftp.LsEntry lsEntry : session.channelSftp.ls(".")) {
					builder.add(JsonFactoryHelper.JSON_BUILDER_FACTORY.createObjectBuilder().add("name", lsEntry.getFilename()).add("dir", lsEntry.getAttrs().isDir())
							.add("length", lsEntry.getAttrs().getSize()).add("modificationTime", lsEntry.getAttrs().getMtimeString()).build());
				}
				message.reset(BodyType.JSON_VALUE, builder.build());
				message.setContentType(HTTP_HEADER_CONTENT_TYPE_JSON);
				break;
			default:
				message.putVariable(ESBConstants.HttpResponseCode, HttpConstants.SC_METHOD_NOT_ALLOWED);
				throw new ExecutionException(this, verb);
			}
		}
	}

}
