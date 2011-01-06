//Copyright 2010 333pei@gmail.com

package net.sanpei.tor;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.Date;
import java.util.Map;

import android.text.TextUtils;

import net.sanpei.common.Pair;
import net.sanpei.common.Logs;
import net.sanpei.tor.Globals.Event;
import net.sanpei.tor.Globals.SocksError;
import net.sanpei.tor.Globals.TorVersionStatus;
import net.sanpei.tor.entities.Bootstrap;
import net.sanpei.tor.entities.Circuit;
import net.sanpei.tor.entities.ControlReply;
import net.sanpei.tor.entities.OrConnection;
import net.sanpei.tor.entities.ReplyLine;
import net.sanpei.tor.entities.Stream;


public class EventDispatcher {

	private static final String TAG = EventDispatcher.class.toString();
	private Handler listener;

	public EventDispatcher(Handler handler) {
		if(handler == null) throw new RuntimeException("argument 'handler' cannot be null");
		listener = handler;
	}

	/**
	 * Handles an event message from Tor. An event message can potentially have
	 * more than one line, so we will iterate through them all and dispatch the
	 * necessary events.
	 */
	void handleEvent(final ControlReply reply) {
		for (ReplyLine line : reply.getLines()) {
			Event evt = parseEventType(line);
			switch (evt) {
			case Bandwidth:
				handleBandwidthUpdate(line);
				break;
			case OrConnStatus:
				handleOrConnStatus(line);
				break;
			case CircuitStatus:
				handleCircuitStatus(line);
				break;
			case StreamStatus:
				handleStreamStatus(line);
				break;
			case NewDescriptor:
				handleNewDescriptor(line);
				break;
			case AddressMap:
				handleAddressMap(line);
				break;

			case GeneralStatus:
			case ClientStatus:
			case ServerStatus:
				handleStatusEvent(evt, line);
				break;
			case LogDebug:
			case LogInfo:
			case LogNotice:
			case LogWarn:
			case LogError:
				handleLogMessage(line);
				break;
			default:
				Logs.w(TAG, "Unknow Event, not be dispatched: " + line.toString());
				break;
			}
		}
	}

	/**
	 * Handle a bandwidth update event, to inform the controller of the
	 * bandwidth used in the last second. The format of this message is:
	 * 
	 * "650" SP "BW" SP BytesRead SP BytesWritten BytesRead = 1*DIGIT
	 * BytesWritten = 1*DIGIT
	 */
	void handleBandwidthUpdate(final ReplyLine line) {
		String[] msg = line.getMessage().split(" ");
		long bytesIn = -1;
		long bytesOut = -1;
		if (msg.length >= 3) {
			try {
				bytesIn = Long.parseLong(msg[1]);
				bytesOut = Long.parseLong(msg[2]);
			} catch (Exception e) {

			}

			/* Post the event to each of the interested targets */
			listener.bandwidthUpdate(bytesIn, bytesOut);
		}
	}
	
	private void handleOrConnStatus(ReplyLine line) {
		String msg = line.getMessage().trim();
		int i = msg.indexOf(" ") + 1;
		if(i > 0) {
			OrConnection conn = OrConnection.parse(msg.substring(i));
			if(conn != null) {
				listener.orConnStatusChanged(conn);
			}
		}		
	}

	/**
	 * Handle a circuit status event. The format of this message is:
	 * 
	 * "650" SP "CIRC" SP CircuitID SP CircStatus SP Path CircStatus =
	 * "LAUNCHED" / ; circuit ID assigned to new circuit "BUILT" / ; all hops
	 * finished, can now accept streams "EXTENDED" / ; one more hop has been
	 * completed "FAILED" / ; circuit closed (was not built) "CLOSED" ; circuit
	 * closed (was built) Path = ServerID *("," ServerID)
	 */
	void handleCircuitStatus(final ReplyLine line) {
		String msg = line.getMessage().trim();
		int i = msg.indexOf(" ") + 1;
		if (i > 0) {
			/* Post the event to each of the interested targets */
			Circuit circ = new Circuit(msg.substring(i));
			if (circ.isValid()) {
				listener.circuitStatusChanged(circ);
			}
		}
	}

	/**
	 * Handle a stream status event. The format of this message is:
	 * 
	 * "650" SP "STREAM" SP StreamID SP StreamStatus SP CircID SP Target SP
	 * StreamStatus = "NEW" / ; New request to connect "NEWRESOLVE" / ; New
	 * request to resolve an address "SENTCONNECT" / ; Sent a connect cell along
	 * a circuit "SENTRESOLVE" / ; Sent a resolve cell along a circuit
	 * "SUCCEEDED" / ; Received a reply; stream established "FAILED" / ; Stream
	 * failed and not retriable. "CLOSED" / ; Stream closed "DETACHED" ;
	 * Detached from circuit; still retriable. Target = Address ":" Port
	 * 
	 * If the circuit ID is 0, then the stream is unattached.
	 */
	void handleStreamStatus(final ReplyLine line) {
		String msg = line.getMessage().trim();
		int i = msg.indexOf(" ") + 1;
		if (i > 0) {
			Stream stream = Stream.parse(msg.substring(i));
			if (stream.isValid())
				listener.streamStatusChanged(stream);
		}
	}

	/**
	 * Handle a log message event. The format of this message is: The syntax is:
	 * 
	 * "650" SP Severity SP ReplyText or "650+" Severity CRLF Data Severity =
	 * "DEBUG" / "INFO" / "NOTICE" / "WARN"/ "ERR"
	 */
	void handleLogMessage(final ReplyLine line) {
		String msg = line.getMessage();
		int i = msg.indexOf(" ");
		int severity = Globals.Severity.parse(msg.substring(0, i));
		String logLine = (line.getData().size() > 0 ? TextUtils.join("\n", line.getData()) : msg.substring(i + 1));

		listener.logMessage(severity, logLine);
	}

	/**
	 * Handles a new descriptor event. The format for event messages of this
	 * type is:
	 * 
	 * "650" SP "NEWDESC" 1*(SP ServerID)
	 */
	void handleNewDescriptor(final ReplyLine line) {
		String descs = line.getMessage();
		String[] descList = descs.substring(descs.indexOf(" ") + 1).split(" ");
		listener.newDescriptors(descList);
	}

	/**
	 * Handles a new or updated address mapping event. The format for event
	 * messages of this type is:
	 * 
	 * "650" SP "ADDRMAP" SP Address SP Address SP Expiry Expiry = DQUOTE
	 * ISOTime DQUOTE / "NEVER"
	 * 
	 * Expiry is expressed as the local time (rather than GMT).
	 */
	void handleAddressMap(final ReplyLine line) {
		String[] msg = line.getMessage().split(" ");
		if (msg.length >= 4) {
			Date expires = null;
			if (msg.length >= 5 & msg[3] != "NEVER")
				expires = new Date(Date.parse((msg[3] + " " + msg[4])));
			listener.addressMapped(msg[1], msg[2], expires);
		}
	}

	/**
	 * Handles a Tor status event. The format for event messages of this type
	 * is:
	 * 
	 * "650" SP StatusType SP StatusSeverity SP StatusAction [SP
	 * StatusArguments] CRLF
	 * 
	 * StatusType = "STATUS_GENERAL" / "STATUS_CLIENT" / "STATUS_SERVER"
	 * StatusSeverity = "NOTICE" / "WARN" / "ERR" StatusAction = 1*ALPHA
	 * StatusArguments = StatusArgument *(SP StatusArgument) StatusArgument =
	 * StatusKeyword '=' StatusValue StatusKeyword = 1*(ALNUM / "_") StatusValue
	 * = 1*(ALNUM / '_') / QuotedString
	 */
	void handleStatusEvent(Event e, final ReplyLine line) {		
		String status;
		int severity;
		Map<String, String> args;
		String msg = line.getMessage();
		String[] msgs = msg.split(" ");
		if(msgs.length < 3) {
			Logs.w(TAG, "a message has " + msgs.length +" segs only, return..");
			return;
		}
		severity = Globals.Severity.parse(msgs[1]);
		status = msgs[2];
		if(msgs.length < 4) {
			Logs.d(TAG, "a status event with empty args.");
			args = Collections.emptyMap();
		}
		else {
			for(int i=4;i < msgs.length;i++) {
				msgs[3] += " " + msgs[i];
			}
			args = Globals.parseKeyValues(msgs[3]);
		}
		switch (e) {
		case ClientStatus:
			handleClientStatusEvent(severity, status, args);
			break;

		case ServerStatus:
			handleServerStatusEvent(severity, status, args);
			break;

		case GeneralStatus:
			handleGeneralStatusEvent(severity, status, args);
			break;

		default:
			break;
		}
	}

	/** Parses and emits a general Tor status event. */
	void handleGeneralStatusEvent(int severity, final String action, final Map<String, String> args) {
		if (action.equalsIgnoreCase("DANGEROUS_TOR_VERSION")) {
			String reason = args.get("REASON");
			String current = args.get("CURRENT");
			String[] recommended = args.get("RECOMMENDED").split("(\\s)*,(\\s)*");
			if (reason.equalsIgnoreCase("NEW"))
				listener.dangerousTorVersion(TorVersionStatus.New, current, recommended);
			else if (reason.equalsIgnoreCase("UNRECOMMENDED"))
				listener.dangerousTorVersion(TorVersionStatus.Unrecommended, current, recommended);
			else if (reason.equalsIgnoreCase("OBSOLETE") || !reason.equalsIgnoreCase("OLD"))
				listener.dangerousTorVersion(TorVersionStatus.Obsolete, current, recommended);
		} else if (action.equalsIgnoreCase("CLOCK_SKEW")) {
			Integer skew = null;
			if (args.containsKey("SKEW"))
				try {
					skew = Integer.parseInt(args.get("SKEW"));
				} catch (Exception e) {
					skew = null;
				}
			else if (args.containsKey("MIN_SKEW"))
				try {
					skew = Integer.parseInt(args.get("MIN_SKEW"));
				} catch (Exception e) {
					skew = null;
				}
			if (skew == null)
				listener.clockSkewed(skew, args.get("SOURCE"));
		} else if (action.equalsIgnoreCase("BUG")) {
			listener.bug(args.get("REASON"));
		}
	}

	/** Parses and emits a Tor client status event. */
	void handleClientStatusEvent(int severity, final String action, final Map<String, String> args) {
		if (action.equalsIgnoreCase("CIRCUIT_ESTABLISHED")) {
			listener.circuitEstablished();
		} else if (action.equalsIgnoreCase("DANGEROUS_PORT")) {
			boolean reject = !args.get("RESULT").equalsIgnoreCase("REJECT");

			Integer port = null;
			try {
				port = Integer.parseInt(args.get("PORT"));
			} catch (Exception e) {
				port = null;
			}
			listener.dangerousPort(port, reject);
		} else if (action.equalsIgnoreCase("DANGEROUS_SOCKS")) {
			listener.socksError(SocksError.DangerousSocksTypeError, args.get("ADDRESS"));
		} else if (action.equalsIgnoreCase("SOCKS_UNKNOWN_PROTOCOL")) {
			listener.socksError(SocksError.UnknownSocksProtocolError, null);
		} else if (action.equalsIgnoreCase("SOCKS_BAD_HOSTNAME")) {
			listener.socksError(SocksError.BadSocksHostnameError, args.get("HOSTNAME"));
		} else if (action.equalsIgnoreCase("BOOTSTRAP")) {
			int progress = -1;
			try {
				progress = Integer.parseInt(args.get("PROGRESS"));
			} catch (Exception e) {
				progress = -1;
			}
			Bootstrap.Status tag = Bootstrap.Status.parse(args.get("TAG"));
			String summary = args.get("SUMMARY");
			String warn = args.get("WARNING");
			OrConnection.Reason reason = OrConnection.Reason.parse(args.get("REASON"));
			Bootstrap.Recommendation recommendation = Bootstrap.Recommendation.parse(args.get("RECOMMENDATION"));
			Bootstrap bs = new Bootstrap(severity, tag, progress, summary, warn, reason, recommendation);
			listener.bootstrapStatusChanged(bs);
		}
	}

	/** Parses and emits a Tor server status event. */
	void handleServerStatusEvent(int severity, final String action, final Map<String, String> args) {
		if (action.equalsIgnoreCase("EXTERNAL_ADDRESS")) {
			InetAddress addr = null;
			try {
				addr = InetAddress.getByName(args.get("ADDRESS"));
			} catch (UnknownHostException e) {
				addr = null;
				e.printStackTrace();
			}
			listener.externalAddressChanged(addr, args.get("HOSTNAME"));
		} else if (action.equalsIgnoreCase("CHECKING_REACHABILITY")) {
			if (args.containsKey("ORADDRESS")) {
				Pair<InetAddress, Integer> pair = splitAddress(args.get("ORADDRESS"));
				if (pair != null)
					listener.checkingOrPortReachability(pair.first, pair.second);
			} else if (args.containsKey("DIRADDRESS")) {
				Pair<InetAddress, Integer> pair = splitAddress(args.get("DIRADDRESS"));
				if (pair != null)
					listener.checkingDirPortReachability(pair.first, pair.second);
			}
		} else if (action.equalsIgnoreCase("REACHABILITY_SUCCEEDED")) {
			if (args.containsKey("ORADDRESS")) {
				Pair<InetAddress, Integer> pair = splitAddress(args.get("ORADDRESS"));
				if (pair != null)
					listener.orPortReachabilityFinished(pair.first, pair.second, true);
			} else if (args.containsKey("DIRADDRESS")) {
				Pair<InetAddress, Integer> pair = splitAddress(args.get("DIRADDRESS"));
				if (pair != null)
					listener.dirPortReachabilityFinished(pair.first, pair.second, true);
			}
		} else if (action.equalsIgnoreCase("REACHABILITY_FAILED")) {
			if (args.containsKey("ORADDRESS")) {
				Pair<InetAddress, Integer> pair = splitAddress(args.get("ORADDRESS"));
				if (pair != null)
					listener.orPortReachabilityFinished(pair.first, pair.second, false);
			} else if (args.containsKey("DIRADDRESS")) {
				Pair<InetAddress, Integer> pair = splitAddress(args.get("DIRADDRESS"));
				if (pair != null)
					listener.dirPortReachabilityFinished(pair.first, pair.second, false);
			}
		} else if (action.equalsIgnoreCase("GOOD_SERVER_DESCRIPTOR")) {
			listener.serverDescriptorAccepted();
		} else if (action.equalsIgnoreCase("ACCEPTED_SERVER_DESCRIPTOR")) {
			Pair<InetAddress, Integer> pair = splitAddress(args.get("DIRAUTH"));
			if (pair != null)
				listener.serverDescriptorAccepted(pair.first, pair.second);
		} else if (action.equalsIgnoreCase("BAD_SERVER_DESCRIPTOR")) {
			Pair<InetAddress, Integer> pair = splitAddress(args.get("DIRAUTH"));
			if (pair != null)
				listener.serverDescriptorRejected(pair.first, pair.second, args.get("REASON"));
		} else if (action.equalsIgnoreCase("DNS_HIJACKED")) {
			listener.dnsHijacked();
		} else if (action.equalsIgnoreCase("DNS_USELESS")) {
			listener.dnsUseless();
		}
	}

	/**
	 * Splits a string in the form "IP:PORT" into a InetAddress and Integer
	 * pair. If either portion is invalid, null is returned
	 */
	Pair<InetAddress, Integer> splitAddress(final String address) {

		int idx = address.indexOf(":");
		if (idx <= 0 || idx >= address.length() - 1)
			return null;

		InetAddress ip = null;
		try {
			ip = InetAddress.getByName(address.substring(0, idx));
		} catch (UnknownHostException e1) {
		}
		Integer port = null;
		try {
			port = Integer.parseInt(address.substring(idx + 1));
		} catch (NumberFormatException e) {
			port = null;
		}
		if (ip == null || port == null)
			return null;
		return new Pair<InetAddress, Integer>(ip, port);
	}

	public static class Handler {
		public void bandwidthUpdate(long bytesIn, long bytesOut) {		}
		public void bug(String string) {		}
		public void clockSkewed(Integer skew, String string) {		}
		public void dangerousTorVersion(TorVersionStatus new1, String current, String[] recommended) {		}
		public void bootstrapStatusChanged(Bootstrap status) {		}
		public void circuitEstablished() {		}
		public void externalAddressChanged(InetAddress byName, String string) {		}
		public void serverDescriptorRejected(InetAddress first, Integer second, String string) {		}
		public void dnsUseless() {		}
		public void dnsHijacked() {		}
		public void serverDescriptorAccepted(InetAddress first, Integer second) {		}
		public void serverDescriptorAccepted() {		}
		public void dirPortReachabilityFinished(InetAddress first, Integer second, boolean b) {		}
		public void orPortReachabilityFinished(InetAddress first, Integer second, boolean b) {		}
		public void checkingDirPortReachability(InetAddress first, Integer second) {		}
		public void checkingOrPortReachability(InetAddress first, Integer second) {		}
		public void socksError(SocksError err, String string) {		}
		public void dangerousPort(Integer port, boolean reject) {		}
		public void addressMapped(String string, String string2, Date expires) {		}
		public void newDescriptors(String[] descList) {		}
		public void logMessage(int severity, String logLine) {		}
		public void orConnStatusChanged(OrConnection conn) { }
		public void circuitStatusChanged(Circuit circ) {		}		
		public void streamStatusChanged(Stream stream) {		}
	}

	/**
	 * Parse the event type out of a message line and return the corresponding
	 * Event enum value
	 */
	public Event parseEventType(final ReplyLine line) {
		String msg = line.getMessage();
		int i = msg.indexOf(" ");
		return Event.parse(msg.substring(0, i));
	}

}
