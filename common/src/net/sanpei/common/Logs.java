//Copyright 2010 333pei@gmail.com

package net.sanpei.common;

import android.util.Log;

public class Logs {
	private static final int ERROR = 0;
	private static final int WARN = 1;
	private static final int DEBUG = 2;	
	private static final int INFO = 4;
	private static final int VERBOSE = 8;
	private static final int FLAG = ERROR | DEBUG |WARN | INFO | VERBOSE;
	
	
	public static void v(String TAG, String msg) {
		if ((VERBOSE | FLAG) == FLAG) Log.v(TAG, msg);
	}

	public static void d(String TAG, String msg) {		
		if ((DEBUG | FLAG) == FLAG)	Log.d(TAG, msg);
	}

	public static void i(String TAG, String msg) {
		if ((INFO | FLAG) == FLAG)	Log.i(TAG, msg);
	}

	public static void e(String TAG, String msg) {
		if ((ERROR | FLAG) == FLAG) Log.e(TAG, msg);
	}

	public static void e(String TAG, String msg, Throwable e) {
		if ((ERROR | FLAG) == FLAG) Log.e(TAG, msg, e);
	}
	
	public static void w(String TAG, String msg) {
		if ((WARN | FLAG) == FLAG)	Log.w(TAG, msg);
	}

}
