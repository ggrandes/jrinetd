/*
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package org.javastack.jrinetd;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.javastack.jrinetd.GenericPool.GenericPoolFactory;
import org.javastack.jrinetd.Listeners.Listener;
import org.javastack.jrinetd.LoadBalanceStrategy.NoStrategy;
import org.javastack.jrinetd.LoadBalanceStrategy.OrderedRoundRobinStrategy;
import org.javastack.jrinetd.LoadBalanceStrategy.RandomRoundRobinStrategy;
import org.javastack.jrinetd.LoadBalanceStrategy.RandomStrategy;
import org.javastack.jrinetd.LoadBalanceStrategy.RoundRobinStrategy;

/**
 * Basic Server
 * 
 * @author Guillermo Grandes / guillermo.grandes[at]gmail.com
 */
public class Server implements Runnable {
	private static final AtomicInteger runningServers = new AtomicInteger(0);
	private static final AtomicInteger idSeq = new AtomicInteger();
	private static final GenericPoolFactory<ByteBuffer> byteBufferFactory = new GenericPoolFactory<ByteBuffer>() {
		@Override
		public ByteBuffer newInstance() {
			return ByteBuffer.allocateDirect(Constants.BUFFER_LEN);
		}
	};
	private static final StickyStoreFactory<InetAddress, InetSocketAddress> stickyFactory = new StickyStoreFactory<InetAddress, InetSocketAddress>();

	private final InetSocketAddress listenAddress;
	private final Endpoint remoteAddress;
	private final Options opts;
	private final GlobalEventHandler events;

	private final int id = getId();
	private final AtomicBoolean running = new AtomicBoolean(false);
	private final GenericPool<ByteBuffer> poolBB = new GenericPool<ByteBuffer>(byteBufferFactory,
			Constants.BUFFER_POOL_SIZE);
	private final CopyOnWriteArrayList<ServerEventHandler> handlers = new CopyOnWriteArrayList<ServerEventHandler>();
	private final AtomicInteger handler = new AtomicInteger(0);

	private final ThreadPool tp;
	private final Listener listener;
	private long started = 0;

	public Server(final ThreadPool tp, final Listeners listeners, final String listenAddress, final String remoteAddress,
			final Options opts, final GlobalEventHandler events) throws IOException {
		this.tp = tp;
		this.listenAddress = IOHelper.parseAddress(listenAddress);
		this.remoteAddress = new Endpoint(remoteAddress, getLoadBalanceStrategy(opts));
		this.opts = opts;
		this.events = events;
		try {
			listener = listeners.getServerSocketChannel(this.listenAddress);
		} catch (IOException e) {
			Log.error(getName(), "IOException on Server[" + listenAddress + "]: " + e.toString());
			throw e;
		}
	}

	public static StickyStoreFactory<InetAddress, InetSocketAddress> getStickyFactory() {
		return stickyFactory;
	}

	public static int getRunningServers() {
		return runningServers.get();
	}

	public static int getId() {
		return idSeq.incrementAndGet();
	}

	public String getName() {
		return Integer.toHexString(id | Integer.MIN_VALUE);
	}

	public InetSocketAddress getListenAddress() {
		return listenAddress;
	}

	public Endpoint getEndPoint() {
		return remoteAddress;
	}

	public Options getOpts() {
		return opts;
	}

	public GlobalEventHandler getGlobalEventHandler() {
		return events;
	}

	LoadBalanceStrategy<InetAddress, InetSocketAddress> getLoadBalanceStrategy(final Options opts) {
		final StickyConfig stickyConfig = opts.getStickyConfig();
		final StickyStore<InetAddress, InetSocketAddress> stickies = stickyFactory.getInstance(stickyConfig);
		final boolean useFailOver = opts.isOption(Options.FAILOVER);
		final int filterFlags = (Options.LB_ORDER | Options.LB_RR | Options.LB_RAND | Options.LB_RANDRR);
		switch (opts.getFlags(filterFlags)) {
			case Options.LB_ORDER:
				return new OrderedRoundRobinStrategy<InetAddress, InetSocketAddress>(stickies, useFailOver,
						InetSocketAddressComparator.getInstance());
			case Options.LB_RR:
				return new RoundRobinStrategy<InetAddress, InetSocketAddress>(stickies, useFailOver);
			case Options.LB_RAND:
				return new RandomStrategy<InetAddress, InetSocketAddress>(stickies, useFailOver);
			case Options.LB_RANDRR:
				return new RandomRoundRobinStrategy<InetAddress, InetSocketAddress>(stickies, useFailOver);
		}
		return new NoStrategy<InetAddress, InetSocketAddress>(stickies, useFailOver);
	}

	public int getUptime() {
		return (int) ((System.currentTimeMillis() - started) / 1000);
	}

	public ByteBuffer allocateByteBuffer() {
		return poolBB.checkout();
	}

	public void releaseByteBuffer(final ByteBuffer bb) {
		if (Constants.CLEAN_BUF_ONRELEASE) {
			bb.clear();
			IOHelper.cleanBuffer(bb);
		}
		bb.clear();
		poolBB.release(bb);
	}

	public boolean isRunning() {
		return running.get();
	}

	public void shutdown() {
		listener.release();
		running.set(false);
		listener.s.wakeup();
	}

	public ServerEventHandler getClientHandler() {
		if (handlers.isEmpty()) {
			return null;
		}
		return handlers.get((handler.incrementAndGet() & Integer.MAX_VALUE) % handlers.size());
	}

	@Override
	public void run() {
		runningServers.incrementAndGet();
		running.set(true);
		try {
			for (int i = 0; i < listener.clientSelectors(); i++) {
				final ServerEventHandler handler = new ServerEventHandler(this, listener.get(i));
				handlers.add(handler);
				tp.newTask(handler);
			}
			Thread.currentThread().setName(
					"srv-" + Server.getId() + "-" + IOHelper.inetAddrToHoman(getListenAddress()));
			Log.info(getName(), "Accepting connections on " + IOHelper.inetAddrToHoman(getListenAddress()));
			final ServerEventHandler handler = new ServerEventHandler(this, listener.s);
			while (isRunning()) {
				handler.process();
			}
		} catch (Throwable t) {
			Log.error(getName(), "Unhandled Exception: " + t.toString(), t);
		} finally {
			running.compareAndSet(true, false);
			runningServers.decrementAndGet();
			Log.info(getName(), "Ending handler on " + IOHelper.inetAddrToHoman(listenAddress));
		}
	}
}
