//Copyright 2010 333pei@gmail.com

package net.sanpei.common;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;

public class ProcessHelpers {
	private static final String TAG = ProcessHelpers.class.getName();

	public static Handlers.NoArgumentHandler startStderrThread(final Process process, final Handlers.Handler<String> onError) {
		if (process == null)
			return null;
		final Helpers.Holder<Boolean> holder = new Helpers.Holder<Boolean>(true);
		new Thread(new Runnable() {
			@Override public void run() {
				InputStreamReader isReader = new InputStreamReader(process.getErrorStream());
				LineNumberReader errReader = new LineNumberReader(isReader);
				String errLine = null;
				try {
					while (holder.value && (errLine = errReader.readLine()) != null) {
						Logs.e(TAG, "error output: " + errLine);
						if (onError != null)
							onError.onFired(errLine);
					}
				} catch (IOException e) {
					Logs.e(TAG, "read error output FAILED: " + e.getMessage());
				}
				Helpers.close(TAG, errReader);
				Helpers.close(TAG, isReader);
			}
		}).start();
		return new Handlers.NoArgumentHandler() {
			@Override public void onFired() {
				holder.value = false;
			}
		};
	}

	public static Handlers.NoArgumentHandler startExitWaitingThread(final Process process, final Handlers.Handler<Integer> onExit) {
		if (process == null)
			return null;
		final Thread t = new Thread(new Runnable() {
			@Override public void run() {
				try {
					int value = process.waitFor();
					Logs.d(TAG, "exit value: " + value);
					if (onExit != null)
						onExit.onFired(value);
				} catch (InterruptedException e) {
					Logs.e(TAG, "waitFor FAILED: " + e.getMessage());
				}
			}
		});
		t.start();
		return new Handlers.NoArgumentHandler() {
			@Override public void onFired() {
				t.interrupt();
			}
		};
	}

	public static Handlers.NoArgumentHandler startStdoutThread(final Process process, final Handlers.Handler<String> onStdout) {
		if (process == null)
			return null;
		final Helpers.Holder<Boolean> holder = new Helpers.Holder<Boolean>(true);
		new Thread(new Runnable() {
			@Override public void run() {
				InputStream input = process.getInputStream();
				InputStreamReader isReader = new InputStreamReader(input);
				LineNumberReader reader = new LineNumberReader(isReader);
				String line = null;
				try {
					while (holder.value && (line = reader.readLine()) != null) {
						if (onStdout != null) {
							Logs.d(TAG, "stdout: " + line);
							onStdout.onFired(line);
						}
					}
				} catch (IOException e) {
					Logs.e(TAG, "read stdout FAILED: " + e.getMessage());
				}
				Helpers.close(TAG, reader);
				Helpers.close(TAG, isReader);
			}
		}).start();
		return new Handlers.NoArgumentHandler() {
			@Override public void onFired() {
				holder.value = false;
			}
		};
	}
}
