package edu.ucla.encryption;
import java.util.ArrayList;

/*
 * Measure:
 * 1) storage cost of certificates
 * 2) size of message packet
 */

public class Evaluation {

	public ArrayList<String> generateFriendList(int num, int length) {
		StringBuffer buffer = new StringBuffer();
		String characters = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890";
		
		ArrayList<String> friendList = new ArrayList<String>();
		
		int charactersLength = characters.length();

		for (int i = 0; i < num; i++) {
			for (int j = 0; j < length; j++) {
				double index = Math.random() * charactersLength;
				buffer.append(characters.charAt((int) index));
			}
			friendList.add(buffer.toString());
		}
		
		return friendList;
	}
	
	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}

}
