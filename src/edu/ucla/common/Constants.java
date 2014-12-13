package edu.ucla.common;

public class Constants {
	// Identifier strings
	public static final String USER_LABEL = "user";
	public static final String USER_INITIATOR = "initiator";
	public static final String USER_TARGET = "target";

	// Network strings
	public static final int BYTE_ARRAY_SIZE = 5000;
	public static final int PORT = 8988;
	public static final int SOCKET_TIMEOUT = 5000; // in ms

	// Security strings
	//public static final String USER_CERTIFICATE_ALIAS = "self";
	public static final int ENCRYPTED_KEY_SIZE = 128;
	public static final String INITIATOR_ALIAS = "initiator";
	public static final String KEYSTORE_NAME = "user.keystore";
	
	// IntentService action names
	public static final String NETWORK_INITIATOR_GET_ENCRYPTED_CERTIFICATE = "network_initiator_get_encrypted_certificate";
	public static final String NETWORK_INITIATOR_KEY_AND_MESSAGE = "edu.ucla.discoverfriends.NETWORK_INITIATOR_MESSAGE_AND_KEY";
	public static final String NETWORK_INITIATOR_MESSAGE_LISTENER = "network_initiator_message_listener";
	public static final String NETWORK_INITIATOR_SETUP = "edu.ucla.discoverfriends.NETWORK_INITIATOR_SETUP";
	public static final String NETWORK_INITIATOR_SETUP_CERTIFICATE_LIST = "edu.ucla.discoverfriends.NETWORK_INITIATOR_SETUP_CERTIFICATE_LIST";
	public static final String NETWORK_TARGET_MESSAGE = "edu.ucla.discoverfriends.NETWORK_INITIATOR_MESSAGE";
	public static final String NETWORK_TARGET_MESSAGE_LISTENER = "network_target_message_listener";
	public static final String NETWORK_TARGET_SEND_CERTIFICATE = "edu.ucla.discoverfriends.NETWORK_TARGET_SEND_CERTIFICATE";
	
	// Broadcast (received) intent names
	public static final String NETWORK_INITIATOR_GET_ENCRYPTED_CERTIFICATE_RECEIVED = "network_initiator_get_encrypted_certificate_received";
	public static final String NETWORK_INITIATOR_GET_SETUP_ENCRYPTED_CERTIFICATE_RECEIVED = "network_initiator_get_setup_encrypted_certificate_received";
	public static final String NETWORK_INITIATOR_MESSAGE_LISTENER_RECEIVED = "network_initiator_message_listener_received";
	public static final String NETWORK_TARGET_MESSAGE_LISTENER_RECEIVED = "network_target_message_listener_received";
	
	// Intent extras strings
	public static final String EXTRAS_CERTIFICATE = "certificate"; // X509Certificate
	public static final String EXTRAS_CERTIFICATE_LIST = "certificate_list"; // ArrayList<X509Certificate>
	public static final String EXTRAS_ENCRYPTED_CERTIFICATE = "encrypted_certificate"; // byte[]
	public static final String EXTRAS_ENCRYPTED_GENERAL_KEY = "general_key"; // byte[]
	public static final String EXTRAS_ENCRYPTED_MESSAGE = "encrypted_message"; // byte[]
	public static final String EXTRAS_ENCRYPTED_SYMMETRIC_KEY = "encrypted_symmetric_key"; // byte[]
	public static final String EXTRAS_FRIENDS_ID = "friends_id"; // String[]
	public static final String EXTRAS_MESSAGE = "message"; // String
	public static final String EXTRAS_MAC_ADDRESS = "mac_address"; // String
	public static final String EXTRAS_PUBLIC_KEY = "public_key"; // PublicKey
	public static final String EXTRAS_PRIVATE_KEY = "private_key"; // PrivateKey
	public static final String EXTRAS_KEYSTORE_PASSWORD = "keystore_password"; // String
	public static final String EXTRAS_SENDER_IP = "sender_ip"; // String
	public static final String EXTRAS_SNP = "snp"; // SetupNetworkPacket
	public static final String EXTRAS_SYMMETRIC_KEY_ENCODED = "symmetric_key_encoded"; // byte[]
	public static final String EXTRAS_USER_ID = "user_id"; // String
	
	// ProgressDialog strings
	public static final String PROGRESS_DIALOG_CONNECTING_ALL_PEERS = "Waiting till all peers to reply.";
	public static final String PROGRESS_DIALOG_CONNECTING_ALL_PEERS_TITLE = "Receiving Peer Replies";
}
