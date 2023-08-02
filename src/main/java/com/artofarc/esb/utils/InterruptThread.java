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
package com.artofarc.esb.utils;

import java.util.function.Function;

public class InterruptThread {

	public static String interrupt(String threadName) {
		return apply(threadName, (thread) -> {
			thread.interrupt();
			return String.valueOf(thread.isInterrupted());
		});
	}

	@SuppressWarnings("deprecation")
	public static String stop(String threadName) {
		return apply(threadName, (thread) -> {
			thread.stop();
			return String.valueOf(!thread.isAlive());
		});
	}

	private static String apply(String threadName, Function<Thread, String> function) {
		StringBuilder builder = new StringBuilder();
		ThreadGroup _threadGroup = Thread.currentThread().getThreadGroup();
		Thread[] list = new Thread[_threadGroup.activeCount() * 2];
		int c = _threadGroup.enumerate(list);
		for (int i = 0; i < c; ++i) {
			Thread thread = list[i];
			String name = thread.getName();
			if (name.equals(threadName)) {
				return function.apply(thread);
			}
			builder.append(name).append("\n");
		}
		return builder.toString();
	}

}
