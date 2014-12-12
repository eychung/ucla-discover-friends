package edu.ucla.discoverfriends;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;

import android.app.IntentService;
import android.content.Intent;
import android.os.AsyncTask;
import android.util.Log;

import com.google.common.hash.BloomFilter;

import edu.ucla.common.Constants;
import edu.ucla.common.Utils;
import edu.ucla.encryption.AES;

/**
 * TargetSetupTask is an AsyncTask, which can only be run once. Note that,
 * it can be called again by making a new instance.
 */
public class DataReceiverService extends IntentService {

	private static final String TAG = DataReceiverService.class.getName();

	private static SetupNetworkPacket snp = null;

	public DataReceiverService(String name) {
		super(name);
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		// The initiator receives an encrypted certificate from the target.
		if (intent.getAction().equals(Constants.NETWORK_INITIATOR_GET_ENCRYPTED_CERTIFICATE)) {
			try {
				DatagramSocket socket = new DatagramSocket(Constants.PORT);

				while (true) {
					// Wait to receive encrypted certificate from target.
					byte[] data = new byte[4];
					DatagramPacket packet = new DatagramPacket(data, data.length);
					socket.receive(packet);

					int byteCount = 0;
					// byte[] -> int
					for (int i = 0; i < 4; ++i) {
						byteCount |= (data[3-i] & 0xff) << (i << 3);
					}

					// From above, know the length of the payload.
					byte[] packetSize = new byte[byteCount];
					packet = new DatagramPacket(packetSize, packetSize.length);
					socket.receive(packet);

					byte[] encryptedCertificate = packet.getData();
					Log.i(TAG, "Received encrypted certificate from target.");

					socket.close();

					// Broadcast packet back to calling activity.
					intent = new Intent();
					intent.setAction(Constants.NETWORK_INITIATOR_GET_ENCRYPTED_CERTIFICATE_RECEIVED);
					intent.putExtra(Constants.EXTRAS_ENCRYPTED_CERTIFICATE, encryptedCertificate);
					intent.putExtra(Constants.EXTRAS_SENDER_IP, packet.getAddress().toString());
					sendBroadcast(intent);
				}
			} catch (IOException e) {
				Log.e(TAG, e.getMessage());
			} catch (Exception e) {
				Log.e(TAG, e.getMessage());
			}
		}
		
		// Initiator receives an encrypted message from the target.
		else if (intent.getAction().equals(Constants.NETWORK_INITIATOR_MESSAGE_LISTENER)) {
			try {
				DatagramSocket socket = new DatagramSocket(Constants.PORT);

				while (true) {
					// Wait to receive message.
					byte[] data = new byte[4];
					DatagramPacket packet = new DatagramPacket(data, data.length);
					socket.receive(packet);

					int byteCount = 0;
					// byte[] -> int
					for (int i = 0; i < 4; ++i) {
						byteCount |= (data[3-i] & 0xff) << (i << 3);
					}

					// From above, know the length of the payload.
					byte[] packetSize = new byte[byteCount];
					packet = new DatagramPacket(packetSize, packetSize.length);
					socket.receive(packet);

					byte[] ciphertext = packet.getData();
					Log.i(TAG, "Received encrypted message.");

					socket.close();
					
					// Broadcast packet back to calling activity.
					intent = new Intent();
					intent.setAction(Constants.NETWORK_INITIATOR_MESSAGE_LISTENER_RECEIVED);
					intent.putExtra(Constants.EXTRAS_ENCRYPTED_MESSAGE, ciphertext);
					intent.putExtra(Constants.EXTRAS_SENDER_IP, packet.getAddress().toString());
					sendBroadcast(intent);
				}
			} catch (IOException e) {
				Log.e(TAG, e.getMessage());
			} catch (Exception e) {
				Log.e(TAG, e.getMessage());
			}
		}
		
		/*
		 * Target receives an encrypted key and an encrypted message from the initiator.
		 * Special case as it receives two in one. Fortunately, the size of the encrypted
		 * key is known.
		 */
		else if (intent.getAction().equals(Constants.NETWORK_TARGET_MESSAGE_LISTENER)) {
			try {
				DatagramSocket socket = new DatagramSocket(Constants.PORT);

				while (true) {
					// Wait to receive size of key.
					byte[] data = new byte[4];
					DatagramPacket packet = new DatagramPacket(data, data.length);
					socket.receive(packet);
					int byteCount = 0;
					for (int i = 0; i < 4; ++i) {
						byteCount |= (data[3-i] & 0xff) << (i << 3);
					}
					
					// It is known that the encrypted key is 128 bytes long.
					byte[] packetSize = new byte[Constants.ENCRYPTED_KEY_SIZE];
					packet = new DatagramPacket(packetSize, packetSize.length);
					socket.receive(packet);
					byte[] encryptedSymmetricKey = packet.getData();
					
					// Next 4 bytes is the size of the message.
					packet = new DatagramPacket(data, data.length);
					socket.receive(packet);
					byteCount = 0;
					for (int i = 0; i < 4; ++i) {
						byteCount |= (data[3-i] & 0xff) << (i << 3);
					}
					
					// From above, know the length of the payload.
					packetSize = new byte[byteCount];
					packet = new DatagramPacket(packetSize, packetSize.length);
					socket.receive(packet);

					byte[] ciphertext = packet.getData();
					Log.i(TAG, "Received encrypted key and encrypted message.");

					socket.close();
					
					// Broadcast packet back to calling activity.
					intent = new Intent();
					intent.setAction(Constants.NETWORK_TARGET_MESSAGE_LISTENER_RECEIVED);
					intent.putExtra(Constants.EXTRAS_ENCRYPTED_SYMMETRIC_KEY, encryptedSymmetricKey);
					intent.putExtra(Constants.EXTRAS_ENCRYPTED_MESSAGE, ciphertext);
					intent.putExtra(Constants.EXTRAS_SENDER_IP, packet.getAddress().toString());
					sendBroadcast(intent);
				}
			} catch (IOException e) {
				Log.e(TAG, e.getMessage());
			} catch (Exception e) {
				Log.e(TAG, e.getMessage());
			}
		}
	}

	/**
	 * TargetSetupTask is run at the network initialization phase. Target
	 * receives a SetupNetworkPacket from initiator and sends back an
	 * encrypted certificate.
	 * 
	 * Decryption happens here to check for sender's authentication before
	 * sending back the target's certificate. Normally, decryption happens
	 * at the activity level rather than the network level.
	 */
	public static class TargetSetupTask extends AsyncTask<Object, Void, SetupNetworkPacket> {

		@Override
		protected SetupNetworkPacket doInBackground(Object... params) {
			X509Certificate ownCrt = (X509Certificate) params[0];
			String uid = (String) params[1];
			String[] friendsUid = (String[]) params[2];

			try {
				DatagramSocket socket = new DatagramSocket(Constants.PORT);

				while (true) {
					// Wait to receive (BF, BF+, CF) from initiator.
					byte[] data = new byte[4];
					DatagramPacket packet = new DatagramPacket(data, data.length);
					socket.receive(packet);

					int byteCount = 0;
					// byte[] -> int
					for (int i = 0; i < 4; ++i) {
						byteCount |= (data[3-i] & 0xff) << (i << 3);
					}

					// From above, know the length of the payload.
					byte[] packetSize = new byte[byteCount];
					packet = new DatagramPacket(packetSize, packetSize.length);
					socket.receive(packet);

					ByteArrayInputStream byteInputStream = new ByteArrayInputStream(packetSize);
					ObjectInputStream inputStream = new ObjectInputStream(byteInputStream);
					SetupNetworkPacket snp = (SetupNetworkPacket) inputStream.readObject();
					Log.i(TAG, "Received (BF, BF+, CF) from initiator.");

					// Find initiator's ID. Only if target is in initiator's list of friends, continue.
					BloomFilter<String> bf = snp.getBf();
					BloomFilter<String> bfp = snp.getBfp();
					String hashedInitiatorUid = "";
					String hashedUid = Utils.hash(uid);
					if (bf.mightContain(hashedUid)) {
						String hashedFriendUid;
						for (int i = 0; i < friendsUid.length; i++) {
							hashedFriendUid = Utils.hash(friendsUid[i]);
							if (bfp.mightContain(hashedFriendUid) && !bf.mightContain(hashedFriendUid)) {
								hashedInitiatorUid = hashedFriendUid;
							}
						}
					}
					else {
						socket.close();
						Log.i(TAG, "User is not the initiator's target.");
						break;
					}

					// Decrypt and validate sender's certificate.
					if (hashedInitiatorUid.isEmpty()) {
						byte[] key = Utils.charToByte(hashedInitiatorUid.toCharArray());
						byte[] ecf = snp.getEcf();
						byte[] cf = AES.decrypt(key, ecf);
						byteInputStream = new ByteArrayInputStream(cf);
						inputStream = new ObjectInputStream(byteInputStream);
						X509Certificate crt = (X509Certificate) inputStream.readObject();
						crt.checkValidity();
					}
					else {
						socket.close();
						Log.i(TAG, "Could not find match with initiator's ID.");
						break;
					}

					// Send own encrypted certificate back to initiator.
					ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream(Constants.BYTE_ARRAY_SIZE);
					ObjectOutputStream outputStream = new ObjectOutputStream(new BufferedOutputStream(byteOutputStream));
					outputStream.writeObject(ownCrt);
					outputStream.flush();
					byte[] key = Utils.charToByte(hashedInitiatorUid.toCharArray());
					byte[] payload = byteOutputStream.toByteArray();
					byte[] ecf = AES.encrypt(key, payload);
					DatagramPacket sendPacket = new DatagramPacket(ecf, ecf.length, packet.getAddress(), packet.getPort());
					socket.send(sendPacket);
					Log.i(TAG, "Sent encrypted certificate to initiator.");

					socket.close();
					return snp;
				}
			} catch (CertificateExpiredException e) {
				Log.e(TAG, "Certificate has expired.");
			} catch (CertificateNotYetValidException e) {
				Log.e(TAG, "Certificate not yet valid.");
			} catch (ClassNotFoundException e) {
				Log.e(TAG, "Could not cast packet as SetupNetworkPacket Object.");
			} catch (IOException e) {
				Log.e(TAG, e.getMessage());
			} catch (NoSuchAlgorithmException e) {
				Log.e(TAG, e.getMessage());
			} catch (Exception e) {
				Log.e(TAG, e.getMessage());
			}
			return null;
		}

		@Override
		protected void onPostExecute(SetupNetworkPacket result) {
			snp = result;
		}
	}

}
