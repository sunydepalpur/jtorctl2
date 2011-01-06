//Copyright 2010 333pei@gmail.com

package net.sanpei.tor.entities;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import android.text.TextUtils;

public class ReplyLine {
	private String _status;
	private String _message;
	private List<String> _data;	

	/** Default consructor */
	public ReplyLine() {
		_data = new ArrayList<String>();
	}

	/** finalructor */
	public ReplyLine(final String status, final String msg) {
		this();
		_status = status;
		setMessage(msg);
	}

	/** Constructor */
	public ReplyLine(final String status, final String msg, final String data) {
		this(status, msg);
		if(TextUtils.isEmpty(data))
			return;
		appendData(data);		
	}

	/**
	 * Set the status code for this reply line. See Tor Control Protocol V1
	 * specification for a description of status codes.
	 */
	public void setStatus(final String status) {
		_status = status;
	}

	/** Returns the status code for this reply line. */
	public String getStatus() {
		return _status;
	}

	/** Sets the ReplyText message this reply line to <b>msg</b>. */
	public void setMessage(final String msg) {
		_message = unescape(msg);
	}

	/** Returns the ReplyText portion of this reply line. */
	public String getMessage() {
		return _message;
	}

	/** Appends <b>data</b> to this reply line. */
	public void appendData(final String data) {
		_data.add(data);
	}
	
	public String getRawData() {
		return TextUtils.join("\n", _data);
	}

	/** Returns a List<String> of all data lines for this reply line */
	public List<String> getData() {
		return Collections.unmodifiableList(_data);
	}

	/**
	 * Unescapes special characters in <b>str</b> and returns the unescaped
	 * result.
	 */
	public static String unescape(final String escaped) {
		String str = escaped;
		/* If the line starts with a "." and was escaped, then unescape it */
		if (str.startsWith("..")) {
			str = str.substring(1);
		}

		/* Trim off trailing whitespace (including \r\n) */
		return str.trim();
	}
	


	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(_status + " " + _message);
		if (!(_data == null) && !_data.isEmpty()) {
			sb.append("\n");	
			sb.append(getRawData());
		}		
		return sb.toString();
	}
}
