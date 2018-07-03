package org.fsteff;
import net.iharder.Base64;

import org.xbill.DNS.Lookup;
import org.xbill.DNS.Record;
import org.xbill.DNS.SimpleResolver;
import org.xbill.DNS.Type;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Logger;

public class DatDNS {
	private static final ExecutorService pool = Executors.newCachedThreadPool();
    public static final String[] defaultServers = { "discovery1.datprotocol.com", "discovery2.datprotocol.com" };
    private static final Logger LOGGER = Logger.getLogger(DatDNS.class.getName());

    static Set<InetSocketAddress> lookupDefaultServers(String name){
        @SuppressWarnings("unchecked")
		Future<Set<InetSocketAddress>>[] futures = new Future[defaultServers.length];
        Set<InetSocketAddress> peers = null;
        int i = 0;
        for(String server : defaultServers){
            futures[i++] = lookupAsync(name, server);
        }

        for(i = 0; i < defaultServers.length; i++){
            try {
                Set<InetSocketAddress> res = futures[i].get();
                if(peers == null){
                    peers = res;
                }else{
                    peers.addAll(res);
                }
            }catch (Exception e){
                e.printStackTrace();
            }
        }
        if(peers == null){
           peers = new HashSet<InetSocketAddress>();
        }
        return peers;
    }

	static Future<Set<InetSocketAddress>> lookupAsync(final String name, final String server){
        Callable<Set<InetSocketAddress>> doLookup = new Callable<Set<InetSocketAddress>>() {
            public Set<InetSocketAddress> call() throws Exception {
                return lookup(name, server);
            }
        };
	    return pool.submit(doLookup);
    }

	static Set<InetSocketAddress> lookup(String name, String server) {
		HashSet<InetSocketAddress> peers = new HashSet<InetSocketAddress>();
		try {
			if (!name.contains(".")) {
				name += ".dat.local";
			} else {
				System.err.println("invalid dat discovery key: " + name);
			}

			SimpleResolver res = new SimpleResolver(server);
			Lookup l = new Lookup(name, Type.TXT);

			l.setResolver(res);
			l.run();

			if (l.getResult() == Lookup.SUCCESSFUL) {
				HashMap<String, String> entries = new HashMap<String, String>();
				for (Record r : l.getAnswers()) {
					String txt = r.rdataToString();

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
						int port = fromUInt16BE(data, i+4);

						peers.add(new InetSocketAddress(ip, port));

					}
                    LOGGER.info("Found peers for "+name+": " + peers.toString());
				}else{
					LOGGER.info("No peers found for " + name);
                }
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

	static int fromUInt16BE(byte[] data, int offs) {
		return ((((int) (data[offs] & 0xFF)) << 8) | (data[offs + 1] & 0xFF));
	}

}
