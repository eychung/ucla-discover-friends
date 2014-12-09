package edu.ucla.encryption;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Date;

import sun.security.x509.AlgorithmId;
import sun.security.x509.CertificateAlgorithmId;
import sun.security.x509.CertificateIssuerName;
import sun.security.x509.CertificateSerialNumber;
import sun.security.x509.CertificateSubjectName;
import sun.security.x509.CertificateValidity;
import sun.security.x509.CertificateVersion;
import sun.security.x509.CertificateX509Key;
import sun.security.x509.X500Name;
import sun.security.x509.X509CertImpl;
import sun.security.x509.X509CertInfo;


/**
 * Measure:
 * 1) storage cost of certificates
 * 2) size of message packet
 */

public class Evaluation {

	public ArrayList<String> generateFriendList(int num, int length) {
		ArrayList<String> friendList = new ArrayList<String>();

		for (int i = 0; i < num; i++) {
			friendList.add(Common.getRandomName(length));
		}

		return friendList;
	}

	/** 
	 * Create a self-signed X.509 Certificate
	 * @param dn the X.509 Distinguished Name, eg "CN=Test, L=London, C=GB"
	 * @param pair the KeyPair
	 * @param days how many days from now the Certificate is valid for
	 * @param algorithm the signing algorithm, eg "SHA1withRSA" or "MD5WithRSA"
	 */ 
	public static X509Certificate generateCertificate(String dn, KeyPair pair, int days, String algorithm)
			throws GeneralSecurityException, IOException {
		PrivateKey privkey = pair.getPrivate();
		X509CertInfo info = new X509CertInfo();
		Date from = new Date();
		Date to = new Date(from.getTime() + days * 86400000l);
		CertificateValidity interval = new CertificateValidity(from, to);
		BigInteger sn = new BigInteger(64, new SecureRandom());
		X500Name owner = new X500Name(dn);

		info.set(X509CertInfo.VALIDITY, interval);
		info.set(X509CertInfo.SERIAL_NUMBER, new CertificateSerialNumber(sn));
		info.set(X509CertInfo.SUBJECT, new CertificateSubjectName(owner));
		info.set(X509CertInfo.ISSUER, new CertificateIssuerName(owner));
		info.set(X509CertInfo.KEY, new CertificateX509Key(pair.getPublic()));
		info.set(X509CertInfo.VERSION, new CertificateVersion(CertificateVersion.V3));
		AlgorithmId algo = new AlgorithmId(AlgorithmId.md5WithRSAEncryption_oid);
		info.set(X509CertInfo.ALGORITHM_ID, new CertificateAlgorithmId(algo));

		// Sign the cert to identify the algorithm that's used.
		X509CertImpl cert = new X509CertImpl(info);
		cert.sign(privkey, algorithm);

		// Update the algorithm, and resign.
		algo = (AlgorithmId)cert.get(X509CertImpl.SIG_ALG);
		info.set(CertificateAlgorithmId.NAME + "." + CertificateAlgorithmId.ALGORITHM, algo);
		cert = new X509CertImpl(info);
		cert.sign(privkey, algorithm);
		return cert;
	}
	
	public static void createKeyStore(int num) throws IOException, GeneralSecurityException {
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
			crt = generateCertificate(dn, keyPair, days, algorithm);
			ks.setCertificateEntry(alias, crt);
		}
		FileOutputStream fos = new FileOutputStream(num + "friends.keystore");
		ks.store(fos, password);
		fos.close();
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
		// Symmetric encryption: generate secret key
		AES.getRawKey();
		
		// Asymmetric encryption: generate key pair
		KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
		keyPairGenerator.initialize(1024);
		KeyPair keyPair = keyPairGenerator.genKeyPair();
		
		// Create certificate
		String dn = "cn=Eric Chung, o=UCLA, c=US";
		int days = 1;
		String algorithm = "MD5WithRSA";
		X509Certificate crt = generateCertificate(dn, keyPair, days, algorithm);
		
		// Store away the keystore
		KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
		char[] password = "password".toCharArray();
		ks.load(null, password);
		ks.setCertificateEntry("self", crt);
		FileOutputStream fos = new FileOutputStream("my.keystore");
		ks.store(fos, password);
		fos.close();
	}

}
