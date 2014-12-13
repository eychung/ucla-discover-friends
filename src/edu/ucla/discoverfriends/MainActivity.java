package edu.ucla.discoverfriends;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.facebook.Request;
import com.facebook.Response;
import com.facebook.Session;
import com.facebook.SessionState;
import com.facebook.model.GraphUser;

import edu.ucla.common.Constants;
import edu.ucla.common.Utils;
import edu.ucla.discoverfriends.FacebookFragment.FacebookFragmentListener;
import edu.ucla.encryption.Certificate;
import edu.ucla.encryption.KeyRepository;

/**
 * MainActivity is the home screen activity, allowing users to choose between
 * Initiator and Target role. A keystore is created with a newly generated
 * certificate and is stored using the user's ID as the alias.
 * 
 * Connects to Facebook to retrieve user ID and list of friends' IDs. Also,
 * creates a keystore with self-signed certificate.
 * 
 * Sends intent with (String userId, String[] friendsId, X509Certificate crt,
 * String keystorePassword). 
 */
public class MainActivity extends Activity implements FacebookFragmentListener {

	private static final String TAG = MainActivity.class.getName();

	// UI
	@SuppressWarnings("unused")
	private TextView textDebug;
	private Button buttonInitiator, buttonTarget;

	// Intent contents to pass to next activity
	private String userId = "";
	private String[] friendsId = null;
	private X509Certificate crt = null;
	private String keystorePassword = "";
	private PrivateKey privateKey = null;


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
	

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.home);

		textDebug = (TextView) findViewById(R.id.txt_debug);
		buttonInitiator = (Button) findViewById(R.id.btn_initiator);
		buttonTarget = (Button) findViewById(R.id.btn_target);
		
		buttonInitiator.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent intent = new Intent(MainActivity.this, InitiatorActivity.class);
				intent.putExtra(Constants.EXTRAS_USER_ID, getUserId());
				intent.putExtra(Constants.EXTRAS_FRIENDS_ID, getFriendsId());
				intent.putExtra(Constants.EXTRAS_CERTIFICATE, getCrt());
				intent.putExtra(Constants.EXTRAS_KEYSTORE_PASSWORD, getKeystorePassword());
				intent.putExtra(Constants.EXTRAS_PRIVATE_KEY, getPrivateKey());
				startActivity(intent);
			}
		});
		
		buttonTarget.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent intent = new Intent(MainActivity.this, TargetActivity.class);
				intent.putExtra(Constants.EXTRAS_USER_ID, getUserId());
				intent.putExtra(Constants.EXTRAS_FRIENDS_ID, getFriendsId());
				intent.putExtra(Constants.EXTRAS_CERTIFICATE, getCrt());
				intent.putExtra(Constants.EXTRAS_KEYSTORE_PASSWORD, getKeystorePassword());
				intent.putExtra(Constants.EXTRAS_PRIVATE_KEY, getPrivateKey());
				startActivity(intent);
			}
		});

		// Open Facebook session and retrieves user's list of friends' IDs.
		Session.openActiveSession(this, true, new Session.StatusCallback() {
			@Override
			public void call(Session session, SessionState state, Exception exception) {
				if (session.isOpened()) {
					Request.newMeRequest(session, new Request.GraphUserCallback() {
						@Override
						public void onCompleted(GraphUser user, Response response) {
							if (user != null) {
								// Sets Facebook user's ID.
								setUserId(user.getId());
								Log.d(TAG, "User ID set as " + getUserId());

								// Sets list of Facebook friends' IDs using FacebookFragmentListener.
								FacebookFragment fragment = (FacebookFragment) getFragmentManager().findFragmentById(R.id.frag_facebook);
								if (fragment != null) {
									fragment.getFriendsId();
								}
							}
						}
					}).executeAsync();
				}
			}
		});

		// Create keystore file and save it into device's internal storage.
		// Then, create user's certificate and store it into the keystore.
		try {
			String keystorePassword = Utils.generateRandomKeystorePassword();
			this.setKeystorePassword(keystorePassword);
			KeyRepository.createUserKeyStore(getFilesDir().getAbsolutePath(), this.getKeystorePassword());
			PrivateKey pkey = Certificate.createUserAndStoreCertificate(
					this.getUserId(), getFilesDir().getAbsolutePath(), this.getKeystorePassword());
			this.setPrivateKey(pkey);
			Log.i(TAG, "Created local keystore.");
		} catch (GeneralSecurityException e) {
			Log.e(TAG, e.getMessage());
		} catch (IOException e) {
			Log.e(TAG, e.getMessage());
		}

		try {
			this.setCrt(this.getOwnCertificate());
		} catch (CertificateException e) {
			Log.e(TAG, e.getMessage());
		} catch (IOException e) {
			Log.e(TAG, e.getMessage());
		} catch (KeyStoreException e) {
			Log.e(TAG, e.getMessage());
		} catch (NoSuchAlgorithmException e) {
			Log.e(TAG, e.getMessage());
		}
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		Session.getActiveSession().onActivityResult(this, requestCode, resultCode, data);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.home, menu);
		return true;
	}

	@Override
	protected void onResume() {
		super.onResume();
	}

	@Override
	protected void onPause() {
		super.onPause();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
	}

	@Override
	public void saveFriendsId(String[] friendsId) {
		this.setFriendsId(friendsId);
	}

	private X509Certificate getOwnCertificate() throws CertificateException, IOException, KeyStoreException, NoSuchAlgorithmException {
		FileInputStream is = new FileInputStream(getFilesDir().getAbsolutePath() +
				Constants.KEYSTORE_NAME);
		KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
		keystore.load(is, "password".toCharArray());
		is.close();
		return (X509Certificate) keystore.getCertificate(this.getUserId());
	}

}
