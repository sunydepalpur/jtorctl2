//Copyright 2010 333pei@gmail.com

package net.sanpei.common;

import java.io.Closeable;
import java.io.IOException;
import java.net.Socket;

public class Helpers {

	public static class Holder<T> {
		public Holder() {
		}

		public Holder(T v) {
			value = v;
		}

		public T value;
	}

	public static Integer readInteger(byte[] bytes) {
		java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(bytes);
		java.io.DataInputStream dis = new java.io.DataInputStream(bais);
		Integer x = null;
		try {
			x = dis.readInt();
		} catch (IOException ex) {
			x = null;
		}
		return x;
	}

	public static void close(String TAG, Socket socket) {
		if (socket != null) {
			try {
				if (!socket.isClosed())
					socket.close();
			} catch (IOException e) {
				Logs.e(TAG, "close socket FAILED!");
			} finally {
				try {
					socket.close();
				} catch (Exception e) {
					Logs.e(TAG, "close same socket FAILED again!");
				}
			}
		}
	}

	public static void close(String TAG, Closeable obj) {
		if (obj != null) {
			try {
				obj.close();
			} catch (IOException e) {
				Logs.e(TAG, String.format("close %s FAILED!", obj.getClass().getName()));
				e.printStackTrace();
			}
		}
	}

	public static void testSleep(long time) {
		try {
			Thread.sleep(time);
		} catch (InterruptedException e) {
		}
	}

	public static boolean isNullOrEmpty(String str) {
		return str == null || str.length() == 0;
	}

	public static int tryParse(String s, int defVal) {
		try {
			return Integer.parseInt(s);
		} catch (Exception e) {
			return defVal;
		}
	}

}
