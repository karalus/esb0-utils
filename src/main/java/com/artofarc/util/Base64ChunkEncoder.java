/*
 * Copyright 2023 Andre Karalus
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

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.NoSuchElementException;

import javax.xml.bind.DatatypeConverter;

public class Base64ChunkEncoder implements Iterator<String> {

	private final InputStream _inputStream;
	private final byte[] _chunk;
	private int pos;

	public Base64ChunkEncoder(InputStream inputStream, int chunkSize) {
		_inputStream = inputStream;
		_chunk = new byte[chunkSize / 4 * 3];
	}

	@Override
	public boolean hasNext() {
		if (pos == 0) {
			final int chunkSize = _chunk.length;
			try {
				do {
					final int len = _inputStream.read(_chunk, pos, chunkSize - pos);
					if (len < 0) {
						break;
					}
					pos += len;
				} while (pos < chunkSize);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		return pos > 0;
	}

	@Override
	public String next() {
		if (!hasNext()) {
			throw new NoSuchElementException();
		}
		byte[] ba;
		if (pos == _chunk.length) {
			ba = _chunk;
			pos = 0;
		} else {
			ba = new byte[pos];
			System.arraycopy(_chunk, 0, ba, 0, pos);
			pos = -1;
		}
		return DatatypeConverter.printBase64Binary(ba);
	}

}
