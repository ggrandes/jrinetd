package org.javastack.jrinetd;

import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Simple logging wrapper (you want log4j/logback/slfj? easy to do!)
 */
public class Log {
	public static final int LOG_NULL = 0x00;
	public static final int LOG_CURR_STDOUT = 0x01;
	public static final int LOG_ORIG_STDOUT = 0x02;

	private static final String DEBUG = "DEBUG", INFO = "INFO", WARN = "WARN", ERROR = "ERROR";
	private static final SimpleDateFormat ISO8601DATEFORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
	private static final StringBuilder sb = new StringBuilder();
	private static boolean isDebugEnabled = false;
	private static int outMode = LOG_CURR_STDOUT;
	private static PrintStream stdOut = System.out;
	private static PrintStream stdErr = System.err;

	static void setMode(final int newMode) {
		outMode = newMode;
	}

	static boolean isModeOrigEnabled() {
		return ((outMode & LOG_ORIG_STDOUT) != 0);
	}

	static boolean isModeCurrEnabled() {
		return ((outMode & LOG_CURR_STDOUT) != 0);
	}

	static void redirStdOutLog(final String stdFile) {
		System.setOut(new PrintStream(new AutoRotateFileOutputStream(stdFile)));
	}

	static void restoreStdOutLog() {
		System.setOut(stdOut);
	}

	static void redirStdErrLog(final String errFile) {
		System.setErr(new PrintStream(new AutoRotateFileOutputStream(errFile)));
	}

	static void restoreStdErrLog() {
		System.setErr(stdErr);
	}

	static void enableDebug() {
		isDebugEnabled = true;
	}

	static boolean isDebugEnabled() {
		return isDebugEnabled;
	}

	private static final String getThreadName() {
		return Thread.currentThread().getName();
	}

	private static String getLine(final String level, final String id, final String msg) {
		synchronized (sb) {
			sb.setLength(0);
			sb.append(ISO8601DATEFORMAT.format(new Date())) // Time
					.append(" [").append(level).append("] ") // Level
					.append("[").append(id).append("] ") // ID
					.append("[").append(getThreadName()).append("] ") // Thread Name
					.append(msg); // Message
			return sb.toString();
		}
	}

	static void debug(final String id, final String str) {
		if (isDebugEnabled) {
			final String msg = getLine(DEBUG, id, str);
			if (isModeOrigEnabled())
				stdOut.println(msg);
			if (isModeCurrEnabled())
				System.out.println(msg);
		}
	}

	static void info(final String id, final String str) {
		final String msg = getLine(INFO, id, str);
		if (isModeOrigEnabled())
			stdOut.println(msg);
		if (isModeCurrEnabled())
			System.out.println(msg);
	}

	static void warn(final String id, final String str) {
		final String msg = getLine(WARN, id, str);
		if (isModeOrigEnabled())
			stdOut.println(msg);
		if (isModeCurrEnabled())
			System.out.println(msg);
	}

	static void error(final String id, final String str) {
		final String msg = getLine(ERROR, id, str);
		if (isModeOrigEnabled())
			stdOut.println(msg);
		if (isModeCurrEnabled())
			System.out.println(msg);
	}

	static void error(final String id, final String str, final Throwable t) {
		final String msg = getLine(ERROR, id, str);
		if (isModeOrigEnabled()) {
			synchronized (stdOut) {
				stdOut.println(msg);
				t.printStackTrace(stdOut);
			}
		}
		if (isModeCurrEnabled()) {
			synchronized (System.out) {
				System.out.println(msg);
				t.printStackTrace(System.out);
			}
		}
	}
}
