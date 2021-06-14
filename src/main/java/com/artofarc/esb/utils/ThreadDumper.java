package com.artofarc.esb.utils;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;

public class ThreadDumper {

	public static String dumpThreads() {
		StringBuilder builder = new StringBuilder();
		ThreadMXBean bean = ManagementFactory.getThreadMXBean();
		ThreadInfo[] infos = bean.dumpAllThreads(true, true);
		for (ThreadInfo info : infos) {
			builder.append(info);
		}
		return builder.toString();
	}

}
