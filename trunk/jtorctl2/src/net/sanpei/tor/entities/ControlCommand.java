//Copyright 2010 333pei@gmail.com

package net.sanpei.tor.entities;

import java.util.ArrayList;
import java.util.List;

import android.text.TextUtils;

public class ControlCommand {
	private String _keyword;
	private ArrayList<String> _data;
	/** Default constructor. */
	private List<String> _arguments;

	public ControlCommand()
	{
		_arguments = new ArrayList<String>();
		_data = new ArrayList<String>();
	}

	/** Creates a command using the specified keyword. */
	public ControlCommand(final String keyword) {
		this();
		_keyword = keyword;
	}

	/** Creates a control command using the specified keyword and argument. */
	public ControlCommand(final String keyword, final String arg) {
		this();
		_keyword = keyword;
		addArgument(arg);
	}

	/**
	 * Creates a control command using the specified keyword and list of
	 * arguments.
	 */
	public ControlCommand(final String keyword, final List<String> args) {
		_data = new ArrayList<String>();
		_keyword = keyword;
		_arguments = args;
	}

	/** Sets the keyword for this command. */
	void setKeyword(final String keyword) {
		_keyword = keyword;
	}

	/** Adds an argument to this command's argument list. */
	void addArgument(final String arg) {
		_arguments.add(arg);
	}

	/** Adds all arguments in <b>args</b> to this control command. */
	void addArguments(final List<String> args) {
		for (String arg : args) {
			addArgument(arg);
		}
	}

	/** Adds data to the end of this command. */
	void appendData(final String data) {
		_data.add(data);
	}

	/** Escapes any special characters in this command. */
	String escape(final String unescaped) {
		String str = unescaped;
		if (str.startsWith(".")) {
			str = "." + str;
		}
		if (str.endsWith("\r")) {
			str += "\n";
		} else {
			str += "\r\n";
		}
		return str;
	}

	/**
	 * Formats a command according to Tor's Control Protocol V1. The proper
	 * format of a command is as follows:
	 * 
	 * Command = Keyword Arguments CRLF / "+" Keyword Arguments CRLF Data
	 * Keyword = 1*ALPHA Arguments = *(SP / VCHAR)
	 */
	public String toString() {
		int i;
		String str = "";

		/* If this command contains data, then a "+" is prepended to the keyword */
		if (_data.size() > 0) {
			str = "+";
		}
		str += _keyword + " ";

		/* Append all specified arguments separated by a space */
		str += TextUtils.join(" ", _arguments);

		/* Append whatever data lines have been specified */
		if (_data.size() > 0) {
			str += "\r\n";
			for (i = 0; i < _data.size(); i++) {
				str += escape(_data.get(i));
			}
			str += ".";
		}
		str += "\r\n";
		return str;
	}
}
