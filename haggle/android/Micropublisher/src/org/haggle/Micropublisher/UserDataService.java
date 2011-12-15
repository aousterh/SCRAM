package org.haggle.Micropublisher;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;


public class UserDataService extends Service {
	Micropublisher mp = null;
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.e(Micropublisher.LOG_TAG, "STARTED SERVICE");
		mp = (Micropublisher) getApplication();
		
		// register the receiver to receive intents
		IntentFilter filter = new IntentFilter("edu.pu.ao.MiniApp.KEYS");
        this.registerReceiver(userDataReceiver, filter);
        filter = new IntentFilter("edu.pu.ao.MiniApp.USER_DATA");
        this.registerReceiver(userDataReceiver, filter);
        filter = new IntentFilter("edu.pu.mf.iw.ProjectOne.ACTION_KEYS");
        this.registerReceiver(userDataReceiver, filter);
        filter = new IntentFilter("edu.pu.mf.iw.ProjectOne.ACTION_NODE_COMMIT");
        this.registerReceiver(userDataReceiver, filter);
        return START_STICKY;
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
	
	private final BroadcastReceiver userDataReceiver = new BroadcastReceiver() {
	
		public void onReceive(Context context, Intent intent) {
			UserDbAdapter db;
			
			String action = intent.getAction();
			if (action.equals("edu.pu.mf.iw.ProjectOne.ACTION_NODE_COMMIT") ||
					(action.equals("edu.pu.ao.MiniApp.USER_DATA"))) {
				Bundle bundle = intent.getExtras();
				db = new UserDbAdapter(context);
				db.open();
				UserNode user = new UserNode(db);
				user.setPublicKey(bundle.getString("publickey"));
				user.setDistance(bundle.getInt("distance"));
				user.setName(bundle.getString("name"));
				user.commitUserNode();
				db.close();
			} else if (action.equals("edu.pu.mf.iw.ProjectOne.ACTION_KEYS") ||
					(action.equals("edu.pu.ao.MiniApp.KEYS"))) {
				Log.e(Micropublisher.LOG_TAG, "RECEIVED KEYS");
				Bundle bundle = intent.getExtras();
			    String publicKeyString = bundle.getString("publickey");
			    String privateKeyString = bundle.getString("privatekey");
			    
			    mp.setKeys(publicKeyString, privateKeyString);
			}
		}
	};

}
