//Copyright 2010 333pei@gmail.com

package net.sanpei.common;

import java.lang.reflect.Field;

public class Utils {

	public static Object getField(String className, Object obj, String memberName) throws SecurityException, IllegalArgumentException, NoSuchFieldException,
			IllegalAccessException, ClassNotFoundException {
		Class<?> c = Class.forName(className);
		return getField(c, obj, memberName);
	}

	public static Object getField(Class<?> c, Object obj, String memberName) throws SecurityException, NoSuchFieldException, IllegalArgumentException,
			IllegalAccessException {
		Field f = c.getDeclaredField(memberName);
		boolean tmp = f.isAccessible();
		f.setAccessible(true);
		Object result = f.get(obj);
		f.setAccessible(tmp);
		return result;
	}

	public static String bytesToHexString(byte[] bytes) {
		return bytesToHexString(bytes, 0, bytes.length);
	}

	public static String bytesToHexString(byte[] bytes, int start, int offset) {
		String stmp = "";
		StringBuilder sb = new StringBuilder();
		for (int n = start; n < offset; n++) {
			stmp = (Integer.toHexString(bytes[n] & 0XFF));
			if (stmp.length() == 1) {
				sb.append("0");
			}
			sb.append(stmp);
		}
		return sb.toString().toUpperCase();
	}

	public static <T> void copyArray(T[] src, T[] dst, int dstIndex, boolean bzero) {
		int len = -1;
		if (src.length > dst.length - dstIndex) {
			len = dst.length - dstIndex;
		} else {
			len = src.length;
			if (bzero) {
				for (int i = src.length + dstIndex; i < dst.length; i++) {
					dst[i] = null;
				}
			}
		}
		for (int i = 0; i < len; i++) {
			dst[i + dstIndex] = src[i];
		}
	}

	public static <T> void copyArray(T[] src, T[] dst, boolean bzero) {
		copyArray(src, dst, 0, bzero);
	}

}
