//Copyright 2011 Google Inc. All Rights Reserved.

package edu.ucla.discoverfriends;

import java.io.IOException;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

import android.app.IntentService;
import android.content.Intent;
import android.util.Log;

/**
 * A service that process each data transfer request i.e Intent by opening a
 * socket connection with the client and sending the data
 */
public class DataTransferService extends IntentService {

	public static final String NETWORK_INITIATOR_SETUP = "edu.ucla.discoverfriends.NETWORK_INITIATOR_SETUP";
	public static final String NETWORK_TARGET_SETUP = "edu.ucla.discoverfriends.NETWORK_TARGET_SETUP";
	public static final String EXTRAS_DATA = "data";
	public static final String EXTRAS_DEVICE_ADDRESS = "go_client";
	public static final String EXTRAS_GROUP_OWNER_PORT = "go_port";

	public DataTransferService(String name) {
		super(name);
	}

	public DataTransferService() {
		super("DataTransferService");
	}

	/*
	 * (non-Javadoc)
	 * @see android.app.IntentService#onHandleIntent(android.content.Intent)
	 */
	@Override
	protected void onHandleIntent(Intent intent) {
		try {
			/**
			 * Create a server socket and wait for client connections. This
			 * call blocks until a connection is accepted from a client
			 */
			ServerSocket serverSocket = new ServerSocket(8988);
			Socket client = serverSocket.accept();

			/**
			 * If this code is reached, a client has connected
			 */
			OutputStream stream = client.getOutputStream();
			ObjectOutput output = new ObjectOutputStream(stream);
			try {
				CustomNetworkPacket data = null;
				if (intent.getAction().equals(NETWORK_INITIATOR_SETUP))
					data = (CustomNetworkPacket) intent.getExtras().getSerializable(EXTRAS_DATA);
				output.writeObject(data);
				output.flush();
			}
			finally{
				output.close();
			}

			Log.d(MainActivity.TAG, "Server: Data written");
			serverSocket.close();
		} catch (IOException e) {
			Log.e(MainActivity.TAG, e.getMessage());
		}
	}
}
