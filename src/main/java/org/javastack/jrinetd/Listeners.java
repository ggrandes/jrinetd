package org.javastack.jrinetd;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.concurrent.CopyOnWriteArrayList;

public class Listeners {
	private final HashMap<InetSocketAddress, Listener> listeners = new HashMap<InetSocketAddress, Listener>();

	public synchronized Listener getServerSocketChannel(final InetSocketAddress listenAddress)
			throws IOException {
		Listener listener = listeners.get(listenAddress);
		if (listener == null) {
			listener = createServerSocketChannel(listenAddress);
			listeners.put(listenAddress, listener);
		}
		return listener.retain();
	}

	public synchronized void unregister(final InetSocketAddress listenAddress) {
		listeners.remove(listenAddress);
	}

	public synchronized void close(final InetSocketAddress listenAddress) {
		final Listener listener = listeners.get(listenAddress);
		if (listener != null) {
			IOHelper.closeSilent(listener);
			listeners.remove(listenAddress);
		}
	}

	public synchronized void closeReleased() {
		for (final Entry<InetSocketAddress, Listener> e : listeners.entrySet()) {
			if (e.getValue().isReleased()) {
				Log.info(Listeners.class.getName(), "Closing: " + IOHelper.inetAddrToHoman(e.getKey()));
				IOHelper.closeSilent(e.getValue());
			}
		}
	}

	@Override
	public synchronized Listeners clone() {
		final Listeners cloned = new Listeners();
		cloned.listeners.putAll(listeners);
		return cloned;
	}

	private final Listener createServerSocketChannel(final InetSocketAddress listenAddress)
			throws IOException {
		ServerSocketChannel ssc = null;
		Selector s = null;
		Listener l = null;
		try {
			ssc = ServerSocketChannel.open();
			ssc.configureBlocking(false);
			IOHelper.setupSocket(ssc.socket(), listenAddress);
			s = Selector.open();
			ssc.register(s, SelectionKey.OP_ACCEPT);
			l = new Listener(ssc, s);
			final int process = Runtime.getRuntime().availableProcessors();
			for (int i = 0; i < process; i++) {
				l.add(Selector.open());
			}
		} catch (IOException e) {
			IOHelper.closeSilent(l);
			IOHelper.closeSilent(s);
			IOHelper.closeSilent(ssc);
			throw e;
		}
		return l;
	}

	public static class Listener implements Closeable {
		public final ServerSocketChannel ssc;
		public final Selector s;
		private final CopyOnWriteArrayList<Selector> clients = new CopyOnWriteArrayList<Selector>();
		private boolean inUse = false;

		public Listener(final ServerSocketChannel ssc, final Selector s) {
			this.ssc = ssc;
			this.s = s;
		}

		public int clientSelectors() {
			return clients.size();
		}

		public void add(final Selector s) {
			clients.add(s);
		}

		public Selector get(final int selectorNum) {
			if (clients.isEmpty())
				return null;
			return clients.get((selectorNum & Integer.MAX_VALUE) % clients.size());
		}

		synchronized Listener retain() {
			inUse = true;
			return this;
		}

		public synchronized Listener release() {
			inUse = false;
			return this;
		}

		public synchronized boolean isReleased() {
			return !inUse;
		}

		@Override
		public void close() throws IOException {
			for (final Selector s : clients) {
				IOHelper.closeSilent(s);
			}
			IOHelper.closeSilent(s);
			IOHelper.closeSilent(ssc);
		}
	}
}
