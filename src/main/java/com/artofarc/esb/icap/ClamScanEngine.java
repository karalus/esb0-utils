package com.artofarc.esb.icap;

import java.util.Map;

class ClamScanEngine extends ICAP.ScanEngine {

	@Override
	public boolean isVirus(int status, Map<String, String> responseMap) {
		return status == 201 || responseMap.containsKey("X-Infection-Found") || "yes".equals(responseMap.get("X-Clam-Virus"));
	}

	@Override
	public boolean isOk(int status) {
		return status == 200;
	}

}
