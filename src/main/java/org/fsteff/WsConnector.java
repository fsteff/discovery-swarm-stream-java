package org.fsteff;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.logging.Logger;

public class WsConnector extends Connector{
	public final int connectionId;
	private volatile WsConnector other;
	private Consumer<ByteBuffer> onData;
	private final Logger LOGGER;
	
	public WsConnector(String id, int connId) throws IOException {
		super(id);
		this.connectionId = connId;
		this.other = null;
		LOGGER = Logger.getLogger(WsConnector.class.getName() + "#" + connectionId + "->" + id);
	}

	@Override
	public void connect(Consumer<ByteBuffer> onData) {
		this.onData = onData;
		synchronized (isConnected) {
			isConnected.set(true);
			isConnected.notifyAll();
		}
	}

	@Override
	public void send(ByteBuffer data) {
		if(other != null && isConnected.get() && other.isConnected.get()) {
			other.receive(data.asReadOnlyBuffer());
			LOGGER.info("forward message for "+id+" from WS#" + connectionId + 
					" to WS#" + other.connectionId);
		}else {
			LOGGER.severe("closed connector sends data to " + (other != null ? other.id : "null"));
		}
	}

	@Override
	public void disconnect() {
		if(! this.isConnected.getAndSet(false)) {
			onClose.run();
		}else {
			return;
		}
		if(other.isConnected.get())
			other.disconnect();

		other = null;
		onData = null;
	}
	
	public void receive(ByteBuffer data) {
		if(isConnected.get())
			onData.accept(data);
		else
			LOGGER.severe("closed connector received data from " + (other != null ? other.id : "null"));
	}
	
	public void bind(WsConnector other) {
		this.other = other;
		other.other = this;
	}
	
	public void awaitConnection() throws InterruptedException {
		if(this.isConnected.get() && other != null && other.isConnected.get()) return;
		synchronized (isConnected) {
			while(! isConnected.get()) {
				isConnected.wait(1000);
			}
		}
	}

}
