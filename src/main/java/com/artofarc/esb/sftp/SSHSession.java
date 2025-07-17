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

import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

public class SSHSession implements AutoCloseable {

	private final Session session;

	public SSHSession(SSHConfiguration configuration, SSHSessionData sessionData) throws JSchException {
		session = configuration.getJSch().getSession(sessionData.getUser(), sessionData.getHost(), sessionData.getPort());
		if (sessionData.getPassword() != null) {
			session.setPassword(sessionData.getPassword());
		}
		session.setConfig("StrictHostKeyChecking", "no");
		session.connect(sessionData.getConnectTimeout());
		session.setServerAliveInterval(sessionData.getServerAliveInterval());
		session.setServerAliveCountMax(sessionData.getServerAliveCountMax());
	}

	protected Session getSession() {
		return session;
	}

	public synchronized int getLocalPortForwardingLport(String host, int rport, int lport) throws JSchException {
		String suffix = ":" + host + ":" + rport;
		for (String portForwardingL : session.getPortForwardingL()) {
			if (portForwardingL.endsWith(suffix)) {
				return Integer.parseInt(portForwardingL.substring(0, portForwardingL.indexOf(':')));
			}
		}
		return session.setPortForwardingL(lport, host, rport);
	}

	@Override
	public void close() {
		session.disconnect();
	}

}
