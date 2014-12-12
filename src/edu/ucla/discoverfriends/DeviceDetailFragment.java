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

import android.app.Fragment;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * A fragment that manages a particular peer and allows interaction with device
 * such as setting up network connection and transferring data.
 */
public class DeviceDetailFragment extends Fragment {
	
	private static final String TAG = DeviceDetailFragment.class.getName();

	// UI
	private View mContentView = null;
	
	public WifiP2pDevice device;
	public WifiP2pInfo info;
	
	SetupNetworkPacket receivedCNP = null;
	
	protected static final int CHOOSE_FILE_RESULT_CODE = 20;

	
	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		mContentView = inflater.inflate(R.layout.device_detail, container);
		return mContentView;
	}

	/**
	 * Updates the UI with device address and info.
	 */
	public void showDetails(WifiP2pDevice device) {
		this.device = device;
		this.getView().setVisibility(View.VISIBLE);
		TextView view = (TextView) mContentView.findViewById(R.id.device_address);
		view.setText(device.deviceAddress);
		view = (TextView) mContentView.findViewById(R.id.device_info);
		view.setText(device.toString());
		Log.i(TAG, "Showing details of device: " + device.deviceName);
	}

	/**
	 * Removes the view of this fragment.
	 */
	public void removeView() {
		this.getView().setVisibility(View.GONE);
	}

}