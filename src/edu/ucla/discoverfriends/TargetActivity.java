package edu.ucla.discoverfriends;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.net.wifi.p2p.WifiP2pManager.ChannelListener;
import android.net.wifi.p2p.WifiP2pManager.GroupInfoListener;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnKeyListener;
import android.widget.EditText;
import android.widget.Toast;
import edu.ucla.common.Constants;
import edu.ucla.common.Utils;
import edu.ucla.discoverfriends.DataReceiverService.TargetSetupCertificateListTask;
import edu.ucla.discoverfriends.DataReceiverService.TargetSetupSnpTask;
import edu.ucla.encryption.AES;
import edu.ucla.encryption.KeyRepository;
import edu.ucla.encryption.PKE;

public class TargetActivity extends Activity implements ChannelListener, GroupInfoListener {

	private final static String TAG = TargetActivity.class.getName();

	// UI
	private EditText editMessage = null;
	private ProgressDialog progressDialog = null;

	private boolean isWifiP2pEnabled = false;
	private boolean retryChannel = false;
	private final IntentFilter intentFilter = new IntentFilter();
	private BroadcastReceiver receiver = null;
	private Channel channel;
	private WifiP2pManager manager;

	private String userId = "";
	private String[] friendsId = null;
	private X509Certificate crt = null;
	private String keystorePassword = null;
	private PrivateKey privateKey = null;
	private SetupNetworkPacket snp = null;
	private String hashedInitiatorUid = "";

	public IntentFilter protocolIntentFilter = null; 
	public TargetBroadcastReceiver protocolReceiver;


	public void setIsWifiP2pEnabled(boolean isWifiP2pEnabled) {
		this.isWifiP2pEnabled = isWifiP2pEnabled;
	}

	public String getUserId() {
		return userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public String[] getFriendsId() {
		return friendsId;
	}

	public void setFriendsId(String[] friendsId) {
		this.friendsId = friendsId;
	}

	public X509Certificate getCrt() {
		return crt;
	}

	public void setCrt(X509Certificate crt) {
		this.crt = crt;
	}

	public String getKeystorePassword() {
		return keystorePassword;
	}

	public void setKeystorePassword(String keystorePassword) {
		this.keystorePassword = keystorePassword;
	}

	public PrivateKey getPrivateKey() {
		return privateKey;
	}

	public void setPrivateKey(PrivateKey privateKey) {
		this.privateKey = privateKey;
	}

	public SetupNetworkPacket getSnp() {
		return snp;
	}

	public void setSnp(SetupNetworkPacket snp) {
		this.snp = snp;
	}

	public String getHashedInitiatorUid() {
		return hashedInitiatorUid;
	}

	public void setHashedInitiatorUid(String hashedInitiatorUid) {
		this.hashedInitiatorUid = hashedInitiatorUid;
	}


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.target);

		editMessage = (EditText) findViewById(R.id.edt_message);

		Intent intent = getIntent();
		this.setUserId(intent.getExtras().getString(Constants.EXTRAS_USER_ID));
		this.setFriendsId(intent.getExtras().getStringArray(Constants.EXTRAS_FRIENDS_ID));
		this.setCrt((X509Certificate) intent.getExtras().getSerializable(Constants.EXTRAS_CERTIFICATE));
		this.setKeystorePassword(intent.getExtras().getString(Constants.EXTRAS_KEYSTORE_PASSWORD));
		this.setPrivateKey((PrivateKey) intent.getExtras().getSerializable(Constants.EXTRAS_PRIVATE_KEY));

		//  Indicates a change in the Wi-Fi P2P status.
		intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);

		// Indicates a change in the list of available peers.
		intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);

		// Indicates a change in the state of Wi-Fi P2P connectivity.
		intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);

		// Indicates a change in this device's details.
		intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

		manager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
		channel = manager.initialize(this, getMainLooper(), null);
		receiver = new WiFiDirectBroadcastReceiver(manager, channel, this);

		protocolIntentFilter = new IntentFilter();
		protocolIntentFilter.addAction(Constants.NETWORK_TARGET_MESSAGE_LISTENER_RECEIVED);
		protocolReceiver = new TargetBroadcastReceiver();

		editMessage.setOnKeyListener(new OnKeyListener() {
			@Override
			public boolean onKey(View v, int keyCode, KeyEvent event) {
				if ((event.getAction() == KeyEvent.ACTION_DOWN) && (keyCode == KeyEvent.KEYCODE_ENTER)) {
					try {
						PublicKey publicKey = KeyRepository.getCertificate(Constants.INITIATOR_ALIAS, 
								getFilesDir().getAbsolutePath(), getKeystorePassword()).getPublicKey();
						Intent serviceIntent = new Intent(TargetActivity.this, DataTransferService.class);
						Bundle extras = new Bundle();
						extras.putSerializable(Constants.EXTRAS_PUBLIC_KEY, publicKey);
						extras.putSerializable(Constants.EXTRAS_MESSAGE, editMessage.getText().toString());
						serviceIntent.setAction(Constants.NETWORK_TARGET_MESSAGE);
						serviceIntent.putExtras(extras);
						startService(serviceIntent);
						return true;
					} catch (CertificateException e) {
						Log.e(TAG, e.getMessage());
					} catch (KeyStoreException e) {
						Log.e(TAG, e.getMessage());
					} catch (IOException e) {
						Log.e(TAG, e.getMessage());
					} catch (NoSuchAlgorithmException e) {
						Log.e(TAG, e.getMessage());
					}
				}
				return false;
			}
		});
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.target, menu);
		return true;
	}

	@Override
	protected void onPause() {
		super.onPause();
		unregisterReceiver(receiver);
		unregisterReceiver(protocolReceiver);
	}

	@Override
	protected void onResume() {
		super.onResume();
		registerReceiver(receiver, intentFilter);
		registerReceiver(protocolReceiver, protocolIntentFilter);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		unregisterReceiver(receiver);
		unregisterReceiver(protocolReceiver);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.atn_direct_enable:
			if (manager != null && channel != null) {
				startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
			} else {
				Log.e(TAG, "The channel or manager is null.");
			}
			return true;

		case R.id.atn_direct_discover:
			if (!isWifiP2pEnabled) {
				Toast.makeText(TargetActivity.this, R.string.p2p_off_warning, Toast.LENGTH_LONG).show();
				return true;
			}

			manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
				@Override
				public void onSuccess() {
					Toast.makeText(TargetActivity.this, R.string.discovery_success, Toast.LENGTH_SHORT).show();
				}

				@Override
				public void onFailure(int reasonCode) {
					Toast.makeText(TargetActivity.this, R.string.discovery_failed + reasonCode, Toast.LENGTH_LONG).show();
				}
			});
			return true;

		default:
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void onChannelDisconnected() {
		// Attempt to retry.
		if (manager != null && !retryChannel) {
			Toast.makeText(this, R.string.channel_lost_temporarily, Toast.LENGTH_LONG).show();
			retryChannel = true;
			manager.initialize(this, getMainLooper(), this);
		} else {
			Toast.makeText(this, R.string.channel_lost_permanently, Toast.LENGTH_LONG).show();
		}
	}

	/**
	 * Provides information about the P2P group. The callback provides a
	 * WifiP2pGroup, which has information such as the owner, the network name,
	 * and passphrase.
	 * 
	 * Legacy devices connected through Wi-Fi do not enter through this flow.
	 * Instead, they use onConnectionInfoAvailable in ConnectionInfoListener.
	 * Although the peer list includes them in group.getClientList(), the
	 * PeerListListener only reflects changes in connections made by Wi-Fi
	 * Direct compatible devices.
	 */
	@Override
	public void onGroupInfoAvailable(WifiP2pGroup group) {
		if (progressDialog != null && progressDialog.isShowing()) {
			progressDialog.dismiss();
		}

		if (!group.isGroupOwner()) {
			WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
			WifiInfo info = wifiManager.getConnectionInfo();
			String address = info.getMacAddress();

			// Target prepares to accept setup message passed by the initiator.
			TargetSetupSnpTask targetSetupSnpTask = new DataReceiverService.TargetSetupSnpTask(TargetActivity.this) {
				@Override
				public void receiveData(SetupNetworkPacket snp, String hashedInitiatorUid) {
					setSnp(snp);
					setHashedInitiatorUid(hashedInitiatorUid);
					try {
						// Stores initiator's certificate into own keystore.
						byte[] encryptedCertificate = getSnp().getEcf();
						byte[] cf = AES.decrypt(Utils.charToByte(hashedInitiatorUid.toCharArray()), encryptedCertificate);
						ByteArrayInputStream byteInputStream = new ByteArrayInputStream(cf);
						ObjectInputStream inputStream = new ObjectInputStream(byteInputStream);
						X509Certificate crt = (X509Certificate) inputStream.readObject();
						KeyRepository.storeCertificate(Constants.INITIATOR_ALIAS, crt, getFilesDir().getAbsolutePath(), getKeystorePassword());
					} catch (CertificateException e) {
						Log.e(TAG, e.getMessage());
					} catch (ClassNotFoundException e) {
						Log.e(TAG, e.getMessage());
					} catch (IOException e) {
						Log.e(TAG, e.getMessage());
					} catch (KeyStoreException e) {
						Log.e(TAG, e.getMessage());
					} catch (NoSuchAlgorithmException e) {
						Log.e(TAG, e.getMessage());
					} catch (Exception e) {
						Log.e(TAG, e.getMessage());
					}
				}

				@Override
				public void receiveData(byte[] encryptedCrtList) {
				}
			};
			targetSetupSnpTask.execute(this.getCrt(), this.getUserId(), this.getFriendsId(), address);

			// Target prepares to accept certificate list passed by the initiator.
			TargetSetupCertificateListTask targetSetupCertificateListTask = new DataReceiverService.
					TargetSetupCertificateListTask(TargetActivity.this) {
				@Override
				public void receiveData(SetupNetworkPacket snp, String hashedInitiatorUid) {
				}

				@Override
				public void receiveData(byte[] encryptedCrtList) {
					// Uses initiator's ID to decrypt the encrypted list of certificates.
					try {
						byte[] cfList = AES.decrypt(Utils.charToByte(getHashedInitiatorUid().toCharArray()), encryptedCrtList);
						ByteArrayInputStream byteInputStream = new ByteArrayInputStream(cfList);
						ObjectInputStream inputStream = new ObjectInputStream(byteInputStream);
						@SuppressWarnings("unchecked")
						ArrayList<X509Certificate> crtList = (ArrayList<X509Certificate>) inputStream.readObject();
						KeyRepository.storeCertificateList(crtList, getFilesDir().getAbsolutePath(), getKeystorePassword());
					} catch (Exception e) {
						Log.e(TAG, e.getMessage());
					}
				}
			};
			targetSetupCertificateListTask.execute();

			// Finish initialization phase.
			setPostNetworkInitializationView();
		}
	}

	public void setPostNetworkInitializationView() {
		editMessage.setVisibility(View.VISIBLE);
	}


	public interface CallbackReceiver {
		public void receiveData(SetupNetworkPacket snp, String hashedInitiatorUid);
		public void receiveData(byte[] encryptedCrtList);
	}

	public class TargetBroadcastReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			// Decrypt key to decrypt message or certificate.
			if (intent.getAction().equals(Constants.NETWORK_TARGET_MESSAGE_LISTENER_RECEIVED) ||
					intent.getAction().equals(Constants.NETWORK_TARGET_CERTIFICATE_LISTENER_RECEIVED)) {
				try {
					byte[] encryptedSymmetricKey = intent.getByteArrayExtra(Constants.EXTRAS_ENCRYPTED_SYMMETRIC_KEY);
					byte[] symmetricKey = PKE.decrypt(getPrivateKey(), encryptedSymmetricKey);

					if (intent.getAction().equals(Constants.NETWORK_TARGET_MESSAGE_LISTENER_RECEIVED)) {
						String senderIp = intent.getStringExtra(Constants.EXTRAS_SENDER_IP);
						byte[] encryptedMessage = intent.getByteArrayExtra(Constants.EXTRAS_ENCRYPTED_MESSAGE);
						byte[] message = AES.decrypt(symmetricKey, encryptedMessage);
						Toast.makeText(TargetActivity.this, senderIp + ": " + Utils.byteToString(message), Toast.LENGTH_SHORT).show();
					}
					
					// Stores the certificate.
					else {
						String certificateOwnerIp = intent.getStringExtra(Constants.EXTRAS_CERTIFICATE_OWNER_IP);
						byte[] encryptedCertificate = intent.getByteArrayExtra(Constants.EXTRAS_ENCRYPTED_CERTIFICATE);
						byte[] cf = AES.decrypt(symmetricKey, encryptedCertificate);
						ByteArrayInputStream byteInputStream = new ByteArrayInputStream(cf);
						ObjectInputStream inputStream = new ObjectInputStream(byteInputStream);
						X509Certificate crt = (X509Certificate) inputStream.readObject();
						KeyRepository.storeCertificate(certificateOwnerIp, crt, getFilesDir().getAbsolutePath(), getKeystorePassword());
					}
				} catch (CertificateException e) {
					Log.e(TAG, e.getMessage());
				} catch (IOException e) {
					Log.e(TAG, e.getMessage());
				} catch (KeyStoreException e) {
					Log.e(TAG, e.getMessage());
				} catch (NoSuchAlgorithmException e) {
					Log.e(TAG, e.getMessage());
				} catch (Exception e) {
					Log.e(TAG, e.getMessage());
				}
			}
		}
	}

}
