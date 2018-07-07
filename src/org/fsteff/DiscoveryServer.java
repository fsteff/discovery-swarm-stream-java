package org.fsteff;
import static java.nio.charset.StandardCharsets.*;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

public class DiscoveryServer extends WebSocketServer {
	private final ConcurrentHashMap<WebSocket, Connection> connections = new ConcurrentHashMap<WebSocket, Connection>();
	private static final Logger LOGGER = Logger.getLogger(DiscoveryServer.class.getName());

	public DiscoveryServer() {
		super(new InetSocketAddress("127.0.0.1", 3495));
	}

	public DiscoveryServer(InetSocketAddress address) {
		super(address);
	}

	@Override
	public void onClose(WebSocket ws, int code, String reason, boolean remote) {
		Connection c = connections.get(ws);
		if(c != null) {
			c.close();
			connections.remove(ws);
		}
		
		LOGGER.info("connection closed - code=" + code + " - " + reason + " remote="+remote + " from " + ws.getResourceDescriptor());
	}

	@Override
	public void onError(WebSocket ws, Exception err) {
		Connection c = connections.get(ws);
		if(c != null) {
			c.close();
			connections.remove(ws);
		}
		LOGGER.warning("Websocket error: '" + err.getMessage()+"' from " + ws.getResourceDescriptor());
		err.printStackTrace();
	}
	
	@Override
	public void onMessage(WebSocket ws, String message) {
		Connection c = connections.get(ws);
		ByteBuffer buf = ByteBuffer.wrap(message.getBytes(UTF_8));
		if(c != null) {
			c.handle(buf);
		}
		LOGGER.info("Incoming text message: '" + message + "' from " + ws.getResourceDescriptor());
	}

	@Override
	public void onMessage(WebSocket ws, ByteBuffer message) {
		Connection c = connections.get(ws);
		if(c != null) {
			c.handle(message);
		}
		LOGGER.info("incoming binary message from " + ws.getResourceDescriptor());
	}

	@Override
	public void onOpen(WebSocket ws, ClientHandshake arg1) {
		LOGGER.info("new connection: " + ws.getResourceDescriptor());
		Connection connection = new Connection(ws);
		this.connections.put(ws, connection);
	}

	@Override
	public void onStart() {
		LOGGER.info("DiscoveryServer running on " + this.getAddress()); 
	}

}
