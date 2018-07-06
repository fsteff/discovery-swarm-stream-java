package org.fsteff;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.function.Consumer;
import java.util.logging.Logger;

public class TcpConnector extends Connector {
	public final InetSocketAddress address;
	private SocketChannel channel;
	
	private static final Logger LOGGER = Logger.getLogger(TcpConnector.class.getName());
	
	public TcpConnector(InetSocketAddress addr, String id) throws IOException{
		super(id);
		this.address = addr;
		
		channel = SocketChannel.open(addr);
	}

	@Override
	public void connect(final Consumer<ByteBuffer> onData) {
		final SocketAddress remote = this.address;
		Thread t = new Thread(new Runnable() {
			
			@Override
			public void run() {
				isConnected.set(true);
				LOGGER.info("SocketChannel "+remote.toString()+" reading thread started");
				final ByteBuffer buf = ByteBuffer.allocate(2000);			
				try {
					channel.configureBlocking(true);
					while( isConnected.get() && channel.isOpen()) {
						channel.read(buf);
						buf.flip();
						if(buf.remaining() > 0) {
							LOGGER.info("new message from SocketChannel "+remote.toString());
							
							onData.accept(buf.asReadOnlyBuffer());
							
							buf.clear();
							LOGGER.info("forwarded message from Socketchannel "+remote.toString());
						}
					} 
				}catch (IOException e) {
					e.printStackTrace();
				}finally {
					LOGGER.info("shutting down listener of SocketChannel " + remote.toString());
					
					// if closed by client
					if(channel.isOpen()) {
						try {
							channel.close();
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
						
					if(onClose != null) {
						onClose.run();
					}
				}
			}
		});
		t.setDaemon(true);
		t.start();
	}
	
	

	@Override
	public void send(ByteBuffer data) {
		try {
			channel.write(data);
		} catch (IOException e) {
			
			e.printStackTrace();
		}
	}

	@Override
	public void disconnect() {
		isConnected.set(false);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((address == null) ? 0 : address.hashCode());
		result = prime * result + TcpConnector.class.getName().hashCode();
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		TcpConnector other = (TcpConnector) obj;
		if (address == null) {
			if (other.address != null)
				return false;
		} else if (!address.equals(other.address))
			return false;
		return true;
	}

}
