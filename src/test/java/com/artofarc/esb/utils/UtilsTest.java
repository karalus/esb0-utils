package com.artofarc.esb.utils;

import org.junit.Test;

public class UtilsTest {

	@Test
	public void testThreadDumper() {
		System.out.println(ThreadDumper.dumpThreads());
	}

}
