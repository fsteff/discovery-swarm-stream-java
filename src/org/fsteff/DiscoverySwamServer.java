package org.fsteff;

import java.net.InetSocketAddress;

public class DiscoverySwamServer {

	public DiscoverySwamServer() {
		// TODO Auto-generated constructor stub
	}

	public static void main(String[] args) {
		/*Properties props = System.getProperties();
        props.setProperty("java.net.preferIPv4Stack","true");
        System.setProperties(props);*/
		DiscoveryServer srv = new DiscoveryServer(new InetSocketAddress("localhost", 3495));
		srv.start();
	}

}
