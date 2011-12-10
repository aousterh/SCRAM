package org.haggle.Micropublisher;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;

import org.haggle.DataObject;
import org.haggle.DataObject.DataObjectException;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ListView;

public class MicropublisherView extends Activity {
	public static final int MENU_PUBLISH  = 1;
	public static final int MENU_SHUTDOWN_HAGGLE = 2;
	
	private MessageAdapter messageAdpt = null;
	private Micropublisher mp = null;
	private boolean shouldRegisterWithHaggle = true;
	private PublicKey publicKey = null;
	private PrivateKey privateKey = null;
	
	/** Called when the activity is first created. */
	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(Micropublisher.LOG_TAG, "MicropublisherActivity:onCreate()");
        setContentView(R.layout.main);
        
        mp = (Micropublisher) getApplication();
        mp.setMicropublisherView(this);
        
        ListView messageList = (ListView) findViewById(R.id.message_list);
        messageAdpt = new MessageAdapter(this);
        messageList.setAdapter(messageAdpt);
        registerForContextMenu(messageList);
        
        KeyPair keys = Cryptography.initKeys();
        publicKey = keys.getPublic();
        privateKey = keys.getPrivate();
        
        String filepath = ContentParser.createFile("message", "signature");
        ContentParser.parseContent(filepath);
    }
	
	@Override
    public void onRestart() {
    	super.onRestart();
    	Log.d(Micropublisher.LOG_TAG, "Micropublisher:onRestart()");
    }
	
	@Override
	public void onStart() {
		super.onStart();
		
		Log.d(Micropublisher.LOG_TAG, "MicropublisherActivity:OnStart()");
		 if (shouldRegisterWithHaggle) {
			 int ret = mp.initHaggle();
			 
			 if (ret != Micropublisher.STATUS_OK) {
				 Log.d(Micropublisher.LOG_TAG, "Micropublisher could not start Haggle daemon.");
			 } else {
				 Log.d(Micropublisher.LOG_TAG, "Registration with Haggle successful");
			 }
		 }
	}
	
	@Override
    protected void onResume() {
    	super.onResume();
    	Log.d(Micropublisher.LOG_TAG, "Micropublisher:onResume()");
	}
	@Override
	protected void onPause() {
    	super.onPause();
    	Log.d(Micropublisher.LOG_TAG, "Micropublisher:onPause()");

 	}
    @Override
    protected void onStop() {
    	super.onStop();
    	Log.d(Micropublisher.LOG_TAG, "Micropublisher:onStop()");
    }
    @Override
    protected void onDestroy() {
    	super.onDestroy();
    	Log.d(Micropublisher.LOG_TAG, "Micropublisher:onDestroy()");
    }
	
	@Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	super.onCreateOptionsMenu(menu);

        menu.add(0, MENU_PUBLISH, 0, R.string.menu_publish).setIcon(android.R.drawable.ic_menu_edit);
      
        return true;
	}
	
	@Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	final Intent intent = new Intent();
    	
    	switch (item.getItemId()) {
	    	case MENU_PUBLISH:
	    		intent.setClass(getApplicationContext(), PublishView.class);
	        	this.startActivityForResult(intent, Micropublisher.PUBLISH_MESSAGE_REQUEST);
	    		return true;
	    	case MENU_SHUTDOWN_HAGGLE:
	    		shouldRegisterWithHaggle = true;
	    		mp.shutdownHaggle();
	    		return true;
    	}
    	return false;
    }
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		switch (keyCode) {
	    	case KeyEvent.KEYCODE_BACK:
	    	case KeyEvent.KEYCODE_HOME:
	    		Log.d(Micropublisher.LOG_TAG,"Key back, exit application and deregister with Haggle");
	    		mp.finiHaggle();
	    		shouldRegisterWithHaggle = true;
	    		this.finish();
	    		break;
	    	}
	    	
			return super.onKeyDown(keyCode, event);
		}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		
		switch(requestCode) {
			case Micropublisher.PUBLISH_MESSAGE_REQUEST:
				onPublishMessageResult(resultCode, data);
				break;
		}
	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		Log.d(Micropublisher.LOG_TAG, "onCreateContextMenu");
		if (messageAdpt.getDataObjects().length != 0) {
			menu.add("Delete");
			menu.add("Cancel");
		}
	}
	
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
		Log.d(Micropublisher.LOG_TAG, "onContextItemSelected" + item.getTitle() + "target view id=" + info.targetView.getId());
		
		if (messageAdpt.getDataObjects().length == 0)
			return true;
		
		if (item.getTitle() == "Delete") {
			DataObject dObj = messageAdpt.deleteMessage(info.position);
			mp.getHaggleHandle().deleteDataObject(dObj);
			dObj.dispose();
			Log.d(Micropublisher.LOG_TAG, "Disposed of data object");
		}
		return true;
	}
	
	private void onPublishMessageResult(int resultCode, Intent data) {
		String message = data.getStringExtra("message");
		Log.d(Micropublisher.LOG_TAG, "message is: " + message);
		
		try {
			String signature = Cryptography.generateSignature(privateKey, message);
			String contentFilepath = ContentParser.createFile(message, signature);
			DataObject dObj = new DataObject(contentFilepath);
	        dObj.addAttribute("Message", "news");
	        Log.d(Micropublisher.LOG_TAG, "data object: " + dObj);
	        mp.getHaggleHandle().publishDataObject(dObj);
	        dObj.dispose();
		} catch (DataObjectException e) {
			Log.d(Micropublisher.LOG_TAG, "Could not create data object");
		}
	}
	
	public class DataUpdater implements Runnable {
		int type;
		DataObject dObj = null;
		
		public DataUpdater(DataObject dObj) {
			this.type = org.haggle.EventHandler.EVENT_NEW_DATAOBJECT;
			this.dObj = dObj;
		}
		public void run() {
			Log.d(Micropublisher.LOG_TAG, "Running data updater");
			switch(type) {
			case org.haggle.EventHandler.EVENT_NEW_DATAOBJECT:
				Log.d(Micropublisher.LOG_TAG, "Event new data object");
				messageAdpt.updateMessages(dObj, publicKey);
				break;
			}
			Log.d(Micropublisher.LOG_TAG, "data updater done");
		}
	}
}