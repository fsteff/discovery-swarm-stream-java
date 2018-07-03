package org.fsteff;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;

public class LPMInputStream implements Iterable<ByteBuffer>{
	private int remaining = 0;
	private Queue<ByteBuffer> buffers = new LinkedList<ByteBuffer>();
	private ByteBuffer current = null;

	public void write(ByteBuffer data) throws BufferUnderflowException, IOException {
		if(remaining == 0) {
			nextMsg(data);
		}
		
		while(data.remaining() > 0) {
			current.put(data.get());
			remaining--;
			
			if(remaining <= 0) {
				nextMsg(data);
			}
		}
	}
	
	public ByteBuffer next(){
		return buffers.poll();
	}
	
	public boolean hasNext() {
		return buffers.size() > 0;
	}
	
	@Override
	public Iterator<ByteBuffer> iterator() {
		Iterator<ByteBuffer> it = new Iterator<ByteBuffer>() {

			@Override
			public boolean hasNext() {
				return LPMInputStream.this.hasNext();
			}

			@Override
			public ByteBuffer next() {
				return LPMInputStream.this.next();
			}
		};
		
		return it;
	}
	
	private void nextMsg(ByteBuffer in) throws BufferUnderflowException, IOException{
		if(current != null) {
			buffers.add(current);
		}
		remaining = parseVarint(in);	
		if(remaining <= 0) {
			current = null;
			remaining = 0;
			// in.clear(); //(?)
			throw new IOException("invalid message length");
		}
		
		current = ByteBuffer.allocate(remaining);
	}
	
	private int parseVarint(ByteBuffer in) throws BufferUnderflowException{
		int num = 0;
		byte b = 0;
		while((b = in.get()) < 128) {
			num <<= 8;
			num |= b;
		}
		return num;
	}



}
