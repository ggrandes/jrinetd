package org.javastack.jrinetd;

import java.net.InetSocketAddress;
import java.util.Comparator;

public class InetSocketAddressComparator implements Comparator<InetSocketAddress> {
	private static final InetSocketAddressComparator singleton = new InetSocketAddressComparator();

	private InetSocketAddressComparator() {
	}

	public static InetSocketAddressComparator getInstance() {
		return singleton;
	}

	@Override
	public int compare(final InetSocketAddress o1, final InetSocketAddress o2) {
		final byte[] a1 = o1.getAddress().getAddress();
		final byte[] a2 = o2.getAddress().getAddress();
		final int len = Math.min(a1.length, a2.length);
		for (int i = 0; i < len; i++) {
			int b1 = (int) a1[i] & 0xFF;
			int b2 = (int) a2[i] & 0xFF;
			if (b1 == b2) {
				continue;
			} else if (b1 < b2) {
				return -1;
			} else {
				return 1;
			}
		}
		if (a1.length < a2.length) {
			return -1;
		}
		if (a1.length > a2.length) {
			return 1;
		}
		final int p1 = o1.getPort();
		final int p2 = o2.getPort();
		if (p1 < p2) {
			return -1;
		} else if (p1 > p2) {
			return 1;
		}
		return 0;
	}
}
