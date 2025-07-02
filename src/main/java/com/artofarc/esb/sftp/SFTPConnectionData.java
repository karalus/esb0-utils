package com.artofarc.esb.sftp;

import java.util.Objects;

public final class SFTPConnectionData {

	private final String knownHostsFile;
	private final String identityFile;
	private final byte[] identityPassword;

	public SFTPConnectionData(String knownHostsFile, String identityFile, byte[] identityPassword) {
		this.knownHostsFile = knownHostsFile;
		this.identityFile = identityFile;
		this.identityPassword = identityPassword;
	}

	public String getKnownHostsFile() {
		return knownHostsFile;
	}

	public String getIdentityFile() {
		return identityFile;
	}

	public byte[] getIdentityPassword() {
		return identityPassword;
	}

	@Override
	public int hashCode() {
		return Objects.hash(identityFile, knownHostsFile);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		SFTPConnectionData other = (SFTPConnectionData) obj;
		return Objects.equals(identityFile, other.identityFile) && Objects.equals(knownHostsFile, other.knownHostsFile);
	}

}
