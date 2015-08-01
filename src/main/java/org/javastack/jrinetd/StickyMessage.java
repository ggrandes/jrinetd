package org.javastack.jrinetd;

import java.net.InetAddress;
import java.net.InetSocketAddress;

public class StickyMessage {
	public final long stickyId;
	public final InetAddress stickyAddress;
	public final InetSocketAddress remoteAddress;

	public StickyMessage(final long stickyId, final InetAddress stickyAddress, final InetSocketAddress remoteAddress) {
		this.stickyId = stickyId;
		this.stickyAddress = stickyAddress;
		this.remoteAddress = remoteAddress;
	}
}
