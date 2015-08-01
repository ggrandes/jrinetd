package org.javastack.jrinetd;

import java.nio.channels.Selector;

public class BridgeContext {
	private final int id;
	private final Server srv;
	private final Selector sel;

	private ConnectionHandler conHandA = null;
	private ConnectionHandler conHandB = null;

	public BridgeContext(final Server srv, final Selector sel) {
		this.srv = srv;
		this.sel = sel;
		this.id = Server.getId();
	}

	public String getName() {
		return Integer.toHexString(id | Integer.MIN_VALUE);
	}

	public Server getServer() {
		return srv;
	}

	public Selector getSelector() {
		return sel;
	}

	public void setConnectionHandlerA(final ConnectionHandler conHandA) {
		this.conHandA = conHandA;
	}

	public ConnectionHandler getConnectionHandlerA() {
		return conHandA;
	}

	public void setConnectionHandlerB(final ConnectionHandler conHandB) {
		this.conHandB = conHandB;
	}

	public ConnectionHandler getConnectionHandlerB() {
		return conHandB;
	}

	public ConnectionHandler getPeer(final ConnectionHandler conHanX) {
		if (conHanX != null) {
			if (conHanX == conHandA) {
				return conHandB;
			}
			if (conHanX == conHandB) {
				return conHandA;
			}
		}
		throw new IllegalArgumentException("[" + conHanX + "] vs [" + conHandA + " / " + conHandB + "]");
	}
}
