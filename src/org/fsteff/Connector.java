package org.fsteff;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public abstract class Connector {
	public final String id;
	protected final AtomicBoolean isConnected;
	protected Runnable onClose;
	
	public Connector(String id) throws IOException{
		this.id = id;
		isConnected = new AtomicBoolean(false);
	}
	
	public void setOnClose(Runnable onClose) {
		this.onClose = onClose;
	}
	
	public abstract void connect(Consumer<ByteBuffer> onData);
	public abstract void send(ByteBuffer data);
	public abstract void disconnect();
}
