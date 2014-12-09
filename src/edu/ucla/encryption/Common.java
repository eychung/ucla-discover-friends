package edu.ucla.encryption;


public class Common {
	public static String getRandomName(int length) {
		StringBuffer buffer = new StringBuffer();
		String characters = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890";

		int charactersLength = characters.length();
		
		for (int j = 0; j < length; j++) {
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
}
