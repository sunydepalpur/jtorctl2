//Copyright 2010 333pei@gmail.com

package net.sanpei.tor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.freehaven.tor.control.ConfigEntry;
import net.sanpei.tor.entities.ControlReply;
import net.sanpei.tor.entities.ReplyLine;

public class Commands {
	/**
	 * Queries the Tor server for keyed values that are not stored in the torrc
	 * configuration file. Returns a map of keys to values.
	 * 
	 * Recognized keys include:
	 * <ul>
	 * <li>"version" : The version of the server's software, including the name
	 * of the software. (example: "Tor 0.0.9.4")</li>
	 * <li>"desc/id/<OR identity>" or "desc/name/<OR nickname>" : the latest
	 * server descriptor for a given OR, NUL-terminated. If no such OR is known,
	 * the corresponding value is an empty string.</li>
	 * <li>"network-status" : a space-separated list of all known OR identities.
	 * This is in the same format as the router-status line in directories; see
	 * tor-spec.txt for details.</li>
	 * <li>"addr-mappings/all"</li>
	 * <li>"addr-mappings/config"</li>
	 * <li>"addr-mappings/cache"</li>
	 * <li>"addr-mappings/control" : a space-separated list of address mappings,
	 * each in the form of "from-address=to-address". The 'config' key returns
	 * those address mappings set in the configuration; the 'cache' key returns
	 * the mappings in the client-side DNS cache; the 'control' key returns the
	 * mappings set via the control interface; the 'all' target returns the
	 * mappings set through any mechanism.</li>
	 * <li>"circuit-status" : A series of lines as for a circuit status event.
	 * Each line is of the form: "CircuitID CircStatus Path"</li>
	 * <li>"stream-status" : A series of lines as for a stream status event.
	 * Each is of the form: "StreamID StreamStatus CircID Target"</li>
	 * <li>"orconn-status" : A series of lines as for an OR connection status
	 * event. Each is of the form: "ServerID ORStatus"</li>
	 * </ul>
	 */
	public static Map<String, String> getInfo(Connection conn, Collection<String> keys) throws IOException {
		StringBuffer sb = new StringBuffer("GETINFO");
		for (Iterator<String> it = keys.iterator(); it.hasNext();) {
			sb.append(" ").append(it.next());
		}
		sb.append("\r\n");
		ControlReply r = conn.sendAndWaitForResponse(sb.toString(), null);
		Map<String, String> m = new HashMap<String, String>();
		for (Iterator<ReplyLine> it = r.getLines().iterator(); it.hasNext();) {
			ReplyLine line = it.next();
			int idx = line.getMessage().indexOf('=');
			if (idx < 0)
				break;
			String k = line.getMessage().substring(0, idx);
			String v;
			List<String> data = line.getData();
			if (data != null && !data.isEmpty()) {
				v = line.getRawData();
			} else {
				v = line.getMessage().substring(idx + 1);
			}
			m.put(k, v);
		}
		return m;
	}

	/** Return the value of the information field 'key' */
	public static String getInfo(Connection conn, String key) throws IOException {
		List<String> lst = new ArrayList<String>();
		lst.add(key);
		Map<String, String> m = getInfo(conn, lst);
		return m.get(key);
	}
	
	/**
	 * Tells the Tor server that future SOCKS requests for connections to a set
	 * of original addresses should be replaced with connections to the
	 * specified replacement addresses. Each element of <b>kvLines</b> is a
	 * String of the form "old-address new-address". This function returns the
	 * new address mapping.
	 * 
	 * The client may decline to provide a body for the original address, and
	 * instead send a special null address ("0.0.0.0" for IPv4, "::0" for IPv6,
	 * or "." for hostname), signifying that the server should choose the
	 * original address itself, and return that address in the reply. The server
	 * should ensure that it returns an element of address space that is
	 * unlikely to be in actual use. If there is already an address mapped to
	 * the destination address, the server may reuse that mapping.
	 * 
	 * If the original address is already mapped to a different address, the old
	 * mapping is removed. If the original address and the destination address
	 * are the same, the server removes any mapping in place for the original
	 * address.
	 * 
	 * Mappings set by the controller last until the Tor process exits: they
	 * never expire. If the controller wants the mapping to last only a certain
	 * time, then it must explicitly un-map the address when that time has
	 * elapsed.
	 */
	public static Map<String, String> mapAddresses(Connection conn, Collection<String> kvLines) throws IOException {
		StringBuffer sb = new StringBuffer("MAPADDRESS");
		for (Iterator<String> it = kvLines.iterator(); it.hasNext();) {
			String kv = it.next();
			int i = kv.indexOf(' ');
			sb.append(" ").append(kv.substring(0, i)).append("=").append(quote(kv.substring(i + 1)));
		}
		sb.append("\r\n");
		ControlReply r = conn.sendAndWaitForResponse(sb.toString(), null);
		Map<String, String> result = new HashMap<String, String>();
		for (Iterator<ReplyLine> it = r.getLines().iterator(); it.hasNext();) {
			String kv = (it.next()).getMessage();
			int idx = kv.indexOf('=');
			result.put(kv.substring(0, idx), kv.substring(idx + 1));
		}
		return result;
	}

	public static Map<String, String> mapAddresses(Connection conn, Map<String, String> addresses) throws IOException {
		List<String> kvList = new ArrayList<String>();
		for (Iterator<Map.Entry<String, String>> it = addresses.entrySet().iterator(); it.hasNext();) {
			Map.Entry<String, String> e = it.next();
			kvList.add(e.getKey() + " " + e.getValue());
		}
		return mapAddresses(conn, kvList);
	}

	public static String mapAddress(Connection conn, String fromAddr, String toAddr) throws IOException {
		List<String> lst = new ArrayList<String>();
		lst.add(fromAddr + " " + toAddr + "\n");
		Map<String, String> m = mapAddresses(conn, lst);
		return m.get(fromAddr);
	}



	/**
	 * An extendCircuit request takes one of two forms: either the <b>circID</b>
	 * is zero, in which case it is a request for the server to build a new
	 * circuit according to the specified path, or the <b>circID</b> is nonzero,
	 * in which case it is a request for the server to extend an existing
	 * circuit with that ID according to the specified <b>path</b>.
	 * 
	 * If successful, returns the Circuit ID of the (maybe newly created)
	 * circuit.
	 */
	public static String extendCircuit(Connection conn, String circID, String path) throws IOException {
		ControlReply r = conn.sendAndWaitForResponse("EXTENDCIRCUIT " + circID + " " + path + "\r\n", null);
		return (r.getLine(0)).getMessage();
	}

	/**
	 * Informs the Tor server that the stream specified by <b>streamID</b>
	 * should be associated with the circuit specified by <b>circID</b>.
	 * 
	 * Each stream may be associated with at most one circuit, and multiple
	 * streams may share the same circuit. Streams can only be attached to
	 * completed circuits (that is, circuits that have sent a circuit status
	 * "BUILT" event or are listed as built in a getInfo circuit-status
	 * request).
	 * 
	 * If <b>circID</b> is 0, responsibility for attaching the given stream is
	 * returned to Tor.
	 * 
	 * By default, Tor automatically attaches streams to circuits itself, unless
	 * the configuration variable "__LeaveStreamsUnattached" is set to "1".
	 * Attempting to attach streams via TC when "__LeaveStreamsUnattached" is
	 * false may cause a race between Tor and the controller, as both attempt to
	 * attach streams to circuits.
	 */
	public static void attachStream(Connection conn, String streamID, String circID) throws IOException {
		conn.sendAndWaitForResponse("ATTACHSTREAM " + streamID + " " + circID + "\r\n", null);
	}

	/**
	 * Tells Tor about the server descriptor in <b>desc</b>.
	 * 
	 * The descriptor, when parsed, must contain a number of well-specified
	 * fields, including fields for its nickname and identity.
	 */
	// More documentation here on format of desc?
	// No need for return value? control-spec.txt says reply is merely "250 OK"
	// on success...
	public static String postDescriptor(Connection conn, String desc) throws IOException {
		ControlReply r = conn.sendAndWaitForResponse("+POSTDESCRIPTOR\r\n", desc);
		return (r.getLine(0)).getMessage();
	}

	/**
	 * Tells Tor to change the exit address of the stream identified by
	 * <b>streamID</b> to <b>address</b>. No remapping is performed on the new
	 * provided address.
	 * 
	 * To be sure that the modified address will be used, this event must be
	 * sent after a new stream event is received, and before attaching this
	 * stream to a circuit.
	 */
	public static void redirectStream(Connection conn, String streamID, String address) throws IOException {
		conn.sendAndWaitForResponse("REDIRECTSTREAM " + streamID + " " + address + "\r\n", null);
	}

	/**
	 * Tells Tor to close the stream identified by <b>streamID</b>.
	 * <b>reason</b> should be one of the Tor RELAY_END reasons given in
	 * tor-spec.txt, as a decimal:
	 * <ul>
	 * <li>1 -- REASON_MISC (catch-all for unlisted reasons)</li>
	 * <li>2 -- REASON_RESOLVEFAILED (couldn't look up hostname)</li>
	 * <li>3 -- REASON_CONNECTREFUSED (remote host refused connection)</li>
	 * <li>4 -- REASON_EXITPOLICY (OR refuses to connect to host or port)</li>
	 * <li>5 -- REASON_DESTROY (Circuit is being destroyed)</li>
	 * <li>6 -- REASON_DONE (Anonymized TCP connection was closed)</li>
	 * <li>7 -- REASON_TIMEOUT (Connection timed out, or OR timed out while
	 * connecting)</li>
	 * <li>8 -- (unallocated)</li>
	 * <li>9 -- REASON_HIBERNATING (OR is temporarily hibernating)</li>
	 * <li>10 -- REASON_INTERNAL (Internal error at the OR)</li>
	 * <li>11 -- REASON_RESOURCELIMIT (OR has no resources to fulfill request)</li>
	 * <li>12 -- REASON_CONNRESET (Connection was unexpectedly reset)</li>
	 * <li>13 -- REASON_TORPROTOCOL (Sent when closing connection because of Tor
	 * protocol violations)</li>
	 * </ul>
	 * 
	 * Tor may hold the stream open for a while to flush any data that is
	 * pending.
	 */
	public static void closeStream(Connection conn, String streamID, byte reason) throws IOException {
		conn.sendAndWaitForResponse("CLOSESTREAM " + streamID + " " + reason + "\r\n", null);
	}

	/**
	 * Tells Tor to close the circuit identified by <b>circID</b>. If
	 * <b>ifUnused</b> is true, do not close the circuit unless it is unused.
	 */
	public static void closeCircuit(Connection conn, String circID, boolean ifUnused) throws IOException {
		conn.sendAndWaitForResponse("CLOSECIRCUIT " + circID + (ifUnused ? " IFUNUSED" : "") + "\r\n", null);
	}
	
	/**
	 * Change the value of the configuration option 'key' to 'val'.
	 */
	public static void setConf(Connection conn, String key, String value) throws IOException {
		List<String> lst = new ArrayList<String>();
		lst.add(key + " " + value);
		setConf(conn, lst);
	}

	/** Change the values of the configuration options stored in kvMap. */
	public static void setConf(Connection conn, Map<String, String> kvMap) throws IOException {
		List<String> lst = new ArrayList<String>();
		for (Iterator<Map.Entry<String, String>> it = kvMap.entrySet().iterator(); it.hasNext();) {
			Map.Entry<String, String> ent = it.next();
			lst.add(ent.getKey() + " " + ent.getValue() + "\n");
		}
		setConf(conn, lst);
	}

	/**
	 * Changes the values of the configuration options stored in <b>kvList</b>.
	 * Each list element in <b>kvList</b> is expected to be String of the format
	 * "key value".
	 * 
	 * Tor behaves as though it had just read each of the key-value pairs from
	 * its configuration file. Keywords with no corresponding values have their
	 * configuration values reset to their defaults. setConf is all-or-nothing:
	 * if there is an error in any of the configuration settings, Tor sets none
	 * of them.
	 * 
	 * When a configuration option takes multiple values, or when multiple
	 * configuration keys form a context-sensitive group (see getConf below),
	 * then setting any of the options in a setConf command is taken to reset
	 * all of the others. For example, if two ORBindAddress values are
	 * configured, and a command arrives containing a single ORBindAddress
	 * value, the new command's value replaces the two old values.
	 * 
	 * To remove all settings for a given option entirely (and go back to its
	 * default value), include a String in <b>kvList</b> containing the key and
	 * no value.
	 */
	public static void setConf(Connection conn, Collection<String> kvList) throws IOException {
		if (kvList.size() == 0)
			return;
		StringBuffer b = new StringBuffer("SETCONF");
		for (Iterator<String> it = kvList.iterator(); it.hasNext();) {
			String kv = it.next();
			int i = kv.indexOf(' ');
			if (i == -1)
				b.append(" ").append(kv);
			b.append(" ").append(kv.substring(0, i)).append("=").append(quote(kv.substring(i + 1)));
		}
		b.append("\r\n");
		conn.sendAndWaitForResponse(b.toString(), null);
	}

	/**
	 * Try to reset the values listed in the collection 'keys' to their default
	 * values.
	 **/
	public static void resetConf(Connection conn, Collection<String> keys) throws IOException {
		if (keys.size() == 0)
			return;
		StringBuffer b = new StringBuffer("RESETCONF");
		for (Iterator<String> it = keys.iterator(); it.hasNext();) {
			String key = it.next();
			b.append(" ").append(key);
		}
		b.append("\r\n");
		conn.sendAndWaitForResponse(b.toString(), null);
	}

	/** Return the value of the configuration option 'key' */
	public static List<ConfigEntry> getConf(Connection conn, String key) throws IOException {
		List<String> lst = new ArrayList<String>();
		lst.add(key);
		return getConf(conn, lst);
	}

	/**
	 * Requests the values of the configuration variables listed in <b>keys</b>.
	 * Results are returned as a list of ConfigEntry objects.
	 * 
	 * If an option appears multiple times in the configuration, all of its
	 * key-value pairs are returned in order.
	 * 
	 * Some options are context-sensitive, and depend on other options with
	 * different keywords. These cannot be fetched directly. Currently there is
	 * only one such option: clients should use the "HiddenServiceOptions"
	 * virtual keyword to get all HiddenServiceDir, HiddenServicePort,
	 * HiddenServiceNodes, and HiddenServiceExcludeNodes option settings.
	 */
	public static List<ConfigEntry> getConf(Connection conn, Collection<String> keys) throws IOException {
		StringBuffer sb = new StringBuffer("GETCONF");
		for (Iterator<String> it = keys.iterator(); it.hasNext();) {
			String key = it.next();
			sb.append(" ").append(key);
		}
		sb.append("\r\n");
		ControlReply r = conn.sendAndWaitForResponse(sb.toString(), null);
		List<ConfigEntry> result = new ArrayList<ConfigEntry>();
		for (Iterator<ReplyLine> it = r.getLines().iterator(); it.hasNext();) {
			String kv = (it.next()).getMessage();
			int idx = kv.indexOf('=');
			if (idx >= 0)
				result.add(new ConfigEntry(kv.substring(0, idx), kv.substring(idx + 1)));
			else
				result.add(new ConfigEntry(kv));
		}
		return result;
	}



	/**
	 * Authenticates the controller to the Tor server.
	 * 
	 * By default, the current Tor implementation trusts all local users, and
	 * the controller can authenticate itself by calling authenticate(new
	 * byte[0]).
	 * 
	 * If the 'CookieAuthentication' option is true, Tor writes a "magic cookie"
	 * file named "control_auth_cookie" into its data directory. To
	 * authenticate, the controller must send the contents of this file in
	 * <b>auth</b>.
	 * 
	 * If the 'HashedControlPassword' option is set, <b>auth</b> must contain
	 * the salted hash of a secret password. The salted hash is computed
	 * according to the S2K algorithm in RFC 2440 (OpenPGP), and prefixed with
	 * the s2k specifier. This is then encoded in hexadecimal, prefixed by the
	 * indicator sequence "16:".
	 * 
	 * You can generate the salt of a password by calling 'tor --hash-password
	 * <password>' or by using the provided PasswordDigest class. To
	 * authenticate under this scheme, the controller sends Tor the original
	 * secret that was used to generate the password.
	 */
	public static void authenticate(Connection conn, byte[] auth) throws IOException {		
		String cmd = "AUTHENTICATE " + net.freehaven.tor.control.Bytes.hex(auth) + "\r\n";
		conn.sendAndWaitForResponse(cmd, null);
	}
	
	public static void authenticate(Connection conn, String pwd) throws IOException {
		String cmd = "AUTHENTICATE \"" + pwd + "\"\r\n";
		conn.sendAndWaitForResponse(cmd, null);
	}

	/**
	 * Instructs the server to write out its configuration options into its
	 * torrc.
	 */
	public static void saveConf(Connection conn) throws IOException {
		conn.sendAndWaitForResponse("SAVECONF\r\n", null);
	}
	
	// ----------------- helpers ----------------
	
	private static final String quote(String s) {
		StringBuffer sb = new StringBuffer("\"");
		for (int i = 0; i < s.length(); ++i) {
			char c = s.charAt(i);
			switch (c) {
			case '\r':
			case '\n':
			case '\\':
			case '\"':
				sb.append('\\');
			}
			sb.append(c);
		}
		sb.append('\"');
		return sb.toString();
	}
}
