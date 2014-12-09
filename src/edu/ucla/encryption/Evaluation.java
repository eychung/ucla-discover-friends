package edu.ucla.encryption;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.Arrays;

import edu.ucla.common.Utils;

public class Evaluation {
	public static void calculateEncryptedMessageSize() throws Exception {
		String id = Utils.getRandomName(10);
		byte[] key = ByteBuffer.allocate(4).putInt(id.hashCode()).array();
		char[] message = new char[160];
		System.out.println(message.length);
		byte[] plaintext = Utils.toBytes(message); //Charset.forName("UTF-8").encode(CharBuffer.wrap(message)).array();
		System.out.println(plaintext.length);
		byte[] encrypted = AES.encrypt(key, plaintext);
		System.out.println(encrypted.length);
	}
	
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
		//KeyRepository.createTestKeyStore(10);
		//KeyRepository.createTestKeyStore(100);
		//KeyRepository.createTestKeyStore(1000);
		
		//KeyRepository.createUserKeyStore("");
		//KeyRepository.exportCertificate();
		
		 calculateEncryptedMessageSize();
	}

}
