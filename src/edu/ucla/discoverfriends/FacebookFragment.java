package edu.ucla.discoverfriends;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.app.Fragment;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.facebook.HttpMethod;
import com.facebook.Request;
import com.facebook.Response;
import com.facebook.Session;
import com.facebook.SessionState;
import com.facebook.UiLifecycleHelper;
import com.facebook.android.FacebookError;
import com.facebook.model.GraphObject;
import com.facebook.widget.LoginButton;
import com.google.common.base.Charsets;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnel;
import com.google.common.hash.PrimitiveSink;

import edu.ucla.common.Constants;
import edu.ucla.discoverfriend.R;

public class FacebookFragment extends Fragment {

	private static final String TAG = "FacebookFragment";
	private String uid = "";
	
	private static final int EXPECTED_INSERTIONS = 1000;
	private static final double FALSE_POSITIVE_PROBABILITY = 0.02;

	private UiLifecycleHelper uiHelper;

	private Button queryButton;
	OnQueryClickListener mListener;
	
	private String[] friendId;

	private View mContentView = null;
	
	public void setUid(String uid) {
		this.uid = uid;
	}
	
	public String getUid() {
		return this.uid;
	}
	
	public String[] getFriendId() {
		return friendId;
	}

	public void setFriendId(String[] friendId) {
		this.friendId = friendId;
	}


	class StringFunnel implements Funnel<String> {
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		@Override
		public void funnel(String from, PrimitiveSink into) {
			into.putString(from, Charsets.UTF_8);
		}
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		mContentView = inflater.inflate(R.layout.facebook, null);

		// To allow the fragment to receive the onActivityResult()
		LoginButton authButton = (LoginButton) mContentView.findViewById(R.id.authButton);
		//authButton.setFragment(this);

		queryButton = (Button) mContentView.findViewById(R.id.queryButton);

		queryButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				Session session = Session.getActiveSession();
				Request request = new Request(session, "/me/friends", null, HttpMethod.GET, new Request.Callback() {
					public void onCompleted(Response response) {
						try {
							GraphObject graphObject = response.getGraphObject();
							if (graphObject != null) 
							{
								JSONObject data = graphObject.getInnerJSONObject();
								JSONArray friendsData = data.getJSONArray("data");
								String ids[] = new String[friendsData.length()];
								String names[] = new String[friendsData.length()];

								Log.d(TAG, "" + friendsData.length());

								// EXPECTED_INSERTIONS and FALSE_POSITIVE_PROBABILITY are used to calculate
								// optimalNumOfBits and consequently, numHashFunctions. Guava uses built-in
								// BloomFilterStrategies.MURMUR128_MITZ_32 as hashing function.
								BloomFilter<String> bf = BloomFilter.create(new StringFunnel(), 
										EXPECTED_INSERTIONS, FALSE_POSITIVE_PROBABILITY);

								for(int i = 0; i < friendsData.length(); i++){ 
									ids[i] = friendsData.getJSONObject(i).getString("uid");
									names[i] = friendsData.getJSONObject(i).getString("name");
									bf.put(ids[i]);
								}
								Log.d(TAG, "" + ids.length);
								Log.d(TAG, bf.toString());
								Log.d(TAG, getUid());
								
								friendId = ids;

								BloomFilter<String> bfc = bf.copy();
								bfc.put(getUid());
								
								FileInputStream is = new FileInputStream(getActivity().getFilesDir().getAbsolutePath() +
										Constants.KEYSTORE_NAME);
							    KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
							    keystore.load(is, "password".toCharArray());
							    X509Certificate crt = (X509Certificate) keystore.getCertificate(Constants.USER_CERTIFICATE_ALIAS);
							    is.close();
							    
							    Log.i(TAG, "BF: " + bf.toString());
							    Log.i(TAG, "BFC: " + bfc.toString());
							    Log.i(TAG, "Certificate: " + crt.toString());
								
								mListener.onQueryClick(bf, bfc, crt);

							}

						} catch (FacebookError e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						} catch (JSONException e) {
							Log.e(TAG, "JSON parsing error: " + e.getMessage());
						} catch (KeyStoreException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						} catch (FileNotFoundException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						} catch (NoSuchAlgorithmException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						} catch (CertificateException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}

						Log.i(TAG, "Result: " + response.toString());
					}                  
				}); 
				Request.executeBatchAsync(request);
			}
		});

		return mContentView;
	}

	private void onSessionStateChange(Session session, SessionState state, Exception exception) {
		if (state.isOpened()) {
			Log.i(TAG, "Logged in...");
			queryButton.setVisibility(View.VISIBLE);
		} else if (state.isClosed()) {
			Log.i(TAG, "Logged out...");
			queryButton.setVisibility(View.INVISIBLE);
		}
	}

	private Session.StatusCallback callback = new Session.StatusCallback() {
		@Override
		public void call(Session session, SessionState state, Exception exception) {
			onSessionStateChange(session, state, exception);
		}
	};

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		uiHelper = new UiLifecycleHelper(getActivity(), callback);
		uiHelper.onCreate(savedInstanceState);
	}

	@Override
	public void onResume() {
		super.onResume();

		// For scenarios where the main activity is launched and user
		// session is not null, the session state change notification
		// may not be triggered. Trigger it if it's open/closed.
		Session session = Session.getActiveSession();
		if (session != null &&
				(session.isOpened() || session.isClosed()) ) {
			onSessionStateChange(session, session.getState(), null);
		}
		uiHelper.onResume();
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		uiHelper.onActivityResult(requestCode, resultCode, data);
	}

	@Override
	public void onPause() {
		super.onPause();
		uiHelper.onPause();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		uiHelper.onDestroy();
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		uiHelper.onSaveInstanceState(outState);
	}

	public interface OnQueryClickListener {
		public void onQueryClick(BloomFilter<String> bf, BloomFilter<String> bfc, X509Certificate crt);
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		try {
			mListener = (OnQueryClickListener) activity;
		} catch (ClassCastException e) {
			throw new ClassCastException(activity.toString() + " must implement OnQueryClickListener");
		}
		
	}

}
