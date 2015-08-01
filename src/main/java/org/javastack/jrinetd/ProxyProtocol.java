package org.javastack.jrinetd;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;

public class ProxyProtocol {
	private static final ProxyProtocol singleton = new ProxyProtocol();

	public static ProxyProtocol getInstance() {
		return singleton;
	}

	/**
	 * Generate Haproxy PROXY protocol v1 header.
	 * 
	 * <pre>
	 * "PROXY &lt;TCP4|TCP6&gt; &lt;srcaddr&gt; &lt;dstaddr&gt; &lt;srcport&gt; &lt;dstport&gt;\r\n"
	 * "PROXY TCP4 255.255.255.255 255.255.255.255 65535 65535\r\n"
	 * </pre>
	 * 
	 * <a href="http://www.haproxy.org/download/1.5/doc/proxy-protocol.txt">PROXY protocol</a>
	 * 
	 * @param bb
	 * @param sock
	 * @return
	 */
	public void formatV1(final ByteBuffer bb, final Socket sock) {
		final InetAddress srcAddr = sock.getInetAddress();
		final InetAddress dstAddr = sock.getLocalAddress();
		final String proto;
		if (srcAddr instanceof Inet4Address) {
			proto = "TCP4";
		} else if (srcAddr instanceof Inet6Address) {
			proto = "TCP6";
		} else {
			put(bb, "PROXY UNKNOWN\r\n");
			return;
		}
		final int srcPort = sock.getPort();
		final int dstPort = sock.getLocalPort();
		put(bb, "PROXY").put((byte) ' ');
		put(bb, proto).put((byte) ' ');
		put(bb, srcAddr.getHostAddress()).put((byte) ' ');
		put(bb, dstAddr.getHostAddress()).put((byte) ' ');
		put(bb, srcPort).put((byte) ' ');
		put(bb, dstPort).put((byte) '\r').put((byte) '\n');
	}

	private final ByteBuffer put(final ByteBuffer bb, final int n) {
		return put(bb, Integer.toString(n));
	}

	private final ByteBuffer put(final ByteBuffer bb, final String s) {
		final int len = s.length();
		for (int i = 0; i < len; i++) {
			final int c = s.charAt(i);
			bb.put((byte) c);
		}
		return bb;
	}

	/**
	 * Generate HELO proxy header (Apache mod_myfixip -legacy header-).
	 * 
	 * <pre>
	 * &quot;HELO&lt;ipv4binary32BitAddress&gt;&quot;
	 * </pre>
	 * 
	 * <a href="https://github.com/ggrandes/apache22-modules/blob/master/mod_myfixip.c">mod_myfixip</a>
	 * 
	 * @param addr
	 * @return
	 */
	public byte[] formatHELO(final Inet4Address addr) {
		final byte[] b = addr.getAddress();
		return new byte[] {
				// HELO
				'H', 'E', 'L', 'O',
				// IPv4
				b[0], b[1], b[2], b[3]
		};
	}

	/**
	 * Simple Test
	 */
	public static void main(final String[] args) throws Throwable {
		final ServerSocket listen = new ServerSocket(9876);
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					final Socket client = new Socket("127.0.0.2", 9876);
					client.close();
				} catch (Exception e) {
					e.printStackTrace(System.out);
				}
			}
		}).start();
		final Socket remote = listen.accept();
		final ByteBuffer bb = ByteBuffer.allocate(108);
		getInstance().formatV1(bb, remote);
		System.out.println(new String(bb.array(), 0, bb.position()));
		remote.close();
	}
}
