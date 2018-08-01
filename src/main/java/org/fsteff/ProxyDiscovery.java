package org.fsteff;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class ProxyDiscovery {
	private final static ConcurrentHashMap<String, List<Connection>> connections = new ConcurrentHashMap<>();
	private static final Logger LOGGER = Logger.getLogger(ProxyDiscovery.class.getName());
	
	public static void join(String id, Connection c1) {
		synchronized (connections) {
			if(connections.containsKey(id)) {
				List<Connection> list = connections.get(id);
				Iterator<Connection> iter = list.iterator();
				while(iter.hasNext()) {
					Connection c2 = iter.next();
					LOGGER.info("connecting WS#"+c1.connId + " and WS#" + c2.connId);
					WsConnector ctr1 = c1.getWsConnector(id);
					WsConnector ctr2 = c2.getWsConnector(id);
					if(ctr1 == null || ctr2 == null)
						continue;
					ctr1.bind(ctr2);
					c2.onWsConnection(ctr1);
					c1.onWsConnection(ctr2);
				}
				list.add(c1);
			}else {
				List<Connection> list = Collections.synchronizedList(new ArrayList<Connection>());
				list.add(c1);
				connections.put(id, list);	
				LOGGER.info("WS#"+c1.connId + " joined " + id);
			}
		}
	}
	
	public static void leave(String id, Connection conn) {
		synchronized (connections) {
			if(connections.containsKey(id)) {
				List<Connection> list = connections.get(id);
				list.remove(conn);
				if(list.isEmpty()) {
					connections.remove(id);
				}
			}
		}
	}

}
