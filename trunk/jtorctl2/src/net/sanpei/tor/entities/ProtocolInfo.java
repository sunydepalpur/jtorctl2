//Copyright 2010 333pei@gmail.com

package net.sanpei.tor.entities;

import android.text.TextUtils;

public class ProtocolInfo {
	/** Default constructor. */
	ProtocolInfo() {
	}

	/** Returns true if this ProtocolInfo object contains no data. */
	boolean isEmpty() {
		return (TextUtils.isEmpty(_torVersion) && _authMethods.length == 0 && TextUtils.isEmpty(_cookieAuthFile));
	}

	/**
	 * Sets the authentication methods Tor currently accepts. <b>methods</b>
	 * should be a comma-delimited list of authentication methods.
	 */
	void setAuthMethods(final String authMethods) {
		_authMethods = authMethods.split(",");
	}

	/** Returns the authentication methods Tor currently accepts. */
	public String[] authMethods() {
		String[] ary = new String[_authMethods.length];
		System.arraycopy(_authMethods, 0, ary, 0, _authMethods.length);
		return ary;
	}

	/** Sets the file to which Tor has written its authentication cookie. */
	void setCookieAuthFile(final String cookieAuthFile) {
		_cookieAuthFile = cookieAuthFile;
	}

	/** Returns the file to which Tor has written its authentication cookie. */
	String cookieAuthFile() {
		return _cookieAuthFile;
	}

	/** Sets the version of Tor to which the controller is connected. */
	void setTorVersion(final String torVersion) {
		_torVersion = torVersion;
	}

	/** Returns the version of Tor to which the controller is connected. */
	String torVersionString() {
		return _torVersion;
	}

	private String _torVersion;
	/** < The Tor version in the PROTOCOLINFO reply. */
	private String _cookieAuthFile;
	/** < Tor's authentication cookie file. */
	private String[] _authMethods;
	/** < Tor's ccepted authentication methods. */
}
