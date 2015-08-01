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
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

import org.javastack.jrinetd.LoadBalanceStrategy.LoadBalanceContext;

/**
 * Client Handler
 * 
 * @author Guillermo Grandes / guillermo.grandes[at]gmail.com
 */
public class ConnectionHandler {
	// Connection
	private final BridgeContext bc;

	private Endpoint endpoint = null;
	private InetSocketAddress remoteAddress = null;
	private SocketChannel sc = null;
	private ByteBuffer bufIn = null;
	private ByteBuffer bufOut = null;

	private LoadBalanceContext<InetAddress, InetSocketAddress> loadBalanceContext = null;

	public ConnectionHandler(final BridgeContext bc, final InetAddress client, final Endpoint endpoint)
			throws IOException {
		this.bc = bc;
		this.endpoint = endpoint;
		//
		this.bufIn = bc.getServer().allocateByteBuffer();
		this.loadBalanceContext = this.endpoint.createLoadBalanceContext(client);
		//
		// connect();
	}

	public ConnectionHandler(final BridgeContext bc, final SocketChannel sc) throws IOException {
		this.bc = bc;
		this.sc = sc;
		//
		this.bufIn = bc.getServer().allocateByteBuffer();
		this.remoteAddress = (InetSocketAddress) sc.socket().getRemoteSocketAddress();
		this.sc.configureBlocking(false);
	}

	public String getName() {
		return bc.getName(); // Integer.toHexString(hashCode() | Integer.MIN_VALUE);
	}

	public BridgeContext getContext() {
		return bc;
	}

	public InetSocketAddress getRemoteAddress() {
		return remoteAddress;
	}

	public void connect() throws IOException {
		remoteAddress = endpoint.onConnect(loadBalanceContext);
		sc = SocketChannel.open();
		sc.configureBlocking(false);
		if (sc.connect(remoteAddress)) {
			Log.warn(getName(), "connect=true");
		}
		wantConnect(true);
	}

	/**
	 * Handle connected to remote
	 * 
	 * @throws IOException
	 */
	public void onConnect() throws IOException {
		final GlobalEventHandler events = bc.getServer().getGlobalEventHandler();
		try {
			sc.finishConnect();
			if (events != null) {
				events.onStickyFromLocal(bc, loadBalanceContext);
			}
			endpoint.onConnectFinished(loadBalanceContext);
		} catch (ConnectException e) {
			Log.error(getName(), "Unable to Connected: " + IOHelper.inetAddrToHoman(getRemoteAddress()));
			loadBalanceContext.setRemoteAddress(null);
			if (events != null) {
				events.onStickyFromLocal(bc, loadBalanceContext);
			}
			if (endpoint.canRetry(loadBalanceContext)) {
				endpoint.onConnectFinished(loadBalanceContext);
				IOHelper.closeSilent(sc);
				connect();
				return; // Retry
			}
			throw e;
		}
		Log.info(getName(), "Connected: " + IOHelper.inetAddrToHoman(getRemoteAddress()));
		final ConnectionHandler peer = bc.getPeer(this);
		wantConnect(false);
		wantRead(true);
		peer.wantRead(true);
		if (bc.getServer().getOpts().isOption(Options.PROXY_SEND)) {
			if (Log.isDebugEnabled())
				Log.debug(getName(), "fillProxyProtocol()");
			peer.fillProxyProtocol();
			peer.passBufInToPeer();
		}
	}

	private final void fillProxyProtocol() {
		ProxyProtocol.getInstance().formatV1(bufIn, sc.socket());
	}

	/**
	 * Read pending data from client
	 * 
	 * @return
	 * @throws IOException
	 */
	public void onRead() throws IOException {
		if (!canRead()) {
			if (Log.isDebugEnabled())
				Log.debug(getName(), "onRead() canRead()=false");
			return;
		}
		if (Log.isDebugEnabled())
			Log.debug(getName(), "onRead() bufIn=" + bufIn.toString());
		final int len = sc.read(bufIn);
		if (len < 0) {
			sc.close();
			throw new ClosedChannelException();
		}
		if (len > 0) {
			passBufInToPeer();
		}
	}

	private void passBufInToPeer() throws ClosedChannelException {
		if (Log.isDebugEnabled())
			Log.debug(getName(), "passBufInToPeer() bufIn=" + bufIn.toString());
		wantRead(false);
		bc.getPeer(this).recvBufOutFromPeer(bufIn);
		bufIn = null;
	}

	private void recvBufOutFromPeer(final ByteBuffer bufOut) throws ClosedChannelException {
		if (Log.isDebugEnabled())
			Log.debug(getName(), "recvBufOutFromPeer() bufOut=" + bufOut.toString());
		this.bufOut = bufOut;
		bufOut.flip();
		wantWrite(true);
	}

	private boolean canRead() {
		if (bufIn == null) {
			return false;
		}
		if (bc.getPeer(this).bufOut != null) {
			return false;
		}
		return true;
	}

	/**
	 * Write pending output buffer
	 * 
	 * @return
	 * @throws IOException
	 */
	public void onWrite() throws IOException {
		if (!canWrite()) {
			if (Log.isDebugEnabled())
				Log.debug(getName(), "onWrite() cantWrite()=false");
			return;
		}
		if (Log.isDebugEnabled())
			Log.debug(getName(), "onWrite() bufOut=" + bufOut.toString());
		if (bufOut.hasRemaining()) {
			sc.write(bufOut);
		}
		if (!bufOut.hasRemaining()) {
			passBufOutToPeer();
		}
		return;
	}

	private void passBufOutToPeer() throws ClosedChannelException {
		if (Log.isDebugEnabled())
			Log.debug(getName(), "passBufOutToPeer() bufOut=" + bufOut.toString());
		wantWrite(false);
		bc.getPeer(this).recvBufInFromPeer(bufOut);
		bufOut = null;
	}

	private void recvBufInFromPeer(final ByteBuffer bufIn) throws ClosedChannelException {
		if (Log.isDebugEnabled())
			Log.debug(getName(), "recvBufInFromPeer() bufIn=" + bufIn.toString());
		this.bufIn = bufIn;
		if (Constants.CLEAN_BUF_ONREUSE) {
			IOHelper.cleanBuffer(bufIn);
		}
		bufIn.clear();
		wantRead(true);
	}

	private boolean canWrite() {
		if (bufOut == null) {
			return false;
		}
		return true;
	}

	/**
	 * Close connection
	 * 
	 * @throws IOException
	 */
	public void onClose() {
		Log.info(getName(), "End connection: " + IOHelper.inetAddrToHoman(getRemoteAddress()));
		close();
		bc.getPeer(this).close();
		if (bufIn != null) {
			bc.getServer().releaseByteBuffer(bufIn);
			bufIn = null;
		}
		if (bufOut != null) {
			bc.getServer().releaseByteBuffer(bufOut);
			bufOut = null;
		}
	}

	private final void close() {
		IOHelper.closeSilent(sc);
		sc = null;
	}

	public boolean isOpen() {
		return ((sc != null) && sc.isOpen());
	}

	public boolean canClose() {
		if (!isOpen()) {
			return true;
		}
		final Selector sel = bc.getSelector();
		final SelectionKey key = sc.keyFor(sel);
		final int current = (key == null || !key.isValid() ? 0 : key.interestOps());
		return (current == SelectionKey.OP_READ);
	}

	void wantConnect(final boolean wanted) throws ClosedChannelException {
		want(SelectionKey.OP_CONNECT, wanted);
	}

	void wantRead(final boolean wanted) throws ClosedChannelException {
		want(SelectionKey.OP_READ, wanted);
	}

	void wantWrite(final boolean wanted) throws ClosedChannelException {
		want(SelectionKey.OP_WRITE, wanted);
	}

	private final void want(final int op, final boolean wanted) throws ClosedChannelException {
		final Selector sel = bc.getSelector();
		final SelectionKey key = sc.keyFor(sel);
		final int current = (key == null || !key.isValid() ? 0 : key.interestOps());
		sc.register(sel.wakeup(), (wanted ? (current | op) : (current & ~op)), this);
	}
}
