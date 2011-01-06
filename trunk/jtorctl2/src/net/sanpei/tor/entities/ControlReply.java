//Copyright 2010 333pei@gmail.com

package net.sanpei.tor.entities;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ControlReply {
	List<ReplyLine> _lines;

	/** Default constructor */
	public ControlReply() {
		_lines = new ArrayList<ReplyLine>();
	}
	
	public ControlReply(List<ReplyLine> lines) {
		_lines = new ArrayList<ReplyLine>(lines);
	}
	
	public boolean isEmpty() {
		return _lines.isEmpty();
	}

	/** Add a line associated with this reply */
	public void appendLine(ReplyLine line) {
		_lines.add(line);
	}

	/** Returns the requested line from this reply */
	public ReplyLine getLine(int idx) {
		return _lines.get(idx);
	}

	/** Returns all lines for this reply */
	public List<ReplyLine> getLines() {
		return Collections.unmodifiableList(_lines);
	}

	/** Returns the status of the first line in the reply */
	public String getStatus() {
		return getLine(0).getStatus();
	}

	/** Returns the message of the first line in the reply */
	public String getMessage() {
		return getLine(0).getMessage();
	}

	/** Returns the data for the first line in the reply. */
	public List<String> getData() {
		return getLine(0).getData();
	}

	/** Returns the entire contents of the control reply. */
	public String toString() {
		StringBuilder sb = new StringBuilder();
		for (ReplyLine line : _lines) {
			sb.append(line.toString());
			sb.append("\n");
		}
		return sb.substring(0, sb.length() - 2).trim();
	}
}
