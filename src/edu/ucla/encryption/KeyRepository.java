package edu.ucla.encryption;

import java.io.FileOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.X509Certificate;

import edu.ucla.common.Constants;

public class KeyRepository {
	public static void createUserKeyStore(String path) throws GeneralSecurityException, IOException {
		KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
		keyPairGenerator.initialize(1024);
		KeyPair keyPair = keyPairGenerator.genKeyPair();
		
		// Create certificate
		String dn = "cn=Eric Chung, o=UCLA, c=US";
		int days = 1;
		String algorithm = "MD5WithRSA";
		X509Certificate crt = Certificate.generateCertificate(dn, keyPair, days, algorithm);
		
		// Store away the keystore
		KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
		char[] password = "password".toCharArray();
		ks.load(null, password);
		ks.setCertificateEntry(Constants.USER_CERTIFICATE_ALIAS, crt);
		FileOutputStream fos = new FileOutputStream(path + "user.keystore");
		ks.store(fos, password);
		fos.close();
	}
	
	public static void createTestKeyStore(int num) throws GeneralSecurityException, IOException {
		KeyPair keyPair;
		KeyPairGenerator keyPairGenerator;
		String alias, dn;
		X509Certificate crt;
		
		int days = 1;
		String algorithm = "MD5WithRSA";
		
		KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
		char[] password = "password".toCharArray();
		ks.load(null, password);
		for (int i = 0; i < num; i++) {
			keyPairGenerator = KeyPairGenerator.getInstance("RSA");
			keyPairGenerator.initialize(1024);
			keyPair = keyPairGenerator.genKeyPair();
			alias = Common.getRandomName(10);
			dn = Common.customDName(alias);
			crt = Certificate.generateCertificate(dn, keyPair, days, algorithm);
			ks.setCertificateEntry(alias, crt);
		}
		FileOutputStream fos = new FileOutputStream(num + "users.keystore");
		ks.store(fos, password);
		fos.close();
	}
}
