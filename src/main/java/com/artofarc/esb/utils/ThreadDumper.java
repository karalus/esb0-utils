package com.artofarc.esb.utils;

import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;

public class ThreadDumper {

	public static String dumpThreads() {
		StringBuilder builder = new StringBuilder();
		ThreadMXBean bean = ManagementFactory.getThreadMXBean();
		bean.setThreadContentionMonitoringEnabled(true);
		long currentThreadId = Thread.currentThread().getId();
		for (ThreadInfo info : bean.dumpAllThreads(true, true)) {
			if (info.getThreadId() != currentThreadId) {
				append(builder, info);
			}
		}
		return builder.toString();
	}

	// @see java.lang.management.ThreadInfo
	private static void append(StringBuilder sb, ThreadInfo info) {
		sb.append("\"" + info.getThreadName() + "\"" + " Id=" + info.getThreadId() + " Blocked=" + info.getBlockedTime() + "ms Waited=" + info.getWaitedTime() + "ms " + info.getThreadState());
		if (info.getLockName() != null) {
			sb.append(" on " + info.getLockName());
		}
		if (info.getLockOwnerName() != null) {
			sb.append(" owned by \"" + info.getLockOwnerName() + "\" Id=" + info.getLockOwnerId());
		}
		if (info.isSuspended()) {
			sb.append(" (suspended)");
		}
		if (info.isInNative()) {
			sb.append(" (in native)");
		}
		sb.append('\n');
		StackTraceElement[] stackTrace = info.getStackTrace();
		int i = 0;
		for (; i < stackTrace.length; i++) {
			StackTraceElement ste = stackTrace[i];
			sb.append("\tat " + ste.toString());
			sb.append('\n');
			if (i == 0 && info.getLockInfo() != null) {
				Thread.State ts = info.getThreadState();
				switch (ts) {
				case BLOCKED:
					sb.append("\t-  blocked on " + info.getLockInfo());
					sb.append('\n');
					break;
				case WAITING:
					sb.append("\t-  waiting on " + info.getLockInfo());
					sb.append('\n');
					break;
				case TIMED_WAITING:
					sb.append("\t-  waiting on " + info.getLockInfo());
					sb.append('\n');
					break;
				default:
				}
			}

			for (MonitorInfo mi : info.getLockedMonitors()) {
				if (mi.getLockedStackDepth() == i) {
					sb.append("\t-  locked " + mi);
					sb.append('\n');
				}
			}
		}

		LockInfo[] locks = info.getLockedSynchronizers();
		if (locks.length > 0) {
			sb.append("\n\tNumber of locked synchronizers = " + locks.length);
			sb.append('\n');
			for (LockInfo li : locks) {
				sb.append("\t- " + li);
				sb.append('\n');
			}
		}
		sb.append('\n');
	}

}
