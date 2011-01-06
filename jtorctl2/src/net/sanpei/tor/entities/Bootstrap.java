//Copyright 2010 333pei@gmail.com

package net.sanpei.tor.entities;

import java.io.IOException;
import java.util.Map;

import android.text.TextUtils;
import net.sanpei.common.Helpers;
import net.sanpei.common.Logs;
import net.sanpei.tor.Commands;
import net.sanpei.tor.Connection;
import net.sanpei.tor.Globals;
import net.sanpei.tor.Globals.Severity;


public class Bootstrap {
	/**
	 * Currently enumerated bootstrapping states defined by Tor's control
	 * protocol (Tor >= 0.2.1.0-alpha-dev.
	 */
	public static enum Status {
		UnrecognizedStatus, ConnectingToDirMirror, HandshakingWithDirMirror, CreatingOneHopCircuit, RequestingNetworkStatus, LoadingNetworkStatus, LoadingAuthorityCertificates, RequestingDescriptors, LoadingDescriptors, ConnectingToEntryGuard, HandshakingWithEntryGuard, EstablishingCircuit, BootstrappingDone;

		/** Converts a string TAG value to a BootstrapStatus enum value. */
		public static Status parse(final String str) {
			if(TextUtils.isEmpty(str)) {
				Logs.d(Bootstrap.class.getName(), "parsing meet empty str...");
				return UnrecognizedStatus;
				}
			if (str.equalsIgnoreCase("CONN_DIR"))
				return ConnectingToDirMirror;
			if (str.equalsIgnoreCase("HANDSHAKE_DIR"))
				return HandshakingWithDirMirror;
			if (str.equalsIgnoreCase("ONEHOP_CREATE"))
				return CreatingOneHopCircuit;
			if (str.equalsIgnoreCase("REQUESTING_STATUS"))
				return RequestingNetworkStatus;
			if (str.equalsIgnoreCase("LOADING_STATUS"))
				return LoadingNetworkStatus;
			if (str.equalsIgnoreCase("LOADING_KEYS"))
				return LoadingAuthorityCertificates;
			if (str.equalsIgnoreCase("REQUESTING_DESCRIPTORS"))
				return RequestingDescriptors;
			if (str.equalsIgnoreCase("LOADING_DESCRIPTORS"))
				return LoadingDescriptors;
			if (str.equalsIgnoreCase("CONN_OR"))
				return ConnectingToEntryGuard;
			if (str.equalsIgnoreCase("HANDSHAKE_OR"))
				return HandshakingWithEntryGuard;
			if (str.equalsIgnoreCase("CIRCUIT_CREATE"))
				return EstablishingCircuit;
			if (str.equalsIgnoreCase("DONE"))
				return BootstrappingDone;
			return UnrecognizedStatus;
		}
	}



	/**
	 * Actions the Tor software might recommend controllers take in response to
	 * a bootstrap status problem event.
	 */
	public static enum Recommendation {
		UnrecognizedRecommendation, RecommendIgnore, RecommendWarn;
	
		/**
		 * Returns the action that the Tor software recommended be taken in
		 * response to this bootstrap status.
		 */
		public static Recommendation parse(final String str) {
			if (TextUtils.isEmpty(str)) {
				Logs.d(Recommendation.class.getName(), "parsing meet empty string");
				return UnrecognizedRecommendation;
			}
			if (str.equalsIgnoreCase("WARN"))
				return RecommendWarn;
			if (str.equalsIgnoreCase("IGNORE"))
				return RecommendIgnore;
			return UnrecognizedRecommendation;
		}
	}



	private static final String TAG = Bootstrap.class.getName();;



	public final int severity;
	public final OrConnection.Reason reason;
	public final Status status;
	public final Bootstrap.Recommendation action;
	public final int percentComplete;
	public String description;
	public String warning;

	public Bootstrap() {
		severity = Globals.Severity.Unrecognized;
		reason = OrConnection.Reason.UnrecognizedReason;
		status = Status.UnrecognizedStatus;
		action = Bootstrap.Recommendation.UnrecognizedRecommendation;
		percentComplete = -1;
	}

	public static Bootstrap get(Connection conn) {
		if(conn == null) return null;
		String str;
		try {
			str = Commands.getInfo(conn, "status/bootstrap-phase");
		} catch (IOException e) {
			Logs.e(TAG, "get 'status/bootstrap-phase' info FAILED: " + e.getMessage());
			return null;
		}
		if (!TextUtils.isEmpty(str)) {
			String[] ary = str.split(" ");
			int severity = Severity.parse(ary[0]);
			Map<String, String> args = Globals.parseKeyValues(str);
			return new Bootstrap(severity, //
					Bootstrap.Status.parse(args.get("TAG")), //
					Helpers.tryParse(args.get("PROGRESS"), 0), //
					args.get("SUMMARY"), args.get("WARNING"), //
					OrConnection.Reason.parse(args.get("REASON")), //
					Bootstrap.Recommendation.parse(args.get("RECOMMENDATION")));
		}

		return null;
	}

	/** Constructor. */
	public Bootstrap(int severity, Status status, int percentComplete, final String description, final String warning, OrConnection.Reason reason,
			Bootstrap.Recommendation action) {
		this.severity = severity;
		this.status = status;
		this.percentComplete = Math.max(Math.min(percentComplete, 100), 0);
		this.description = description;
		this.warning = warning;
		this.reason = reason;
		this.action = action;
	}

	/** Returns true if this object represents a valid bootstrap status phase. */
	public boolean isValid() {
		return (severity != Globals.Severity.Unrecognized & status != Status.UnrecognizedStatus & percentComplete >= 0);
	}

}
