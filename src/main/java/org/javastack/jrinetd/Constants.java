package org.javastack.jrinetd;

public class Constants {
	public static final String VERSION_FILE = "/jrinetd-version.mf";

	// System properties (logs)
	public static final String PROP_OUT_FILE = "log.stdOutFile";
	public static final String PROP_ERR_FILE = "log.stdErrFile";
	public static final String PROP_OUT_STDTOO = "log.stdToo";

	public static final int RELOAD_CONFIG = 10000; 			// Default 10seconds
	public static final int RELOAD_TIMEOUT = 30000; 		// Default 30seconds timeout
	public static final int BUFFER_LEN = 2048; 				// Default 2k page
	public static final int BUFFER_POOL_SIZE = 8192;		// Default 8192 elements (max)
	public static final int SELECT_TIMEOUT = 1000; 			// Default 1second timeout
	public static final int ADDR_EXPIRE_TIME = 300000; 		// Default 5min
	public static final int DNS_CACHE_TIME = 3000; 			// Default 3seconds
	public static final boolean DNS_CACHE_NEGATIVE = true;  // Default true (negative response cache)

	// Clean ByteBuffers for paranoids
	public static final boolean CLEAN_BUF_ONREUSE = false;    // clean buffer after write
	public static final boolean CLEAN_BUF_ONRELEASE = false;  // clean buffer after close connection

	public static final int CLUSTER_ACCEPT_TIMEOUT = 1000;		// Default 1second timeout
	public static final int CLUSTER_CONNECT_TIMEOUT = 10000;	// Default 10seconds timeout
	public static final int CLUSTER_READ_TIMEOUT = 2000;		// Default 2seconds timeout
}
