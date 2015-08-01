package org.javastack.jrinetd;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;

import org.javastack.jrinetd.StickyStore.StickyStoreMEM;
import org.javastack.jrinetd.StickyStore.StickyStoreNULL;

public class StickyStoreFactory<K extends InetAddress, V extends InetSocketAddress> {
	private final LinkedHashMap<StickyKey, StickyStore<K, V>> instances = new LinkedHashMap<StickyKey, StickyStore<K, V>>();

	/**
	 * Get Instance of StickyStore
	 * 
	 * @param key
	 * @return
	 */
	public synchronized StickyStore<K, V> getInstance(final StickyKey key) {
		return instances.get(key);
	}

	/**
	 * Get Instances of StickyStore
	 * 
	 * @return
	 */
	public synchronized List<StickyStore<K, V>> getStores() {
		return new ArrayList<StickyStore<K, V>>(instances.values());
	}

	/**
	 * Get Instance of StickyStore
	 * 
	 * @param key
	 * @return
	 */
	synchronized void unregister(final StickyKey key) {
		instances.remove(key);
	}

	/**
	 * Get or Create Instance of StickyStore
	 * 
	 * @param stickyConfig
	 * @return
	 */
	public synchronized StickyStore<K, V> getInstance(final StickyConfig stickyConfig) {
		StickyStore<K, V> store = instances.get(stickyConfig.stickyKey);
		if (store == null) {
			switch (stickyConfig.type) {
				case MEM:
					Log.info(getClass().getSimpleName(), "New StickyStoreMEM config=" + stickyConfig);
					store = new StickyStoreMEM<K, V>(stickyConfig).retain();
					break;
				case NULL:
					Log.info(getClass().getSimpleName(), "New StickyStoreNULL config=" + stickyConfig);
					store = new StickyStoreNULL<K, V>(stickyConfig).retain();
					break;
			}
			if (store != null) {
				instances.put(stickyConfig.stickyKey, store);
			}
		}
		return store.retain();
	}

	public synchronized void releaseAll() {
		for (final Entry<StickyKey, StickyStore<K, V>> e : instances.entrySet()) {
			e.getValue().release();
		}
	}

	public synchronized void unregisterReleased() {
		final ArrayList<StickyKey> free = new ArrayList<StickyKey>();
		for (final Entry<StickyKey, StickyStore<K, V>> e : instances.entrySet()) {
			if (e.getValue().isReleased()) {
				Log.info(Listeners.class.getName(), "Unregistering: " + e.getValue().getConfig());
				free.add(e.getKey());
			}
		}
		for (final StickyKey f : free) {
			unregister(f);
		}
	}
}
