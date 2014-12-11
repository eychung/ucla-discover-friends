package edu.ucla.discoverfriends;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.Charset;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.List;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.ActionListener;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.net.wifi.p2p.WifiP2pManager.ChannelListener;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
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

public class InitiatorActivity extends Activity implements ChannelListener, DeviceActionListener {

	public static final String TAG = "InitiatorActivity";

	// UI
	ProgressDialog progressDialog = null;

	private WifiP2pManager manager;
	private boolean isWifiP2pEnabled = false;
	private boolean retryChannel = false;

	private final IntentFilter intentFilter = new IntentFilter();
	private Channel channel;
	private BroadcastReceiver receiver = null;

	private String userId = "";
	private String[] friendsId = null;
	private X509Certificate crt = null;
	private BloomFilter<String> bf = null;
	private BloomFilter<String> bfp = null;
	private byte[] ecf = null;
	private SetupNetworkPacket snp = null;


	public WifiP2pManager getManager() {
		return manager;
	}

	public Channel getChannel() {
		return channel;
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

		Intent intent = getIntent();
		this.setUserId(intent.getExtras().getString(Constants.EXTRAS_USER_ID));
		this.setFriendsId(intent.getExtras().getStringArray(Constants.EXTRAS_FRIENDS_ID));
		this.setCrt((X509Certificate) intent.getExtras().getSerializable(Constants.EXTRAS_CERTIFICATE));

		try {
			this.setBf(this.createBf(this.getFriendsId()));
			this.setBfp(this.createBfp(this.getFriendsId()));
			this.setEcf(this.encryptCertificate(this.getCrt()));
			this.setSnp(this.createSnp(this.getBf(), this.getBfp(), this.getEcf()));
		} catch (NoSuchAlgorithmException e) {
			Log.e(TAG, e.getMessage());
		} catch (Exception e) {
			Log.e(TAG, e.getMessage());
		}

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

		findViewById(R.id.btn_create_group).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				createGroup();

				DeviceListFragment fragmentDetails = (DeviceListFragment) getFragmentManager().findFragmentById(R.id.frag_list);
				List<WifiP2pDevice> peers = fragmentDetails.getPeers();

				Log.d(TAG, "Got here.");
				BloomFilter<String> bf = BloomFilter.create(new StringFunnel(), 1000, 0.02);
				Intent serviceIntent = new Intent(InitiatorActivity.this, DataTransferService.class);
				Bundle extras = new Bundle();
				extras.putSerializable(Constants.EXTRAS_SNP, bf);
				serviceIntent.setAction(Constants.NETWORK_INITIATOR_SETUP);
				serviceIntent.putExtras(extras);
				startService(serviceIntent);
				Log.d(TAG, "Done.");

				// TODO: Multithread connections for multiple connected devices.
				// TODO: Should be done as an async task... or at least show when a client is connected.
				/*if (!peers.isEmpty()) {
					WifiP2pConfig config;

					for (int i=0; i<peers.size(); i++) {
						config = new WifiP2pConfig();
						config.deviceAddress = peers.get(i).deviceAddress;
						config.wps.setup = WpsInfo.PBC;
						connect(config);

					}

					Log.d(TAG, "Got here.");
					Intent serviceIntent = new Intent(MainActivity.this, DataTransferService.class);
					Bundle extras = new Bundle();
					extras.putSerializable(Constants.EXTRAS_SNP, getSnp());
					serviceIntent.setAction(Constants.NETWORK_INITIATOR_SETUP);
					serviceIntent.putExtras(extras);
					startService(serviceIntent);
				}
				else {
					textView1.setText("Peer list is empty so no connections established.");
				}*/

			}
		});


	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.initiator, menu);
		return true;
	}

	@Override
	protected void onResume() {
		super.onResume();
		registerReceiver(receiver, intentFilter);
	}

	@Override
	protected void onPause() {
		super.onPause();
		unregisterReceiver(receiver);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
	}

	/**
	 * Remove all peers and clear all fields. This is called on
	 * BroadcastReceiver receiving a state change event.
	 */
	public void resetData() {
		DeviceListFragment fragmentList = (DeviceListFragment) getFragmentManager().findFragmentById(R.id.frag_list);
		DeviceDetailFragment fragmentDetails = (DeviceDetailFragment) getFragmentManager().findFragmentById(R.id.frag_detail);
		if (fragmentList != null) {
			fragmentList.clearPeers();
		}
		if (fragmentDetails != null) {
			fragmentDetails.resetViews();
		}
	}

	/*
	 * (non-Javadoc)
	 * @see android.app.Activity#onOptionsItemSelected(android.view.MenuItem)
	 */
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
				Log.e(TAG, "channel or manager is null");
			}
			return true;

		case R.id.atn_direct_discover:
			if (!isWifiP2pEnabled) {
				Toast.makeText(InitiatorActivity.this, R.string.p2p_off_warning, Toast.LENGTH_SHORT).show();
				return true;
			}

			final DeviceListFragment fragment = (DeviceListFragment) getFragmentManager().findFragmentById(R.id.frag_list);
			fragment.onInitiateDiscovery();

			manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {

				@Override
				public void onSuccess() {
					Toast.makeText(InitiatorActivity.this, "Discovery Initiated", Toast.LENGTH_SHORT).show();
				}

				@Override
				public void onFailure(int reasonCode) {
					Toast.makeText(InitiatorActivity.this, "Discovery Failed : " + reasonCode, Toast.LENGTH_SHORT).show();
				}
			});
			return true;

		default:
			return super.onOptionsItemSelected(item);
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
			public void onFailure(int reason) {
				Toast.makeText(InitiatorActivity.this, "Connect failed. Retry.",
						Toast.LENGTH_SHORT).show();
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
			public void onFailure(int reason) {
				Toast.makeText(InitiatorActivity.this, "Create group failed. Retry.",
						Toast.LENGTH_SHORT).show();
			}
		});
	}

	@Override
	public void disconnect() {
		final DeviceDetailFragment fragment = (DeviceDetailFragment) getFragmentManager().findFragmentById(R.id.frag_detail);
		fragment.resetViews();
		manager.removeGroup(channel, new ActionListener() {

			@Override
			public void onFailure(int reasonCode) {
				Log.d(TAG, "Disconnect failed. Reason :" + reasonCode);

			}

			@Override
			public void onSuccess() {
				fragment.getView().setVisibility(View.GONE);
			}

		});
	}

	@Override
	public void onChannelDisconnected() {
		// we will try once more
		if (manager != null && !retryChannel) {
			Toast.makeText(this, "Channel lost. Trying again", Toast.LENGTH_LONG).show();
			resetData();
			retryChannel = true;
			manager.initialize(this, getMainLooper(), this);
		} else {
			Toast.makeText(this,
					"Severe! Channel is probably lost premanently. Try Disable/Re-Enable P2P.", Toast.LENGTH_LONG).show();
		}
	}

	@Override
	public void cancelDisconnect() {

		/*
		 * A cancel abort request by user. Disconnect i.e. removeGroup if
		 * already connected. Else, request WifiP2pManager to abort the ongoing
		 * request
		 */
		if (manager != null) {
			final DeviceListFragment fragment = (DeviceListFragment) getFragmentManager().findFragmentById(R.id.frag_list);
			if (fragment.getDevice() == null
					|| fragment.getDevice().status == WifiP2pDevice.CONNECTED) {
				disconnect();
			} else if (fragment.getDevice().status == WifiP2pDevice.AVAILABLE
					|| fragment.getDevice().status == WifiP2pDevice.INVITED) {

				manager.cancelConnect(channel, new ActionListener() {

					@Override
					public void onSuccess() {
						Toast.makeText(InitiatorActivity.this, "Aborting connection",
								Toast.LENGTH_SHORT).show();
					}

					@Override
					public void onFailure(int reasonCode) {
						Toast.makeText(InitiatorActivity.this,
								"Connect abort request failed. Reason Code: " + reasonCode,
								Toast.LENGTH_SHORT).show();
					}
				});
			}
		}

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

	private byte[] encryptCertificate(X509Certificate crt) throws Exception {
		// Encrypt certificate with AES, using hash of initiator's ID as hash.
		ByteArrayOutputStream byteStream = new ByteArrayOutputStream(Constants.BYTE_ARRAY_SIZE);
		ObjectOutputStream outputStream = new ObjectOutputStream(new BufferedOutputStream(byteStream));
		outputStream.writeObject(crt);
		outputStream.flush();
		byte[] cf = byteStream.toByteArray();
		byte[] key = Utils.hash(this.getUserId()).getBytes(Charset.forName("UTF-8"));
		byte[] ecf = AES.encrypt(key, cf);
		return ecf;
	}

	private SetupNetworkPacket createSnp(BloomFilter<String> bf, BloomFilter<String> bfp, byte[] ecf) {
		return new SetupNetworkPacket(bf, bfp, ecf);
	}
}
