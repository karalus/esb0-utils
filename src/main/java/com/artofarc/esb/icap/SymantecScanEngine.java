package com.artofarc.esb.icap;

import java.util.Map;

class SymantecScanEngine extends ICAP.ScanEngine {

	@Override
	public boolean isVirus(int status, Map<String, String> responseMap) {
		return status == 201;
	}

	@Override
	public boolean isOk(int status) {
		return status == 204;
	}

	@Override
	public String parseResponse(Map<String, String> responseMap, String response) {
		int x = response.indexOf("</title>", 0);
		if (x >= 0) {
			int y = response.indexOf("</html>", x);
			return response.substring(x + 8, y);
		}
		return responseMap.get("X-Violations-Found");
	}

}
