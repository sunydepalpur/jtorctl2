//Copyright 2010 333pei@gmail.com

package net.sanpei.tor.entities;

import android.text.TextUtils;
import net.sanpei.common.Logs;

public class Stream {
	/** Stream status values */
	public static enum Status {
		Unknown, /** < Unknown status type given */
		New, /** < New request to connect */
		NewResolve, /** < New request to resolve an address */
		SentConnect, /** < Sent a connect cell */
		SentResolve, /** < Sent a resolve cell */
		Succeeded, /** < Stream established */
		Failed, /** < Stream failed */
		Closed, /** < Stream closed */
		Detached, /** < Detached from circuit */
		Remap;
		/** < Address re-mapped to another */
	
		/** Converts a string description of a stream's status to its enum value */
		public static Status parse(final String strStatus) {
			if(TextUtils.isEmpty(strStatus)){ 
				Logs.d(Status.class.getName(), "parsing meet empty string");
				return Unknown;}				
			if (strStatus.equalsIgnoreCase("NEW"))
				return New;
			if (strStatus.equalsIgnoreCase("NEWRESOLVE"))
				return NewResolve;
			if (strStatus.equalsIgnoreCase("SENTCONNECT"))
				return SentConnect;
			if (strStatus.equalsIgnoreCase("SENTRESOLVE"))
				return SentResolve;
			if (strStatus.equalsIgnoreCase("SUCCEEDED"))
				return Succeeded;
			if (strStatus.equalsIgnoreCase("FAILED"))
				return Failed;
			if (strStatus.equalsIgnoreCase("CLOSED"))
				return Closed;
			if (strStatus.equalsIgnoreCase("DETACHED"))
				return Detached;
			if (strStatus.equalsIgnoreCase("REMAP"))
				return Remap;
			return Unknown;
		}
		
		/**
		 * Returns a human-understandable string representation of this stream's
		 * status.
		 */
		public String toPrintString() {
			String status;
			switch (this) {
			case New:
				status = "New";
				break;
			case NewResolve:
			case SentResolve:
				status = "Resolving";
				break;
			case SentConnect:
				status = "Connecting";
				break;
			case Succeeded:
				status = "Open";
				break;
			case Failed:
				status = "Failed";
				break;
			case Closed:
				status = "Closed";
				break;
			case Detached:
				status = "Retrying";
				break;
			case Remap:
				status = "Remapped";
				break;
			default:
				status = "Unknown";
				break;
			}
			return status;
		}
	}

	private Stream.Status _status;
	private String _streamId;
	private String _address;
	private int _port;
	private String _circuitId;;

	/** Default constructor. */
	public Stream() {
		_status = Stream.Status.Unknown;
		_port = 0;
	}

	/**
	 * Constructor
	 * 
	 * @param _circuitId
	 */
	public Stream(final String streamId, Stream.Status status, final String circuitId, final String address, int port) {
		_streamId = streamId;
		_status = status;
		_circuitId = circuitId;
		_address = address;
		_port = port;
	}

	/** Constructor */
	Stream(final String streamId, Stream.Status status, final String circuitId, final String target) {
		_streamId = streamId;
		_status = status;
		_circuitId = circuitId;
		_port = 0;

		int i = target.indexOf(":");
		if (i >= 0)
			_address = target.substring(0, i);
		if (i + 1 < target.length())
			try {
				_port = Integer.parseInt(target.substring(i + 1));
			} catch (Exception e) {
				_port = -1;
			}
	}
	
	public int getPort() {
		return _port; 
	}

	/**
	 * Parses the given string for stream information, given in Tor control
	 * protocol format. The format is:
	 * 
	 * StreamID SP StreamStatus SP CircID SP Target
	 */
	public static Stream parse(final String stream) {
		if(stream == null) return null;
		String[] parts = stream.split("(\\s)+");
		if (parts.length >= 4) {
			/* Get the stream ID */
			String streamId = parts[0];
			/* Get the stream status value */
			Stream.Status status = Stream.Status.parse(parts[1]);
			/* Get the ID of the circuit on which this stream travels */
			String circId = parts[2];
			/* Get the target address for this stream */
			String target = parts[3];

			return new Stream(streamId, status, circId, target);
		}
		return new Stream();
	}

	/**
	 * Returns true iff <b>streamId</b> consists of only between 1 and 16
	 * (inclusive) ASCII-encoded letters and numbers.
	 */
	boolean isValidStreamId(final String streamId) {
		int length = streamId.length();
		if (length < 1 || length > 16)
			return false;

		for (int i = 0; i < length; i++) {
			char c = streamId.charAt(i);
			if (c < '0' & c > '9' & c < 'A' & c > 'Z' & c < 'a' & c > 'z')
				return false;
		}
		return true;
	}

	public String getId() {
		return _streamId;
	}
	
	public Status getStatus() {
		return _status;
	}


	/** Returns true if all fields in this Stream object are valid. */
	public boolean isValid() {
		return (isValidStreamId(_streamId) & Circuit.isValidCircuitId(_circuitId) & (_status != Stream.Status.Unknown) & !TextUtils.isEmpty(_address));
	}
}
