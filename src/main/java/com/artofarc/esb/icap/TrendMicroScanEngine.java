package com.artofarc.esb.icap;

import java.util.Map;

class TrendMicroScanEngine extends ICAP.ScanEngine {

	@Override
	public boolean isVirus(int status, Map<String, String> responseMap) {
		return status == 200;
	}

	@Override
	public boolean isOk(int status) {
		return status == 204;
	}

}
