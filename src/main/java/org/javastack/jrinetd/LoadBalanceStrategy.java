package org.javastack.jrinetd;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public abstract class LoadBalanceStrategy<K extends InetAddress, V extends InetSocketAddress> implements
		NodeListChangeEvent<V> {
	protected final StickyStore<K, V> stickies;
	protected final boolean useFailOver;
	protected List<V> address;

	public LoadBalanceStrategy(final StickyStore<K, V> stickies, final boolean useFailOver) {
		this.stickies = stickies;
		this.useFailOver = useFailOver;
	}

	public StickyConfig getStickyConfig() {
		return stickies.getConfig();
	}

	/**
	 * Invoked after list created for special ordering of the nodes
	 * 
	 * @param address
	 */
	@Override
	public void onResolve(final List<V> address) {
		this.address = address;
	}

	/**
	 * Request new Context
	 * 
	 * @param stickyAddr
	 * @return
	 */
	public LoadBalanceContext<K, V> createContext(final K stickyAddr) {
		return new LoadBalanceContext<K, V>(this, stickyAddr);
	}

	/**
	 * Invoked before connect to get next node
	 * 
	 * @param ctx
	 * @return
	 */
	public V onConnect(final LoadBalanceContext<K, V> ctx) {
		// Find on Sticky only first time
		if (ctx.getRemoteAddress() != null) {
			return null;
		}
		// OK, find on stickies
		return ctx.setRemoteAddress(findSticky(ctx.getStickyAddress()));
	}

	/**
	 * Find remote address for specified sticky address
	 * 
	 * @param stickyAddr
	 * @return
	 */
	protected V findSticky(final K stickyAddr) {
		// First, try sticky, if any...
		if ((stickies != null) && (stickyAddr != null)) {
			final V addr = stickies.get(stickyAddr);
			if (addr != null) {
				// Check if addr remain valid
				final int len = address.size();
				for (int i = 0; i < len; i++) { // TODO: Improve this (Set?)
					final V a = address.get(i);
					if (addr.equals(a)) {
						Log.info(getClass().getSimpleName(), "Sticky id=" + stickyAddr + " result=" + addr);
						return addr;
					}
				}
			}
		}
		// Sorry, not found...
		return null;
	}

	/**
	 * Invoked after connect is done
	 * 
	 * @param ctx
	 */
	public void onConnectFinished(final LoadBalanceContext<K, V> ctx) {
		stickies.put(ctx.getStickyAddress(), ctx.getRemoteAddress());
	}

	/**
	 * Has more nodes for retry?
	 * 
	 * @param ctx
	 */
	public boolean canRetry(final LoadBalanceContext<K, V> ctx) {
		return useFailOver;
	}

	public static class NoStrategy<K extends InetAddress, V extends InetSocketAddress> extends
			LoadBalanceStrategy<K, V> {
		public NoStrategy(final StickyStore<K, V> stickies, final boolean useFailOver) {
			super(stickies, useFailOver);
		}

		@Override
		public V onConnect(final LoadBalanceContext<K, V> ctx) {
			if (address.isEmpty())
				return null;
			return address.get(0);
		}

		@Override
		public boolean canRetry(final LoadBalanceContext<K, V> ctx) {
			return false;
		}
	}

	public static class RandomStrategy<K extends InetAddress, V extends InetSocketAddress> extends
			LoadBalanceStrategy<K, V> {
		final Random r = new Random();
		protected int current = 0;

		public RandomStrategy(final StickyStore<K, V> stickies, final boolean useFailOver) {
			super(stickies, useFailOver);
		}

		@Override
		public LoadBalanceContext<K, V> createContext(final K stickyAddr) {
			current = address.size();
			return super.createContext(stickyAddr);
		}

		@Override
		public V onConnect(final LoadBalanceContext<K, V> ctx) {
			final V sticky = super.onConnect(ctx);
			if (sticky != null) {
				return sticky;
			}
			if (address.isEmpty())
				return null;
			current--;
			return ctx.setRemoteAddress(address.get((r.nextInt() & Integer.MAX_VALUE) % address.size()));
		}

		@Override
		public boolean canRetry(final LoadBalanceContext<K, V> ctx) {
			return (super.canRetry(ctx) && (current > 0));
		}
	}

	public static class RoundRobinStrategy<K extends InetAddress, V extends InetSocketAddress> extends
			LoadBalanceStrategy<K, V> {
		protected int current = 0;

		public RoundRobinStrategy(final StickyStore<K, V> stickies, final boolean useFailOver) {
			super(stickies, useFailOver);
		}

		@Override
		public LoadBalanceContext<K, V> createContext(final K stickyAddr) {
			return super.createContext(stickyAddr).set(new RingIterator<V>(address, current++));
		}

		@Override
		public V onConnect(final LoadBalanceContext<K, V> ctx) {
			final V sticky = super.onConnect(ctx);
			if (sticky != null) {
				return sticky;
			}
			return ctx.nextAndSet();
		}

		@Override
		public boolean canRetry(final LoadBalanceContext<K, V> ctx) {
			return (super.canRetry(ctx) && ctx.hasNext());
		}
	}

	public static class OrderedRoundRobinStrategy<K extends InetAddress, V extends InetSocketAddress> extends
			RoundRobinStrategy<K, V> {
		private final Comparator<V> comparator;

		public OrderedRoundRobinStrategy(final StickyStore<K, V> stickies, final boolean useFailOver,
				final Comparator<V> comparator) {
			super(stickies, useFailOver);
			this.comparator = comparator;
		}

		@Override
		public void onResolve(final List<V> list) {
			super.onResolve(list);
			Collections.sort(this.address, comparator);
		}
	}

	public static class RandomRoundRobinStrategy<K extends InetAddress, V extends InetSocketAddress> extends
			RoundRobinStrategy<K, V> {
		public RandomRoundRobinStrategy(final StickyStore<K, V> stickies, final boolean useFailOver) {
			super(stickies, useFailOver);
		}

		@Override
		public void onResolve(final List<V> list) {
			super.onResolve(list);
			Collections.shuffle(this.address);
		}
	}

	public static class LoadBalanceContext<K extends InetAddress, V extends InetSocketAddress> {
		private final LoadBalanceStrategy<K, V> strategy;
		private final K stickyAddress;
		private Iterator<V> i = null;
		private V remoteAddress = null;

		private LoadBalanceContext(final LoadBalanceStrategy<K, V> strategy, final K stickyAddress) {
			this.strategy = strategy;
			this.stickyAddress = stickyAddress;
		}

		public LoadBalanceStrategy<K, V> getStrategy() {
			return strategy;
		}

		public K getStickyAddress() {
			return stickyAddress;
		}

		public V getRemoteAddress() {
			return remoteAddress;
		}

		protected V setRemoteAddress(final V remoteAddress) {
			return (this.remoteAddress = remoteAddress);
		}

		protected LoadBalanceContext<K, V> set(final Iterator<V> i) {
			this.i = i;
			return this;
		}

		protected boolean hasNext() {
			return i.hasNext();
		}

		protected V nextAndSet() {
			return setRemoteAddress(i.hasNext() ? i.next() : null);
		}
	}
}
