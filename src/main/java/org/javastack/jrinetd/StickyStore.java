package org.javastack.jrinetd;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public abstract class StickyStore<K extends InetAddress, V extends InetSocketAddress> {
	protected final StickyConfig stickyConfig;
	private boolean inUse = false;

	protected StickyStore(final StickyConfig stickyConfig) {
		this.stickyConfig = stickyConfig;
	}

	public StickyConfig getConfig() {
		return stickyConfig;
	}

	synchronized StickyStore<K, V> retain() {
		inUse = true;
		return this;
	}

	public synchronized StickyStore<K, V> release() {
		inUse = false;
		return this;
	}

	public synchronized boolean isReleased() {
		return !inUse;
	}

	/**
	 * Put Sticky
	 * 
	 * @param key
	 * @param value
	 */
	public abstract void put(final K key, final V value);

	/**
	 * Get Sticky value
	 * 
	 * @param key
	 * @return
	 */
	public abstract V get(final K key);

	/**
	 * Return inmutable list of associations
	 * 
	 * @return
	 */
	public abstract List<StickyEntry<K, V>> getEntries();

	static class StickyEntry<K extends InetAddress, V extends InetSocketAddress> {
		public final K key;
		public final V value;

		StickyEntry(final K key, final V value) {
			this.key = key;
			this.value = value;
		}
	}

	static class StickyStoreMEM<K extends InetAddress, V extends InetSocketAddress> extends StickyStore<K, V> {
		private final Map<K, TSEntry<V>> stickies;

		StickyStoreMEM(final StickyConfig stickyConfig) {
			super(stickyConfig);
			this.stickies = createMap();
		}

		private final Map<K, TSEntry<V>> createMap() {
			return new LinkedHashMap<K, TSEntry<V>>(16, 0.75f, true) {
				private static final long serialVersionUID = 42L;

				protected boolean removeEldestEntry(final Map.Entry<K, TSEntry<V>> eldest) {
					return size() > stickyConfig.elements;
				}
			};
		}

		@SuppressWarnings("unchecked")
		private final K maskKey(final K key) {
			return (K) IpAddress.getAddressMasked(key, stickyConfig.bitmask);
		}

		@Override
		public synchronized void put(final K key, final V value) {
			stickies.put(maskKey(key), new TSEntry<V>(value));
		}

		@Override
		public synchronized V get(final K key) {
			final TSEntry<V> e = stickies.get(maskKey(key));
			if (e != null) {
				final long now = System.currentTimeMillis();
				if (e.ts + (stickyConfig.ttlsec * 1000) >= now) {
					return e.value;
				}
			}
			return null;
		}

		@Override
		public synchronized List<StickyEntry<K, V>> getEntries() {
			final ArrayList<StickyEntry<K, V>> l = new ArrayList<StickyEntry<K, V>>();
			for (final Entry<K, TSEntry<V>> e : stickies.entrySet()) {
				l.add(new StickyEntry<K, V>(e.getKey(), e.getValue().value));
			}
			return l;
		}

		static class TSEntry<E> {
			final long ts;
			final E value;

			public TSEntry(final E value) {
				this.ts = System.currentTimeMillis();
				this.value = value;
			}
		}
	}

	static class StickyStoreNULL<K extends InetAddress, V extends InetSocketAddress> extends
			StickyStore<K, V> {
		StickyStoreNULL(final StickyConfig stickyConfig) {
			super(stickyConfig);
		}

		@Override
		public void put(final K key, final V value) {
		}

		@Override
		public V get(final K key) {
			return null;
		}

		@Override
		public List<StickyEntry<K, V>> getEntries() {
			return Collections.emptyList();
		}
	}

	/**
	 * Simple Test
	 */
	public static void main(final String[] args) throws Throwable {
		StickyConfig cfg = StickyConfig.valueOf(StickyConfig.Type.MEM, 32, 2, 1, "default", "default");
		StickyStoreFactory<InetAddress, InetSocketAddress> factory = new StickyStoreFactory<InetAddress, InetSocketAddress>();
		StickyStore<InetAddress, InetSocketAddress> store = factory.getInstance(cfg);
		InetAddress k1 = InetAddress.getByName("127.0.0.1");
		InetSocketAddress v1 = new InetSocketAddress(k1, 1234);
		store.put(k1, v1);
		InetAddress k2 = InetAddress.getByName("127.0.0.2");
		InetSocketAddress v2 = new InetSocketAddress(k2, 5678);
		store.put(k2, v2);
		InetAddress k3 = InetAddress.getByName("127.0.0.3");
		InetSocketAddress v3 = new InetSocketAddress(k1, 9012);
		store.put(k3, v3);
		//
		System.out.println(store.get(k1));
		System.out.println(store.get(k2));
		System.out.println(store.get(k3));
		Thread.sleep(1100);
		System.out.println(store.get(k3));
	}
}
