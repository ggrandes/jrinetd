package org.javastack.jrinetd;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public class Options {
	public static final String S_NULL = "";
	public static final Integer I_NULL = Integer.valueOf(0);
	public static final Long L_NULL = Long.valueOf(0);
	// Load Balancing Policies
	// @formatter:off
	public static final int LB_NONE      = 0x00000000; 	// None
	public static final int LB_RR        = 0x00000001; 	// Round robin
	public static final int LB_RAND      = 0x00000002; 	// Random pick
	public static final int LB_RANDRR    = 0x00000004; 	// Random Round robin
	public static final int LB_ORDER     = 0x00000008; 	// Original order, pick next only on error
	public static final int FAILOVER     = 0x00000010; 	// FailOver ON
	public static final int PROXY_SEND   = 0x00001000; 	// Send PROXY protocol (outbound)
	// @formatter:on
	//
	public static final String P_STICKY = "STICKY"; // STICKY=MEM:bitmask:elements:ttl:sticky-name[:cluster-name]
	//
	@SuppressWarnings("serial")
	private final static Map<String, Integer> MAP_FLAGS = Collections
			.unmodifiableMap(new HashMap<String, Integer>() {
				{
					put("LB=NONE", LB_NONE);
					put("LB=ORDER", LB_ORDER);
					put("LB=RR", LB_RR);
					put("LB=RAND", LB_RAND);
					put("LB=RANDRR", LB_RANDRR);
					put("FAILOVER", FAILOVER);
					put("PROXY=SEND", PROXY_SEND);
				}
			});
	//
	StickyConfig stickyConfig = null;
	int flags;
	@SuppressWarnings("serial")
	final Map<String, String> strParams = Collections.synchronizedMap(new HashMap<String, String>() {
		{
			put(P_STICKY, S_NULL);		// STICKY=<name>
		}
	});
	@SuppressWarnings("serial")
	final Map<String, Integer> intParams = Collections.synchronizedMap(new HashMap<String, Integer>() {
		{
			// put(P_CONNECT_TIMEOUT, I_NULL); // CONNECT_TIMEOUT=millis
			// put(P_READ_TIMEOUT, I_NULL); // READ_TIMEOUT=millis
		}
	});

	@SuppressWarnings("serial")
	final Map<String, Long> longParams = Collections.synchronizedMap(new HashMap<String, Long>() {
		{
			// put(P_CLUSTER_ID, L_NULL); // CLUSTER_ID=<idCluster>
		}
	});

	public Options(final String strOpts) {
		this.flags = parseOptions(strOpts);
	}

	// Clone Constructor
	public Options(final Options old) {
		this.flags = old.flags;
		for (Entry<String, String> e : old.strParams.entrySet()) {
			strParams.put(e.getKey(), e.getValue());
		}
		for (Entry<String, Integer> e : old.intParams.entrySet()) {
			intParams.put(e.getKey(), e.getValue());
		}
		for (Entry<String, Long> e : old.longParams.entrySet()) {
			longParams.put(e.getKey(), e.getValue());
		}
	}

	public int getFlags(final int filterBits) {
		return (flags & filterBits);
	}

	public void setFlags(final int bits) {
		flags |= bits;
	}

	public void unsetFlags(final int bits) {
		flags &= ~bits;
	}

	public String getString(final String name, final String def) {
		final String value = strParams.get(name);
		if (value == S_NULL) {
			return def;
		}
		return value;
	}

	public String getString(final String name) {
		return getString(name, null);
	}

	public void setString(final String name, String value) {
		if (value == null) {
			value = S_NULL;
		}
		strParams.put(name, value);
	}

	public Integer getInteger(final String name, final Integer def) {
		final Integer value = intParams.get(name);
		if (value == I_NULL) {
			return def;
		}
		return value;
	}

	public Integer getInteger(final String name) {
		return getInteger(name, null);
	}

	public void setInteger(final String name, Integer value) {
		if (value == null) {
			value = I_NULL;
		}
		intParams.put(name, value);
	}

	public Long getLong(final String name, final Long def) {
		final Long value = longParams.get(name);
		if (value == L_NULL) {
			return def;
		}
		return value;
	}

	public Long getLong(final String name) {
		return getLong(name, null);
	}

	public void setLong(final String name, Long value) {
		if (value == null) {
			value = L_NULL;
		}
		longParams.put(name, value);
	}

	/**
	 * Check is specified flag is active
	 * 
	 * @param opt
	 * @param FLAG
	 * @return true or false
	 */
	public boolean isOption(final int FLAG) {
		return ((flags & FLAG) != 0);
	}

	/**
	 * Return options in numeric form (bitwise-flags)
	 * 
	 * @param string to parse
	 * @return int with enabled flags
	 */
	int parseOptions(final String str) {
		final String[] opts = str.split(",");
		int ret = 0;
		for (String opt : opts) {
			final int KEY = 0, VALUE = 1;
			final String[] optKV = opt.split("=");
			// Process Flags
			final Integer f = MAP_FLAGS.get(opt.toUpperCase());
			if (f != null) {
				ret |= f.intValue();
			}
			// Process String Params
			final String s = strParams.get(optKV[KEY].toUpperCase());
			if (s != null) {
				strParams.put(optKV[KEY], optKV[VALUE]);
			}
			// Process Integer Params
			final Integer i = intParams.get(optKV[KEY].toUpperCase());
			if (i != null) {
				intParams.put(optKV[KEY], Integer.valueOf(optKV[VALUE]));
			}
			// Process Long Params
			final Long l = longParams.get(optKV[KEY].toUpperCase());
			if (l != null) {
				longParams.put(optKV[KEY], Long.valueOf(optKV[VALUE]));
			}
		}
		return ret;
	}

	/**
	 * For humans, return options parsed/validated
	 * 
	 * @return human readable string
	 */
	@Override
	public synchronized String toString() {
		int i = 0;
		final StringBuilder sb = new StringBuilder();
		// Flags
		for (Entry<String, Integer> e : MAP_FLAGS.entrySet()) {
			final String key = e.getKey();
			final Integer value = e.getValue();
			if ((flags & value) != 0) {
				if (i > 0)
					sb.append(",");
				sb.append(key);
				i++;
			}
		}
		// Strings
		for (Entry<String, String> e : strParams.entrySet()) {
			final String key = e.getKey();
			final String value = e.getValue();
			if (value != S_NULL) {
				if (i > 0)
					sb.append(",");
				sb.append(key).append("=").append(value);
				i++;
			}
		}
		// Integers
		for (Entry<String, Integer> e : intParams.entrySet()) {
			final String key = e.getKey();
			final Integer value = e.getValue();
			if (value != I_NULL) {
				if (i > 0)
					sb.append(",");
				sb.append(key).append("=").append(value);
				i++;
			}
		}
		// Longs
		for (Entry<String, Long> e : longParams.entrySet()) {
			final String key = e.getKey();
			final Long value = e.getValue();
			if (value != L_NULL) {
				if (i > 0)
					sb.append(",");
				sb.append(key).append("=").append(value);
				i++;
			}
		}
		return sb.toString();
	}

	public StickyConfig getStickyConfig() {
		// STICKY=MEM:bitmask:elements:ttl:sticky-name[:cluster-name]
		if (stickyConfig == null) {
			int i = 0;
			final String cfg = getString(P_STICKY);
			if (cfg == null) {
				stickyConfig = StickyConfig.NULL;
			} else {
				final String[] toks = cfg.split(":");
				final StickyConfig.Type type = StickyConfig.Type.valueOf(toks[i++].toUpperCase());
				final int bitmask = Short.parseShort(toks[i++]);
				final int elements = Integer.parseInt(toks[i++]);
				final int ttl = Integer.parseInt(toks[i++]);
				final String stickyName = toks[i++];
				final String clusterName = ((i < toks.length) ? toks[i++] : null);
				stickyConfig = StickyConfig.valueOf(type, bitmask, elements, ttl, clusterName, stickyName);
			}
		}
		return stickyConfig;
	}
}
