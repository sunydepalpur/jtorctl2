//Copyright 2010 333pei@gmail.com

package net.sanpei.tor;

import java.io.File;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.LineNumberReader;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import android.text.TextUtils;

import net.sanpei.common.Handlers;
import net.sanpei.common.Helpers;
import net.sanpei.common.Logs;
import net.sanpei.common.Pair;
import net.sanpei.tor.Globals.Signal;
import net.sanpei.tor.Globals.SocksError;
import net.sanpei.tor.Globals.TorVersionStatus;
import net.sanpei.tor.entities.Bootstrap;
import net.sanpei.tor.entities.Circuit;
import net.sanpei.tor.entities.OrConnection;
import net.sanpei.tor.entities.Stream;

public class Operator {
	private static final String CONTROL_PASSWORD = "niubee";
	public static final int DefaultControlPort = 9051;
	private volatile Connection conn = null;
	private HashSet<EventDispatcher.Handler> handlers = null;
	private static final String TAG = Operator.class.getName();
	private int controlPort = -1;
	private String _torVersion;
	private final String cfgFilePath;
	private boolean newNymAllowed = true;
	private final Object torrcLock = new Object();
	private final Object connLock = new Object();
	private Socket connSocket;

	public Operator(int controlPort, String cfgFilePath) {
		this.controlPort = controlPort;
		this.cfgFilePath = cfgFilePath;
		handlers = new HashSet<EventDispatcher.Handler>();
	}

	public boolean connect() throws IOException {
		synchronized (connLock) {			
			if(conn != null && connSocket != null && connSocket.isConnected()) return true;
			try {
				connSocket = new Socket("127.0.0.1", controlPort);
				Logs.d(TAG, "control socket created.");
			} catch (IOException e) {
				Logs.e(TAG, "cannot connect to ControlPort: " + e.getMessage());
				return false;
			}
			conn = makeControlConnection(connSocket);
			if (conn != null) {
				someInitWork(conn);
				attachEventHandler(conn, handlers);
				return true;
			}
			return false;
		}
	}

	public boolean isConnected() {
		// XXX we don't verify if the conn have a good health
		// we can modify auth method to do this		
		synchronized (connLock) {
			return conn != null && connSocket != null && connSocket.isConnected();
		}
	}

	/** this call don't block your thread, it delegates clean job to another thread */
	public void disconnect() {
		Connection c = null;
		Socket skt = null;

		synchronized (connLock) {
			c = conn;
			skt = connSocket;
			conn = null;
			connSocket = null;
		}
		final Socket s = skt;
		final Connection c1 = c;
		if (s == null) {
			return;
		}
		new Thread(new Runnable() {
			@Override public void run() {
				if (c1 == null) {
					Helpers.close(TAG, s);
					return;
				}
				c1.close(new Handlers.NoArgumentHandler() {
					@Override public void onFired() {
						Helpers.close(TAG, s);
					}
				});
			}
		}).start();
	}

	private void someInitWork(Connection c) {
		getVersionPrivate(c);
		/* We want to use verbose names in events and GETINFO results. */
		// useFeature("VERBOSE_NAMES");
		/* We want to use extended events in all async events */
		// useFeature("EXTENDED_EVENTS");
	}

	private String getVersionPrivate(Connection c) {
		if (c == null)
			return "not connected";

		if (TextUtils.isEmpty(_torVersion)) {
			/*
			 * The version of Tor isn't going to change while we're connected to
			 * it, so save it for later.
			 */
			try {
				_torVersion = Commands.getInfo(c, "version");
			} catch (IOException e) {
				Logs.e(TAG, "get tor version failed");
				_torVersion = "";
			}

		}
		return _torVersion;
	}

	public String getVersion() {
		return getVersionPrivate(getConn());
	}

	Operator(String cfgFilePath) {
		this(DefaultControlPort, cfgFilePath);

	}
	
	/** block until we think Tor has been shutdown. */
	public void shutdown(boolean immediately) {
		Connection c = getConn();
		if (c == null)
			return;
		c.setEventHandler(null);
		try {
			if (immediately)
				c.signal(Signal.Halt);
			else
				c.signal(Signal.Shutdown);
		} catch (IOException e) {
			Logs.e(TAG, "send Signal 'shutdown' FAILED: " + e.getMessage());
		}
		Helpers.close(TAG, connSocket);
		connSocket = null;
		conn = null;
	}

	public void addEventHandler(EventDispatcher.Handler handler) {
		handlers.add(handler);
	}

	public void removeEventHandler(EventDispatcher.Handler handler) {
		handlers.remove(handler);
	}

	public void cleanEventHandlers() {
		handlers.clear();
	}

	private Connection getConn() {
		synchronized (connLock) {
			return conn;
		}
	}

	public Bootstrap getBootstrap() {
		Connection c = getConn();
		if (c == null) {
			Logs.e(TAG, "getBootstrap called, no connection, return null~");
			return null;
		}
		return Bootstrap.get(c);
	}

	public void refreshTor() {
		Connection c = getConn();
		if (c == null) {
			Logs.e(TAG, "refreshTor called, no connection, return~");
			return;
		}
		try {
			c.signal(Signal.Reload);
		} catch (IOException e) {
			Logs.e(TAG, "send 'RELOAD' Signal FAILED: " + e.getMessage());
		}
	}

	private void updateTorrc(List<Pair<String, String>> entries) throws IOException {
		HashMap<String, LinkedList<String>> dct = new HashMap<String, LinkedList<String>>();
		LinkedList<String> lst = null;
		for (Pair<String, String> pair : entries) {
			lst = null;
			if ((null == (lst = dct.get(pair.first)))) {
				lst = new LinkedList<String>();
				dct.put(pair.first, lst);
			}
			if (!TextUtils.isEmpty(pair.second)) {
				lst.addFirst(pair.second);
			}
		}
		synchronized (torrcLock) {
			File file = new File(cfgFilePath);
			LineNumberReader reader = new LineNumberReader(new FileReader(file));
			String line = null;
			StringBuilder sb = new StringBuilder();

			while ((line = reader.readLine()) != null) {
				String[] ary = line.split("\\s+");
				String key = null;
				int i = 0;
				while (TextUtils.isEmpty(key) && i < ary.length) {
					key = ary[i];
					i++;
				}

				if ((!TextUtils.isEmpty(key)) && dct.containsKey(key)) {
					lst = dct.get(key);
					if (lst.isEmpty()) {
						// Logs.i(TAG, "torrc, removed Line:" + line);
						line = "";
					} else {
						line = key + " " + lst.removeFirst();
					}
				}
				if (!TextUtils.isEmpty(line)) {
					// Logs.v(TAG, "torrc: " + line);
					sb.append(line);
					sb.append("\n");
				}
			}

			sb.append("\n");

			for (String key : dct.keySet()) {
				lst = dct.get(key);
				while (!lst.isEmpty()) {
					line = key + " " + lst.removeFirst();
					// Logs.i(TAG, "torrc, removed Line:" + line);
					sb.append(line);
					sb.append("\n");
				}
			}

			reader.close();
			FileWriter writer = new FileWriter(file);
			writer.write(sb.toString());
			writer.flush();
			writer.close();
		}
	}

	public void newIdentity() {
		Connection c = getConn();
		if (c == null) {
			Logs.e(TAG, "newIdentity called, but no conn, return");
			return;
		}
		if (!newNymAllowed) {
			Logs.w(TAG, "NewNym twice in 10s, ignored~");
			return;
		}
		newNymAllowed = false;
		try {
			Logs.i(TAG, "send NewNym signal.");
			c.signal(Signal.NewNym);
		} catch (IOException e) {
			Logs.e(TAG, "we have an exception when execute newIdentity: " + e.getMessage());
		}

		Timer t = new Timer();
		t.schedule(new TimerTask() {
			@Override public void run() {
				newNymAllowed = true;
			}
		}, 10 * 1000);
	}

	public void useBridges(boolean enabled, String[] bridges) {
		Connection c = getConn();
		if (c == null) {
			Logs.e(TAG, "useBridges called, but no conn, return~");
			return;
		}
		List<Pair<String, String>> lst = new ArrayList<Pair<String, String>>();
		lst.add(new Pair<String, String>("UseBridges", enabled ? "1" : "0"));
		for (String b : bridges) {
			lst.add(new Pair<String, String>("Bridge", b));
		}
		try {
			updateTorrc(lst);
			c.signal(Signal.Reload);
		} catch (IOException e) {
			Logs.e(TAG, "set useBridges and save to torrc FAILED: " + e.getMessage());
		}
	}

	private static Connection makeControlConnection(Socket s) throws IOException {
		Logs.i(TAG, "makeControlConnection has been Called.");
		Connection x = null;
		try {
			x = Connection.getConnection(s);
		} catch (IOException e) {
			Logs.e(TAG, "get stream FAILED.");
			Helpers.close(TAG, s);
			throw e;
		}
		x.open(true);
		try {
			Commands.authenticate(x, CONTROL_PASSWORD);
		} catch (IOException e) {
			Logs.e(TAG, "authenticate to tor FAILED.");
			Helpers.close(TAG, s);
			throw e;
		}
		Logs.i(TAG, "connection created");
		return x;
	}

	private static void attachEventHandler(Connection c, final HashSet<EventDispatcher.Handler> handlers) {
		if (c == null)
			return;
		try {
			c.setEvents(new Globals.Event[] { Globals.Event.LogWarn, Globals.Event.LogNotice,
			// Globals.Event.LogInfo, //
					Globals.Event.LogError, //
					// Globals.Event.LogDebug, //
					Globals.Event.GeneralStatus, //
					Globals.Event.ClientStatus, //
					Globals.Event.ServerStatus, //
					Globals.Event.CircuitStatus, //					
					Globals.Event.NewDescriptor, //
					// Globals.Event.Bandwidth, //
					Globals.Event.OrConnStatus, Globals.Event.StreamStatus, //
					Globals.Event.AddressMap //
					});
		} catch (IOException e) {
			Logs.e(TAG, "setting events FAILED, return");
			return;
		}
		c.setEventHandler(new EventDispatcher.Handler() {
			@Override public void logMessage(int i, String message) {
				for (EventDispatcher.Handler handler : handlers) {
					handler.logMessage(i, message);
				}
			}

			@Override public void bandwidthUpdate(long bytesIn, long bytesOut) {
				for (EventDispatcher.Handler handler : handlers) {
					handler.bandwidthUpdate(bytesIn, bytesOut);
				}
			}

			@Override public void clockSkewed(Integer skew, String string) {
				for (EventDispatcher.Handler handler : handlers) {
					handler.clockSkewed(skew, string);
				}
			}

			@Override public void dangerousTorVersion(TorVersionStatus new1, String current, String[] recommended) {
				for (EventDispatcher.Handler handler : handlers) {
					handler.dangerousTorVersion(new1, current, recommended);
				}
			}

			@Override public void circuitEstablished() {
				for (EventDispatcher.Handler handler : handlers) {
					handler.circuitEstablished();
				}
			}

			@Override public void externalAddressChanged(InetAddress byName, String string) {
				for (EventDispatcher.Handler handler : handlers) {
					handler.externalAddressChanged(byName, string);
				}
			}

			@Override public void serverDescriptorRejected(InetAddress first, Integer second, String string) {
				for (EventDispatcher.Handler handler : handlers) {
					handler.serverDescriptorRejected(first, second, string);
				}
			}

			@Override public void dnsUseless() {
				for (EventDispatcher.Handler handler : handlers) {
					handler.dnsUseless();
				}
			}

			@Override public void dnsHijacked() {
				for (EventDispatcher.Handler handler : handlers) {
					handler.dnsHijacked();
				}
			}

			@Override public void serverDescriptorAccepted(InetAddress first, Integer second) {
				for (EventDispatcher.Handler handler : handlers) {
					handler.serverDescriptorAccepted(first, second);
				}
			}

			@Override public void serverDescriptorAccepted() {
				for (EventDispatcher.Handler handler : handlers) {
					handler.serverDescriptorAccepted();
				}
			}

			@Override public void dirPortReachabilityFinished(InetAddress first, Integer second, boolean b) {
				for (EventDispatcher.Handler handler : handlers) {
					handler.dirPortReachabilityFinished(first, second, b);
				}
			}

			@Override public void orPortReachabilityFinished(InetAddress first, Integer second, boolean b) {
				for (EventDispatcher.Handler handler : handlers) {
					handler.orPortReachabilityFinished(first, second, b);
				}
			}

			@Override public void checkingDirPortReachability(InetAddress first, Integer second) {
				for (EventDispatcher.Handler handler : handlers) {
					handler.checkingDirPortReachability(first, second);
				}
			}

			@Override public void checkingOrPortReachability(InetAddress first, Integer second) {
				for (EventDispatcher.Handler handler : handlers) {
					handler.checkingOrPortReachability(first, second);
				}
			}

			@Override public void socksError(SocksError err, String string) {
				for (EventDispatcher.Handler handler : handlers) {
					handler.socksError(err, string);
				}
			}

			@Override public void dangerousPort(Integer port, boolean reject) {
				for (EventDispatcher.Handler handler : handlers) {
					handler.dangerousPort(port, reject);
				}
			}

			@Override public void addressMapped(String string, String string2, Date expires) {
				for (EventDispatcher.Handler handler : handlers) {
					handler.addressMapped(string, string2, expires);
				}
			}

			@Override public void newDescriptors(String[] descList) {
				for (EventDispatcher.Handler handler : handlers) {
					handler.newDescriptors(descList);
				}
			}

			@Override public void circuitStatusChanged(Circuit circ) {
				for (EventDispatcher.Handler handler : handlers) {
					handler.circuitStatusChanged(circ);
				}
			}

			@Override public void streamStatusChanged(Stream stream) {
				for (EventDispatcher.Handler handler : handlers) {
					handler.streamStatusChanged(stream);
				}
			}

			@Override public void orConnStatusChanged(OrConnection conn) {
				for (EventDispatcher.Handler handler : handlers) {
					handler.orConnStatusChanged(conn);
				}
			}

			@Override public void bootstrapStatusChanged(Bootstrap bs) {
				for (EventDispatcher.Handler handler : handlers) {
					handler.bootstrapStatusChanged(bs);
				}
			}

			@Override public void bug(String s) {
				for (EventDispatcher.Handler handler : handlers) {
					handler.bug(s);
				}
			}
		});
	}
}
