package edu.ucla.discoverfriend;

import java.io.Serializable;

import com.google.common.hash.BloomFilter;

public class CustomNetworkPacket implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private BloomFilter<String> bf;	
	private BloomFilter<String> bfc;
	private String cf = "certificate";
	
	public CustomNetworkPacket(BloomFilter<String> bf, BloomFilter<String> bfc, String cf) {
		this.bf = bf;
		this.bfc = bfc;
		this.cf = cf;
	}

	public BloomFilter<String> getBf() {
		return bf;
	}

	public void setBf(BloomFilter<String> bf) {
		this.bf = bf;
	}

	public BloomFilter<String> getBfc() {
		return bfc;
	}

	public void setBfc(BloomFilter<String> bfc) {
		this.bfc = bfc;
	}
	
	public String getCf() {
		return cf;
	}

	public void setCf(String cf) {
		this.cf = cf;
	}
	
}
