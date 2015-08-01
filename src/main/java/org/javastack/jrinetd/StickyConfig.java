package org.javastack.jrinetd;

public class StickyConfig {
	public static final StickyConfig NULL = new StickyConfig(Type.NULL, 0, 0, 0, StickyKey.DEFAULT);
	public final Type type;
	public final int bitmask;
	public final int elements;
	public final int ttlsec;
	public final StickyKey stickyKey;

	private StickyConfig(final Type type, final int bitmask, final int elements, final int ttlsec,
			final StickyKey stickyKey) {
		this.type = type;
		this.bitmask = bitmask;
		this.elements = elements;
		this.ttlsec = ttlsec;
		this.stickyKey = stickyKey;
	}

	public static StickyConfig valueOf(final Type type, final int bitmask, final int elements,
			final int ttlsec, final String clusterName, final String stickyName) {
		return new StickyConfig(type, bitmask, elements, ttlsec, StickyKey.valueOf(clusterName, stickyName));
	}

	public boolean isReplicated() {
		return ((stickyKey.clusterId > 0) && (stickyKey.stickyId > 0));
	}

	@Override
	public String toString() {
		return super.toString() + "[type=" + type + " bitmask=" + bitmask + " elements=" + elements
				+ " ttlsec=" + ttlsec + " stickyKey=" + stickyKey + "]";
	}

	public enum Type {
		/**
		 * NULL
		 */
		NULL,
		/**
		 * MEMORY
		 */
		MEM;
	}
}
