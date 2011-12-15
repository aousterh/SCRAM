package org.haggle.Micropublisher;

import org.haggle.Attribute;
import org.haggle.DataObject;
import org.haggle.Handle;
import org.haggle.LaunchCallback;
import org.haggle.Node;

import android.app.Application;
import android.content.Intent;
import android.util.Log;

public class Micropublisher extends Application implements org.haggle.EventHandler {
	public static final String LOG_TAG = "Micropublisher";
	
	public static final int STATUS_OK = 0;
	public static final int STATUS_ERROR = -1;
	public static final int STATUS_REGISTRATION_FAILED = -2;
	public static final int STATUS_SPAWN_DAEMON_FAILED = -3;
	
	static final int PUBLISH_MESSAGE_REQUEST = 0;
	static final int MESSAGE_LENGTH_IN_BYTES = 140;
	
	private MicropublisherView mv = null;
	private org.haggle.Handle hh = null;
	private String publicKeyString = null;
	private String privateKeyString = null;
	private boolean keysSet = false;
	
	@Override
    public void onCreate() {
        super.onCreate();
        
        // start data service
        Intent intent = new Intent(this, UserDataService.class);
        startService(intent);
        Log.d(Micropublisher.LOG_TAG, "Micropublisher:onCreate()");
    }
	
	public Handle getHaggleHandle() {
		return hh;
	}
	
	public void setMicropublisherView(MicropublisherView mv) {
		Log.d(Micropublisher.LOG_TAG, "Micropublisher: Setting pv");
		this.mv = mv;
	}
	
	public void setKeys(String publicKeyString, String privateKeyString) {
		this.publicKeyString = publicKeyString;
		this.privateKeyString = privateKeyString;
		this.keysSet = true;
	}
	
	public boolean keysSet() {
		return keysSet;
	}
	
	public String getPublicKeyString() {
		return publicKeyString;
	}
	
	public String getPrivateKeyString() {
		return privateKeyString;
	}
	
	public int initHaggle() {
		if (hh != null)
			return STATUS_OK;
		
		/* Start up Haggle */
		int status = Handle.getDaemonStatus();
		
		if (status == Handle.HAGGLE_DAEMON_NOT_RUNNING || status == Handle.HAGGLE_DAEMON_CRASHED) {
			Log.d(Micropublisher.LOG_TAG, "Trying to spawn Haggle daemon");

			if (!Handle.spawnDaemon(new LaunchCallback() {
				
				public int callback(long milliseconds) {

					Log.d(Micropublisher.LOG_TAG, "Spawning milliseconds..." + milliseconds);

					if (milliseconds == 10000) {
						Log.d(Micropublisher.LOG_TAG, "Spawning failed, giving up");

						return -1;
					}
					return 0;
				}
			})) {
				Log.d(Micropublisher.LOG_TAG, "Spawning failed...");
				return STATUS_SPAWN_DAEMON_FAILED;
			}
		}
		long pid = Handle.getDaemonPid();

		Log.d(Micropublisher.LOG_TAG, "Haggle daemon pid is " + pid);

		/* Try to get a handle to a running Haggle instance */
		int tries = 1;
		while (tries > 0) {
			try {
				hh = new Handle("Micropublisher");

			} catch (Handle.RegistrationFailedException e) {
				Log.e(Micropublisher.LOG_TAG, "Registration failed : " + e.getMessage());

				if (e.getError() == Handle.HAGGLE_BUSY_ERROR) {
					Handle.unregister("Micropublisher");
					continue;
				} else if (--tries > 0) 
					continue;

				Log.e(Micropublisher.LOG_TAG, "Registration failed, giving up");
				return STATUS_REGISTRATION_FAILED;
			}
			break;
		}

		/* Register for events */
		hh.registerEventInterest(EVENT_NEIGHBOR_UPDATE, this);
		hh.registerEventInterest(EVENT_NEW_DATAOBJECT, this);
		hh.registerEventInterest(EVENT_INTEREST_LIST_UPDATE, this);
		hh.registerEventInterest(EVENT_HAGGLE_SHUTDOWN, this);
		
		hh.eventLoopRunAsync(this);
		
		Attribute attr = new Attribute("Message", "news", 3);
		hh.registerInterest(attr);
		
		return STATUS_OK;
	}
	

	//@Override
	public void onNeighborUpdate(Node[] neighbors) {
		Log.d(Micropublisher.LOG_TAG, "Got neighbor update, thread id=" + Thread.currentThread().getId());
		// TODO Auto-generated method stub
	}

	public void shutdownHaggle() {
		hh.shutdown();
	}
	
	//@Override
	public void onNewDataObject(DataObject dObj) {
		Log.d(Micropublisher.LOG_TAG, "Got new data object, thread id=" + Thread.currentThread().getId());

		if (dObj.getAttribute("Message", 0) != null) {
			Log.d(Micropublisher.LOG_TAG, "received message!!");
		}
		
		mv.runOnUiThread(mv.new DataUpdater(dObj));
	}

	//@Override
	public void onInterestListUpdate(Attribute[] interests) {
		Log.d(Micropublisher.LOG_TAG, "Setting interests (size=" + interests.length + ")");
		// TODO Auto-generated method stub
		for (int i = 0; i < interests.length; i++)
			Log.d(Micropublisher.LOG_TAG, "interest: " + interests[i].getName());
	}

	//@Override
	public void onShutdown(int reason) {
		Log.d(Micropublisher.LOG_TAG, "Shutdown event, reason=" + reason);
		if (hh != null) {
			hh.dispose();
			hh = null;
		} else {
			Log.d(Micropublisher.LOG_TAG, "Shutdown: handle is null!");
		}
	}

	//@Override
	public void onEventLoopStart() {
		Log.d(Micropublisher.LOG_TAG, "Event loop started.");
		// TODO Auto-generated method stub
		
	}

	//@Override
	public void onEventLoopStop() {
		Log.d(Micropublisher.LOG_TAG, "Event loop stopped.");
		// TODO Auto-generated method stub
		
	}

	// why is only this method synchronized?
	public void finiHaggle() {
		if (hh != null) {
			hh.eventLoopStop();
			hh.dispose();
			hh = null;
		}
	}
	
}