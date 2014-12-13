package edu.ucla.common;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;


public class Utils {
	public static String getRandomAlias(int length) {
		StringBuffer buffer = new StringBuffer();
		String characters = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890";

		int charactersLength = characters.length();

		for (int j = 0; j < length; j++) {
			double index = Math.random() * charactersLength;
			buffer.append(characters.charAt((int) index));
		}
		return buffer.toString();
	}
	
	public static String generateRandomKeystorePassword() {
		StringBuffer buffer = new StringBuffer();
		String characters = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890";

		int charactersLength = characters.length();

		for (int j = 0; j < 20; j++) {
			double index = Math.random() * charactersLength;
			buffer.append(characters.charAt((int) index));
		}
		return buffer.toString();
	}

	public static String customDName(String name) {
		StringBuffer buffer = new StringBuffer();
		buffer.append("cn=");
		buffer.append(name);
		return buffer.toString();
	}

	public static byte[] charToByte(char[] chars) {
		CharBuffer charBuffer = CharBuffer.wrap(chars);
		ByteBuffer byteBuffer = Charset.forName("UTF-8").encode(charBuffer);
		byte[] bytes = Arrays.copyOfRange(byteBuffer.array(),
				byteBuffer.position(), byteBuffer.limit());
		Arrays.fill(charBuffer.array(), '\u0000'); // clear sensitive data
		Arrays.fill(byteBuffer.array(), (byte) 0); // clear sensitive data
		return bytes;
	}
	
	/**
	 * Specific for byte[4].
	 */
	public static byte[] intToByte(int number) {
		byte[] bytes = new byte[4];
		for (int i = 0; i < 4; ++i) {
			int shift = i << 3; // i * 8
			bytes[3-i] = (byte)((number & (0xff << shift)) >>> shift);
		}
		return bytes;
	}
	
	public static String byteToString(byte[] bytes) {
		return new String(bytes, Charset.forName("UTF-8"));
	}
	
	/**
	 * Specific for byte[4].
	 */
	public static int byteToInt(byte[] bytes) {
		int byteCount = 0;
		for (int i = 0; i < 4; ++i) {
			byteCount |= (bytes[3-i] & 0xff) << (i << 3);
		}
		return byteCount;
	}

	public static String hash(String s) throws NoSuchAlgorithmException {
		MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
		messageDigest.update(charToByte(s.toCharArray()));
		return new String(messageDigest.digest(), Charset.forName("UTF-8"));
	}
}
