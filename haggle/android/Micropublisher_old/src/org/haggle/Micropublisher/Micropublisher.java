package org.haggle.Micropublisher;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;

import org.haggle.Attribute;
import org.haggle.DataObject;
import org.haggle.Handle;
import org.haggle.Node;
import org.haggle.LaunchCallback;

import android.app.AlertDialog;
import android.app.Application;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.util.Log;

public class Micropublisher extends Application implements org.haggle.EventHandler {
	public static final String LOG_TAG = "Micropublisher";
	
	public static final int STATUS_OK = 0;
	public static final int STATUS_ERROR = -1;
	public static final int STATUS_REGISTRATION_FAILED = -2;
	public static final int STATUS_SPAWN_DAEMON_FAILED = -3;

	static final int ADD_INTEREST_REQUEST = 0;
	static final int IMAGE_CAPTURE_REQUEST = 1;
	static final int ADD_PICTURE_ATTRIBUTES_REQUEST = 2;

	private PhotoView pv = null;
	private org.haggle.Handle hh = null;
	private int status = STATUS_OK;
	private android.os.Vibrator vibrator = null;
	private long lastNeighborVibrateTime = -1, lastDataObjectVibrateTime = -1;
	
	public void onConfigurationChanged(Configuration newConfig) {
		// TODO Auto-generated method stub
		super.onConfigurationChanged(newConfig);
	}

	public void onCreate() {
		super.onCreate();
		
		Log.d(Micropublisher.LOG_TAG, "Micropublisher:onCreate(), thread id=" + Thread.currentThread().getId());
		
		vibrator = (android.os.Vibrator)getSystemService(VIBRATOR_SERVICE);
	}

	public void onLowMemory() {
		super.onLowMemory();
		Log.d(Micropublisher.LOG_TAG, "Micropublisher:onLowMemory()");	
	}

	public void onTerminate() {
		super.onTerminate();
		Log.d(Micropublisher.LOG_TAG, "Micropublisher:onTerminate()");	
		//finiHaggle();
	}
	public void setPhotoView(PhotoView pv) {
		Log.d(Micropublisher.LOG_TAG, "Micropublisher: Setting pv");
		this.pv = pv;
	}
	public PhotoView getPhotoView() {
		return pv;
	}
	public Handle getHaggleHandle() {
		return hh;
	}

	public void tryDeregisterHaggleHandle() {
		finiHaggle();
	}
	public int initHaggle() {
		
		if (hh != null)
			return STATUS_OK;

		int status = Handle.getDaemonStatus();
		
		if (status == Handle.HAGGLE_DAEMON_NOT_RUNNING || status == Handle.HAGGLE_DAEMON_CRASHED) {
			Log.d(Micropublisher.LOG_TAG, "Trying to spawn Haggle daemon");

			if (!Handle.spawnDaemon(new LaunchCallback() {
				ProgressDialog progress = null;
				
				public int callback(long milliseconds) {

					Log.d(Micropublisher.LOG_TAG, "Spawning milliseconds..." + milliseconds);

					if (milliseconds == 0) {
						// Daemon launched
					} else if (milliseconds == 2000) {
						/*progress = ProgressDialog.show(getBaseContext(), "",
				        		"Launching Haggle...", true);*/
					} else if (milliseconds == 10000) {
						Log.d(Micropublisher.LOG_TAG, "Spawning failed, giving up");
						
						if (progress != null)
							progress.dismiss();
						return -1;
					}

					if (progress != null)
						progress.dismiss();
					return 0;
				}
			})) {
				Log.d(Micropublisher.LOG_TAG, "Spawning failed...");
				return STATUS_SPAWN_DAEMON_FAILED;
			}
		}
		long pid = Handle.getDaemonPid();

		Log.d(Micropublisher.LOG_TAG, "Haggle daemon pid is " + pid);

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

		hh.registerEventInterest(EVENT_NEIGHBOR_UPDATE, this);
		hh.registerEventInterest(EVENT_NEW_DATAOBJECT, this);
		hh.registerEventInterest(EVENT_INTEREST_LIST_UPDATE, this);
		hh.registerEventInterest(EVENT_HAGGLE_SHUTDOWN, this);
		
		hh.eventLoopRunAsync(this);
		Attribute interest = new Attribute("Message", "Message", 3);  // what is the first arg?!
		hh.registerInterest(interest); //check this call!!		
		
		return STATUS_OK;
	}
	public synchronized void finiHaggle() {
		if (hh != null) {
			hh.eventLoopStop();
			hh.dispose();
			hh = null;
		}
	}
	public int getStatus() {
		return status;
	}
	public void shutdownHaggle() {
		hh.shutdown();
	}
	/*public boolean registerInterest(Attribute interest) {
		if (hh.registerInterest(interest) == 0) 
			return true;
		return false;
	}*/
	public void onNewDataObject(DataObject dObj)
	{
		if (pv == null)
			return;
		
		Log.d(Micropublisher.LOG_TAG, "Got new data object, thread id=" + Thread.currentThread().getId());

		if (dObj.getAttribute("Message", 0) == null) {
			Log.d(Micropublisher.LOG_TAG, "DataObject has no Message attribute");
			return;
		}
		Log.d(Micropublisher.LOG_TAG, "Got something with a message: " + dObj.getAttribute("Message", 1));
		
		if (dObj.getAttribute("Picture", 0) == null) {
			Log.d(Micropublisher.LOG_TAG, "DataObject has no Picture attribute");
			return;
		}

		Log.d(Micropublisher.LOG_TAG, "Getting filepath");
		
		String filepath = dObj.getFilePath();

		if (filepath == null || filepath.length() == 0) {
			Log.d(Micropublisher.LOG_TAG, "Bad filepath");
			return;
		}

		// Make sure we do not vibrate more than once every 5 secs or so
		long currTime = System.currentTimeMillis();
		
		if (lastDataObjectVibrateTime == -1 || currTime - lastDataObjectVibrateTime > 5000) {
			long[] pattern = { 0, 300, 100, 400 };
			vibrator.vibrate(pattern, -1);
			lastDataObjectVibrateTime = currTime;
		}

		Log.d(Micropublisher.LOG_TAG, "Filepath=" + filepath);

		Log.d(Micropublisher.LOG_TAG, "Updating UI dobj");
		pv.runOnUiThread(pv.new DataUpdater(dObj));
	
	}
	@Override
	public void onNeighborUpdate(Node[] neighbors) {

		if (pv == null)
			return;
		
		Log.d(Micropublisher.LOG_TAG, "Got neighbor update, thread id=" + 
				Thread.currentThread().getId() + " num_neighbors=" + neighbors.length);

		// Make sure we do not vibrate more than once every 5 secs or so
		long currTime = System.currentTimeMillis();

		if (lastNeighborVibrateTime == -1 || currTime - lastNeighborVibrateTime > 5000) {
			long[] pattern = { 0, 500, 100, 300 };
			
			if (neighbors.length > 0 || lastNeighborVibrateTime != -1)
				vibrator.vibrate(pattern, -1);
			
			lastNeighborVibrateTime = currTime;
		}
		Log.d(Micropublisher.LOG_TAG, "Updating UI neigh");
		pv.runOnUiThread(pv.new DataUpdater(neighbors));
	}

	public void onShutdown(int reason) {
		Log.d(Micropublisher.LOG_TAG, "Shutdown event, reason=" + reason);
		if (hh != null) {
			hh.dispose();
			hh = null;
		} else {
			Log.d(Micropublisher.LOG_TAG, "Shutdown: handle is null!");
		}
		pv.runOnUiThread(new Runnable() {
			public void run() {
				AlertDialog.Builder builder = new AlertDialog.Builder(pv);
    			builder.setMessage("Haggle was shutdown and is no longer running. Press Quit to exit Micropublisher.")
    			.setCancelable(false)
    			.setNegativeButton("Quit", new DialogInterface.OnClickListener() {
    				public void onClick(DialogInterface dialog, int id) {
    					dialog.cancel();
    					pv.finish();
    				}
    			});
    			AlertDialog alert = builder.create();
    			alert.show();
			}
		});
	}
	
	public void onInterestListUpdate(Attribute[] interests) {
		Log.d(Micropublisher.LOG_TAG, "Setting interests (size=" + interests.length + ")");
		InterestView.setInterests(interests);
	}

	public void onEventLoopStart() {
		Log.d(Micropublisher.LOG_TAG, "Event loop started.");
		hh.getApplicationInterestsAsync();
	}

	public void onEventLoopStop() {
		Log.d(Micropublisher.LOG_TAG, "Event loop stopped.");
	}
}
