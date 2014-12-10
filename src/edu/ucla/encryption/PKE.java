package edu.ucla.encryption;

import java.security.Key;

import javax.crypto.Cipher;

public class PKE {
	public static byte[] encrypt(Key key, byte[] plaintext)
			throws Exception {
		Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
		cipher.init(Cipher.ENCRYPT_MODE, key);
		byte[] encrypted = cipher.doFinal(plaintext);
		return encrypted;
	}

	public static byte[] decrypt(Key key, byte[] ciphertext)
			throws Exception {
		Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
		cipher.init(Cipher.DECRYPT_MODE, key);
		byte[] decrypted = cipher.doFinal(ciphertext);

		return decrypted;
	}
}
