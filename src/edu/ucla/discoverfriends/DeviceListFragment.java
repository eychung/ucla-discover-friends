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

import android.app.ListFragment;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pManager.PeerListListener;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import edu.ucla.discoverfriends.R;

/**
 * A ListFragment that displays available peers on discovery and requests the
 * parent activity to handle user interaction events.
 * 
 * Defines a DeviceActionListener interface and WiFiPeerListAdapter class.
 */
public class DeviceListFragment extends ListFragment implements PeerListListener {
	
	private static final String TAG = DeviceListFragment.class.getName();

	private ProgressDialog progressDialog = null;
	View mContentView = null;
	private WifiP2pDevice device;

	private List<WifiP2pDevice> peers = new ArrayList<WifiP2pDevice>();


	public List<WifiP2pDevice> getPeers() {
		return peers;
	}

	public void setPeers(List<WifiP2pDevice> peers) {
		this.peers = peers;
	}

	public WifiP2pDevice getDevice() {
		return device;
	}


	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		this.setListAdapter(new WiFiPeerListAdapter(getActivity(), R.layout.row_devices, peers));
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		mContentView = inflater.inflate(R.layout.device_list, container);
		return mContentView;
	}
	
	/**
	 * Update DeviceDetailFragment to show details of the clicked device.
	 */
	@Override
	public void onListItemClick(ListView l, View v, int position, long id) {
		WifiP2pDevice device = (WifiP2pDevice) getListAdapter().getItem(position);
		((DeviceActionListener) getActivity()).showDetails(device);
	}

	@Override
	public void onPeersAvailable(WifiP2pDeviceList peerList) {
		if (progressDialog != null && progressDialog.isShowing()) {
			progressDialog.dismiss();
		}
		peers.clear();
		peers.addAll(peerList.getDeviceList());

		((WiFiPeerListAdapter) getListAdapter()).notifyDataSetChanged();
		if (peers.size() == 0) {
			Log.d(TAG, "No devices found.");
		}
	}
	
	// TODO: Make this an async task.
	public void onInitiateDiscovery() {
		if (progressDialog != null && progressDialog.isShowing()) {
			progressDialog.dismiss();
		}
		progressDialog = ProgressDialog.show(getActivity(), "Press back to cancel", "finding peers", true,
				true, new DialogInterface.OnCancelListener() {
			@Override
			public void onCancel(DialogInterface dialog) {
			}
		});
	}
	
	/**
	 * Converts an encoded status code to a human-understandable status.
	 */
	private static String getDeviceStatus(int deviceStatus) {
		Log.d(TAG, "Peer status :" + deviceStatus);
		switch (deviceStatus) {
		case WifiP2pDevice.AVAILABLE:
			return "Available";
		case WifiP2pDevice.INVITED:
			return "Invited";
		case WifiP2pDevice.CONNECTED:
			return "Connected";
		case WifiP2pDevice.FAILED:
			return "Failed";
		case WifiP2pDevice.UNAVAILABLE:
			return "Unavailable";
		default:
			return "Unknown";
		}
	}
	
	/**
	 * Update the name and status UI for this device.
	 */
	public void updateThisDevice(WifiP2pDevice device) {
		this.device = device;
		TextView view = (TextView) mContentView.findViewById(R.id.my_name);
		view.setText(device.deviceName);
		view = (TextView) mContentView.findViewById(R.id.my_status);
		view.setText(getDeviceStatus(device.status));
	}

	/**
	 * Clear the list of peers and call the adapter to update changes. 
	 */
	public void clearPeers() {
		peers.clear();
		((WiFiPeerListAdapter) getListAdapter()).notifyDataSetChanged();
	}

	
	/**
	 * An interface-callback for the activity to listen to fragment interaction events.
	 */
	public interface DeviceActionListener {
		void showDetails(WifiP2pDevice device);
		void cancelConnect();
		void connect(WifiP2pConfig config);
		void createGroup();
		void disconnect();
	}

	/**
	 * Array adapter for ListFragment that maintains WifiP2pDevice list.
	 */
	private class WiFiPeerListAdapter extends ArrayAdapter<WifiP2pDevice> {

		private List<WifiP2pDevice> items;

		public WiFiPeerListAdapter(Context context, int textViewResourceId, List<WifiP2pDevice> objects) {
			super(context, textViewResourceId, objects);
			items = objects;

		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View v = convertView;
			if (v == null) {
				LayoutInflater vi = (LayoutInflater) getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				v = vi.inflate(R.layout.row_devices, null);
			}
			WifiP2pDevice device = items.get(position);
			if (device != null) {
				TextView top = (TextView) v.findViewById(R.id.device_name);
				TextView bottom = (TextView) v.findViewById(R.id.device_details);
				if (top != null) {
					top.setText(device.deviceName);
				}
				if (bottom != null) {
					bottom.setText(getDeviceStatus(device.status));
				}
			}

			return v;
		}
	}
	
}