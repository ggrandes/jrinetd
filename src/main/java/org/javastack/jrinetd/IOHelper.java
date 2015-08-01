package org.javastack.jrinetd;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.util.Set;

public class IOHelper {
	private static final Charset UTF8 = Charset.forName("UTF-8");
	private static final String MD_ALG = "MD5";

	public static final int fullRead(final InputStream is, final byte[] buf, final int len)
			throws IOException {
		int readed;
		if (len > 0) {
			int total = 0;
			while (total < len) {
				readed = is.read(buf, total, len - total);
				if (readed < 0)
					break;
				total += readed;
			}
			return total;
		}
		return 0;
	}

	public static final void intToByteArray(final int v, final byte[] buf, final int offset) {
		buf[offset + 0] = (byte) ((v >> 24) & 0xFF);
		buf[offset + 1] = (byte) ((v >> 16) & 0xFF);
		buf[offset + 2] = (byte) ((v >> 8) & 0xFF);
		buf[offset + 3] = (byte) ((v >> 0) & 0xFF);
	}

	public static final int intFromByteArray(final byte[] buf, final int offset) {
		int v = 0;
		v |= ((((int) buf[offset + 0]) & 0xFF) << 24);
		v |= ((((int) buf[offset + 1]) & 0xFF) << 16);
		v |= ((((int) buf[offset + 2]) & 0xFF) << 8);
		v |= ((((int) buf[offset + 3]) & 0xFF) << 0);
		return v;
	}

	public static final void longToByteArray(final long v, final byte[] buf, final int offset) {
		buf[offset + 0] = (byte) ((v >> 56) & 0xFF);
		buf[offset + 1] = (byte) ((v >> 48) & 0xFF);
		buf[offset + 2] = (byte) ((v >> 40) & 0xFF);
		buf[offset + 3] = (byte) ((v >> 32) & 0xFF);
		buf[offset + 4] = (byte) ((v >> 24) & 0xFF);
		buf[offset + 5] = (byte) ((v >> 16) & 0xFF);
		buf[offset + 6] = (byte) ((v >> 8) & 0xFF);
		buf[offset + 7] = (byte) ((v >> 0) & 0xFF);
	}

	public static final long longFromByteArray(final byte[] buf, final int offset) {
		long v = 0;
		v |= ((((long) buf[offset + 0]) & 0xFF) << 56);
		v |= ((((long) buf[offset + 1]) & 0xFF) << 48);
		v |= ((((long) buf[offset + 2]) & 0xFF) << 40);
		v |= ((((long) buf[offset + 3]) & 0xFF) << 32);
		v |= ((((long) buf[offset + 4]) & 0xFF) << 24);
		v |= ((((long) buf[offset + 5]) & 0xFF) << 16);
		v |= ((((long) buf[offset + 6]) & 0xFF) << 8);
		v |= ((((long) buf[offset + 7]) & 0xFF) << 0);
		return v;
	}

	public static int intIdFromString(final String in) {
		try {
			final MessageDigest md = MessageDigest.getInstance(MD_ALG);
			final byte[] b = md.digest(in.getBytes(UTF8));
			return (IOHelper.intFromByteArray(b, 0) ^ IOHelper.intFromByteArray(b, 4)
					^ IOHelper.intFromByteArray(b, 8) ^ IOHelper.intFromByteArray(b, 12))
					& Integer.MAX_VALUE;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static long longIdFromString(final String in) {
		try {
			final MessageDigest md = MessageDigest.getInstance(MD_ALG);
			final byte[] b = md.digest(in.getBytes(UTF8));
			return (IOHelper.longFromByteArray(b, 0) ^ IOHelper.longFromByteArray(b, 8)) & Long.MAX_VALUE;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static void setupSocket(final ServerSocket sock, final InetSocketAddress listenAddress)
			throws IOException {
		sock.setReuseAddress(true);
		sock.bind(listenAddress);
		// sock.setReceiveBufferSize(Math.max(sock.getReceiveBufferSize(), Constants.BUFFER_LEN *
		// Constants.IO_BUFFERS));
	}

	public static void setupSocket(final Socket sock) throws SocketException {
		sock.setReuseAddress(true);
		sock.setKeepAlive(true);
		// sock.setSendBufferSize(Math.max(sock.getSendBufferSize(), Constants.BUFFER_LEN *
		// Constants.IO_BUFFERS));
		// sock.setReceiveBufferSize(Math.max(sock.getReceiveBufferSize(), Constants.BUFFER_LEN *
		// Constants.IO_BUFFERS));
	}

	public static void closeSilent(final Reader ir) {
		if (ir == null)
			return;
		try {
			ir.close();
		} catch (Exception ign) {
		}
	}

	public static void closeSilent(final Closeable c) {
		if (c == null)
			return;
		try {
			c.close();
		} catch (Exception ign) {
		}
	}

	public static void closeSilent(final Selector s) {
		if (s == null)
			return;
		try {
			final Set<SelectionKey> keys = s.keys();
			for (final SelectionKey k : keys) {
				if (k.isValid()) {
					IOHelper.closeSilent(k.channel());
				}
			}
			s.close();
		} catch (Exception ign) {
		}
	}

	public static void closeSilent(final Socket sock) {
		if (sock == null)
			return;
		try {
			sock.shutdownInput();
		} catch (Exception ign) {
		}
		try {
			sock.shutdownOutput();
		} catch (Exception ign) {
		}
		try {
			sock.close();
		} catch (Exception ign) {
		}
	}

	public static void closeSilent(final ServerSocket sock) {
		if (sock == null)
			return;
		try {
			sock.close();
		} catch (Exception ign) {
		}
	}

	public static String socketRemoteToString(final Socket socket) {
		return socket.getRemoteSocketAddress().toString();
	}

	public static String inetAddrToHoman(final InetSocketAddress sockAddr) {
		return sockAddr.getAddress().getHostAddress() + ":" + sockAddr.getPort();
	}

	public static InetSocketAddress parseAddress(final String addr) {
		final String[] tokHP = addr.split(":", 2);
		final String host = tokHP[0];
		final int port = Integer.valueOf(tokHP[1]);
		return new InetSocketAddress(host, port);
	}

	public static void cleanBuffer(final ByteBuffer bb) {
		bb.rewind();
		int remain = 0;
		while ((remain = bb.remaining()) > 0) {
			final int r = remain & 0x7;
			if (r == 0) {
				bb.putLong(0L);
			} else {
				if ((r & 0x4) != 0) {
					bb.putInt(0);
				}
				if ((r & 0x2) != 0) {
					bb.putShort((short) 0);
				}
				if ((r & 0x1) != 0) {
					bb.put((byte) 0);
				}
			}
		}
	}
}
