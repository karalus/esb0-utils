package com.artofarc.esb.sftp;

import com.artofarc.esb.resource.ResourceFactory;
import com.jcraft.jsch.JSchException;

public class SFTPConnectionFactory extends ResourceFactory<SFTPConnection, SFTPConnectionData, Void, JSchException>{

	@Override
	protected SFTPConnection createResource(SFTPConnectionData connectionData, Void param) throws JSchException {
		return new SFTPConnection(connectionData);
	}

}
