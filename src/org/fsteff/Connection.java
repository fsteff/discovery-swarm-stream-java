package org.fsteff;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Set;
import java.util.logging.Logger;

import org.java_websocket.WebSocket;

import com.google.protobuf.ByteString;

import protocol.Protocol.EventType;
import protocol.Protocol.SwarmEvent;

public class Connection {
	private final LPMInputStream lpmIn = new LPMInputStream();
	private WebSocket websocket;
	private HashMap<String, SocketChannel> sockets = new HashMap<>();
	
	private static final Logger LOGGER = Logger.getLogger(DiscoveryServer.class.getName());
	
	public Connection(WebSocket ws) {
		this.websocket = ws;
	}
	
	public void handle(ByteBuffer message) {
		try {
			lpmIn.write(message);
		} catch (BufferUnderflowException | IOException e) {
			e.printStackTrace();
		}

		for(ByteBuffer msg : lpmIn) {
			try {
				SwarmEvent evt = SwarmEvent.parseFrom(msg);
				switch (evt.getType().getNumber()) {
				case EventType.OPEN_VALUE:
					onOpen(evt);
					break;				
				case EventType.CLOSE_VALUE:
					onClose(evt);
					break;
				case EventType.CONNECT_VALUE:
					onConnect(evt);
					break;
				case EventType.DATA_VALUE:
					onData(evt);
					break;
				case EventType.JOIN_VALUE:
					onJoin(evt);
					break;
				case EventType.LEAVE_VALUE:
					onLeave(evt);
					break;

				default:
					throw new IOException("invalid message type");
				}
				
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	public void close() {
		for(SocketChannel chan : sockets.values()) {
			try {
				chan.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		sockets.clear();
	}
	
	private void onOpen(SwarmEvent evt) {
		
	}
	
	private void onClose(SwarmEvent evt) {
		
	}
	
	private void onConnect(SwarmEvent evt) {
		
	}
	
	private void onData(SwarmEvent evt) {
		
	}
	
	private void onJoin(SwarmEvent evt) {
		ByteString idStr = evt.getId();
		String id = idStr.toStringUtf8();
		Set<InetSocketAddress> addrs = DatDNS.lookupDefaultServers(id);
		for(InetSocketAddress addr : addrs) {
			try {
				SocketChannel chan = SocketChannel.open(addr);
				// TODO: generate id and put into hashmap
			}catch (IOException e) {
				e.printStackTrace();
			}	
		}
	}
	
	private void onLeave(SwarmEvent evt) {
		
	}
}
