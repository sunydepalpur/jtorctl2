//Copyright 2010 333pei@gmail.com

package net.sanpei.tor;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Date;

import android.text.TextUtils;

import net.sanpei.common.Handlers;
import net.sanpei.common.Helpers;
import net.sanpei.common.ProcessHelpers;
import net.sanpei.common.Logs;
import net.sanpei.common.Handlers.NoArgumentHandler;
import net.sanpei.tor.Globals.Severity;
import net.sanpei.tor.Globals.SocksError;
import net.sanpei.tor.Globals.TorVersionStatus;
import net.sanpei.tor.entities.Bootstrap;
import net.sanpei.tor.entities.Circuit;
import net.sanpei.tor.entities.OrConnection;
import net.sanpei.tor.entities.Stream;

public class Controller {
	private static final String TAG = Controller.class.getName();

	private static class ConfigArguments {
		public static final String HttpProxy = "HttpProxy";
		public static final String HttpsProxy = "HttpsProxy";
		public static final String DataDirectory = "DataDirectory";
		public static final String PidFile = "PidFile";
	}

	public static final int DefaultTransPort = 9040;
	public static final int DefaultSocksPort = 9050;
	private String command = null;
	private final String argument;
	private final String pidFilePath;
	private Handlers.Handler<String> stdoutListener = null;

	private final static Object syncLock = new Object();
	private Operator operations = null;
	private NoArgumentHandler processHelpersStopper;

	public Controller(String torExecPath, String torCfgPath, String httpProxy, String httpsProxy) {
		command = torExecPath;
		operations = new Operator(torCfgPath);
		String dir = torExecPath.substring(0, torExecPath.lastIndexOf("/"));
		pidFilePath = dir + "/tor.pid";
		String tmp = commonArguments(torCfgPath, pidFilePath, dir);
		tmp += " --" + ConfigArguments.HttpProxy + " " + httpProxy;
		tmp += " --" + ConfigArguments.HttpsProxy + " " + httpsProxy;
		argument = tmp;
	}

	public Controller(String torExecPath, String torCfgPath) {
		command = torExecPath;
		operations = new Operator(torCfgPath);
		String dir = torExecPath.substring(0, torExecPath.lastIndexOf("/"));
		pidFilePath = dir + "/tor.pid";
		argument = commonArguments(torCfgPath, pidFilePath, dir);
	}

	private static String commonArguments(String torCfgPath, String pidFilePath, String dir) {
		String tmp = String.format(" -f %s", torCfgPath);
		tmp += " --" + ConfigArguments.PidFile + " " + pidFilePath;
		tmp += " --" + ConfigArguments.DataDirectory + " " + dir;
		return tmp;
	}

	public void setStdoutListener(Handlers.Handler<String> listener) {
		stdoutListener = listener;
	}

	public void stopProcess() {
		Logs.i(TAG, "stopProcess called.");
		if (!stopAndClose()) {
			synchronized (syncLock) {				
				try {
					operations.connect();
				} catch (IOException e) {
					Logs.e(TAG, "Operator Connecting (for stopProcess) Failed: " + e.getMessage());
				}
				stopAndClose();
			}
		}
	}

	private void clean() {
		operations.cleanEventHandlers();
		if (processHelpersStopper != null) {
			processHelpersStopper.onFired();
			processHelpersStopper = null;
		}
	}

	public void deattach() {
		synchronized (syncLock) {
			clean();
			if (operations.isConnected()) {
				operations.disconnect();
			}
		}
	}

	public void attachOrStart() {
		Logs.i(TAG, "start TOR process called");

		if (operations.isConnected()) {
			Logs.i(TAG, "already have a operator, return.");
			return;
		}

		synchronized (syncLock) {
			if (operations.isConnected()) {
				Logs.i(TAG, "already have a operator, return.");
				return;
			}

			try {
				// XXX we don't verify if it is Tor on the other end
				// but we are auth in the Operator, though auth method do not
				// give the result.
				if (!operations.connect()) {
					startProcessPrivate();
					int i=0;
					while (!operations.connect() && i < 3) {
						Logs.w(TAG, "owned process started, connect to Tor Failed, retry after 80ms...");
						Helpers.testSleep(80);
						i++;
					}
					if(!operations.isConnected()) {
						Logs.e(TAG, "cannot connect to Tor (" + String.valueOf(i+1) + " times), not started?");
					}
				} else {
					Logs.w(TAG, "already have aprocess, attatch Operator.");
				}
			} catch (IOException e) {
				Logs.e(TAG, "connect to Tor Failed: " + e.getMessage());
			}
		}
		
		operations.addEventHandler(debugHandler);
	}

	private void startProcessPrivate() {
		try {
			Logs.i(TAG, "executing: " + command + " " + argument);
			File file = new File(command);
			if (!file.exists()) {
				Logs.e(TAG, command + " not exists.");
				return;
			}
			Process process = Runtime.getRuntime().exec(command + " " + argument);
			invokeProcessHelpers(process);

		} catch (IOException e) {
			Logs.e(TAG, "start process FAILED: " + e.getMessage());
		}
	}

	private boolean stopAndClose() {		
		Logs.d(TAG, "stopAndClose called.");
		if(!operations.isConnected()) return false;
		clean();
		synchronized (syncLock) {			
			if (!operations.isConnected()) return false;
			
			Logs.d(TAG, "isConnected, prepare send shutdown signal.");
			operations.shutdown(true);
		}
		return true;
	}

	private void invokeProcessHelpers(final Process process) {
		final Handlers.NoArgumentHandler[] handlers = new Handlers.NoArgumentHandler[1];
		handlers[0] = ProcessHelpers.startStdoutThread(process, new Handlers.Handler<String>() {
			@Override public void onFired(String arg) {
				if (stdoutListener != null) {
					stdoutListener.onFired(arg);
				}
			}
		});
		/*
		handlers[2] = ProcessHelpers.startStderrThread(process, new Handlers.Handler<String>() {
			@Override public void onFired(String arg) {
				Logs.e(TAG, "stderr: " + arg);
			}
		});
		*/
		//handlers[1] = 
		ProcessHelpers.startExitWaitingThread(process, new Handlers.Handler<Integer>() {
			@Override public void onFired(Integer arg) {
				Logs.i(TAG, "waiting handler fired.");
				synchronized (syncLock) {
					if (process != null) {
						process.exitValue();
						process.destroy();
					}
				}
			}
		});

		processHelpersStopper = new Handlers.NoArgumentHandler() {
			@Override public void onFired() {
				for (Handlers.NoArgumentHandler handler : handlers) {
					handler.onFired();
				}
			}
		};
	}

	private static EventDispatcher.Handler debugHandler = new EventDispatcher.Handler() {
		@Override public void logMessage(int i, String message) {
			String msg = String.format("[%s] from ControlSocket: %s", Severity.toString(i), message);
			switch (i) {
			case Severity.Debug:
				Logs.d(TAG, msg);
				break;
			case Severity.Error:
				Logs.e(TAG, msg);
				break;
			case Severity.Info:
				Logs.v(TAG, msg);
				break;
			case Severity.Notice:
				Logs.i(TAG, msg);
				break;
			case Severity.Warn:
				Logs.w(TAG, msg);
				break;
			default:
				Logs.v(TAG, msg);
			}
		}

		@Override public void bandwidthUpdate(long bytesIn, long bytesOut) {
			Logs.i(TAG, "Bandwidth Update, IN: " + String.valueOf(bytesIn) + " OUT: " + String.valueOf(bytesOut));
		}

		@Override public void clockSkewed(Integer skew, String string) {
			Logs.i(TAG, "Clock Skewed: " + string);
		}

		@Override public void dangerousTorVersion(TorVersionStatus new1, String current, String[] recommended) {
			Logs.w(TAG, "Dangerous Tor Version: " + new1.toString() + ", Current: " + current + ", Recommended: " + TextUtils.join(",", recommended));
		}

		@Override public void circuitEstablished() {
			Logs.i(TAG, "Circuit Established ~ ");
		}

		@Override public void externalAddressChanged(InetAddress byName, String string) {
			Logs.i(TAG, "External Address Changed: " + byName.toString() + ", More: " + string);
		}

		@Override public void serverDescriptorRejected(InetAddress first, Integer second, String string) {
			Logs.i(TAG, "Server Descriptor Rejected: " + first.toString() + ":" + second.toString() + ", More: " + string);
		}

		@Override public void dnsUseless() {
			Logs.i(TAG, "DNS Useless");
		}

		@Override public void dnsHijacked() {
			Logs.i(TAG, "DNS Hijacked.");
		}

		@Override public void serverDescriptorAccepted(InetAddress first, Integer second) {
			if (second == null)
				second = -1;
			Logs.i(TAG, "Server Descriptor Accepted: " + first.toString() + ":" + second.toString());
		}

		@Override public void serverDescriptorAccepted() {
			Logs.i(TAG, "Server Descriptor Accepted");
		}

		@Override public void dirPortReachabilityFinished(InetAddress first, Integer second, boolean b) {
			if (second == null)
				second = -1;
			Logs.i(TAG, "DIR Port Reachability has been Checked: " + String.valueOf(b) + ", " + first.toString() + ":" + second.toString());
		}

		@Override public void orPortReachabilityFinished(InetAddress first, Integer second, boolean b) {
			if (second == null)
				second = -1;
			Logs.i(TAG, "OR Port Reachability has been Checked: " + String.valueOf(b) + ", " + first.toString() + ":" + second.toString());
		}

		@Override public void checkingDirPortReachability(InetAddress first, Integer second) {
			if (second == null)
				second = -1;
			Logs.i(TAG, "Checking DIR Port Reachability: " + first.toString() + ":" + second.toString());
		}

		@Override public void checkingOrPortReachability(InetAddress first, Integer second) {
			if (second == null)
				second = -1;
			Logs.i(TAG, "Checking OR Port Reachability: " + first.toString() + ":" + second.toString());
		}

		@Override public void socksError(SocksError err, String string) {
			Logs.e(TAG, "Socks Error: " + err.toString() + ", More: " + string);
		}

		@Override public void dangerousPort(Integer port, boolean reject) {
			Logs.w(TAG, "Dangerous Port: " + port.toString() + ", Reject: " + String.valueOf(reject));
		}

		@Override public void addressMapped(String string, String string2, Date expires) {
			Logs.i(TAG, "Address Mapped: " + string + "/" + string + ", " + expires.toString());
		}

		@Override public void newDescriptors(String[] descList) {
			String txt = TextUtils.join(", ", descList);
			Logs.i(TAG, "New Descriptors: " + txt);
		}

		@Override public void circuitStatusChanged(Circuit circ) {
			String msg = "Circuit '" + circ.getId() + "' Status Changed to " + circ.getStatus().toPrintString();
			if (circ.getStatus() == Circuit.Status.Failed)
				Logs.w(TAG, msg);
			else if (circ.getStatus() == Circuit.Status.Unknown)
				Logs.d(TAG, msg);
			else
				Logs.i(TAG, msg);
		}

		@Override public void streamStatusChanged(Stream stream) {
			String msg = "Stream '" + stream.getId() + "' Status Changed to " + stream.getStatus().toPrintString();
			if (stream.getStatus() == Stream.Status.Failed)
				Logs.w(TAG, msg);
			else if (stream.getStatus() == Stream.Status.Unknown)
				Logs.d(TAG, msg);
			else
				Logs.i(TAG, msg);
		}

		@Override public void orConnStatusChanged(OrConnection conn) {
			String msg = "OrConn of target '" + conn.target + "/" + conn.name + "' Status Changed to " + conn.status.toString();
			if (conn.status == OrConnection.Status.Failed)
				Logs.w(TAG, msg);
			else if (conn.status == OrConnection.Status.Unknown)
				Logs.d(TAG, msg);
			else
				Logs.i(TAG, msg);
		}

		@Override public void bootstrapStatusChanged(Bootstrap bs) {
			Logs.i(TAG, String.format("BootStrap: %d, Status: %s, Reason: %s", bs.percentComplete, bs.status.toString(), bs.reason.toString()));
		}

		@Override public void bug(String s) {
			Logs.e(TAG, s);
		}
	};

}
