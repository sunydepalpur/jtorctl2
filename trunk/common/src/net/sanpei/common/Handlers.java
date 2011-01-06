//Copyright 2010 333pei@gmail.com

package net.sanpei.common;

public class Handlers {

	public static abstract class ReturnHandler<T> {
		public abstract T onFired();
	}

	public static abstract class NoArgumentHandler {
		public abstract void onFired();
	}

	public static abstract class Handler<T> {
		public abstract void onFired(T t);
	}
		
	public static abstract class Handler2<T1, T2> {
		public abstract void onFired(T1 arg1, T2 arg2);
	}
	
	public static abstract class Handler3<T1, T2, T3> {
		public abstract void onFired(T1 arg1, T2 arg2, T3 arg3);
	}

	/*
	public static boolean bindAndUnbind(String serviceName, Context context, ServiceConnection sc) {
		if (context.bindService(new Intent(serviceName), sc, Context.BIND_AUTO_CREATE)) {
			context.unbindService(sc);
			return true;
		} else {
			SPLog.w(TAG, "bind to NameResolver Service FAILED");
			return false;
		}
	} */

}
