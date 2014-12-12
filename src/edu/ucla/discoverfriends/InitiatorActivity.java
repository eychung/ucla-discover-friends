package edu.ucla.discoverfriends;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.Charset;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.List;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.ActionListener;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.net.wifi.p2p.WifiP2pManager.ChannelListener;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnKeyListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.common.base.Charsets;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnel;
import com.google.common.hash.PrimitiveSink;

import edu.ucla.common.Constants;
import edu.ucla.common.Parameters;
import edu.ucla.common.Utils;
import edu.ucla.discoverfriends.DeviceListFragment.DeviceActionListener;
import edu.ucla.encryption.AES;
import edu.ucla.encryption.KeyRepository;

/**
 * Includes functions to encrypt and decrypt certificates using the hash of the
 * initiator's id as the key.
 * 
 * Defines a InitiatorBroadcastReceiver class.
 */
public class InitiatorActivity extends Activity implements ChannelListener, DeviceActionListener {

	private static final String TAG = InitiatorActivity.class.getName();

	// UI
	private Button buttonInitializeManet;
	private EditText editMessage;

	private boolean isWifiP2pEnabled = false;
	private boolean retryChannel = false;
	private IntentFilter intentFilter = null;
	private BroadcastReceiver receiver = null;
	private Channel channel;
	private WifiP2pManager manager;

	private String userId = "";
	private String[] friendsId = null;
	private X509Certificate crt = null;
	private String keystorePassword = "";
	private PrivateKey privateKey = null;
	private BloomFilter<String> bf = null;
	private BloomFilter<String> bfp = null;
	private byte[] ecf = null;
	private SetupNetworkPacket snp = null;
	private byte[] currentSymmetricKey = null;

	public IntentFilter protocolIntentFilter = null; 
	public InitiatorBroadcastReceiver protocolReceiver;


	public Channel getChannel() {
		return channel;
	}

	public WifiP2pManager getManager() {
		return manager;
	}

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

	public BloomFilter<String> getBf() {
		return bf;
	}

	public void setBf(BloomFilter<String> bf) {
		this.bf = bf;
	}

	public BloomFilter<String> getBfp() {
		return bfp;
	}

	public void setBfp(BloomFilter<String> bfp) {
		this.bfp = bfp;
	}

	public byte[] getEcf() {
		return ecf;
	}

	public void setEcf(byte[] ecf) {
		this.ecf = ecf;
	}

	public SetupNetworkPacket getSnp() {
		return snp;
	}

	public void setSnp(SetupNetworkPacket snp) {
		this.snp = snp;
	}

	public byte[] getCurrentSymmetricKey() {
		return currentSymmetricKey;
	}

	public void setCurrentSymmetricKey(byte[] currentSymmetricKey) {
		this.currentSymmetricKey = currentSymmetricKey;
	}


	public static class StringFunnel implements Funnel<String> {
		private static final long serialVersionUID = 1L;

		@Override
		public void funnel(String from, PrimitiveSink into) {
			into.putString(from, Charsets.UTF_8);
		}
	}


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.initiator);

		buttonInitializeManet = (Button) findViewById(R.id.btn_initialize_manet);
		editMessage = (EditText) findViewById(R.id.edt_message);

		Intent intent = getIntent();
		this.setUserId(intent.getExtras().getString(Constants.EXTRAS_USER_ID));
		this.setFriendsId(intent.getExtras().getStringArray(Constants.EXTRAS_FRIENDS_ID));
		this.setCrt((X509Certificate) intent.getExtras().getSerializable(Constants.EXTRAS_CERTIFICATE));
		this.setKeystorePassword(intent.getExtras().getString(Constants.EXTRAS_KEYSTORE_PASSWORD));
		this.setPrivateKey((PrivateKey) intent.getExtras().getSerializable(Constants.EXTRAS_PRIVATE_KEY));

		try {
			this.setBf(this.createBf(this.getFriendsId()));
			this.setBfp(this.createBfp(this.getFriendsId()));
			this.setEcf(this.encryptCertificate(this.getCrt(), this.getUserId()));
			this.setSnp(this.createSnp(this.getBf(), this.getBfp(), this.getEcf()));
		} catch (NoSuchAlgorithmException e) {
			Log.e(TAG, e.getMessage());
		} catch (Exception e) {
			Log.e(TAG, e.getMessage());
		}

		intentFilter = new IntentFilter();
		intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
		intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
		intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
		intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

		manager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
		channel = manager.initialize(this, getMainLooper(), null);
		receiver = new WiFiDirectBroadcastReceiver(manager, channel, this);

		protocolIntentFilter = new IntentFilter();
		protocolIntentFilter.addAction(Constants.NETWORK_INITIATOR_GET_ENCRYPTED_CERTIFICATE_RECEIVED);
		protocolIntentFilter.addAction(Constants.NETWORK_INITIATOR_MESSAGE_LISTENER_RECEIVED);
		protocolReceiver = new InitiatorBroadcastReceiver();

		buttonInitializeManet.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				createGroup();

				DeviceListFragment fragmentDetails = (DeviceListFragment) getFragmentManager().findFragmentById(R.id.frag_list);
				List<WifiP2pDevice> peers = fragmentDetails.getPeers();

				// TODO: Multithread connections for multiple connected devices.
				// TODO: Should be done as an async task... or at least show when a client is connected.
				if (!peers.isEmpty()) {
					WifiP2pConfig config;
					for (int i = 0; i < peers.size(); i++) {
						config = new WifiP2pConfig();
						config.deviceAddress = peers.get(i).deviceAddress;
						config.wps.setup = WpsInfo.PBC;
						connect(config);
					}

					// After connecting to all the peers, broadcast the initiator's setup network packet.
					Intent serviceIntent = new Intent(InitiatorActivity.this, DataTransferService.class);
					Bundle extras = new Bundle();
					extras.putSerializable(Constants.EXTRAS_SNP, getSnp());
					serviceIntent.setAction(Constants.NETWORK_INITIATOR_SETUP);
					serviceIntent.putExtras(extras);
					startService(serviceIntent);
					
					setPostNetworkInitializationView();
				}
				else {
					Toast.makeText(InitiatorActivity.this, R.string.peer_list_empty, Toast.LENGTH_LONG).show();
				}
			}
		});

		editMessage.setOnKeyListener(new OnKeyListener() {
			@Override
			public boolean onKey(View v, int keyCode, KeyEvent event) {
				if ((event.getAction() == KeyEvent.ACTION_DOWN) && (keyCode == KeyEvent.KEYCODE_ENTER)) {
					try {
						setCurrentSymmetricKey(AES.getRandomKey());
						KeyStore keystore = KeyRepository.getKeyStore(getFilesDir().getAbsolutePath());
						for (String alias : Collections.list(keystore.aliases())) {
							if (!alias.equals(getUserId())) {
								Intent serviceIntent = new Intent(InitiatorActivity.this, DataTransferService.class);
								Bundle extras = new Bundle();
								extras.putString(Constants.EXTRAS_SENDER_IP, alias);
								extras.putByteArray(Constants.EXTRAS_SYMMETRIC_KEY_ENCODED, getCurrentSymmetricKey());
								extras.putSerializable(Constants.EXTRAS_PUBLIC_KEY, keystore.getCertificate(alias).getPublicKey());
								extras.putSerializable(Constants.EXTRAS_MESSAGE, editMessage.getText().toString());
								serviceIntent.setAction(Constants.NETWORK_INITIATOR_KEY_AND_MESSAGE);
								serviceIntent.putExtras(extras);
								startService(serviceIntent);
							}
						}
						return true;
					} catch (Exception e) {
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
		inflater.inflate(R.menu.initiator, menu);
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
				// Since this is the system wireless settings activity, it's
				// not going to send us a result. We will be notified by
				// WiFiDeviceBroadcastReceiver instead.
				startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
			} else {
				Log.e(TAG, "The channel or manager is null.");
			}
			return true;

		case R.id.atn_direct_discover:
			if (!isWifiP2pEnabled) {
				Toast.makeText(InitiatorActivity.this, R.string.p2p_off_warning, Toast.LENGTH_LONG).show();
				return true;
			}

			final DeviceListFragment fragment = (DeviceListFragment) getFragmentManager().findFragmentById(R.id.frag_list);
			fragment.onInitiateDiscovery();

			manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
				@Override
				public void onSuccess() {
					Toast.makeText(InitiatorActivity.this, R.string.discovery_success, Toast.LENGTH_SHORT).show();
				}

				@Override
				public void onFailure(int reasonCode) {
					Toast.makeText(InitiatorActivity.this, R.string.discovery_failed + reasonCode, Toast.LENGTH_LONG).show();
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
			resetData();
			retryChannel = true;
			manager.initialize(this, getMainLooper(), this);
		} else {
			Toast.makeText(this, R.string.channel_lost_permanently, Toast.LENGTH_LONG).show();
		}
	}

	@Override
	public void showDetails(WifiP2pDevice device) {
		DeviceDetailFragment fragment = (DeviceDetailFragment) getFragmentManager().findFragmentById(R.id.frag_detail);
		fragment.showDetails(device);
	}

	/**
	 * Calls WifiP2pManager to connect to a single device (WifiP2pConfig) on a
	 * given channel.
	 */
	@Override
	public void connect(WifiP2pConfig config) {
		manager.connect(channel, config, new ActionListener() {
			@Override
			public void onSuccess() {
				// WiFiDirectBroadcastReceiver will notify us. Ignore for now.
				// Want to listen and increment total counts of successful connections.
			}

			@Override
			public void onFailure(int reasonCode) {
				Toast.makeText(InitiatorActivity.this, R.string.manager_connect_failed + reasonCode, Toast.LENGTH_LONG).show();
			}
		});
	}

	/**
	 * Calls WifiP2pManager to create a group.
	 */
	@Override
	public void createGroup() {
		manager.createGroup(channel, new ActionListener() {
			@Override
			public void onSuccess() {
				// WiFiDirectBroadcastReceiver will notify us. Ignore for now.
			}

			@Override
			public void onFailure(int reasonCode) {
				Toast.makeText(InitiatorActivity.this, R.string.manager_create_group_failed + reasonCode, Toast.LENGTH_LONG).show();
			}
		});
	}

	@Override
	public void disconnect() {
		manager.removeGroup(channel, new ActionListener() {
			@Override
			public void onSuccess() {
				DeviceDetailFragment fragment = (DeviceDetailFragment) getFragmentManager().findFragmentById(R.id.frag_detail);
				fragment.removeView();
			}

			@Override
			public void onFailure(int reasonCode) {
				Toast.makeText(InitiatorActivity.this, R.string.manager_disconnect_failed + reasonCode, Toast.LENGTH_LONG).show();
			}
		});
	}

	/**
	 * A cancel abort request by user. Disconnect if already connected.
	 * Else, have WifiP2pManager abort the ongoing request.
	 */
	@Override
	public void cancelConnect() {
		if (manager != null) {
			DeviceListFragment fragment = (DeviceListFragment) getFragmentManager().findFragmentById(R.id.frag_list);
			WifiP2pDevice device = fragment.getDevice();
			if (device == null || device.status == WifiP2pDevice.CONNECTED) {
				disconnect();
			} else if (device.status == WifiP2pDevice.AVAILABLE || device.status == WifiP2pDevice.INVITED) {
				manager.cancelConnect(channel, new ActionListener() {
					@Override
					public void onSuccess() {
						Toast.makeText(InitiatorActivity.this, R.string.manager_cancel_connect_success, Toast.LENGTH_SHORT).show();
					}

					@Override
					public void onFailure(int reasonCode) {
						Toast.makeText(InitiatorActivity.this, R.string.manager_cancel_connect_failed + reasonCode, Toast.LENGTH_LONG).show();
					}
				});
			}
		}

	}

	/**
	 * Remove all peers and clear all fields. This is called on in
	 * BroadcastReceiver receiving a state change event.
	 */
	public void resetData() {
		DeviceListFragment fragmentList = (DeviceListFragment) getFragmentManager().findFragmentById(R.id.frag_list);
		DeviceDetailFragment fragmentDetails = (DeviceDetailFragment) getFragmentManager().findFragmentById(R.id.frag_detail);
		if (fragmentList != null) {
			fragmentList.clearPeers();
		}
		if (fragmentDetails != null) {
			fragmentDetails.removeView();
		}
	}
	
	public void setPostNetworkInitializationView() {
		buttonInitializeManet.setVisibility(View.INVISIBLE);
		editMessage.setVisibility(View.VISIBLE);
	}

	private BloomFilter<String> createBf(String[] ids) throws NoSuchAlgorithmException {
		// EXPECTED_INSERTIONS and FALSE_POSITIVE_PROBABILITY are used to calculate
		// optimalNumOfBits and consequently, numHashFunctions. Guava uses built-in
		// BloomFilterStrategies.MURMUR128_MITZ_32 as hashing function.
		BloomFilter<String> bf = BloomFilter.create(new StringFunnel(), 
				Parameters.EXPECTED_INSERTIONS, Parameters.FALSE_POSITIVE_PROBABILITY);
		for(int i = 0; i < ids.length; i++) { 
			bf.put(Utils.hash(ids[i]));
		}

		return bf;
	}

	private BloomFilter<String> createBfp(String[] ids) throws NoSuchAlgorithmException {
		// EXPECTED_INSERTIONS and FALSE_POSITIVE_PROBABILITY are used to calculate
		// optimalNumOfBits and consequently, numHashFunctions. Guava uses built-in
		// BloomFilterStrategies.MURMUR128_MITZ_32 as hashing function.
		BloomFilter<String> bfp = BloomFilter.create(new StringFunnel(), 
				Parameters.EXPECTED_INSERTIONS, Parameters.FALSE_POSITIVE_PROBABILITY);
		for(int i = 0; i < ids.length; i++) { 
			bf.put(Utils.hash(ids[i]));
		}
		bfp.put(Utils.hash(this.getUserId()));

		return bfp;
	}

	/**
	 * Encrypt certificate with AES, using hash of initiator's ID as key.
	 */
	private byte[] encryptCertificate(X509Certificate crt, String initiatorId) throws Exception {
		ByteArrayOutputStream byteStream = new ByteArrayOutputStream(Constants.BYTE_ARRAY_SIZE);
		ObjectOutputStream outputStream = new ObjectOutputStream(new BufferedOutputStream(byteStream));
		outputStream.writeObject(crt);
		outputStream.flush();
		byte[] cf = byteStream.toByteArray();
		byte[] key = Utils.hash(initiatorId).getBytes(Charset.forName("UTF-8"));
		byte[] ecf = AES.encrypt(key, cf);
		return ecf;
	}

	/**
	 * Decrypt certificate, using hash of initiator's ID as key.
	 */
	private X509Certificate decryptCertificate(byte[] encryptedCertificate, byte[] key) throws Exception {
		byte[] cf = AES.decrypt(key, encryptedCertificate);
		ByteArrayInputStream byteInputStream = new ByteArrayInputStream(cf);
		ObjectInputStream inputStream = new ObjectInputStream(byteInputStream);
		X509Certificate snp = (X509Certificate) inputStream.readObject();
		return snp;
	}

	private SetupNetworkPacket createSnp(BloomFilter<String> bf, BloomFilter<String> bfp, byte[] ecf) {
		return new SetupNetworkPacket(bf, bfp, ecf);
	}


	/**
	 * Listens to broadcast messages related to initiator receiving network
	 * packets in DataReceiverService and DataTransferService.
	 */
	public class InitiatorBroadcastReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			// Decrypt certificate, check its validity, and store in target's certificate into own keystore.
			if (intent.getAction().equals(Constants.NETWORK_INITIATOR_GET_SETUP_ENCRYPTED_CERTIFICATE_RECEIVED) ||
					intent.getAction().equals(Constants.NETWORK_INITIATOR_GET_ENCRYPTED_CERTIFICATE_RECEIVED)) {
				byte[] ecf = intent.getExtras().getByteArray(Constants.EXTRAS_ENCRYPTED_CERTIFICATE);
				String senderIp = intent.getExtras().getString(Constants.EXTRAS_SENDER_IP);
				try {
					X509Certificate crt;
					if (intent.getAction().equals(Constants.NETWORK_INITIATOR_GET_SETUP_ENCRYPTED_CERTIFICATE_RECEIVED)) {
						crt = decryptCertificate(ecf, Utils.charToByte(getUserId().toCharArray()));
					}
					else {
						crt = decryptCertificate(ecf, getCurrentSymmetricKey());
					}
					crt.checkValidity();
					String alias = senderIp;
					KeyRepository.storeCertificate(alias, crt, getFilesDir().getAbsolutePath(), getKeystorePassword());
				} catch (CertificateExpiredException e) {
					// Should disconnect the associated peer.
				} catch (CertificateNotYetValidException e) {
					// Should disconnect the associated peer.
				} catch (Exception e) {
					Log.e(TAG, e.getMessage());
				}
			}

			// Decrypted message using the current symmetric key and display to user.
			else if (intent.getAction().equals(Constants.NETWORK_INITIATOR_MESSAGE_LISTENER_RECEIVED)) {
				try {
					byte[] encryptedMessage = intent.getByteArrayExtra(Constants.EXTRAS_ENCRYPTED_MESSAGE);
					byte[] message = AES.decrypt(getCurrentSymmetricKey(), encryptedMessage);
					String displayMessage = Utils.byteToString(message);
					Toast.makeText(InitiatorActivity.this, displayMessage, Toast.LENGTH_SHORT).show();
				} catch (Exception e) {
					Log.e(TAG, e.getMessage());
				}
			}
		}
	}

}
