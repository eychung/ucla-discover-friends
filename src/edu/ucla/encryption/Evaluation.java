package edu.ucla.encryption;

public class Evaluation {
	
	/**
	 * Own server certificate in keystore.jks
	 * Trusted (client) certificates in cacerts.jks
	 * keytool utility stores keys and certificate in a keystore
	 * 
	 * 1) Create keystore
	 * 2) Export certificate from the keystore
	 * 3) Sign certificate
	 * 4) Import certificate into a truststore
	 */
	public static void main(String[] args) throws Exception {
		KeyRepository.createUserKeyStore("");
		//KeyRepository.createTestKeyStore(10);
		//KeyRepository.createTestKeyStore(100);
		//KeyRepository.createTestKeyStore(1000);
	}

}
