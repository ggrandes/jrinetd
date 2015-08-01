package org.javastack.jrinetd;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArraySet;

public class BIOConnection {
	private static final HashMap<InetSocketAddress, Listener> listeners = new HashMap<InetSocketAddress, Listener>();

	public final InetSocketAddress address;

	BIOConnection(final InetSocketAddress address) {
		this.address = address;
	}

	String getName() {
		return getClass().getSimpleName() + "-" + String.valueOf(address);
	}

	public synchronized static Listener getListener(final InetSocketAddress address) throws IOException {
		Listener listener = listeners.get(address);
		if (listener == null) {
			ServerSocket sock = null;
			try {
				sock = new ServerSocket();
				sock.setReuseAddress(true);
				sock.bind(address);
			} catch (IOException e) {
				IOHelper.closeSilent(sock);
				throw e;
			}
			listener = new Listener(address, sock);
		}
		return listener.retain();
	}

	public synchronized static void unregister(final InetSocketAddress address) {
		listeners.remove(address);
	}

	public static Connection acceptConnection(final Listener listen, final int acceptTimeout,
			final int readTimeout) throws IOException {
		Socket remote = null;
		try {
			listen.sock.setSoTimeout(acceptTimeout);
			remote = listen.sock.accept();
			remote.setReuseAddress(true);
			remote.setKeepAlive(true);
			remote.setSoTimeout(readTimeout);
		} catch (SocketTimeoutException e) {
			IOHelper.closeSilent(remote);
			return null;
		} catch (IOException e) {
			IOHelper.closeSilent(remote);
			throw e;
		}
		return new Connection(listen.address, remote, listen);
	}

	public static Connection openConnection(final InetSocketAddress address, final int connectTimeout,
			final int readTimeout) throws IOException {
		Socket remote = null;
		try {
			remote = new Socket();
			remote.setReuseAddress(true);
			remote.setKeepAlive(true);
			remote.connect(address, connectTimeout);
			remote.setSoTimeout(readTimeout);
		} catch (IOException e) {
			IOHelper.closeSilent(remote);
			throw e;
		}
		return new Connection(address, remote);
	}

	static class Listener extends BIOConnection implements Closeable {
		private final ServerSocket sock;
		private final CopyOnWriteArraySet<Connection> clients = new CopyOnWriteArraySet<Connection>();
		private boolean inUse = false;

		public Listener(final InetSocketAddress addr, final ServerSocket listen) {
			super(addr);
			this.sock = listen;
		}

		synchronized Listener add(final Connection client) {
			clients.add(client);
			return this;
		}

		synchronized Listener remove(final Connection client) {
			clients.remove(client);
			return this;
		}

		synchronized Iterator<Connection> connections() {
			return clients.iterator();
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
		public synchronized void close() {
			IOHelper.closeSilent(sock);
			if (!clients.isEmpty()) {
				for (final Connection c : clients) {
					IOHelper.closeSilent(c);
				}
			}
			unregister(address);
		}
	}

	static class Connection extends BIOConnection implements Closeable {
		private final Socket sock;
		private final Listener parent;
		private InputStream is = null;
		private OutputStream os = null;

		public Connection(final InetSocketAddress addr, final Socket sock) {
			this(addr, sock, null);
		}

		public Connection(final InetSocketAddress addr, final Socket sock, final Listener parent) {
			super(addr);
			this.sock = sock;
			this.parent = parent;
			if (parent != null) {
				parent.add(this);
			}
		}

		public InputStream getInputStream() throws IOException {
			if (is == null) {
				is = new BufferedInputStream(sock.getInputStream(), 256);
			}
			return is;
		}

		public OutputStream getOutputStream() throws IOException {
			if (os == null) {
				os = new BufferedOutputStream(sock.getOutputStream(), 256);
			}
			return os;
		}

		public int read() throws IOException {
			return getInputStream().read();
		}

		public int read(final byte[] b) throws IOException {
			return read(b, b.length);
		}

		public int read(final byte[] b, final int len) throws IOException {
			return IOHelper.fullRead(getInputStream(), b, len);
		}

		public Connection write(final int b) throws IOException {
			getOutputStream().write(b);
			return this;
		}

		public Connection write(final byte[] b) throws IOException {
			getOutputStream().write(b);
			return this;
		}

		public Connection write(final byte[] b, final int off, final int len) throws IOException {
			getOutputStream().write(b, off, len);
			return this;
		}

		public Connection flush() throws IOException {
			getOutputStream().flush();
			return this;
		}

		@Override
		public void close() {
			IOHelper.closeSilent(os);
			IOHelper.closeSilent(is);
			IOHelper.closeSilent(sock);
			if (parent != null) {
				parent.remove(this);
			}
		}
	}
}
