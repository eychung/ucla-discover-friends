/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package edu.ucla.discoverfriends;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.net.wifi.p2p.WifiP2pManager.PeerListListener;
import android.util.Log;

/**
 * A BroadcastReceiver that notifies of important Wi-Fi P2P events.
 * InitiatorActivity uses this class to update changes to peers.
 * TargetActivity only uses this class to detect Wi-Fi status.
 */
public class WiFiDirectBroadcastReceiver extends BroadcastReceiver {

	private static final String TAG = WiFiDirectBroadcastReceiver.class.getName();;

	private WifiP2pManager manager;
	private Channel channel;
	private InitiatorActivity initiatorActivity;
	private TargetActivity targetActivity;

	/**
	 * @param manager WifiP2pManager system service
	 * @param channel Wi-Fii P2P channel
	 * @param activity activity associated with the receiver
	 */
	public WiFiDirectBroadcastReceiver(WifiP2pManager manager, Channel channel, InitiatorActivity activity) {
		super();
		this.manager = manager;
		this.channel = channel;
		this.initiatorActivity = activity;
	}

	public WiFiDirectBroadcastReceiver(WifiP2pManager manager, Channel channel, TargetActivity activity) {
		super();
		this.manager = manager;
		this.channel = channel;
		this.targetActivity = activity;
	}

	/*
	 * (non-Javadoc)
	 * @see android.content.BroadcastReceiver#onReceive(android.content.Context,
	 * android.content.Intent)
	 */
	@Override
	public void onReceive(Context context, Intent intent) {
		String action = intent.getAction();

		// Check to see if Wi-Fi P2P is enabled or disabled.
		if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
			int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
			if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
				initiatorActivity.setIsWifiP2pEnabled(true);
				targetActivity.setIsWifiP2pEnabled(true);
			} else {
				initiatorActivity.setIsWifiP2pEnabled(false);
				targetActivity.setIsWifiP2pEnabled(false);
				initiatorActivity.resetData();
			}
			Log.d(TAG, "P2P state is changed to: " + state);
		}

		/*
		 *  Request available peers from the WifiP2pManager. This is an
		 *  asynchronous call and the calling activity is notified with a
		 *  callback on PeerListListener.onPeersAvailable().
		 */
		else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
			if (manager != null) {
				manager.requestPeers(channel, (PeerListListener) initiatorActivity.getFragmentManager().findFragmentById(R.id.frag_list));
				Log.d(TAG, "Available peer list has changed and fetched new list of peers.");
			}
		}

		else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
			if (manager == null) {
				return;
			}

			NetworkInfo networkInfo = (NetworkInfo) intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);

			if (networkInfo.isConnected()) {
				manager.requestGroupInfo(channel, targetActivity);
			} else {
				initiatorActivity.resetData();
			}
		}

		// A change in a device's status has been detected so update its info in list.
		else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {
			DeviceListFragment fragment = (DeviceListFragment) initiatorActivity.getFragmentManager().findFragmentById(R.id.frag_list);
			fragment.updateThisDevice((WifiP2pDevice) intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE));
		}
	}
}
