package org.javastack.jrinetd;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.javastack.jrinetd.LoadBalanceStrategy.LoadBalanceContext;

public class Endpoint {
	private static final DNSCache cache = new DNSCache();

	private final EndpointAddress[] addresses;
	private final LoadBalanceStrategy<InetAddress, InetSocketAddress> loadBalancing;
	private boolean used = false;
	private long expire = 0;

	public Endpoint(final String address,
			final LoadBalanceStrategy<InetAddress, InetSocketAddress> loadBalancing)
			throws UnknownHostException {
		this.addresses = EndpointAddress.valueOf(address);
		this.loadBalancing = loadBalancing;
		resolve(); // Try to resolve
	}

	public synchronized boolean isUsed() {
		return used;
	}

	public synchronized boolean isExpired() {
		return (System.currentTimeMillis() > expire);
	}

	public void resolve() throws UnknownHostException {
		final ArrayList<InetSocketAddress> inetAddr = new ArrayList<InetSocketAddress>(addresses.length);
		for (int i = 0; i < addresses.length; i++) {
			final List<InetAddress> la = cache.getAddressList(addresses[i].host);
			if ((la != null) && !la.isEmpty()) {
				for (int j = 0; j < la.size(); j++) {
					final InetAddress a = la.get(j);
					inetAddr.add(new InetSocketAddress(a, addresses[i].port));
				}
			}
		}
		if (inetAddr.isEmpty()) {
			throw new UnknownHostException(addresses == null ? "<NULL>" : String.valueOf(Arrays
					.asList(addresses)));
		}
		synchronized (this) {
			this.used = false;
			this.expire = System.currentTimeMillis() + Constants.ADDR_EXPIRE_TIME;
			loadBalancing.onResolve(inetAddr);
		}
		Log.info(getClass().getSimpleName(), "Resolved endpoint=" + Arrays.asList(addresses) + " as "
				+ inetAddr);
	}

	public synchronized LoadBalanceContext<InetAddress, InetSocketAddress> createLoadBalanceContext(
			final InetAddress stickyAddr) {
		return loadBalancing.createContext(stickyAddr);
	}

	public synchronized InetSocketAddress onConnect(
			final LoadBalanceContext<InetAddress, InetSocketAddress> ctx) {
		used = true;
		return loadBalancing.onConnect(ctx);
	}

	public synchronized void onConnectFinished(final LoadBalanceContext<InetAddress, InetSocketAddress> ctx) {
		loadBalancing.onConnectFinished(ctx);
	}

	public synchronized boolean canRetry(final LoadBalanceContext<InetAddress, InetSocketAddress> ctx) {
		return loadBalancing.canRetry(ctx);
	}

	static class EndpointAddress {
		final String host;
		final int port;

		EndpointAddress(final String host, final int port) {
			this.host = host;
			this.port = port;
		}

		static EndpointAddress[] valueOf(final String addressList) {
			final String tokA[] = addressList.split(",");
			final EndpointAddress[] addresses = new EndpointAddress[tokA.length];
			for (int i = 0; i < addresses.length; i++) {
				final String[] tokHP = tokA[i].split(":", 2);
				final String host = tokHP[0];
				final int port = Integer.valueOf(tokHP[1]);
				addresses[i] = new EndpointAddress(host, port);
			}
			return addresses;
		}

		@Override
		public String toString() {
			return host + ":" + port;
		}
	}
}
