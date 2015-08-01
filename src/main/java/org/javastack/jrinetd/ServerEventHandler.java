package org.javastack.jrinetd;

import java.io.IOException;
import java.net.ConnectException;
import java.net.Socket;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;

public class ServerEventHandler implements Runnable {
	private final Server srv;
	private final int id = Server.getId();
	private final Selector selector;
	private final ArrayBlockingQueue<SocketChannel> clientQueue = new ArrayBlockingQueue<SocketChannel>(8);

	public ServerEventHandler(final Server srv, final Selector selector) {
		this.srv = srv;
		this.selector = selector;
	}

	public String getName() {
		return Integer.toHexString(id | Integer.MIN_VALUE);
	}

	private void queueRegisterClient(final SocketChannel client) throws InterruptedException {
		clientQueue.put(client);
		selector.wakeup();
	}

	private void registerClient(final SocketChannel sc) throws IOException {
		final Socket sock = sc.socket();
		IOHelper.setupSocket(sock);
		final BridgeContext bc = new BridgeContext(srv, selector);
		final ConnectionHandler cli = new ConnectionHandler(bc, sc);
		bc.setConnectionHandlerA(cli);
		final ConnectionHandler rem = new ConnectionHandler(bc, sock.getInetAddress(), srv.getEndPoint());
		bc.setConnectionHandlerB(rem);
		rem.connect();
		Log.info(cli.getName(),
				"New connection: " + IOHelper.inetAddrToHoman(bc.getConnectionHandlerA().getRemoteAddress())
						+ " > " + IOHelper.inetAddrToHoman(srv.getListenAddress()));
	}

	protected void process() throws InterruptedException {
		try {
			SocketChannel sc = null;
			if (!clientQueue.isEmpty()) {
				while ((sc = clientQueue.poll()) != null) {
					registerClient(sc);
				}
			}
			final int events = selector.select(Constants.SELECT_TIMEOUT);
			if (events <= 0) {
				Thread.yield();
				return;
			}
		} catch (IOException e) {
			// TODO: java.net.SocketException: No buffer space available
			Log.error(getName(), "IOException on " + srv.getListenAddress() + " select(): " + e.toString(), e);
			Thread.sleep(1);
			return;
		}
		final Set<SelectionKey> keys = selector.selectedKeys();
		final Iterator<SelectionKey> i = keys.iterator();
		while (i.hasNext()) {
			final SelectionKey key = i.next();
			try {
				if (key.isValid() && key.isAcceptable()) {
					final ServerSocketChannel schan = (ServerSocketChannel) key.channel();
					final SocketChannel sc = schan.accept();
					final ServerEventHandler handler = srv.getClientHandler();
					if (handler != null) {
						handler.queueRegisterClient(sc);
					} else {
						registerClient(sc);
					}
				}
				if (key.isValid() && key.isConnectable()) {
					final ConnectionHandler ctx = (ConnectionHandler) key.attachment();
					if (Log.isDebugEnabled())
						Log.debug(ctx.getName(), "ctx.isConnectable() ctx=" + ctx);
					ctx.onConnect();
				}
				if (key.isValid() && key.isReadable()) {
					final ConnectionHandler ctx = (ConnectionHandler) key.attachment();
					if (Log.isDebugEnabled())
						Log.debug(ctx.getName(), "ctx.isReadable() ctx=" + ctx);
					ctx.onRead();
				}
				if (key.isValid() && key.isWritable()) {
					final ConnectionHandler ctx = (ConnectionHandler) key.attachment();
					if (Log.isDebugEnabled())
						Log.debug(ctx.getName(), "ctx.isWritable() ctx=" + ctx);
					ctx.onWrite();
				}
			} catch (ConnectException e) {
				final ConnectionHandler ctx = (ConnectionHandler) key.attachment();
				Log.error(ctx.getName(), "ConnectException[" + ctx.getRemoteAddress() + "]: " + e.toString());
				ctx.onClose();
			} catch (ClosedChannelException e) {
				final ConnectionHandler ctx = (ConnectionHandler) key.attachment();
				final String msg = "ClosedChannelException[" + ctx.getRemoteAddress() + "]: " + e.toString();
				if (ctx.canClose() && ctx.getContext().getPeer(ctx).canClose()) {
					Log.info(ctx.getName(), msg);
				} else {
					Log.error(ctx.getName(), msg);
				}
				ctx.onClose();
			} catch (IOException e) {
				final ConnectionHandler ctx = (ConnectionHandler) key.attachment();
				Log.error(ctx.getName(), "IOException[" + ctx.getRemoteAddress() + "]: " + e.toString(), e);
				ctx.onClose();
			} catch (Throwable t) {
				Log.error(getClass().getSimpleName(), "Exception: " + t.toString(), t);
				final ConnectionHandler ctx = (ConnectionHandler) key.attachment();
				if (ctx != null) {
					ctx.onClose();
				}
			}
			i.remove();
		}
	}

	@Override
	public void run() {
		try {
			Thread.currentThread().setName(
					"events-" + id + "-" + IOHelper.inetAddrToHoman(srv.getListenAddress()));
			while (srv.isRunning()) {
				process();
			}
		} catch (Throwable t) {
			Log.error(getName(), "Unhandled Exception: " + t.toString(), t);
		} finally {
			Log.info(getName(), "Ending handler on " + IOHelper.inetAddrToHoman(srv.getListenAddress()));
		}
	}
}
