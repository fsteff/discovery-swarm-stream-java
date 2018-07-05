package org.fsteff;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Logger;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;
import static java.nio.charset.StandardCharsets.UTF_8;

public class DatMDNS {
	private JmDNS jmdns;
	private final ConcurrentHashMap<String, ServiceInfo> services = new ConcurrentHashMap<>();
	private static final Logger LOGGER = Logger.getLogger(DatMDNS.class.getName());
	
	private static DatMDNS instance = new DatMDNS();
	
	public DatMDNS() {
		try {
			jmdns = JmDNS.create(InetAddress.getLocalHost());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public static DatMDNS getInstance() {
		return instance;
	}
	
	public void lookup(String name, Consumer<InetSocketAddress> onPeer, boolean announce, int port) {
		if(name.length() > 40) {
			name = name.substring(0, 40);
		}
		jmdns.addServiceListener(name+".dat.local", new DatServiceListener(onPeer));
		LOGGER.info("mDNS lookup for " + name + ".dat.local");
		if(announce) {
			ServiceInfo info = ServiceInfo.create("TXT", name+".dat.local", port, "Dat");
			try {
				jmdns.registerService(info);
				services.put(name, info);
				LOGGER.info("mDNS registered service for " + name+".dat.local");
			} catch (IOException e) {
				e.printStackTrace();
			}
			
		}
	}
	
	public void unregister(String name) {
		ServiceInfo info = services.get(name);
		if(info != null) {
			jmdns.unregisterService(info);
		}
	}
	
	private static class DatServiceListener implements ServiceListener{
		private Consumer<InetSocketAddress> onPeer;
		
		public DatServiceListener(Consumer<InetSocketAddress> onPeer) {
			this.onPeer = onPeer;
		}
		
		@Override
		public void serviceAdded(ServiceEvent evt) {
			String name = evt.getInfo().getQualifiedName();
			LOGGER.info("mDNS found peer for " + name);
			byte[] txt = evt.getInfo().getTextBytes();
			String[] strs = {new String(txt, UTF_8)};
			try {
				Set<InetSocketAddress> addrs = DatDNS.decodeTXT(strs, name);
				LOGGER.info("peers: " + addrs);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			String host = evt.getInfo().getInetAddresses()[0].getHostAddress();
			int port = evt.getInfo().getPort();
			InetSocketAddress addr = new InetSocketAddress(host, port);
			onPeer.accept(addr);
		}

		@Override
		public void serviceRemoved(ServiceEvent evt) {}

		@Override
		public void serviceResolved(ServiceEvent evt) {
			LOGGER.info("mDNS found peer for " + evt.getInfo().getQualifiedName());
			String host = evt.getInfo().getInetAddresses()[0].getHostAddress();
			int port = evt.getInfo().getPort();
			InetSocketAddress addr = new InetSocketAddress(host, port);
			onPeer.accept(addr);
		}
		
	}

}
