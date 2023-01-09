package com.artofarc.esb.utils;

public class InterruptThread {

	public static String interrupt(String threadName) {
		StringBuilder builder = new StringBuilder();
		ThreadGroup _threadGroup = Thread.currentThread().getThreadGroup();
		Thread[] list = new Thread[_threadGroup.activeCount()];
		int c = _threadGroup.enumerate(list);
		for (int i = 0; i < c; ++i) {
			Thread thread = list[i];
			String name = thread.getName();
			if (name.equals(threadName)) {
				thread.interrupt();
				if (thread.isInterrupted()) {
					return "true";
				}
			}
			builder.append(name).append("\n");
		}
		return builder.toString();
	}

}
