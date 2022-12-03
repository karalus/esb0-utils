/*
 * Copyright 2022 Andre Karalus
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
package com.artofarc.esb.icap;

import java.util.Objects;

public final class ICAPConnectionData {

	private final String _ICAPRemoteHost;
	private final int _ICAPRemotePort;
	private final String _ICAPRemoteURI;

	public ICAPConnectionData(String iCAPRemoteHost, String iCAPRemotePort, String iCAPRemoteURI) {
		_ICAPRemoteHost = Objects.requireNonNull(iCAPRemoteHost, "ICAPRemoteHost");
		_ICAPRemotePort = iCAPRemotePort != null ? Integer.parseInt(iCAPRemotePort) : 1344;
		_ICAPRemoteURI = Objects.requireNonNull(iCAPRemoteURI, "ICAPRemoteURI");
	}

	public String getICAPRemoteHost() {
		return _ICAPRemoteHost;
	}

	public int getICAPRemotePort() {
		return _ICAPRemotePort;
	}

	public String getICAPRemoteURI() {
		return _ICAPRemoteURI;
	}

	@Override
	public String toString() {
		return "icap://" + _ICAPRemoteHost + ":" + _ICAPRemotePort + "/" + _ICAPRemoteURI;
	}

	@Override
	public int hashCode() {
		return toString().hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof ICAPConnectionData) {
			ICAPConnectionData other = (ICAPConnectionData) obj;
			return toString().equals(other.toString());
		}
		return false;
	}

}
