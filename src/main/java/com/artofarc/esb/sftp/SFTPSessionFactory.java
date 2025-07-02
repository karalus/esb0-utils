package com.artofarc.esb.sftp;

import com.artofarc.esb.resource.ResourceFactory;
import com.jcraft.jsch.JSchException;

public class SFTPSessionFactory extends ResourceFactory<SFTPSession, SFTPSessionData, SFTPConnection, JSchException> {

	@Override
	protected SFTPSession createResource(SFTPSessionData sessionData, SFTPConnection connection) throws JSchException {
		return new SFTPSession(connection, sessionData);
	}

}
