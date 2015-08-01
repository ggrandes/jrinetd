package org.javastack.jrinetd;

import java.net.InetAddress;
import java.net.InetSocketAddress;

import org.javastack.jrinetd.BIOConnection.Connection;
import org.javastack.jrinetd.LoadBalanceStrategy.LoadBalanceContext;

public interface GlobalEventHandler {
	public void onStickyFromLocal(final BridgeContext bc,
			final LoadBalanceContext<InetAddress, InetSocketAddress> loadBalanceContext);

	public void onStickyFromCluster(final long clusterId, final StickyMessage msg);

	public void onClusterClient(final long clusterId, final Connection c);
}
