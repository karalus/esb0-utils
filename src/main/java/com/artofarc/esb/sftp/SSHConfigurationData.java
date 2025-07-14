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

import java.util.Objects;

public final class SSHConfigurationData {

	private final String knownHostsFile;
	private final String identityFile;
	private final byte[] identityPassword;

	public SSHConfigurationData(String knownHostsFile, String identityFile, byte[] identityPassword) {
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
		SSHConfigurationData other = (SSHConfigurationData) obj;
		return Objects.equals(identityFile, other.identityFile) && Objects.equals(knownHostsFile, other.knownHostsFile);
	}

}
