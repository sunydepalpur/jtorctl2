package net.sanpei.tor;

import java.util.HashMap;
import java.util.Map;

import net.sanpei.common.Pair;
import net.sanpei.common.Logs;

import android.text.TextUtils;

public class Globals {

	public static class Severity {
		public static final int Unrecognized = 0;
		/** < An unrecognized severity value. */
		public static final int Debug = 16;
		/** < Hyper-verbose events used for debugging. */
		public static final int Info = 8;
		/** < Verbose events that can occur frequently. */
		public static final int Notice = 4;
		/** < A not-so-bad event. */
		public static final int Warn = 2;
		/** < An important, but non-fatal event. */
		public static final int Error = 1;

		/** < A critical event. */
		public static int parse(String str) {
			if (TextUtils.isEmpty(str)) {
				Logs.d(Severity.class.getName(), "parsing meet empty string");
				return Unrecognized;
			}
			if (str.equalsIgnoreCase("DEBUG"))
				return Debug;
			if (str.equalsIgnoreCase("INFO"))
				return Info;
			if (str.equalsIgnoreCase("NOTICE"))
				return Notice;
			if (str.equalsIgnoreCase("WARN"))
				return Warn;
			if (str.equalsIgnoreCase("ERR"))
				return Error;
			return Unrecognized;
		}

		public static String toString(int severity) {
			switch (severity) {
			case Debug:
				return "DEBUG";
			case Info:
				return "INFO";
			case Notice:
				return "NOTICE";
			case Warn:
				return "WARN";
			case Error:
				return "ERR";
			default:
				return "Unrecognized";
			}
		}
	}

	/**
	 * Parses a series of space-separated key[=value|="value"] tokens from
	 * <b>str</b> and returns the mappings in a Map. If <b>str</b> was unable to
	 * be parsed, <b>ok</b> is set to false.
	 */

	public static Map<String, String> parseKeyValues(final String str) {
		int i, len;
		Map<String, String> keyvals = new HashMap<String, String>();
		i = 0;
		len = str.length();
		while (i < len && Character.isSpace(str.charAt(i)))
			i++; /* Skip initial whitespace */
		while (i < len) {
			String key = "";
			String val = "";

			while (i < len && !Character.isSpace(str.charAt(i)) && str.charAt(i) != '=')
				key += str.charAt(i++);

			if (i < len && str.charAt(i) == '=') {
				if (++i < len && str.charAt(i) == '\"') {
					/* The value is wrapped in quotes */
					val += str.charAt(i);
					while (++i < len) {
						val += str.charAt(i);
						if (str.charAt(i) == '\\') {
							if (++i == len)
								return null;
							val += str.charAt(i);
						} else if (str.charAt(i) == '\"') {
							i++;
							break;
						}
					}
					val = Globals.unescape(val);
					if (val == null)
						return null;
					keyvals.put(key, val);
				} else {
					/* The value was not wrapped in quotes */
					while (i < len && !Character.isSpace(str.charAt(i)))
						val += (str.charAt(i++));
					keyvals.put(key, val);
				}
			} else {
				/* The key had no value */
				keyvals.put(key, "");
			}
			while (i < len && Character.isSpace(str.charAt(i)))
				i++;
		}
		return keyvals;
	}

	public static String unescape(final String str) {
		if (TextUtils.isEmpty(str)) {
			Logs.d(Globals.class.getName(), "unescape meet empty string");
			return "";
		}
		StringBuilder out = new StringBuilder();

		/* The string must start and end with an unescaped dquote */
		if (str.length() < 2 || !str.startsWith("\"") || !str.endsWith("\"") || (str.endsWith("\\\"") && !str.endsWith("\\\\\""))) {
			return null;
		}
		for (int i = 1; i < str.length() - 1; i++) {
			if (str.charAt(i) == '\\')
				i++;
			out.append(str.charAt(i));
		}

		return out.toString();
	}

	public enum SocksError {
		DangerousSocksTypeError, UnknownSocksProtocolError, BadSocksHostnameError

	}

	public enum TorVersionStatus {
		New, Obsolete, Unrecommended
	}

	public enum Signal {
		/** < SIGHUP: Reloads config items and refetch directory */
		Reload, 
		/** < SIGINT: Controlled shutdown */
		Shutdown,
		/** < SIGUSR1: Log information about current circuits */
		Dump, 
		/** < SIGUSR2: Switch all open logs to loglevel debug */
		Debug,
		/** < SIGTERM: Immediate shutdown */
		Halt,
		/** < NEWNYM: Switch to all new clean circuits */
		NewNym;		

		public String toString() {
			String sigtype;
			switch (this) {
			case Reload:
				sigtype = "RELOAD";
				break;
			case Shutdown:
				sigtype = "SHUTDOWN";
				break;
			case Dump:
				sigtype = "DUMP";
				break;
			case Debug:
				sigtype = "DEBUG";
				break;
			case Halt:
				sigtype = "HALT";
				break;
			case NewNym:
				sigtype = "NEWNYM";
				break;
			default:
				return "UNKNOWN";
			}
			return sigtype;
		}
	}

	public enum Event {
		Unknown, Bandwidth, LogDebug, LogInfo, LogNotice, LogWarn, LogError, CircuitStatus, StreamStatus, OrConnStatus, NewDescriptor, AddressMap, GeneralStatus, ClientStatus, ServerStatus;

		public String toString() {
			return toString(this);
		}

		/** Converts an event type to a string Tor understands */
		public static String toString(Event e) {
			String event;
			switch (e) {
			case Bandwidth:
				event = "BW";
				break;
			case LogDebug:
				event = "DEBUG";
				break;
			case LogInfo:
				event = "INFO";
				break;
			case LogNotice:
				event = "NOTICE";
				break;
			case LogWarn:
				event = "WARN";
				break;
			case LogError:
				event = "ERR";
				break;
			case CircuitStatus:
				event = "CIRC";
				break;
			case StreamStatus:
				event = "STREAM";
				break;
			case OrConnStatus:
				event = "ORCONN";
				break;
			case NewDescriptor:
				event = "NEWDESC";
				break;
			case AddressMap:
				event = "ADDRMAP";
				break;
			case GeneralStatus:
				event = "STATUS_GENERAL";
				break;
			case ClientStatus:
				event = "STATUS_CLIENT";
				break;
			case ServerStatus:
				event = "STATUS_SERVER";
				break;
			default:
				event = "UNKNOWN";
				break;
			}
			return event;
		}

		public static Event parse(final String event) {
			if (TextUtils.isEmpty(event)) {
				Logs.d(Event.class.getName(), "parsing meet empty string");
				return Unknown;
			}
			Event e;
			if (event.equals("BW")) {
				e = Bandwidth;
			} else if (event.equals("ORCONN")) {
				e = OrConnStatus;
			} else if (event.equals("CIRC")) {
				e = CircuitStatus;
			} else if (event.equals("STREAM")) {
				e = StreamStatus;
			} else if (event.equals("DEBUG")) {
				e = LogDebug;
			} else if (event.equals("INFO")) {
				e = LogInfo;
			} else if (event.equals("NOTICE")) {
				e = LogNotice;
			} else if (event.equals("WARN")) {
				e = LogWarn;
			} else if (event.equals("ERR")) {
				e = LogError;
			} else if (event.equals("NEWDESC")) {
				e = NewDescriptor;
			} else if (event.equals("ADDRMAP")) {
				e = AddressMap;
			} else if (event.equals("STATUS_GENERAL")) {
				e = GeneralStatus;
			} else if (event.equals("STATUS_CLIENT")) {
				e = ClientStatus;
			} else if (event.equals("STATUS_SERVER")) {
				e = ServerStatus;
			} else {
				e = Unknown;
			}
			return e;
		}
	}
	
	public static Pair<String, String> parseLog(String stdoutLine) {
		int i, j;

		if (!TextUtils.isEmpty(stdoutLine)) {			
			i = stdoutLine.indexOf("[");
			j = stdoutLine.indexOf("]");
			if (i > 0 && j > i && stdoutLine.length() >= j + 2) {
				return new Pair<String, String>(stdoutLine.substring(i + 1, j), stdoutLine.substring(j + 2));
			}
		}
		return null;
	}

}
