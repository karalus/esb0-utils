package com.artofarc.esb.sftp;

import java.util.Objects;

public final class SFTPSessionData {

	private final String user;
	private final String host;
	private final int port, connectTimeout, serverAliveCountMax, serverAliveInterval;

	public SFTPSessionData(String user, String host, int port, int connectTimeout, int serverAliveCountMax, int serverAliveInterval) {
		this.user = user;
		this.host = host;
		this.port = port;
		this.connectTimeout = connectTimeout;
		this.serverAliveCountMax = serverAliveCountMax;
		this.serverAliveInterval = serverAliveInterval;
	}

	public String getUser() {
		return user;
	}

	public String getHost() {
		return host;
	}

	public int getPort() {
		return port;
	}

	public int getConnectTimeout() {
		return connectTimeout;
	}

	public int getServerAliveCountMax() {
		return serverAliveCountMax;
	}

	public int getServerAliveInterval() {
		return serverAliveInterval;
	}

	@Override
	public int hashCode() {
		return Objects.hash(host, port, user);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		SFTPSessionData other = (SFTPSessionData) obj;
		return Objects.equals(host, other.host) && port == other.port && Objects.equals(user, other.user);
	}

	@Override
	public String toString() {
		String str = "sftp://" + user + "@" + host;
		return port != 22 ? str + ":" + port : str;
	}

}
