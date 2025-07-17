/*
 * Copyright 2025 Andre Karalus
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
package com.artofarc.esb.sftp;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
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
import com.artofarc.util.DatatypeHelper;
import com.artofarc.util.JsonFactoryHelper;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.SftpException;

public class SFTPAction extends Action {

	private final SSHConfigurationData configurationData;
	private final String user, host, portExp, remoteDir;
	private final byte[] password;
	private final int connectTimeout, serverAliveCountMax, serverAliveInterval;

	private static String getRequiredProperty(Properties properties, String key) {
		String value = properties.getProperty(key);
		if (value == null) {
			throw new IllegalArgumentException(key + " must be set");
		}
		return value;
	}

	private static byte[] getPasswordProperty(Properties properties, String key) {
		String password = properties.getProperty(key);
		return password != null ? password.getBytes(StandardCharsets.UTF_8) : null;
	}

	public SFTPAction(ClassLoader classLoader, Properties properties) throws FileNotFoundException {
		_pipelineStop = true;
		String identityFile = properties.getProperty("identityFile");
		if (identityFile != null && !new File(identityFile).exists()) {
			throw new FileNotFoundException(identityFile);
		}
		configurationData = new SSHConfigurationData(properties.getProperty("knownHostsFile"), identityFile, getPasswordProperty(properties, "identityPassword"));
		user = getRequiredProperty(properties, "user");
		password = getPasswordProperty(properties, "password");
		host = getRequiredProperty(properties, "host");
		portExp = properties.getProperty("port", "22");
		connectTimeout = Integer.parseInt(properties.getProperty("connectTimeout", "10000"));
		serverAliveCountMax = Integer.parseInt(properties.getProperty("serverAliveCountMax", "1"));
		serverAliveInterval = Integer.parseInt(properties.getProperty("serverAliveInterval", "0"));
		remoteDir = properties.getProperty("remoteDir");
	}

	@Override
	protected void execute(Context context, ExecutionContext execContext, ESBMessage message, boolean nextActionIsPipelineStop) throws Exception {
		SSHConfigurationFactory configurationFactory = context.getGlobalContext().getResourceFactory(SSHConfigurationFactory.class);
		SSHConfiguration configuration = configurationFactory.getResource(configurationData);
		String sshUser = (String) eval(user, context, message);
		int port;
		try {
			port = Integer.parseInt(portExp);
		} catch (NumberFormatException e) {
			port = message.getVariable(portExp);
		}
		SSHSessionFactory sessionFactory = context.getPoolContext().getResourceFactory(SSHSessionFactory.class);
		SSHSessionData sessionData = new SSHSessionData(sshUser, password, host, port, connectTimeout, serverAliveCountMax, serverAliveInterval);
		SSHSession session = sessionFactory.getResource(sessionData, configuration);
		if (!session.getSession().isConnected()) {
			logger.info("SSH session is disconnected. Trying to reconnect.");
			sessionFactory.close(sessionData);
			session = sessionFactory.getResource(sessionData, configuration);
		}
		String verb = message.getVariable(ESBConstants.HttpMethod);
		if ("CONNECT".equals(verb)) {
			String host = message.getVariable("host");
			if (host == null) {
				throw new ExecutionException(this, "host must be set");
			}
			int rport = message.getVariable("rport", 22), lport = message.getVariable("lport", 0);
			message.putVariable("lport", session.getLocalPortForwardingLport(host, rport, lport));
		} else {
			message.clearHeaders();
			SFTPChannelFactory channelFactory = context.getResourceFactory(SFTPChannelFactory.class);
			SFTPChannel channel = channelFactory.getResource(session, sessionData);
			String sftpRemoteDir = remoteDir != null ? (String) eval(remoteDir, context, message) : null;
			String sftpURL = sessionData + (sftpRemoteDir != null ? sftpRemoteDir : "~");
			String filename = message.getVariable(ESBConstants.filename, "");
			try {
				if (sftpRemoteDir != null && !channel.getChannelSftp().pwd().equals(sftpRemoteDir)) {
					channel.getChannelSftp().cd(sftpRemoteDir);
				}
				message.putVariable(ESBConstants.HttpURLOutbound, sftpURL);
				if (filename.isEmpty()) {
					switch (verb) {
					case "GET":
						Calendar calendar = DatatypeHelper.getCalendarInstance();
						JsonArrayBuilder builder = JsonFactoryHelper.JSON_BUILDER_FACTORY.createArrayBuilder();
						for (ChannelSftp.LsEntry lsEntry : channel.getChannelSftp().ls(".")) {
							calendar.setTimeInMillis(Integer.toUnsignedLong(lsEntry.getAttrs().getMTime()) * 1000);
							builder.add(JsonFactoryHelper.JSON_BUILDER_FACTORY.createObjectBuilder().add("name", lsEntry.getFilename()).add("dir", lsEntry.getAttrs().isDir())
									.add("length", lsEntry.getAttrs().getSize()).add("modificationTime", DatatypeHelper.printDateTime(calendar)).build());
						}
						message.reset(BodyType.JSON_VALUE, builder.build());
						message.setContentType(HttpConstants.HTTP_HEADER_CONTENT_TYPE_JSON);
						message.putVariable(ESBConstants.HttpResponseCode, HttpConstants.SC_OK);
						break;
					case "OPTIONS":
						message.putVariable(ESBConstants.HttpResponseCode, HttpConstants.SC_OK);
						break;
					default:
						message.putVariable(ESBConstants.HttpResponseCode, HttpConstants.SC_METHOD_NOT_ALLOWED);
						throw new ExecutionException(this, verb);
					}
				} else {
					switch (verb) {
					case "GET":
						message.reset(BodyType.INPUT_STREAM, channel.getChannelSftp().get(filename));
						String contentType = MimeHelper.guessContentTypeFromName(filename);
						message.setContentType(contentType != null ? contentType : HttpConstants.HTTP_HEADER_CONTENT_TYPE_OCTET_STREAM);
						message.putHeader(HttpConstants.HTTP_HEADER_CONTENT_DISPOSITION, "filename=\"" + filename + '"');
						message.putVariable(ESBConstants.HttpResponseCode, HttpConstants.SC_OK);
						break;
					case "POST":
						try (OutputStream os = channel.getChannelSftp().put(filename)) {
							message.writeRawTo(os, context);
						}
						message.putVariable(ESBConstants.HttpResponseCode, HttpConstants.SC_CREATED);
						break;
					case "DELETE":
						channel.getChannelSftp().rm(filename);
						message.putVariable(ESBConstants.HttpResponseCode, HttpConstants.SC_NO_CONTENT);
						break;
					default:
						message.putVariable(ESBConstants.HttpResponseCode, HttpConstants.SC_METHOD_NOT_ALLOWED);
						throw new ExecutionException(this, verb);
					}
				}
			} catch (SftpException e) {
				if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
					throw new FileNotFoundException(sftpURL + "/" + filename);
				}
				throw e;
			}
		}
	}

}
