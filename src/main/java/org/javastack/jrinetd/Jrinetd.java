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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.URLConnection;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;

import org.javastack.jrinetd.BIOConnection.Connection;
import org.javastack.jrinetd.Cluster.ClusterServer;
import org.javastack.jrinetd.LoadBalanceStrategy.LoadBalanceContext;
import org.javastack.jrinetd.StickyStore.StickyEntry;

/**
 * jrinetd
 * 
 * @author Guillermo Grandes / guillermo.grandes[at]gmail.com
 */
public class Jrinetd implements GlobalEventHandler {
	private final Set<Server> srvs = new LinkedHashSet<Server>();
	private final int id = Server.getId();
	private final Listeners listeners = new Listeners();

	// ============================== Global code

	public static void main(final String[] args) throws Exception {
		if (args.length < 1) {
			System.out.println(Jrinetd.class.getName() + " <configName>");
			return;
		}
		final String configFile = args[0];
		final Jrinetd jrinetd = new Jrinetd();
		//
		// Init Log System
		if (Boolean.getBoolean("DEBUG")) {
			Log.enableDebug(); // Enable debugging messages
			Log.setMode(Log.LOG_ORIG_STDOUT);
		} else {
			// Redir STDOUT to File
			if (System.getProperty(Constants.PROP_OUT_FILE) != null)
				Log.redirStdOutLog(System.getProperty(Constants.PROP_OUT_FILE));
			// Redir STDERR to File
			if (System.getProperty(Constants.PROP_ERR_FILE) != null)
				Log.redirStdErrLog(System.getProperty(Constants.PROP_ERR_FILE));
			if (Boolean.getBoolean(Constants.PROP_OUT_STDTOO)) {
				Log.setMode(Log.LOG_CURR_STDOUT | Log.LOG_ORIG_STDOUT);
			} else {
				Log.setMode(Log.LOG_CURR_STDOUT);
			}
		}
		Log.info(Jrinetd.class.getSimpleName(), "Starting " + jrinetd.getClass() + " version " + getVersion()
				+ (Log.isDebugEnabled() ? " debug-mode" : ""));
		// Read config
		final URL urlConfig = jrinetd.getClass().getResource("/" + configFile);
		if (urlConfig == null) {
			Log.error(Jrinetd.class.getSimpleName(), "Config not found: (classpath) " + configFile);
			return;
		}
		jrinetd.startCacheResolver();
		long lastReloaded = 0;
		while (true) {
			InputStream isConfig = null;
			try {
				final URLConnection connConfig = urlConfig.openConnection();
				connConfig.setUseCaches(false);
				final long lastModified = connConfig.getLastModified();
				// if (Log.isDebugEnabled()) {
				// Log.debug(Jrinetd.class.getSimpleName(), "lastReloaded=" + lastReloaded
				// + " getLastModified()=" + connConfig.getLastModified() + " currentTimeMillis()="
				// + System.currentTimeMillis());
				// }
				isConfig = connConfig.getInputStream();
				if (lastModified > lastReloaded) {
					if (lastReloaded > 0) {
						Log.info(Jrinetd.class.getSimpleName(), "Reloading config");
					}
					lastReloaded = lastModified;
					jrinetd.stopClusters();
					jrinetd.releaseStickies();
					jrinetd.stopServers();
					jrinetd.reload(isConfig);
					jrinetd.cleanOrphanListeners();
					jrinetd.cleanOrphanStickies();
					Log.info(Jrinetd.class.getSimpleName(), "Reloaded config");
				}
			} catch (Exception e) {
				Log.error(Jrinetd.class.getSimpleName(), "Load config error", e);
			} finally {
				IOHelper.closeSilent(isConfig);
			}
			doSleep(Constants.RELOAD_CONFIG);
		}
	}

	static String getVersion() {
		InputStream is = null;
		try {
			final Properties p = new Properties();
			is = Jrinetd.class.getResourceAsStream(Constants.VERSION_FILE);
			p.load(is);
			// Implementation-Vendor-Id: ${project.groupId}
			// Implementation-Title: ${project.name}
			// Implementation-Version: ${project.version}
			return p.getProperty("jrinetd-version");
		} catch (Exception e) {
			return "UNKNOWN";
		} finally {
			IOHelper.closeSilent(is);
		}
	}

	static void doSleep(final long time) {
		try {
			Thread.sleep(time);
		} catch (InterruptedException ie) {
			Thread.currentThread().interrupt();
		}
	}

	public String getName() {
		return Integer.toHexString(id | Integer.MIN_VALUE);
	}

	void reload(final InputStream isConfig) throws NoSuchAlgorithmException, IOException {
		final BufferedReader in = new BufferedReader(new InputStreamReader(isConfig));
		String line = null;
		int lineNum = 0;
		try {
			while ((line = in.readLine()) != null) {
				lineNum++;
				try {
					boolean parseIsOK = true;
					// Skip comments
					if (line.trim().startsWith("#"))
						continue;
					if (line.trim().equals(""))
						continue;
					final String[] toks = line.split("( |\t)+");
					// Invalid params
					if (toks.length < 1) {
						parseIsOK = false;
					}
					final ConnectionType connType = ConnectionType.getTypeFromString(toks[0]);
					switch (connType) {
						case FORWARD: {
							parseIsOK = parseConfigForward(toks);
							break;
						}
						case CLUSTER_IN: {
							parseIsOK = handleConfigCluster(true, toks);
							break;
						}
						case CLUSTER_OUT: {
							parseIsOK = handleConfigCluster(false, toks);
							break;
						}
						default:
							parseIsOK = false;
							break;

					}
					if (!parseIsOK) {
						Log.error(getName(), "Invalid config line[num=" + lineNum + "]: " + line);
					}
				} catch (IOException t) {
					Log.error(getName(),
							"Invalid config line[" + lineNum + "]: " + line + " (" + t.toString() + ")");
				} catch (Throwable t) {
					Log.error(getName(),
							"Invalid config line[" + lineNum + "]: " + line + " (" + t.toString() + ")", t);
				}
			}
		} finally {
			IOHelper.closeSilent(in);
		}
	}

	boolean parseConfigForward(final String[] toks) throws IOException {
		// forward <bind-addr>:<bind-port> <remote-addr>:<remote-port>[,<remote-addr>:<remote-port>] [options]
		int i = 0;
		final String listenAddress = toks[++i].toLowerCase();
		final String remoteAddress = toks[++i].toLowerCase();
		final Options opts = new Options(((toks.length > ++i) ? toks[i] : ""));
		//
		Log.info(getName(), "Readed bind-addr=" + listenAddress + " remote-addr=" + remoteAddress
				+ " options{" + opts + "}");
		final Server srv = new Server(listeners, listenAddress, remoteAddress, opts, this);
		srvs.add(srv);
		Server.newTask(srv);
		return true;
	}

	boolean handleConfigCluster(final boolean server, final String[] toks) throws IOException {
		// ## cluster-in <clustername> <bind-addr>:<bind-port> [opts]
		// ## cluster-out <clustername> <remote-addr>:<remote-port> [opts]
		int i = 0;
		final String clusterName = toks[++i].toLowerCase();
		final String address = toks[++i].toLowerCase();
		final Options opts = new Options(((toks.length > ++i) ? toks[i] : ""));
		Cluster.newTask(Cluster.getInstance(clusterName, address, server, opts, this));
		return true;
	}

	void releaseStickies() {
		Server.getStickyFactory().releaseAll();
	}

	void cleanOrphanStickies() {
		Server.getStickyFactory().unregisterReleased();
	}

	void stopClusters() throws InterruptedException {
		final long shutdownInit = System.currentTimeMillis();
		int running = 0;
		if ((running = Cluster.getRunningInstances()) > 0) {
			Log.info(getName(), "Stoping clusters: " + running);
			Cluster.shutdown();
			while ((running = Cluster.getRunningInstances()) > 0) {
				if ((System.currentTimeMillis() - shutdownInit) > Constants.RELOAD_TIMEOUT) {
					Log.error(getName(), "Shutdown Error: running clusters=" + running);
					break;
				}
				Thread.sleep(100);
			}
			if (running <= 0) {
				Log.info(getName(), "Shutdown completed: running clusters=" + running);
			}
		}
	}

	void stopServers() throws InterruptedException {
		if (!srvs.isEmpty()) {
			final Iterator<Server> i = srvs.iterator();
			while (i.hasNext()) {
				final Server srv = i.next();
				final InetSocketAddress listenAddress = srv.getListenAddress();
				Log.info(getName(), "Stoping server: " + IOHelper.inetAddrToHoman(listenAddress));
				srv.shutdown();
				i.remove();
			}
			final long shutdownInit = System.currentTimeMillis();
			int running = 0;
			while ((running = Server.getRunningServers()) > 0) {
				if ((System.currentTimeMillis() - shutdownInit) > Constants.RELOAD_TIMEOUT) {
					Log.error(getName(), "Shutdown Error: running servers=" + running);
					break;
				}
				Thread.sleep(100);
			}
			if (running <= 0) {
				Log.info(getName(), "Shutdown completed: running servers=" + running);
			}
		}
	}

	void cleanOrphanListeners() {
		listeners.closeReleased();
	}

	@Override
	public void onStickyFromLocal(final BridgeContext bc,
			final LoadBalanceContext<InetAddress, InetSocketAddress> ctx) {
		final Server srv = bc.getServer();
		final Options opts = srv.getOpts();
		final InetSocketAddress listen = srv.getListenAddress();
		final InetAddress stickyAddr = ctx.getStickyAddress();
		final InetSocketAddress remoteAddr = ctx.getRemoteAddress();
		//
		Log.info(getName(), "GlobalEvent: Sticky from Local " + listen + ": sticky=" + stickyAddr
				+ " remote=" + remoteAddr + " options{" + opts + "}");
		//
		final StickyConfig stickyCfg = ctx.getStrategy().getStickyConfig();
		if (!stickyCfg.isReplicated()) {
			return;
		}
		//
		final StickyStore<InetAddress, InetSocketAddress> stickyStore = Server.getStickyFactory()
				.getInstance(stickyCfg.stickyKey);
		if (stickyStore != null) {
			// if (!remoteAddr.equals(currentSticky)) less traffic, but... TTL of sticky are not updated
			final StickyMessage msg = new StickyMessage(stickyCfg.stickyKey.stickyId, stickyAddr, remoteAddr);
			final ClusterServer clusterServer = ClusterServer.getInstance(stickyCfg.stickyKey.clusterId);
			if (clusterServer != null) {
				Log.info(getName(), "Sending to cluster: " + stickyCfg.stickyKey.clusterId + ": sticky="
						+ stickyAddr + " remote=" + remoteAddr);
				clusterServer.send(msg);
			}
		}
	}

	@Override
	public void onStickyFromCluster(final long clusterId, final StickyMessage msg) {
		final StickyStore<InetAddress, InetSocketAddress> stickyStore = Server.getStickyFactory()
				.getInstance(StickyKey.valueOf(clusterId, msg.stickyId));
		//
		Log.info(getName(), "GlobalEvent: Sticky from Cluster clusterId=" + clusterId + " stickyId="
				+ msg.stickyId + " stickyAddr=" + msg.stickyAddress + " remoteAddr=" + msg.remoteAddress);
		//
		if (stickyStore != null) {
			stickyStore.put(msg.stickyAddress, msg.remoteAddress);
		}
	}

	@Override
	public void onClusterClient(final long clusterId, final Connection c) {
		Log.info(getName(), "GlobalEvent: New Cluster Client clusterId=" + clusterId);
		//
		final ClusterServer clusterServer = ClusterServer.getInstance(clusterId);
		if (clusterServer != null) {
			final Collection<StickyStore<InetAddress, InetSocketAddress>> stores = Server.getStickyFactory()
					.getStores();
			if ((stores != null) && !stores.isEmpty()) {
				for (final StickyStore<InetAddress, InetSocketAddress> store : stores) {
					final StickyKey key = store.getConfig().stickyKey;
					if (key.clusterId == clusterId) {
						for (final StickyEntry<InetAddress, InetSocketAddress> s : store.getEntries()) {
							clusterServer.send(c, new StickyMessage(key.stickyId, s.key, s.value));
						}
					}
				}
			}
		}
	}

	void startCacheResolver() {
		Server.newTask(new Runnable() {
			private final int id = Server.getId();

			private String getName() {
				return Integer.toHexString(id | Integer.MIN_VALUE);
			}

			@Override
			public void run() {
				try {
					Thread.currentThread().setName("CacheResolver");
					while (!Thread.currentThread().isInterrupted()) {
						for (final Server s : srvs) {
							final Endpoint addr = s.getEndPoint();
							if (addr.isUsed() || addr.isExpired()) {
								addr.resolve();
							}
						}
						Thread.sleep(Constants.DNS_CACHE_TIME);
					}
				} catch (Exception e) {
					Log.error(getName(), "Exception in CacheResolver", e);
				} finally {
					Log.info(getName(), "Ending");
				}
			}
		});
	}

	static enum ConnectionType {
		/**
		 * Forwarder
		 */
		FORWARD,
		/**
		 * Cluster Connection (Server side)
		 */
		CLUSTER_IN,
		/**
		 * Cluster Connection (Client side)
		 */
		CLUSTER_OUT,
		/**
		 * Unknown Parameter
		 */
		UNKNOWN_VALUE;

		static ConnectionType getTypeFromString(final String value) {
			if (value != null) {
				try {
					return valueOf(value.replace('-', '_').toUpperCase());
				} catch (Exception ign) {
				}
			}
			return UNKNOWN_VALUE;
		}
	}
}
