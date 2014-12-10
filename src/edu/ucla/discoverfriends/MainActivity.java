package edu.ucla.discoverfriends;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.Signature;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.ActionListener;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.net.wifi.p2p.WifiP2pManager.ChannelListener;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Base64;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.facebook.Request;
import com.facebook.Response;
import com.facebook.Session;
import com.facebook.SessionState;
import com.facebook.model.GraphUser;
import com.google.common.hash.BloomFilter;

import edu.ucla.discoverfriend.R;
import edu.ucla.discoverfriends.DeviceListFragment.DeviceActionListener;
import edu.ucla.discoverfriends.FacebookFragment.OnQueryClickListener;
import edu.ucla.encryption.KeyRepository;

/**
 * 
 * @author 530un
 * https://code.google.com/p/boxeeremote/wiki/AndroidUDP
 * https://code.google.com/p/boxeeremote/source/browse/trunk/Boxee+Remote/src/com/andrewchatham/Discoverer.java?spec=svn28&r=28
 * http://stackoverflow.com/questions/11089232/udp-packets-via-wifi-direct-never-arrive
 * http://stackoverflow.com/questions/3997459/send-and-receive-serialize-object-on-udp
 * http://michieldemey.be/blog/network-discovery-using-udp-broadcast/
 * http://www.javaworld.com/article/2077539/learn-java/java-tip-40--object-transport-via-datagram-packets.html
 * http://docs.oracle.com/javase/tutorial/networking/datagrams/clientServer.html
 */
public class MainActivity extends Activity implements ChannelListener, DeviceActionListener, OnQueryClickListener {

	public static final String TAG = "MainActivity";

	private TextView textView1;

	private WifiP2pManager manager;
	private boolean isWifiP2pEnabled = false;
	private boolean retryChannel = false;

	private final IntentFilter intentFilter = new IntentFilter();
	private Channel channel;
	private BroadcastReceiver receiver = null;

	ProgressDialog progressDialog = null;

	SetupNetworkPacket cnp = null;

	public WifiP2pManager getManager() {
		return manager;
	}

	public Channel getChannel() {
		return channel;
	}

	public SetupNetworkPacket getCnp() {
		return cnp;
	}

	/**
	 * @param isWifiP2pEnabled the isWifiP2pEnabled to set
	 */
	public void setIsWifiP2pEnabled(boolean isWifiP2pEnabled) {
		this.isWifiP2pEnabled = isWifiP2pEnabled;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

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

		textView1 = (TextView) findViewById(R.id.textView1);

		Session.openActiveSession(this, true, new Session.StatusCallback() {

			// callback when session changes state
			@Override
			public void call(Session session, SessionState state, Exception exception) {
				if (session.isOpened()) {

					// make request to the /me API
					Request.newMeRequest(session, new Request.GraphUserCallback() {

						// callback after Graph API response with user object
						@Override
						public void onCompleted(GraphUser user, Response response) {
							if (user != null) {
								textView1.setText(user.getId());
								FacebookFragment fragment = (FacebookFragment) getFragmentManager().findFragmentById(R.id.frag_facebook);

								if (fragment != null) {
									fragment.setUid(user.getId());
								}

							}
						}
					}).executeAsync();
				}
			}
		});

		findViewById(R.id.btn_create_group).setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {

				createGroup();
				textView1.setText("Created group owner as me.");

				DeviceListFragment fragmentDetails = (DeviceListFragment) getFragmentManager().findFragmentById(R.id.frag_list);
				List<WifiP2pDevice> peers = fragmentDetails.getPeers();

				// TODO: Multithread connections for multiple connected devices.
				if (!peers.isEmpty()) {
					WifiP2pConfig config;

					for (int i=0; i<peers.size(); i++) {
						config = new WifiP2pConfig();
						config.deviceAddress = peers.get(i).deviceAddress;
						config.wps.setup = WpsInfo.PBC;
						if (progressDialog != null && progressDialog.isShowing()) {
							progressDialog.dismiss();
						}

						connect(config);

					}
				}
				else {
					textView1.setText("Peer list is empty so no connections established.");
				}

			}
		});


		Log.i(TAG, "Checking shared preferences.");
		//SharedPreferences prefs = this.getSharedPreferences(TAG, Context.MODE_PRIVATE);
		//if (!prefs.getBoolean("firstTime", false)) {
		Log.i(TAG, "Attempting to create local keystore.");

		try {
			KeyRepository.createUserKeyStore(getFilesDir().getAbsolutePath());
			Log.i(TAG, "Created local keystore.");
		} catch (GeneralSecurityException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			Log.e(TAG, "File write failed: " + e.toString());
		}

		// mark first time has runned.
		//SharedPreferences.Editor editor = prefs.edit();
		//editor.putBoolean("firstTime", true);
		//editor.commit();
		//}

		try {
			PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(),
					PackageManager.GET_SIGNATURES);
			for (Signature signature : packageInfo.signatures) {
				MessageDigest md = MessageDigest.getInstance("SHA");
				md.update(signature.toByteArray());
				Log.d("KeyHash:", Base64.encodeToString(md.digest(), Base64.DEFAULT));
			}
		}
		catch (NameNotFoundException e1) {
			Log.e("Name not found", e1.toString());
		}
		catch (NoSuchAlgorithmException e) {
			Log.e("No such an algorithm", e.toString());
		}
		catch (Exception e){
			Log.e("Exception", e.toString());
		}

	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		Session.getActiveSession().onActivityResult(this, requestCode, resultCode, data);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.action_items, menu);
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

				startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS));
			} else {
				Log.e(TAG, "channel or manager is null");
			}
			return true;

		case R.id.atn_direct_discover:
			if (!isWifiP2pEnabled) {
				Toast.makeText(MainActivity.this, R.string.p2p_off_warning, Toast.LENGTH_SHORT).show();
				return true;
			}

			final DeviceListFragment fragment = (DeviceListFragment) getFragmentManager().findFragmentById(R.id.frag_list);
			fragment.onInitiateDiscovery();

			manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {

				@Override
				public void onSuccess() {
					Toast.makeText(MainActivity.this, "Discovery Initiated", Toast.LENGTH_SHORT).show();
				}

				@Override
				public void onFailure(int reasonCode) {
					Toast.makeText(MainActivity.this, "Discovery Failed : " + reasonCode, Toast.LENGTH_SHORT).show();
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

	@Override
	public void connect(WifiP2pConfig config) {
		manager.connect(channel, config, new ActionListener() {

			@Override
			public void onSuccess() {
				// WiFiDirectBroadcastReceiver will notify us. Ignore for now.
			}

			@Override
			public void onFailure(int reason) {
				Toast.makeText(MainActivity.this, "Connect failed. Retry.",
						Toast.LENGTH_SHORT).show();
			}
		});
	}

	@Override
	public void createGroup() {
		manager.createGroup(channel, new ActionListener() {

			@Override
			public void onSuccess() {
				// WiFiDirectBroadcastReceiver will notify us. Ignore for now.
			}

			@Override
			public void onFailure(int reason) {
				Toast.makeText(MainActivity.this, "Create group failed. Retry.",
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
						Toast.makeText(MainActivity.this, "Aborting connection",
								Toast.LENGTH_SHORT).show();
					}

					@Override
					public void onFailure(int reasonCode) {
						Toast.makeText(MainActivity.this,
								"Connect abort request failed. Reason Code: " + reasonCode,
								Toast.LENGTH_SHORT).show();
					}
				});
			}
		}

	}

	@Override
	public void onQueryClick(BloomFilter<String> bf, BloomFilter<String> bfp, byte[] ecf) {
		cnp = new SetupNetworkPacket(bf, bfp, ecf);
		textView1.setText("Bloom Filters generated from query.");
	}


}
