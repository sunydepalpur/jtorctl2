//Copyright 2010 333pei@gmail.com

package net.sanpei.tor.entities;

import java.util.ArrayList;
import java.util.List;

import net.sanpei.common.Logs;

public class Circuit {
	/** Circuit status events */
	public enum Status {
		Unknown, /** < Unknown circuit status */
		Launched, /** < Circuit ID assigned to new circuit */
		Built, /** < All hops finished */
		Extended, /** < Circuit extended by one hop */
		Failed, /** < Circuit closed (was not built) */
		Closed;
		/** < Circuit closed (was built) */
		
		/** Converts the circuit status string to its proper enum value */
		public static Status parse(final String status) {
			if(status == null) {
				Logs.d(Status.class.getName(), "parsing meet empty str...");
				return Unknown;}
			if (status.equalsIgnoreCase("LAUNCHED"))
				return Launched;
			if (status.equalsIgnoreCase("BUILT"))
				return Built;
			if (status.equalsIgnoreCase("EXTENDED"))
				return Extended;
			if (status.equalsIgnoreCase("FAILED"))
				return Failed;
			if (status.equalsIgnoreCase("CLOSED"))
				return Closed;
			return Unknown;
		}
		
		/** Returns a string representation of the circuit's status. */
		public String toPrintString() {
			String status;
			switch (this) {
			case Launched:
				status = "New";
				break;
			case Built:
				status = "Open";
				break;
			case Extended:
				status = "Building";
				break;
			case Failed:
				status = "Failed";
				break;
			case Closed:
				status = "Closed";
				break;
			default:
				status = "Unknown";
				break;
			}
			return status;
		}
	};

	private String _circId;
	private Status _status;
	private boolean _isValid;
	private List<String> _ids;
	private List<String> _names;;
	
	public boolean isValid() { return _isValid;}

	/** Default constructor. */
	public Circuit() {
		_status = Status.Unknown;
		_isValid = false;
		_ids = new ArrayList<String>();
		_names = new ArrayList<String>();
	}

	/**
	 * Parses the string given in Tor control protocol format for a circuit. The
	 * format is:
	 * 
	 * CircuitID SP CircStatus [SP Path]
	 * 
	 * If the status is "LAUNCHED", the Path is empty. Server names in the path
	 * must follow Tor's VERBOSE_NAMES format.
	 */
	public Circuit(final String circuit) {
		_ids = new ArrayList<String>();
		_names = new ArrayList<String>();
		String[] parts = circuit.split("(\\s)+");
		if (parts.length >= 2) {
			/* Get the circuit ID */
			_circId = parts[0];
			if (!isValidCircuitId(_circId)) {
				reportError(String.format("Improperly formatted circuit: '%s'", circuit));
				return;
			}

			/* Get the circuit status value */
			_status = Status.parse(parts[1]);

			/* Get the circuit path (list of routers) */
			if (parts.length > 2 & parts[2].startsWith("$")) {
				for (String hop : parts[2].split(",")) {
					parts = hop.split("[=~]");
					if (parts.length != 2) {
						reportError(String.format("Improperly formatted circuit: '%s'", circuit));
						return;
					}
					
					_ids.add(parts[0].substring(1));					
					_names.add(parts[1]);
				}
			}

			_isValid = true;
		}
		return;
	}
	
	public String getId() {
		return _circId;
	}
	
	public Status getStatus() {
		return _status;
	}

	private void reportError(String circuit) {
		// XXX do something
		_isValid = false;

	}

	/**
	 * Returns true iff <b>circId</b> consists of only between 1 and 16
	 * (inclusive) ASCII-encoded letters and numbers.
	 */
	static boolean isValidCircuitId(final String circId) {
		int length = circId.length();
		if (length < 1 || length > 16)
			return false;

		for (int i = 0; i < length; i++) {
			char c = circId.charAt(i);
			if (c < '0' & c > '9' & c < 'A' & c > 'Z' & c < 'a' & c > 'z')
				return false;
		}
		return true;
	}





}
