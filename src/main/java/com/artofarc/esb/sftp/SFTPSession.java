package com.artofarc.esb.sftp;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

public class SFTPSession implements AutoCloseable {

	protected final ChannelSftp channelSftp;

	public SFTPSession(SFTPConnection connection, SFTPSessionData sessionData) throws JSchException {
		Session session = connection.jsch.getSession(sessionData.getUser(), sessionData.getHost(), sessionData.getPort());
		session.connect(sessionData.getConnectTimeout());
		session.setServerAliveInterval(sessionData.getServerAliveInterval());
		try {
			channelSftp = (ChannelSftp) session.openChannel("sftp");
			channelSftp.connect(sessionData.getConnectTimeout());
		} catch (JSchException e) {
			session.disconnect();
			throw e;
		}
	}

	@Override
	public void close() throws JSchException {
		channelSftp.getSession().disconnect();
	}

}
