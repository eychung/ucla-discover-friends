package edu.ucla.discoverfriends;

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

import com.facebook.HttpMethod;
import com.facebook.Request;
import com.facebook.Response;
import com.facebook.Session;
import com.facebook.SessionState;
import com.facebook.UiLifecycleHelper;
import com.facebook.android.FacebookError;
import com.facebook.model.GraphObject;
import com.google.common.base.Charsets;
import com.google.common.hash.Funnel;
import com.google.common.hash.PrimitiveSink;

public class FacebookFragment extends Fragment {

	private static final String TAG = FacebookFragment.class.getName();

	private FacebookFragmentListener mListener;
	
	private UiLifecycleHelper uiHelper;

	private View mContentView = null;

	class StringFunnel implements Funnel<String> {
		private static final long serialVersionUID = 1L;

		@Override
		public void funnel(String from, PrimitiveSink into) {
			into.putString(from, Charsets.UTF_8);
		}
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		mContentView = inflater.inflate(R.layout.facebook, container);

		// To allow the fragment to receive the onActivityResult()
		//LoginButton authButton = (LoginButton) mContentView.findViewById(R.id.authButton);
		//authButton.setFragment(this);

		return mContentView;
	}

	private void onSessionStateChange(Session session, SessionState state, Exception exception) {
		if (state.isOpened()) {
			Log.i(TAG, "Logged in...");
		} else if (state.isClosed()) {
			Log.i(TAG, "Logged out...");
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
	
	public void getFriendsId() {
		Session session = Session.getActiveSession();
		Request request = new Request(session, "/me/friends", null, HttpMethod.GET, new Request.Callback() {
			public void onCompleted(Response response) {
				try {
					GraphObject graphObject = response.getGraphObject();
					if (graphObject != null) {
						JSONObject data = graphObject.getInnerJSONObject();
						JSONArray friendsData = data.getJSONArray("data");
						String ids[] = new String[friendsData.length()];
						Log.d(TAG, "Number of retrieved friends: "  + friendsData.length());

						for(int i = 0; i < friendsData.length(); i++)
							ids[i] = friendsData.getJSONObject(i).getString("uid");

						mListener.saveFriendsId(ids);
					}
				} catch (FacebookError e) {
					Log.e(TAG, e.getMessage());
				} catch (JSONException e) {
					Log.e(TAG, "JSON parsing error: " + e.getMessage());
				} catch (Exception e) {
					Log.e(TAG, e.getMessage());
				}

				Log.i(TAG, "Result: " + response.toString());
			}                  
		}); 
		Request.executeBatchAsync(request);
	}

	public interface FacebookFragmentListener {
		public void saveFriendsId(String[] friendsId);
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		try {
			mListener = (FacebookFragmentListener) activity;
		} catch (ClassCastException e) {
			throw new ClassCastException(activity.toString() + " must implement " + FacebookFragmentListener.class.getName());
		}

	}

}
