package org.fsteff;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.logging.Logger;

import org.xbill.DNS.DClass;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.Record;
import org.xbill.DNS.SimpleResolver;
import org.xbill.DNS.Type;

import net.iharder.Base64;

public class DatDNS {
	protected static final ExecutorService pool = Executors.newCachedThreadPool();
	public static final String[] defaultServers = { "discovery1.datprotocol.com", "discovery2.datprotocol.com" };
	private static final Logger LOGGER = Logger.getLogger(DatDNS.class.getName());

	static void lookupDefaultServers(String name, Consumer<InetSocketAddress> onPeer) {
		// for local debugging the DNS server lookups can be disabled
		if(! DiscoverySwamServer.DISABLE_DNS) {
			for (String server : defaultServers) {
				lookupAsync(name, server, onPeer);
			}
		}
		lookupMDNSAsync(name, onPeer);
	}

	static Set<InetSocketAddress> lookupDefaultServers(String name) {
		@SuppressWarnings("unchecked")
		Future<Set<InetSocketAddress>>[] futures = new Future[defaultServers.length + 1];
		Set<InetSocketAddress> peers = null;
		int i = 0;
		for (String server : defaultServers) {
			futures[i++] = lookupAsync(name, server);
		}
		futures[i] = lookupMDNSAsync(name, null);

		for (i = 0; i < futures.length; i++) {
			try {
				Set<InetSocketAddress> res = futures[i].get();
				if (peers == null) {
					peers = res;
				} else {
					peers.addAll(res);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		if (peers == null) {
			peers = new HashSet<InetSocketAddress>();
		}
		return peers;
	}

	static Future<Set<InetSocketAddress>> lookupAsync(final String name, final String server) {
		return lookupAsync(name, server, null);
	}

	static Future<Set<InetSocketAddress>> lookupAsync(final String name, final String server,
			final Consumer<InetSocketAddress> onPeer) {
		Callable<Set<InetSocketAddress>> doLookup = new Callable<Set<InetSocketAddress>>() {
			public Set<InetSocketAddress> call() throws Exception {
				Set<InetSocketAddress> peers = lookup(name, server);
				if (onPeer != null) {
					for (InetSocketAddress peer : peers) {
						onPeer.accept(peer);
					}
				}
				return peers;
			}
		};
		return pool.submit(doLookup);
	}

	static Set<InetSocketAddress> lookup(String name, String server) {
		Set<InetSocketAddress> peers = null;
		try {
			if (!name.contains(".")) {
				if (name.length() > 40) {
					name = name.substring(0, 40);
				}
				name += ".dat.local";
			} else {
				System.err.println("invalid dat discovery key: " + name);
			}

			SimpleResolver res = new SimpleResolver(server);
			Lookup l = new Lookup(name, Type.TXT);

			l.setResolver(res);
			l.run();

			if (l.getResult() == Lookup.SUCCESSFUL) {
				String[] answers = new String[l.getAnswers().length];
				int nr = 0;
				for (Record r : l.getAnswers()) {
					answers[nr++] = r.rdataToString();
				}
				peers = decodeTXT(answers, name);
			} else {
				LOGGER.warning("Lookup not successful");
			}

		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		return peers;
	}

	public static Set<InetSocketAddress> decodeTXT(String[] answers, String name) throws IOException {
		HashSet<InetSocketAddress> peers = new HashSet<InetSocketAddress>();
		HashMap<String, String> entries = new HashMap<String, String>();
		for (String txt : answers) {
			String[] parts = txt.split("\"");

			for (int i = 0; i < parts.length - 1; i += 2) {
				String msg = parts[i] + parts[i + 1];
				String[] kv = msg.split("=");
				kv[0] = kv[0].trim();
				kv[1] = kv[1].trim();
				entries.put(kv[0], kv[1]);
			}
		}
		if (entries.containsKey("peers")) {
			byte[] data = Base64.decode(entries.get("peers"));
			for (int i = 0; i < data.length; i += 6) {
				String ip = (data[i] & 0xFF) + "." + (data[i + 1] & 0xFF) + "." + (data[i + 2] & 0xFF) + "."
						+ (data[i + 3] & 0xFF);
				int port = fromUInt16BE(data, i + 4);

				peers.add(new InetSocketAddress(ip, port));

			}
			LOGGER.info("Found peers for " + name + ": " + peers.toString());
		} else {
			LOGGER.info("No peers found for " + name);
		}
		return peers;
	}

	static int fromUInt16BE(byte[] data, int offs) {
		return ((((int) (data[offs] & 0xFF)) << 8) | (data[offs + 1] & 0xFF));
	}

	static Set<InetSocketAddress> lookupMDNS(String name) {
		Set<InetSocketAddress> peers = null;
		if(name.length() <= 40) {
			LOGGER.warning("mDNS: invalid dat discoverykey - too short!");
			return new HashSet<>();
		}
		name = name.substring(0, 40) + ".dat";

		LOGGER.info("mDNS lookup for " + name);
		try (net.posick.mDNS.Lookup lookup = new net.posick.mDNS.Lookup(name, Type.TXT, DClass.IN)) {
			List<Record> recs = Arrays.asList(lookup.lookupRecords());

			LOGGER.info("mDNS found " + recs.size() + " peers for " + name);
			String[] answers = new String[recs.size()];
			int nr = 0;
			for (Object r : recs) {
				answers[nr++] = ((Record) r).rdataToString();
			}
			peers = decodeTXT(answers, name);

		} catch (IOException e) {
			e.printStackTrace();
		}
		return peers;
	}

	static Future<Set<InetSocketAddress>> lookupMDNSAsync(final String name, final Consumer<InetSocketAddress> onPeer) {
		Callable<Set<InetSocketAddress>> doLookup = new Callable<Set<InetSocketAddress>>() {
			public Set<InetSocketAddress> call() throws Exception {
				Set<InetSocketAddress> peers = lookupMDNS(name);
				if (onPeer != null) {
					for (InetSocketAddress peer : peers) {
						onPeer.accept(peer);
					}
				}
				return peers;
			}
		};
		return pool.submit(doLookup);
	}
}
