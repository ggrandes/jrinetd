package org.javastack.jrinetd;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.javastack.jrinetd.BIOConnection.Connection;
import org.javastack.jrinetd.BIOConnection.Listener;

public abstract class Cluster implements Runnable {
	private static final int OP_NOP = 0x00;
	private static final int OP_NEW = 0x01;

	private static final ExecutorService threadPool = Executors.newCachedThreadPool();
	private static final AtomicInteger runningClusters = new AtomicInteger(0);
	private static final AtomicInteger idSeq = new AtomicInteger();
	private static final HashSet<Cluster> instances = new HashSet<Cluster>();

	final long clusterId;
	final InetSocketAddress address;
	final Options opts;
	final GlobalEventHandler events;

	final int id = getId();
	final AtomicBoolean running = new AtomicBoolean(false);

	public Cluster(final String clusterName, final String address, final Options opts,
			final GlobalEventHandler events) throws IOException {
		this.clusterId = IOHelper.longIdFromString(clusterName);
		this.address = IOHelper.parseAddress(address);
		this.opts = opts;
		this.events = events;
		synchronized (instances) {
			instances.add(this);
		}
	}

	public static Cluster getInstance(final String clusterName, final String address, final boolean server,
			final Options opts, final GlobalEventHandler events) throws IOException {
		return server ? new ClusterServer(clusterName, address, opts, events) //
				: new ClusterClient(clusterName, address, opts, events);
	}

	public static int getRunningInstances() {
		return runningClusters.get();
	}

	public static int getId() {
		return idSeq.incrementAndGet();
	}

	public static void newTask(final Runnable r) {
		threadPool.submit(r);
	}

	public static void shutdown() {
		synchronized (instances) {
			final Iterator<Cluster> i = instances.iterator();
			while (i.hasNext()) {
				final Cluster c = i.next();
				c.stop();
			}
			instances.clear();
		}
	}

	public String getName() {
		return Integer.toHexString(id | Integer.MIN_VALUE);
	}

	public InetSocketAddress getAddress() {
		return address;
	}

	public Options getOpts() {
		return opts;
	}

	public GlobalEventHandler getGlobalEventHandler() {
		return events;
	}

	void stop() {
		Log.info(getName(), "Stoping: " + getAddress());
		running.set(false);
	}

	public boolean isRunning() {
		return running.get();
	}

	@Override
	public void run() {
		try {
			Thread.currentThread().setName(
					getClass().getSimpleName() + "-" + id + "-" + IOHelper.inetAddrToHoman(getAddress()));
			Log.info(getName(), "Started");
			running.set(true);
			runningClusters.incrementAndGet();
			process();
		} catch (Throwable t) {
			Log.error(getName(), "Exception: " + t, t);
		} finally {
			runningClusters.decrementAndGet();
			running.compareAndSet(true, false);
			Log.info(getName(), "Ended");
		}
	}

	abstract void process();

	static class ClusterServer extends Cluster {
		private static final HashMap<Long, ClusterServer> servers = new HashMap<Long, ClusterServer>();
		private final Listener listen;

		ClusterServer(final String clusterName, final String address, final Options opts,
				final GlobalEventHandler events) throws IOException {
			super(clusterName, address, opts, events);
			listen = BIOConnection.getListener(getAddress());
			if (servers.put(Long.valueOf(clusterId), this) != null) {
				Log.warn(getName(), "ClusterId already defined: " + clusterId + " address=" + getAddress());
			}
		}

		public synchronized static ClusterServer getInstance(final long clusterId) {
			return servers.get(Long.valueOf(clusterId));
		}

		public synchronized static void clearInstances() {
			servers.clear();
		}

		@Override
		void stop() {
			super.stop();
			IOHelper.closeSilent(listen);
		}

		@Override
		void process() {
			while (isRunning()) {
				try {
					final Connection c = BIOConnection.acceptConnection(listen, //
							Constants.CLUSTER_ACCEPT_TIMEOUT, //
							Constants.CLUSTER_READ_TIMEOUT);
					if ((events != null) && (c != null)) {
						events.onClusterClient(clusterId, c);
					}
				} catch (IOException e) {
					if (isRunning()) {
						Log.error(getName(), "IOException: " + e, e);
					}
				}
			}
		}

		public void send(final Connection c, final StickyMessage msg) {
			try {
				c.write(OP_NEW);
				ClusterMessage.serializeStickyMessage(c.getOutputStream(), msg);
			} catch (IOException e) {
				IOHelper.closeSilent(c);
				Log.error(getName(), "IOException: " + e);
			}
		}

		public void send(final StickyMessage msg) {
			final Iterator<Connection> i = listen.connections();
			while (i.hasNext()) {
				final Connection c = i.next();
				send(c, msg);
			}
		}
	}

	static class ClusterClient extends Cluster {
		Connection c = null;

		ClusterClient(final String clusterName, final String address, final Options opts,
				final GlobalEventHandler events) throws IOException {
			super(clusterName, address, opts, events);
		}

		@Override
		void stop() {
			super.stop();
			IOHelper.closeSilent(c);
		}

		@Override
		void process() {
			while (isRunning()) {
				try {
					Log.info(getName(), "Connecting: " + address);
					c = BIOConnection.openConnection(address, //
							Constants.CLUSTER_CONNECT_TIMEOUT, //
							Constants.CLUSTER_READ_TIMEOUT);
				} catch (IOException e) {
					Log.error(getName(), "IOException (open): " + e);
					try {
						Thread.sleep(1000);
					} catch (InterruptedException ie) {
						Thread.currentThread().interrupt();
					}
					continue;
				}
				try {
					Log.info(getName(), "Connected: " + address);
					while (isRunning()) {
						try {
							final int b = c.read();
							if (b < 0) { // EOS
								throw new EOFException();
							}
							if (Log.isDebugEnabled()) {
								Log.debug(getName(), "Received: " + b);
							}
							switch (b) {
								case OP_NOP: {
									continue;
								}
								case OP_NEW: {
									if (!processEventNew(c)) {
										throw new EOFException();
									}
									break;
								}
								default:
									throw new IOException("Invalid Operation: " + b);
							}
						} catch (SocketTimeoutException e) {
							continue;
						}
					}
				} catch (IOException e) {
					if (isRunning()) {
						Log.error(getName(), "IOException (read): " + e);
					}
					IOHelper.closeSilent(c);
					c = null;
				}
			}
		}

		private boolean processEventNew(final Connection c) throws IOException {
			final StickyMessage msg = ClusterMessage.deserializeStickyMessage(c.getInputStream());
			events.onStickyFromCluster(clusterId, msg);
			return true;
		}
	}

	static class ClusterMessage {
		static StickyMessage deserializeStickyMessage(final InputStream is) throws IOException {
			final byte[] stickyIdBuf = new byte[8];
			is.read(stickyIdBuf); // 8
			final long stickyId = IOHelper.longFromByteArray(stickyIdBuf, 0);
			final int stickyAddrLen = is.read(); // 1
			checkAddressLen(stickyAddrLen);
			final byte[] stickyAddrBuf = new byte[stickyAddrLen];
			checkAddressLen(is.read(stickyAddrBuf)); // 4-16max
			final InetAddress stickyAddress = InetAddress.getByAddress(stickyAddrBuf);
			final int remoteAddrLen = is.read(); // 1
			final InetSocketAddress remoteAddress;
			if (remoteAddrLen == 0x42) {
				remoteAddress = null;
			} else {
				checkAddressLen(remoteAddrLen);
				final byte[] remoteAddrBuf = new byte[remoteAddrLen];
				checkAddressLen(is.read(remoteAddrBuf)); // 4-16max
				final InetAddress remoteAddr = InetAddress.getByAddress(remoteAddrBuf);
				final int remotePortHi = is.read(); // 1
				final int remotePortLo = is.read(); // 1
				if ((remotePortHi < 0) || (remotePortLo < 0)) {
					throw new IOException("Invalid Port bytes: " + remotePortHi + ":" + remotePortLo);
				}
				final int remotePort = (((remotePortHi & 0xFF) << 8) | (remotePortLo & 0xFF));
				remoteAddress = new InetSocketAddress(remoteAddr, remotePort);
			}
			return new StickyMessage(stickyId, stickyAddress, remoteAddress);
		}

		private static void checkAddressLen(final int len) throws IOException {
			// IPv4 (32bits / 4 bytes), IPv6 (128bits / 16bytes)
			if ((len != 4) && (len != 16)) {
				throw new IOException("Invalid InetAddress length: " + len);
			}
		}

		static void serializeStickyMessage(final OutputStream os, final StickyMessage msg) throws IOException {
			final long stickyId = msg.stickyId;
			final InetAddress stickyAddr = msg.stickyAddress;
			final InetSocketAddress remoteAddr = msg.remoteAddress;
			//
			final byte[] stickyIdBuf = new byte[8];
			IOHelper.longToByteArray(stickyId, stickyIdBuf, 0);
			final byte[] stickyAddrBuf = stickyAddr.getAddress();
			final byte[] remoteAddrBuf = remoteAddr == null ? null : remoteAddr.getAddress().getAddress();
			final int remotePort = remoteAddr == null ? 0 : remoteAddr.getPort();
			// 8 + 1 + 4/16max + 1 + 4/16 + 2 (max 44bytes)
			os.write(stickyIdBuf);
			os.write((byte) (stickyAddrBuf.length & 0x7F));
			os.write(stickyAddrBuf);
			if (remoteAddrBuf == null) {
				os.write((byte) 0x42);
			} else {
				os.write((byte) (remoteAddrBuf.length & 0x7F));
				os.write(remoteAddrBuf);
				os.write((remotePort >>> 8) & 0xFF);
				os.write((remotePort & 0xFF));
			}
			os.flush();
		}
	}
}
