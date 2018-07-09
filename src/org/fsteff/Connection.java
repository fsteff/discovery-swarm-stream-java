package org.fsteff;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import org.java_websocket.WebSocket;

import com.google.protobuf.ByteString;

import net.iharder.Base64;
import protocol.Protocol.EventType;
import protocol.Protocol.SwarmEvent;

public class Connection {
	private final LPMInputStream lpmIn = new LPMInputStream();
	private final WebSocket websocket;
	private final ConcurrentHashMap<String, Connector> connections = new ConcurrentHashMap<>();
	private final List<InetSocketAddress> connectingSockets = Collections.synchronizedList(new ArrayList<InetSocketAddress>());
	private boolean gotConnect = false;
	
	private static final Logger LOGGER = Logger.getLogger(DiscoveryServer.class.getName());
	private static final Random rand = new Random();
	private static ExecutorService pool = Executors.newCachedThreadPool();
	
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
					throw new IOException("got OPEN from client");			
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
		for(Connector chan : connections.values()) {
			chan.disconnect();
		}
		connections.clear();
		connectingSockets.clear();
	}
	
	private void onClose(SwarmEvent evt) {
		close();
		// anything more to do?
		websocket.close();
	}
	
	private void onConnect(SwarmEvent evt) {
		gotConnect = true;
		LOGGER.info("got CONNECT message");
	}
	
	private void onData(SwarmEvent evt) {
		if(! gotConnect) 
			LOGGER.warning("CONNECT is needed before DATA");
		
		ByteString idStr = evt.getId();
		String id = idStr.toStringUtf8();
		Connector chan = connections.get(id);
		if(chan != null) {
			chan.send(evt.getData().asReadOnlyByteBuffer());
			LOGGER.info("forwarded message from websocket to socketchannel id="+id);
		}else {
			LOGGER.warning("invalid streamId in DATA message");
		}
		
	}
	
	private void onJoin(SwarmEvent evt) {
		if(! gotConnect) 
			LOGGER.warning("CONNECT is needed before JOIN");
		
		ByteString idStr = evt.getId();
		String id = idStr.toStringUtf8();
		LOGGER.info("JOIN " + id);
		
		OnPeer onPeer = new OnPeer(id);
		DatDNS.lookupDefaultServers(id, onPeer);
	}
	
	private void onLeave(SwarmEvent evt) {
		if(! gotConnect) 
			LOGGER.warning("CONNECT is needed before LEAVE");
		ByteString idStr = evt.getId();
		String id = idStr.toStringUtf8();
		Connector chan = connections.get(id);
		if(chan != null) {
			chan.disconnect();
		}else {
			LOGGER.warning("Got invalid LEAVE streamId");
		}
		
		sendMsg(EventType.CLOSE, id, null);
	}
	
	private void sendMsg(EventType type, String id, ByteBuffer data) {
		sendMsg(type, ByteString.copyFromUtf8(id), ByteString.copyFrom(data));
	}
	
	private void sendMsg(int type, byte[] id, byte[] data) {
		sendMsg(EventType.forNumber(type), 
				id != null ? ByteString.copyFrom(id) : null, 
				data != null ? ByteString.copyFrom(data) : null);
	}
	
	private void sendMsg(EventType type, ByteString id, ByteString data) {
		SwarmEvent.Builder builder = SwarmEvent.newBuilder();
		builder.setType(type);
		if(id != null) {
			builder.setId(id);
		}
		if(data != null) {
			builder.setData(data);
		}

		SwarmEvent evt = builder.build();
		byte[] msg = evt.toByteArray();
		byte[] len = toVarint(msg.length);
		ByteBuffer buf = ByteBuffer.allocate(len.length + msg.length);
		buf.put(len);
		buf.put(msg);
		buf.flip();
		try {
			websocket.send(buf);
			LOGGER.info("sent ws message: " + type + " " + id.toStringUtf8() + "(length=" + msg.length+")");
		} catch(Exception e) {
			LOGGER.warning(e.getMessage());
		}
	}
	
	private byte[] toVarint(int num) {
		byte[] res = new byte[getNumBytes(num)];
		for(int i = 0; i < res.length; i++) {
			res[i] = (byte) (num & 0x7f);
			if(i != res.length-1)
				res[i] |= 0x80;
			num >>= 7;
		}
		
		return res;
	}
	
	private int getNumBytes(int num) {
		if(num < 0 || num > 128*128*128*128) {
			LOGGER.severe("number outside of varint / unsigned int32 range");
			return -1;
		}
		
		if(num < 128) return 1;
		else if(num <128*128) return 2;
		else if(num < 128*128*128) return 3;
		else return 4;
	}
	
	private class OnPeer implements Consumer<InetSocketAddress>{
		private final String id;
		
		public OnPeer(String id) {
			this.id = id;
		}
		
		@Override
		public void accept(final InetSocketAddress addr) {
			pool.submit(new Runnable() {
				@Override
				public void run() {
					connectTcp(addr);
				}
			});
		}
		
		private void connectTcp(final InetSocketAddress addr) {
			if(connectingSockets.contains(addr)) {
				LOGGER.info("already connected/connecting to " + addr + " - skipping it");
				return;
			}
			try {
				connectingSockets.add(addr);
				final TcpConnector chan = new TcpConnector(addr, id);
				LOGGER.info("successfully connected to peer (TCP): " + addr.getHostName() + ":" + addr.getPort());
				// generate token
				String strToken = null;
				do {
					byte[] token = new byte[4];
					rand.nextBytes(token);
					strToken = Base64.encodeBytes(token);
					// very unlikely to happen twice
				} while(connections.containsKey(strToken));
				// store channel as token
				connections.put(strToken, chan);
				
				final String token = strToken;
				chan.setOnClose(new Runnable() {		
					@SuppressWarnings("unlikely-arg-type")
					@Override
					public void run() {
						connectingSockets.remove(chan);
						connections.remove(token);
						LOGGER.info("connection to " + addr + " closed");
						if(websocket.isOpen()) {
							sendMsg(EventType.CLOSE, id, null);
						}
					}
				});
				
				chan.connect(new Consumer<ByteBuffer>() {
					@Override
					public void accept(ByteBuffer buf) {
						sendMsg(EventType.DATA, token, buf);
					}
				});
				
				sendMsg(EventType.OPEN_VALUE, strToken.getBytes(UTF_8), id.getBytes(UTF_8));
			}catch (IOException e) {
				LOGGER.warning(e.getMessage());
			}
		}
	}
		
}
