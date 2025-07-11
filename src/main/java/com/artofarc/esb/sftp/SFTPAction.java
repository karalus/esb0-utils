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

	private final SFTPConnectionData connectionData;
	private final String user, host, remoteDir;
	private final int port, connectTimeout, serverAliveCountMax, serverAliveInterval;

	private static String getRequiredProperty(Properties properties, String key) {
		String value = properties.getProperty(key);
		if (value == null) {
			throw new IllegalArgumentException(key + " must be set");
		}
		return value;
	}

	public SFTPAction(ClassLoader classLoader, Properties properties) throws FileNotFoundException {
		_pipelineStop = true;
		String identityFile = getRequiredProperty(properties, "identityFile");
		if (!new File(identityFile).exists()) {
			throw new FileNotFoundException(identityFile);
		}
		String identityPassword = properties.getProperty("identityPassword");
		connectionData = new SFTPConnectionData(properties.getProperty("knownHostsFile"), identityFile, identityPassword != null ? identityPassword.getBytes(StandardCharsets.UTF_8) : null);
		user = getRequiredProperty(properties, "user");
		host = getRequiredProperty(properties, "host");
		port = Integer.parseInt(properties.getProperty("port", "22"));
		connectTimeout = Integer.parseInt(properties.getProperty("connectTimeout", "10000"));
		serverAliveCountMax = Integer.parseInt(properties.getProperty("serverAliveCountMax", "1"));
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
		SFTPSessionData sessionData = new SFTPSessionData(sftpUser, host, port, connectTimeout, serverAliveCountMax, serverAliveInterval);
		SFTPSession session = sessionFactory.getResource(sessionData, connection);
		String sftpURL = sessionData + (sftpRemoteDir != null ? sftpRemoteDir : "~");
		message.clearHeaders();
		String verb = message.getVariable(ESBConstants.HttpMethod);
		String filename = message.getVariable(ESBConstants.filename, "");
		try {
			if (sftpRemoteDir != null && !session.channelSftp.pwd().equals(sftpRemoteDir)) {
				session.channelSftp.cd(sftpRemoteDir);
			}
			message.putVariable(ESBConstants.HttpURLOutbound, sftpURL);
			if (filename.isEmpty()) {
				switch (verb) {
				case "GET":
					Calendar calendar = DatatypeHelper.getCalendarInstance();
					JsonArrayBuilder builder = JsonFactoryHelper.JSON_BUILDER_FACTORY.createArrayBuilder();
					for (ChannelSftp.LsEntry lsEntry : session.channelSftp.ls(".")) {
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
					message.reset(BodyType.INPUT_STREAM, session.channelSftp.get(filename));
					String contentType = MimeHelper.guessContentTypeFromName(filename);
					message.setContentType(contentType != null ? contentType : HttpConstants.HTTP_HEADER_CONTENT_TYPE_OCTET_STREAM);
					message.putHeader(HttpConstants.HTTP_HEADER_CONTENT_DISPOSITION, "filename=\"" + filename + '"');
					message.putVariable(ESBConstants.HttpResponseCode, HttpConstants.SC_OK);
					break;
				case "POST":
					try (OutputStream os = session.channelSftp.put(filename)) {
						message.writeRawTo(os, context);
					}
					message.putVariable(ESBConstants.HttpResponseCode, HttpConstants.SC_CREATED);
					break;
				case "DELETE":
					session.channelSftp.rm(filename);
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
