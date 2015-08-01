package org.javastack.jrinetd;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DNSCache {
	private final Map<String, CacheEntry> cache = new ConcurrentHashMap<String, CacheEntry>();

	public List<InetAddress> getAddressList(final String host) {
		CacheEntry entry = cache.get(host);
		if ((entry == null) || entry.isStale()) {
			final List<InetAddress> address = initialValue(host);
			if ((address != null) || Constants.DNS_CACHE_NEGATIVE) {
				entry = new CacheEntry(address);
				cache.put(host, entry);
			}
		}
		return (entry == null ? null : entry.addr);
	}

	protected List<InetAddress> initialValue(final String host) {
		try {
			return Arrays.asList(InetAddress.getAllByName(host));
		} catch (Exception e) {
			Log.error(DNSCache.class.getSimpleName(), "Unknown Host: " + host + " [" + e.toString() + "]");
		}
		return null;
	}

	static class CacheEntry {
		final List<InetAddress> addr;
		final long expire;

		CacheEntry(final List<InetAddress> addr) {
			this.addr = addr;
			this.expire = System.currentTimeMillis() + Constants.DNS_CACHE_TIME;
		}

		boolean isStale() {
			return (System.currentTimeMillis() > expire);
		}
	}
}
