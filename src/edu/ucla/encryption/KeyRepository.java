package edu.ucla.encryption;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import edu.ucla.common.Constants;
import edu.ucla.common.Utils;

public class KeyRepository {
	public static void createUserKeyStore(String path, String password) throws GeneralSecurityException, IOException {
		KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
		keystore.load(null, password.toCharArray());
		FileOutputStream fos = new FileOutputStream(path + Constants.KEYSTORE_NAME);
		keystore.store(fos, password.toCharArray());
		fos.close();
	}

	public static void createTestKeyStore(int num) throws GeneralSecurityException, IOException {
		KeyPair keyPair;
		KeyPairGenerator keyPairGenerator;
		String alias, dn;
		X509Certificate crt;

		int days = 1;
		String algorithm = "MD5WithRSA";

		KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
		char[] password = "password".toCharArray();
		keystore.load(null, password);
		for (int i = 0; i < num; i++) {
			keyPairGenerator = KeyPairGenerator.getInstance("RSA");
			keyPairGenerator.initialize(1024);
			keyPair = keyPairGenerator.genKeyPair();
			alias = Utils.getRandomAlias(10);
			dn = Utils.customDName(alias);
			crt = Certificate.generateCertificate(dn, keyPair, days, algorithm);
			keystore.setCertificateEntry(alias, crt);
		}
		FileOutputStream fos = new FileOutputStream(num + "users.keystore");
		keystore.store(fos, password);
		fos.close();
	}
	
	public static KeyStore getKeyStore(String path) throws IOException, KeyStoreException {
		FileInputStream fis = new FileInputStream(path + Constants.KEYSTORE_NAME);
	    KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
	    fis.close();
	    return keystore;
	}

	public static void exportCertificate(String alias, String path, String password) 
			throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException {
		byte[] buf = getCertificate(alias, path, password).getEncoded();
		FileOutputStream fos = new FileOutputStream("cert.cer");
		fos.write(buf);
		fos.close();
	}
	
	/**
	 * Retrieves certificate from keystore.
	 */
	public static X509Certificate getCertificate(String alias, String path, String password) 
			throws CertificateException, IOException, NoSuchAlgorithmException, KeyStoreException {
		FileInputStream fis = new FileInputStream(path + Constants.KEYSTORE_NAME);
	    KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
	    keystore.load(fis, password.toCharArray());
	    fis.close();
	    return (X509Certificate) keystore.getCertificate(alias);
	}
	
	/**
	 * Stores certificate into internal storage.
	 */
	public static void storeCertificate(String alias, X509Certificate crt, String path, String password) 
			throws CertificateException, IOException, KeyStoreException, NoSuchAlgorithmException {
		FileInputStream fis = new FileInputStream(path + Constants.KEYSTORE_NAME);
		KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
		keystore.load(fis, password.toCharArray());
		keystore.setCertificateEntry(alias, crt);
		FileOutputStream fos = new FileOutputStream(path + Constants.KEYSTORE_NAME);
		keystore.store(fos, password.toCharArray());
		fis.close();
		fos.close();
	}
}
