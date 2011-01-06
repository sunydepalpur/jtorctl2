//Copyright 2010 333pei@gmail.com

package net.sanpei.tor.entities;

import net.sanpei.common.Logs;
import android.text.TextUtils;

public class OrConnection {
	public enum Status {
		Unknown, //
		New,	//	
		Launched, //
		Connected, //
		Failed, //
		Closed; //
		
		@Override
		public String toString() {
			switch(this) {
			case New:
				return "NEW";			
			case Launched:
				return "LAUNCHED";
			case Connected:
				return "CONNECTED";
			case Failed:
				return "FAILED";
			case Closed:
				return "CLOSED";
			default:
				return "UNKNOWN";
			}			
		}
		
		public static Status parse(String code) {
			if(code.equals("NEW")) {
				return New;
			} else if(code.equals("LAUNCHED")) {
				return Launched;
			} else if(code.equals("CONNECTED")) {
				return Connected;
			} else if(code.equals("FAILED")) {
				return Failed;
			} else if(code.equals("CLOSED")) {
				return Closed;
			}
			return Unknown;
		}
	}
	
	public static enum Reason {
		UnrecognizedReason, MiscellaneousReason, IdentityMismatch, ConnectionDone, ConnectionRefused, ConnectionReset, ConnectionTimeout, ConnectionIoError, NoRouteToHost, ResourceLimitReached;
	
		public static Reason parse(String str) {
			if (TextUtils.isEmpty(str)) {
				Logs.d(Reason.class.getName(), "parsing meet empty string");
				return UnrecognizedReason;
			}
			if (str.equalsIgnoreCase("MISC"))
				return MiscellaneousReason;
			if (str.equals("IDENTITY"))
				return IdentityMismatch;
			if (str.equalsIgnoreCase("RESOURCELIMIT"))
				return ResourceLimitReached;
			if (str.equals("DONE"))
				return ConnectionDone;
			if (str.equals("CONNECTREFUSED"))
				return ConnectionRefused;
			if (str.equalsIgnoreCase("CONNECTRESET"))
				return ConnectionRefused;
			if (str.equalsIgnoreCase("TIMEOUT"))
				return ConnectionTimeout;
			if (str.equalsIgnoreCase("NOROUTE"))
				return NoRouteToHost;
			if (str.equalsIgnoreCase("IOERROR"))
				return ConnectionIoError;
			return UnrecognizedReason;
		}
	}


	public final String target;
	public final String name;
	public final Status status;
	public final  Reason reason;

	
	public OrConnection(String target1, String name1, Status status1, Reason reason1) {
		target = target1;
		name = name1;
		status = status1;
		reason = reason1;
	}


	//(ServerID / Target) SP ORStatus [ SP "REASON="Reason ] [ SP "NCIRCS=" NumCircuits ]
	public static OrConnection parse(final String txt) {
		String id = null;
		String name = null;
		Status status = Status.Unknown;
		Reason reason = Reason.UnrecognizedReason;
		
		String[] parts = txt.trim().split(" ");
		if(parts[0].startsWith("$")) {
			String[] pair = parts[0].split("[=~]");
			if(pair.length > 1) {
				id = pair[0];
				name = pair[1];
			}
			else {
				id = parts[0];
				name = "";
			}
		}
		
		status = Status.parse(parts[1]);
		if(parts.length > 2) {
			reason = Reason.parse(parts[2]);
		}
		return new OrConnection(id, name, status, reason);
	}
}
