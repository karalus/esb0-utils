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

import java.io.FilterReader;
import java.io.IOException;
import java.io.Reader;

public class KMPReader extends FilterReader {

	public final static class Pattern {
		private final char[] pattern;
		private final int[] prefixes;

		public Pattern(CharSequence pattern) {
			if (pattern.length() == 0) {
				throw new IllegalArgumentException("pattern must not be empty");
			}
			this.pattern = new char[pattern.length()];
			prefixes = new int[pattern.length() + 1];
			prefixes[0] = -1;
			int i = 0, j = -1;
			while (i < pattern.length()) {
				this.pattern[i] = pattern.charAt(i);
				while (j >= 0 && pattern.charAt(j) != pattern.charAt(i)) {
					j = prefixes[j];
				}
				prefixes[++i] = ++j;
			}
		}
	}

	private long pos, mark;
	private Pattern kmp;
	private int j;
	private boolean found;

	public KMPReader(Reader in) {
		super(in);
	}

	public void setPattern(Pattern pattern) {
		kmp = pattern;
		j = 0;
		found = false;
	}

	public long indexOf() {
		return found ? pos - kmp.pattern.length : -1;
	}

	public long position() {
		return pos;
	}

	@Override
	public int read() throws IOException {
		final int c = in.read();
		if (c >= 0) {
			++pos;
			if (kmp != null) {
				while (j >= 0 && (char) c != kmp.pattern[j]) {
					j = kmp.prefixes[j];
				}
				if (found = (++j == kmp.pattern.length)) {
					j = kmp.prefixes[j];
				}
			}
		}
		return c;
	}

	@Override
	public int read(char[] cbuf, int off, int len) throws IOException {
		if (len == 0) {
			return 0;
		}
		int c = read();
		if (c == -1) {
			return -1;
		}
		cbuf[off] = (char) c;
		int i = 1;
		try {
			for (; i < len && !found; ++i) {
				c = read();
				if (c == -1) {
					break;
				}
				cbuf[off + i] = (char) c;
			}
		} catch (IOException e) {
		}
		return i;
	}

	@Override
	public long skip(long n) throws IOException {
		if (n <= 0) {
			return 0;
		}
		long remaining = n;
		while (read() >= 0 && --remaining > 0 && !found); // no body in this loop
		return n - remaining;
	}

	@Override
	public void mark(int readlimit) throws IOException {
		in.mark(readlimit);
		mark = pos;
	}

	@Override
	public void reset() throws IOException {
		in.reset();
		pos = mark;
		j = 0;
		found = false;
	}

}
