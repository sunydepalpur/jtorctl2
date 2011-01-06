//Copyright 2010 333pei@gmail.com
//some code from net.freehaven.tor.control 

package net.sanpei.tor;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.StringTokenizer;

import net.freehaven.tor.control.TorControlCommands;
import net.freehaven.tor.control.TorControlError;
import net.freehaven.tor.control.TorControlSyntaxError;

import net.sanpei.common.Handlers;
import net.sanpei.common.Helpers;
import net.sanpei.common.Logs;
import net.sanpei.common.Waiter;
import net.sanpei.tor.Globals.Signal;
import net.sanpei.tor.entities.ControlReply;
import net.sanpei.tor.entities.ReplyLine;

/** A connection to a running Tor process as specified in control-spec.txt. */
public class Connection implements TorControlCommands {
	
	private static final String TAG = Connection.class.getName();	
	protected java.io.PrintWriter debugOutput;
	
	protected class ControlParseThread extends Thread {
		private final String TAG = ControlParseThread.class.getName();
		boolean running = true;
		BufferedReader reader = new BufferedReader(input);
		private Waiter<Object> stopWaiter = new Waiter<Object>();		
		protected final LinkedList<Waiter<ControlReply>> waiters = new LinkedList<Waiter<ControlReply>>();
		
		protected final ControlReply readReply() throws TorControlSyntaxError, IOException {
			ArrayList<ReplyLine> lines = new ArrayList<ReplyLine>();
			char c;
			do {
				String line = reader.readLine();
				if (line == null) {
					// if line is null, the end of the stream has been reached, i.e.
					// the connection to Tor has been closed!
					if (lines.isEmpty()) {
						return null;
					}
					// received half of a reply before the connection broke down
					throw new TorControlSyntaxError("Connection to Tor " + " broke down while receiving reply!");
				}
				if (debugOutput != null)
					debugOutput.println("<< " + line);
				if (line.length() < 4)
					throw new TorControlSyntaxError("Line (\"" + line + "\") too short");
				String status = line.substring(0, 3);
				c = line.charAt(3);
				String msg = line.substring(4);
				String rest = null;
				if (c == '+') {
					StringBuffer data = new StringBuffer();
					while (true) {
						line = reader.readLine();
						if (debugOutput != null)
							debugOutput.print("<< " + line);
						if (line.equals("."))
							break;
						else if (line.startsWith("."))
							line = line.substring(1);
						data.append(line).append('\n');
					}
					rest = data.toString();
				}
				lines.add(new ReplyLine(status, msg, rest));
			} while (c != ' ');

			return new ControlReply(lines);
		}

		/** helper: implement the main background loop. */
		protected void react() throws IOException {
			while (true) {
				ControlReply cr = readReply();
				if (null == cr) {
					Logs.e(TAG, "connection has been closed remotely! end the loop!");
					// connection has been closed remotely! end the loop!
					return;
				}

				if (cr.isEmpty()) {
					Logs.e(TAG, "empty reply encoutered, strange~");
					continue;
				}

				if (cr.getLine(0).getStatus().startsWith("6"))
					handleEvent(cr);
				else {
					Waiter<ControlReply> w;
					synchronized (waiters) {
						/*
						 * if(waiters.isEmpty()) { SPLog.w(TAG,
						 * "a reply, but waiters empty."); continue;} // XXX is
						 * that right? though viladia do this.
						 */
						w = waiters.removeFirst();
					}
					w.setResponse(cr);
				}
			}			
		}

		@Override
		public void run() {
			try {
				react();
			} catch (IOException ex) {
				if (running) {
					Logs.e(TAG, "react FAILED with exception: " + ex.getMessage());
					setStop();
					throw new RuntimeException(ex);
				}
				// we expected this exception
				Logs.w(TAG, "react FAILED because Shutdown: " + ex.getMessage());				
			}
			Logs.w(TAG, "normal quit, strange~ ");
			setStop();			
		}

		private void prepareForStop() {			
			Logs.d(TAG, "prepareForStop Called.");
			running = false;													
		}
		
		private void closeReader() {
			Logs.d(TAG, "closeReader Called.");
			Helpers.close(TAG, reader);
		}
		
		private void waitForStop() {
			Logs.d(TAG, "waitForStop Called.");
			stopWaiter.getResponse();			
		}					

		private void setStop() {
			while (!waiters.isEmpty()) {
				Waiter<ControlReply> w = waiters.removeFirst();
				w.setResponse(new ControlReply());					
			}
			stopWaiter.setResponse(null);								
			Logs.d(TAG, "setStop Called and executed.");			
		}
	}

	protected ControlParseThread thread;
	protected EventDispatcher handler;
	protected java.io.Reader input;
	protected java.io.Writer output;


	public static Connection getConnection(java.net.Socket sock) throws IOException {
		return new Connection(sock);
	}

	/**
	 * Create a new TorControlConnection to communicate with Tor over a given
	 * socket. After calling this constructor, it is typical to call
	 * launchThread and authenticate.
	 */
	public Connection(final java.net.Socket s) throws IOException {
		this(s.getInputStream(), s.getOutputStream());
	}

	/**
	 * Create a new TorControlConnection to communicate with Tor over an
	 * arbitrary pair of data streams.
	 */
	public Connection(java.io.InputStream i, java.io.OutputStream o) {
		this(new java.io.InputStreamReader(i), new java.io.OutputStreamWriter(o));
	}

	public Connection(java.io.Reader i, java.io.Writer o) {
		this.output = o;
		this.input = i;			
	}

	protected final void writeEscaped(String s) throws IOException {
		StringTokenizer st = new StringTokenizer(s, "\n");
		while (st.hasMoreTokens()) {
			String line = st.nextToken();
			if (line.startsWith("."))
				line = "." + line;
			if (line.endsWith("\r"))
				line += "\n";
			else
				line += "\r\n";
			if (debugOutput != null)
				debugOutput.print(">> " + line);
			output.write(line);
		}
		output.write(".\r\n");
		if (debugOutput != null)
			debugOutput.print(">> .\n");
	}

	/*
	 * protected synchronized void send(String s, String rest) throws
	 * IOException { checkThread(); output.write(s); if(rest != null)
	 * writeEscaped(rest); output.flush(); }
	 */
	
	/** this call can be broken by close or shutdown, but synchronized between each send */
	protected synchronized ControlReply sendAndWaitForResponse(String s, String rest) throws TorControlError, IOException {
		if(thread == null) throw new TorControlError("Connection is not open.");
		if(!thread.isAlive() || !thread.running) throw new TorControlError("Connection has been closed.");
		Waiter<ControlReply> w = new Waiter<ControlReply>();
		if (debugOutput != null)
			debugOutput.print(">> " + s);
		synchronized (thread.waiters) {
			output.write(s);
			if (rest != null)
				writeEscaped(rest);
			output.flush();
			thread.waiters.addLast(w);
		}
		if(!thread.isAlive() || !thread.running) throw new TorControlError("Connection has been interrupted and closed.");
		ControlReply r = w.getResponse();		
		for (Iterator<ReplyLine> i = r.getLines().iterator(); i.hasNext();) {
			ReplyLine c = i.next();
			if (!c.getStatus().startsWith("2"))
				throw new TorControlError("Error reply: " + c.getMessage());
		}
		return r;				
	}

	/**
	 * Start a thread to react to Tor's responses in the background. This is
	 * necessary to handle asynchronous events and synchronous responses that
	 * arrive independantly over the same socket.
	 */
	public synchronized void open(boolean daemon) {
		if (thread != null)
			return;
			
		ControlParseThread th = new ControlParseThread();
		th.setDaemon(daemon);
		th.start();
		thread = th;		
	}		

	/** this method will block current thread which make the call */
	public void close(Handlers.NoArgumentHandler stopTrigger) {			
		if (thread == null)
			return;
		if (!thread.isAlive()) {			
			return;
		}						
		thread.prepareForStop();
		Logs.d(TAG, "stop by custom trigger and reader closing.");
		stopTrigger.onFired();
		thread.closeReader();				
		thread.waitForStop();		
	}

	// ----------------------- some functions -------------------------------

	/**
	 * Helper: decode a CMD_EVENT command and dispatch it to our EventHandler
	 * (if any).
	 */
	protected void handleEvent(ControlReply reply) {
		if (handler == null)
			return;
		handler.handleEvent(reply);
	}

	/**
	 * Set the EventHandler object that will be notified of any events Tor
	 * delivers to this connection. To make Tor send us events, call
	 * setEvents().
	 */
	public void setEventHandler(EventDispatcher.Handler onEvents) {
		if (onEvents == null) {
			this.handler = null;
			return;
		}
		this.handler = new EventDispatcher(onEvents);
	}

	/**
	 * Request that the server inform the client about interesting events. Each
	 * element of <b>events</b> is one of the following Strings: ["CIRC" |
	 * "STREAM" | "ORCONN" | "BW" | "DEBUG" | "INFO" | "NOTICE" | "WARN" | "ERR"
	 * | "NEWDESC" | "ADDRMAP"] .
	 * 
	 * Any events not listed in the <b>events</b> are turned off; thus, calling
	 * setEvents with an empty <b>events</b> argument turns off all event
	 * reporting.
	 */
	public void setEvents(Globals.Event[] events) throws IOException {
		StringBuffer sb = new StringBuffer("SETEVENTS");
		for (Globals.Event ev : events) {
			sb.append(" ").append(ev.toString());
		}
		sb.append("\r\n");
		sendAndWaitForResponse(sb.toString(), null);
	}

	/**
	 * Sends a signal from the controller to the Tor server. <b>signal</b> is
	 * one of the following Strings:
	 * <ul>
	 * <li>"RELOAD" or "HUP" : Reload config items, refetch directory</li>
	 * <li>"SHUTDOWN" or "INT" : Controlled shutdown: if server is an OP, exit
	 * immediately. If it's an OR, close listeners and exit after 30 seconds</li>
	 * <li>"DUMP" or "USR1" : Dump stats: log information about open connections
	 * and circuits</li>
	 * <li>"DEBUG" or "USR2" : Debug: switch all open logs to loglevel debug</li>
	 * <li>"HALT" or "TERM" : Immediate shutdown: clean up and exit now</li>
	 * </ul>
	 */
	public void signal(Signal signal) throws IOException {
		if (signal == Signal.Shutdown || signal == Signal.Halt) {
			shutdownTor(signal);
			return;
		}
		String cmd = "SIGNAL " + signal.toString() + "\r\n";
		sendAndWaitForResponse(cmd, null);
	}

	/**
	 * Send a signal to the Tor process to shut it down or halt it. Does not
	 * wait for a response.
	 */
	private void shutdownTor(Signal signal) throws IOException {		
		if(thread == null) return;
		final String s = "SIGNAL " + signal.toString() + "\r\n";
		if (debugOutput != null)
			debugOutput.print(">> " + s);		
		final Waiter<ControlReply> w = new Waiter<ControlReply>();
		 
		thread.prepareForStop();		
		Logs.d(TAG, "stop by reqeust SHUTDOWN.");		
		synchronized (thread.waiters) {			
			output.write(s);
			output.flush();
			thread.waiters.addLast(w); // Prevent react() from finding the list empty
		}
		thread.waitForStop();			
	}

/** Sets <b>w</b> as the PrintWriter for debugging output, 
	 * which writes out all messages passed between Tor and the controller.  
	 * Outgoing messages are preceded by "\>\>" and incoming messages are preceded
	 * by "\<\<"
	 */
	public void setDebugging(java.io.PrintWriter w) {
		debugOutput = w;
	}

/** Sets <b>s</b> as the PrintStream for debugging output, 
	 * which writes out all messages passed between Tor and the controller.  
	 * Outgoing messages are preceded by "\>\>" and incoming messages are preceded
	 * by "\<\<"
	 */
	public void setDebugging(java.io.PrintStream s) {
		debugOutput = new java.io.PrintWriter(s, true);
	}
}
