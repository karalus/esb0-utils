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

import java.nio.ByteBuffer;
import java.util.UUID;
import javax.xml.bind.DatatypeConverter;

public final class UUIDUtil {

	public static String toBase64(UUID uuid) {
		final ByteBuffer byteBuffer = ByteBuffer.allocate(2 * Long.BYTES + 2);
		byteBuffer.putLong(uuid.getMostSignificantBits());
		byteBuffer.putLong(uuid.getLeastSignificantBits());
		return DatatypeConverter.printBase64Binary(byteBuffer.array());
	}

	public static UUID fromBase64(String base64) {
		if (base64.length() != 24) {
			throw new IllegalArgumentException("base64 is expected to have 24 chars");
		}
		final ByteBuffer byteBuffer = ByteBuffer.wrap(DatatypeConverter.parseBase64Binary(base64));
		return new UUID(byteBuffer.getLong(), byteBuffer.getLong());
	}

}
