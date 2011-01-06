//Copyright 2010 333pei@gmail.com

package net.sanpei.common;

import java.util.ArrayList;
import java.util.Iterator;

public class Pair<T1, T2> {
	public T1 first;
	public T2 second;
	public Pair(T1 first, T2 second){
		this.first = first;
		this.second = second;
	}
	
	public static <T1,T2> ArrayList<T2> toSecondList(Iterator<Pair<T1,T2>> it) {		
		ArrayList<T2> result = new ArrayList<T2>();
		for(;it.hasNext();) {
			Pair<T1, T2> pair = it.next();
			result.add(pair.second);
		}
		return result;
	} 
}
