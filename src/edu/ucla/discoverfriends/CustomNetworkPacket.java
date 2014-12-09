package edu.ucla.discoverfriends;

import java.io.Serializable;
import java.security.cert.X509Certificate;

import com.google.common.hash.BloomFilter;

public class CustomNetworkPacket implements Serializable {
	private static final long serialVersionUID = 1L;
	private BloomFilter<String> bf;	
	private BloomFilter<String> bfc;
	private X509Certificate crt;
	
	public CustomNetworkPacket(BloomFilter<String> bf, BloomFilter<String> bfc, X509Certificate crt) {
		this.bf = bf;
		this.bfc = bfc;
		this.crt = crt;
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
	
	public X509Certificate getCrt() {
		return crt;
	}

	public void setCrt(X509Certificate crt) {
		this.crt = crt;
	}
	
}
