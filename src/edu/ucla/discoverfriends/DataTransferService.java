//Copyright 2011 Google Inc. All Rights Reserved.

package edu.ucla.discoverfriends;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;

import android.app.IntentService;
import android.content.Intent;
import android.net.DhcpInfo;
import android.net.wifi.WifiManager;
import android.util.Log;
import edu.ucla.common.Constants;
import edu.ucla.common.Utils;
import edu.ucla.encryption.AES;

/**
 * A service that process each data transfer request i.e Intent by opening a
 * socket connection with the client and sending the data
 */
public class DataTransferService extends IntentService {

	public static final String TAG = "DataTransferService";

	public static final String NETWORK_INITIATOR_SETUP = "edu.ucla.discoverfriends.NETWORK_INITIATOR_SETUP";
	public static final String NETWORK_TARGET_SEND_CERTIFICATE = "edu.ucla.discoverfriends.NETWORK_TARGET_SEND_CERTIFICATE";
	public static final String NETWORK_INITIATOR_MESSAGE_AND_KEY = "edu.ucla.discoverfriends.NETWORK_INITIATOR_MESSAGE_AND_KEY";
	public static final String NETWORK_TARGET_MESSAGE = "edu.ucla.discoverfriends.NETWORK_INITIATOR_MESSAGE";

	// Object extras
	public static final String EXTRAS_CERTIFICATE = "certificate";
	public static final String EXTRAS_SNP = "snp";
	
	// Primitive extras
	public static final String EXTRAS_MESSAGE = "message";
	
	// Byte-encoded extras
	public static final String EXTRAS_PUBLIC_KEY_ENCODED = "public_key_encoded";
	public static final String EXTRAS_SYMMETRIC_KEY_ENCODED = "symmetric_key_encoded";
	
	public static final String EXTRAS_DEVICE_ADDRESS = "go_client";
	public static final String EXTRAS_GROUP_OWNER_PORT = "go_port";

	private WifiManager mWifi;

	public DataTransferService(String name) {
		super(name);
	}

	public DataTransferService() {
		super("DataTransferService");
	}

	/**
	 * Calculate the broadcast IP we need to send the packet along. If we send it
	 * to 255.255.255.255, it never gets sent. I guess this has something to do
	 * with the mobile network not wanting to do broadcast.
	 */
	private InetAddress getBroadcastAddress() throws IOException {
		DhcpInfo dhcp = mWifi.getDhcpInfo();
		if (dhcp == null) {
			Log.d(TAG, "Could not get DHCP information.");
			return null;
		}

		int broadcast = (dhcp.ipAddress & dhcp.netmask) | ~dhcp.netmask;
		byte[] quads = new byte[4];
		for (int k = 0; k < 4; k++)
			quads[k] = (byte) ((broadcast >> k * 8) & 0xFF);
		return InetAddress.getByAddress(quads);
	}

	/*
	 * (non-Javadoc)
	 * @see android.app.IntentService#onHandleIntent(android.content.Intent)
	 */
	@Override
	protected void onHandleIntent(Intent intent) {

		// At network setup, UDP broadcasts (BF, BF+, CF) and waits for encrypted CF.
		if (intent.getAction().equals(NETWORK_INITIATOR_SETUP)) {
			try {
				DatagramSocket socket = new DatagramSocket(Constants.PORT);
				socket.setBroadcast(true);
				socket.setSoTimeout(Constants.SOCKET_TIMEOUT);

				// Broadcast message.
				ByteArrayOutputStream byteStream = new ByteArrayOutputStream(Constants.BYTE_ARRAY_SIZE);
				ObjectOutputStream outputStream = new ObjectOutputStream(new BufferedOutputStream(byteStream));
				outputStream.writeObject(intent.getExtras().getSerializable(EXTRAS_SNP));
				outputStream.flush();
				byte[] payload = byteStream.toByteArray();
				int byteCount = payload.length;
				byte[] payloadSize = new byte[4];

				// int -> byte[]
				for (int i = 0; i < 4; ++i) {
					int shift = i << 3; // i * 8
					payloadSize[3-i] = (byte)((byteCount & (0xff << shift)) >>> shift);
				}

				// Send the payload size.
				DatagramPacket packet = new DatagramPacket(
						payloadSize, 4, getBroadcastAddress(), Constants.PORT);
				socket.send(packet);

				// Send the payload.
				packet = new DatagramPacket(
						payload, payload.length, getBroadcastAddress(), Constants.PORT);
				socket.send(packet);
				Log.i(TAG, "Broadcasted server setup message.");

				// Wait for connected clients to send back their own encrypted certificate until SOCKET_TIMEOUT.
				byte[] buf = new byte[Constants.BYTE_ARRAY_SIZE];
				try {
					while (true) {
						packet = new DatagramPacket(buf, buf.length);
						socket.receive(packet);
						Log.i(TAG, "Received response");
					}
				} catch (SocketTimeoutException e) {
					Log.d(TAG, "Receive timed out.");
				} finally {
					socket.close();
				}

			} catch (IOException e) {
				Log.e(TAG, e.getMessage());
			}
		}

		// UDP broadcasts initiator's encrypted key and encrypted message.
		else if (intent.getAction().equals(NETWORK_INITIATOR_MESSAGE_AND_KEY)) {
			try {
				DatagramSocket socket = new DatagramSocket(Constants.PORT);
				socket.setBroadcast(true);
				socket.setSoTimeout(Constants.SOCKET_TIMEOUT);
				
				// First generate random symmetric key.
				byte[] symmetricKey = AES.getRandomKey();
				
				// Encrypt symmetric key with public key.
				byte[] publicKey = intent.getByteArrayExtra(EXTRAS_PUBLIC_KEY_ENCODED);
				byte[] encryptedKey = AES.encrypt(publicKey, symmetricKey);

				int byteCount = encryptedKey.length;
				byte[] payloadSize = new byte[4];

				// int -> byte[]
				for (int i = 0; i < 4; ++i) {
					int shift = i << 3; // i * 8
					payloadSize[3-i] = (byte)((byteCount & (0xff << shift)) >>> shift);
				}

				// Send the encrypted key size.
				DatagramPacket packet = new DatagramPacket(
						payloadSize, 4, getBroadcastAddress(), Constants.PORT);
				socket.send(packet);

				// Send the encrypted key.
				packet = new DatagramPacket(
						encryptedKey, encryptedKey.length, getBroadcastAddress(), Constants.PORT);
				socket.send(packet);
				

				// Encrypt message with AES.
				String message = intent.getStringExtra(EXTRAS_MESSAGE);
				byte[] plaintext = Utils.toBytes(message.toCharArray());
				byte[] encrypted = AES.encrypt(symmetricKey, plaintext);

				byteCount = encrypted.length;
				payloadSize = new byte[4];

				// int -> byte[]
				for (int i = 0; i < 4; ++i) {
					int shift = i << 3; // i * 8
					payloadSize[3-i] = (byte)((byteCount & (0xff << shift)) >>> shift);
				}

				// Send the encrypted message size.
				packet = new DatagramPacket(
						payloadSize, 4, getBroadcastAddress(), Constants.PORT);
				socket.send(packet);

				// Send the encrypted message.
				packet = new DatagramPacket(
						encrypted, encrypted.length, getBroadcastAddress(), Constants.PORT);
				socket.send(packet);

				socket.close();
				Log.i(TAG, "Broadcasted initiator message.");
			} catch (Exception e) {
				Log.e(TAG, e.getMessage());
			}
		}

		// Using UDP, target sends an encrypted message or encrypted certificate to initiator.
		else if (intent.getAction().equals(NETWORK_TARGET_MESSAGE) || 
				intent.getAction().equals(NETWORK_TARGET_SEND_CERTIFICATE)) {
			try {
				DatagramSocket socket = new DatagramSocket(Constants.PORT);
				socket.setSoTimeout(Constants.SOCKET_TIMEOUT);

				byte[] symmetricKey = intent.getByteArrayExtra(EXTRAS_SYMMETRIC_KEY_ENCODED);
				byte[] encrypted = null;
				// Encrypt message with AES.
				if (intent.getAction().equals(NETWORK_TARGET_MESSAGE)) {
					String message = intent.getStringExtra(EXTRAS_MESSAGE);
					byte[] plaintext = Utils.toBytes(message.toCharArray());
					encrypted = AES.encrypt(symmetricKey, plaintext);
				}
				
				// Encrypt certificate with AES.
				else if (intent.getAction().equals(NETWORK_TARGET_SEND_CERTIFICATE)) {
					ByteArrayOutputStream byteStream = new ByteArrayOutputStream(Constants.BYTE_ARRAY_SIZE);
					ObjectOutputStream outputStream = new ObjectOutputStream(new BufferedOutputStream(byteStream));
					outputStream.writeObject(intent.getSerializableExtra(EXTRAS_CERTIFICATE));
					outputStream.flush();
					byte[] crt = byteStream.toByteArray();
					encrypted = AES.encrypt(symmetricKey, crt);
				}

				int byteCount = encrypted.length;
				byte[] payloadSize = new byte[4];

				// int -> byte[]
				for (int i = 0; i < 4; ++i) {
					int shift = i << 3; // i * 8
					payloadSize[3-i] = (byte)((byteCount & (0xff << shift)) >>> shift);
				}

				// Send the encrypted message size.
				DatagramPacket packet = new DatagramPacket(
						payloadSize, 4, getBroadcastAddress(), Constants.PORT);
				socket.send(packet);

				// Send the encrypted message.
				packet = new DatagramPacket(
						encrypted, encrypted.length, getBroadcastAddress(), Constants.PORT);
				socket.send(packet);
				socket.close();
			} catch (Exception e) {
				Log.e(TAG, e.getMessage());
			}
		}

	}

}
