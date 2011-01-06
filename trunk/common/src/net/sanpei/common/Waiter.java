//Copyright 2010 333pei@gmail.com

package net.sanpei.common;

import java.util.concurrent.CancellationException;

public class Waiter<T> {
	private T response;
	private boolean waiting = true;	

	public synchronized T getResponse() {		
		try {
			while (waiting) {
				wait();
			}
		} catch (InterruptedException ex) {
			throw new CancellationException("Please don't interrupt library calls.");
		}
		return response;
	}

	public synchronized void setResponse(T response) {
		this.response = response;
		waiting = false;
		notifyAll();
	}	
}
