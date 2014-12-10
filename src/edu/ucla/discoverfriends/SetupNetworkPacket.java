package edu.ucla.discoverfriends;

import java.io.Serializable;

import com.google.common.hash.BloomFilter;

public class SetupNetworkPacket implements Serializable {
	private static final long serialVersionUID = 1L;
	private BloomFilter<String> bf;	
	private BloomFilter<String> bfp;
	private byte[] ecf;

	public SetupNetworkPacket(BloomFilter<String> bf, BloomFilter<String> bfp, byte[] ecf) {
		this.bf = bf;
		this.bfp = bfp;
		this.ecf = ecf;
	}

	public BloomFilter<String> getBf() {
		return bf;
	}

	public void setBf(BloomFilter<String> bf) {
		this.bf = bf;
	}

	public BloomFilter<String> getBfp() {
		return bfp;
	}

	public void setBfp(BloomFilter<String> bfp) {
		this.bfp = bfp;
	}

	public byte[] getEcf() {
		return ecf;
	}

	public void setEcf(byte[] ecf) {
		this.ecf = ecf;	
	}
}
