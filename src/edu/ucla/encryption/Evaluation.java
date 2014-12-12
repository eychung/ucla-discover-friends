package edu.ucla.encryption;

import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;

import edu.ucla.common.Utils;

public class Evaluation {
	public static void calculateEncryptedMessageSize() throws Exception {
		byte[] key = Utils.getRandomAlias(10).getBytes("UTF-8");
		System.out.println(key.length);
		char[] message = new char[160];
		System.out.println(message.length);
		byte[] plaintext = Utils.charToByte(message); //Charset.forName("UTF-8").encode(CharBuffer.wrap(message)).array();
		System.out.println(plaintext.length);
		byte[] encrypted = AES.encrypt(key, plaintext);
		System.out.println(encrypted.length);
	}

	public static void testHash() throws NoSuchAlgorithmException {
		String uid = Utils.getRandomAlias(10);
		System.out.println(uid);
		String hashedUid = Utils.hash(uid);
		System.out.println(hashedUid);
		hashedUid = Utils.hash(uid);
		System.out.println(hashedUid);
		hashedUid = Utils.hash(uid);
		System.out.println(hashedUid);
	}

	public static void testSizeOfEncryptedKey() throws Exception {
		byte[] key = AES.getRandomKey();
		KeyRepository.createUserKeyStore("self", "password");
		X509Certificate crt = KeyRepository.getCertificate("self", "", "password");
		byte[] encryptedKey = PKE.encrypt(crt.getPublicKey(), key);
		System.out.println(encryptedKey.length);
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

		//KeyRepository.exportCertificate("self", "", "password");

		//calculateEncryptedMessageSize();

		//testHash();
		testSizeOfEncryptedKey();
	}

}
