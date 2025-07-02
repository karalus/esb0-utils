package com.artofarc.esb.sftp;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;

public class SFTPConnection implements AutoCloseable {

	protected final JSch jsch;

	public SFTPConnection(SFTPConnectionData connectionData) throws JSchException {
		jsch = new JSch();
		jsch.addIdentity(connectionData.getIdentityFile(), connectionData.getIdentityPassword());
		jsch.setKnownHosts(connectionData.getKnownHostsFile());
	}

	@Override
	public void close() {
	}

}
