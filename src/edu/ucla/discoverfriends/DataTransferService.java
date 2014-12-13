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
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;

import android.app.IntentService;
import android.content.Intent;
import android.net.DhcpInfo;
import android.net.wifi.WifiManager;
import android.util.Log;
import edu.ucla.common.Constants;
import edu.ucla.common.Utils;
import edu.ucla.encryption.AES;
import edu.ucla.encryption.PKE;

/**
 * A service that process each data transfer request. Encryption of the files
 * are done here before they are sent through the network.
 * 
 * Each packet is sent in the following encoding:
 * size_of_rest_of_packet_in_bytes, rest_of_packet
 */
public class DataTransferService extends IntentService {

	private static final String TAG = DataTransferService.class.getName();

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
		if (intent.getAction().equals(Constants.NETWORK_INITIATOR_SETUP)) {
			try {
				DatagramSocket socket = new DatagramSocket(Constants.PORT);
				socket.setBroadcast(true);
				socket.setSoTimeout(Constants.SOCKET_TIMEOUT);

				// Broadcast message.
				ByteArrayOutputStream byteStream = new ByteArrayOutputStream(Constants.BYTE_ARRAY_SIZE);
				ObjectOutputStream outputStream = new ObjectOutputStream(new BufferedOutputStream(byteStream));
				outputStream.writeObject(intent.getExtras().getSerializable(Constants.EXTRAS_SNP));
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
						byte[] data = new byte[4];
						packet = new DatagramPacket(data, data.length);
						socket.receive(packet);

						byteCount = 0;
						// byte[] -> int
						for (int i = 0; i < 4; ++i) {
							byteCount |= (data[3-i] & 0xff) << (i << 3);
						}
						
						// From above, know the length of the payload.
						byte[] packetSize = new byte[byteCount];
						packet = new DatagramPacket(packetSize, packetSize.length);
						socket.receive(packet);
						byte[] encryptedCertificate = packet.getData();
						Log.i(TAG, "Received response.");

						// Broadcast packet back to calling activity.
						intent = new Intent();
						intent.setAction(Constants.NETWORK_INITIATOR_GET_SETUP_ENCRYPTED_CERTIFICATE_RECEIVED);
						intent.putExtra(Constants.EXTRAS_ENCRYPTED_CERTIFICATE, encryptedCertificate);
						intent.putExtra(Constants.EXTRAS_SENDER_IP, packet.getAddress().toString());
						sendBroadcast(intent);
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

		// Initiator sends the list of certificates to each user. This call is done multiple times.
		// TODO: It is also possible to grab the public key for hybrid encryption as the IP is the alias.
		else if (intent.getAction().equals(Constants.NETWORK_INITIATOR_SETUP_CERTIFICATE_LIST)) {
			try {
				InetAddress address = InetAddress.getByName(intent.getStringExtra(Constants.EXTRAS_SENDER_IP));
				DatagramSocket socket = new DatagramSocket(Constants.PORT, address);
				socket.setSoTimeout(Constants.SOCKET_TIMEOUT);

				ByteArrayOutputStream byteStream = new ByteArrayOutputStream(Constants.BYTE_ARRAY_SIZE);
				ObjectOutputStream outputStream = new ObjectOutputStream(new BufferedOutputStream(byteStream));
				outputStream.writeObject(intent.getExtras().getSerializable(Constants.EXTRAS_CERTIFICATE_LIST));
				outputStream.flush();

				String initiatorId = intent.getStringExtra(Constants.EXTRAS_USER_ID);
				String hashedInitiatorUid = Utils.hash(initiatorId);
				byte[] key = Utils.charToByte(hashedInitiatorUid.toCharArray());
				byte[] crtList = byteStream.toByteArray();
				byte[] encryptedCrtList = AES.encrypt(key, crtList);

				int byteCount = encryptedCrtList.length;
				byte[] payloadSize = new byte[4];

				// int -> byte[]
				for (int i = 0; i < 4; ++i) {
					int shift = i << 3; // i * 8
					payloadSize[3-i] = (byte)((byteCount & (0xff << shift)) >>> shift);
				}

				// Send the encrypted certificate list size.
				DatagramPacket packet = new DatagramPacket(payloadSize, 4, address, Constants.PORT);
				socket.send(packet);
				
				// Send the encrypted certificate list.
				packet = new DatagramPacket(encryptedCrtList, encryptedCrtList.length, address, Constants.PORT);
				socket.send(packet);
				
				socket.close();
			} catch (IOException e) {
				Log.e(TAG, e.getMessage());
			} catch (NoSuchAlgorithmException e) {
				Log.e(TAG, e.getMessage());
			} catch (Exception e) {
				Log.e(TAG, e.getMessage());
			}

		}

		// Initiator connects to a specific target and sends an encrypted key and encrypted message.
		else if (intent.getAction().equals(Constants.NETWORK_INITIATOR_KEY_AND_MESSAGE)) {
			try {
				InetAddress address = InetAddress.getByName(intent.getStringExtra(Constants.EXTRAS_SENDER_IP));
				DatagramSocket socket = new DatagramSocket(Constants.PORT, address);
				socket.setBroadcast(true);
				socket.setSoTimeout(Constants.SOCKET_TIMEOUT);

				// Encrypt symmetric key with public key.
				byte[] symmetricKey = intent.getByteArrayExtra(Constants.EXTRAS_SYMMETRIC_KEY_ENCODED);
				PublicKey publicKey = (PublicKey) intent.getSerializableExtra(Constants.EXTRAS_PUBLIC_KEY);
				byte[] encryptedKey = PKE.encrypt(publicKey, symmetricKey);

				int byteCount = encryptedKey.length;
				byte[] payloadSize = new byte[4];

				// int -> byte[]
				for (int i = 0; i < 4; ++i) {
					int shift = i << 3; // i * 8
					payloadSize[3-i] = (byte)((byteCount & (0xff << shift)) >>> shift);
				}

				// Send the encrypted key size.
				DatagramPacket packet = new DatagramPacket(payloadSize, 4, address, Constants.PORT);
				socket.send(packet);

				// Send the encrypted key.
				packet = new DatagramPacket(encryptedKey, encryptedKey.length, address, Constants.PORT);
				socket.send(packet);


				// Encrypt message with AES.
				String message = intent.getStringExtra(Constants.EXTRAS_MESSAGE);
				byte[] plaintext = Utils.charToByte(message.toCharArray());
				byte[] encrypted = AES.encrypt(symmetricKey, plaintext);

				byteCount = encrypted.length;
				payloadSize = new byte[4];

				// int -> byte[]
				for (int i = 0; i < 4; ++i) {
					int shift = i << 3; // i * 8
					payloadSize[3-i] = (byte)((byteCount & (0xff << shift)) >>> shift);
				}

				// Send the encrypted message size.
				packet = new DatagramPacket(payloadSize, 4, address, Constants.PORT);
				socket.send(packet);

				// Send the encrypted message.
				packet = new DatagramPacket(encrypted, encrypted.length, address, Constants.PORT);
				socket.send(packet);

				socket.close();
				Log.i(TAG, "Broadcasted initiator message.");
			} catch (Exception e) {
				Log.e(TAG, e.getMessage());
			}
		}

		// Using UDP, target sends an encrypted message or encrypted certificate to initiator.
		// TODO: Determine when target should send new certificate.
		else if (intent.getAction().equals(Constants.NETWORK_TARGET_MESSAGE) || 
				intent.getAction().equals(Constants.NETWORK_TARGET_SEND_CERTIFICATE)) {
			try {
				DatagramSocket socket = new DatagramSocket(Constants.PORT);
				socket.setSoTimeout(Constants.SOCKET_TIMEOUT);

				byte[] key = intent.getByteArrayExtra(Constants.EXTRAS_ENCRYPTED_GENERAL_KEY);
				byte[] encrypted = null;
				// Encrypt message with AES.
				if (intent.getAction().equals(Constants.NETWORK_TARGET_MESSAGE)) {
					String message = intent.getStringExtra(Constants.EXTRAS_MESSAGE);
					byte[] plaintext = Utils.charToByte(message.toCharArray());
					encrypted = AES.encrypt(key, plaintext);
				}

				// Encrypt certificate with AES.
				else if (intent.getAction().equals(Constants.NETWORK_TARGET_SEND_CERTIFICATE)) {
					ByteArrayOutputStream byteStream = new ByteArrayOutputStream(Constants.BYTE_ARRAY_SIZE);
					ObjectOutputStream outputStream = new ObjectOutputStream(new BufferedOutputStream(byteStream));
					outputStream.writeObject(intent.getSerializableExtra(Constants.EXTRAS_CERTIFICATE));
					outputStream.flush();
					byte[] crt = byteStream.toByteArray();
					encrypted = AES.encrypt(key, crt);
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
