package edu.ucla.discoverfriends;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.charset.Charset;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;

import android.os.AsyncTask;
import android.util.Log;
import android.util.Pair;

import com.google.common.hash.BloomFilter;

import edu.ucla.common.Constants;
import edu.ucla.common.Utils;
import edu.ucla.encryption.AES;
import edu.ucla.encryption.PKE;

public class DataReceiver {
	public static final String TAG = "DataReceiverService";
	
	private static SetupNetworkPacket snp = null;
	private X509Certificate crt = null;
	private String message = null;
	private Pair<byte[], String> pair = null;

	/**
	 * TargetSetupTask is run at the network initialization phase. Target
	 * receives a SetupNetworkPacket from initiator and sends back an
	 * encrypted certificate.
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
						byte[] key = Utils.toBytes(hashedInitiatorUid.toCharArray());
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
					byte[] key = Utils.toBytes(hashedInitiatorUid.toCharArray());
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
	
	/**
	 * InitiatorGetCertificateTask is run by the initiator to accept an
	 * encrypted certificate from the target. Returns a decrypted certificate.
	 * For simplicity, all certificates will be encrypted using the hash of the
	 * initiator's ID as the key.
	 */
	public class InitiatorGetCertificateTask extends AsyncTask<byte[], Void, X509Certificate> {
		
		@Override
		protected X509Certificate doInBackground(byte[]... params) {
			byte[] symmetricKey = (byte[]) params[0];
			
			try {
				DatagramSocket socket = new DatagramSocket(Constants.PORT);
				
				while (true) {
					// Wait to receive certificate from target.
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
					
					byte[] ecf = packet.getData();
					byte[] cf = AES.decrypt(symmetricKey, ecf);
					
					ByteArrayInputStream byteInputStream = new ByteArrayInputStream(cf);
					ObjectInputStream inputStream = new ObjectInputStream(byteInputStream);
					X509Certificate crt = (X509Certificate) inputStream.readObject();
					Log.i(TAG, "Received certificate from target.");
					
					socket.close();
					return crt;
				}
			} catch (ClassNotFoundException e) {
				Log.e(TAG, "Could not cast packet as X509Certificate Object.");
			} catch (IOException e) {
				Log.e(TAG, e.getMessage());
			} catch (Exception e) {
				Log.e(TAG, e.getMessage());
			}
			return null;
		}

		@Override
		protected void onPostExecute(X509Certificate result) {
			crt = result;
		}
	}
	
	/**
	 * InitiatorMessageListenerTask is run by the initiator and waits for an
	 * encrypted message from the target. Returns a String message.
	 */
	public class InitiatorMessageListenerTask extends AsyncTask<byte[], Void, String> {
		
		@Override
		protected String doInBackground(byte[]... params) {
			byte[] symmetricKey = (byte[]) params[0];
			
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
					byte[] plaintext = AES.decrypt(symmetricKey, ciphertext);
					Log.i(TAG, "Received message.");
					
					socket.close();
					return new String(plaintext, Charset.forName("UTF-8"));
				}
			} catch (IOException e) {
				Log.e(TAG, e.getMessage());
			} catch (Exception e) {
				Log.e(TAG, e.getMessage());
			}
			return null;
		}

		@Override
		protected void onPostExecute(String result) {
			message = result;
		}
	}

	/**
	 * TargetMessageListenerTask is run by the target and waits for an
	 * encrypted key and encrypted message pair from the initiator.
	 * Returns a Pair(byte[] symmetricKey, String message) message.
	 */
	public class TargetMessageListenerTask extends AsyncTask<PublicKey, Void, Pair<byte[], String>> {
		
		@Override
		protected Pair<byte[], String> doInBackground(PublicKey... params) {
			PublicKey publicKey = (PublicKey) params[0];
			
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

					// Decrypt symmetric key using public key.
					byte[] encryptedSymmetricKey = packet.getData();
					byte[] symmetricKey = PKE.decrypt(publicKey, encryptedSymmetricKey);
					
					// From above, know the length of the payload.
					byte[] packetSize = new byte[byteCount];
					packet = new DatagramPacket(packetSize, packetSize.length);
					socket.receive(packet);
					
					byte[] ciphertext = packet.getData();
					byte[] plaintext = AES.decrypt(symmetricKey, ciphertext);
					String message = new String(plaintext, Charset.forName("UTF-8"));
					Log.i(TAG, "Received key and message.");
					
					socket.close();
					return new Pair<byte[], String>(symmetricKey, message);
				}
			} catch (IOException e) {
				Log.e(TAG, e.getMessage());
			} catch (Exception e) {
				Log.e(TAG, e.getMessage());
			}
			return null;
		}

		@Override
		protected void onPostExecute(Pair<byte[], String> result) {
			pair = result;
		}
	}
}
