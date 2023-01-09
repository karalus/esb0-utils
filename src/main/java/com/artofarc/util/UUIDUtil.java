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
package com.artofarc.util;

import java.util.Base64;
import java.util.UUID;

public final class UUIDUtil {

	public static String toBase64(UUID uuid) {
		final byte[] ba = new byte[2 * Long.BYTES];
		longToBytes(uuid.getMostSignificantBits(), ba, 0);
		longToBytes(uuid.getLeastSignificantBits(), ba, Long.BYTES);
		return Base64.getEncoder().encodeToString(ba);
	}

	public static UUID fromBase64(String base64) {
		if (base64.length() != 24) {
			throw new IllegalArgumentException("base64 is expected to have 24 chars");
		}
		final byte[] ba = Base64.getDecoder().decode(base64);
		return new UUID(bytesToLong(ba, 0), bytesToLong(ba, Long.BYTES));
	}

	private static void longToBytes(long l, final byte[] result, int offset) {
		for (int i = Long.BYTES - 1; i >= 0; i--) {
			result[i + offset] = (byte) (l & 0xFF);
			l >>= Byte.SIZE;
		}
	}

	private static long bytesToLong(final byte[] b, int offset) {
		long result = 0;
		for (int i = 0; i < Long.BYTES; i++) {
			result <<= Byte.SIZE;
			result |= (b[i + offset] & 0xFF);
		}
		return result;
	}

}
