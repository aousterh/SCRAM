package org.haggle.Micropublisher;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.HashMap;

import org.haggle.Attribute;
import org.haggle.DataObject;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListAdapter;
import android.widget.TextView;

class MessageAdapter extends BaseAdapter implements ListAdapter {
	private Context mContext;
	private final HashMap<DataObject,String> messages = new HashMap<DataObject,String>();
	private final ArrayList<DataObject> dataObjects = new ArrayList<DataObject>();

	public MessageAdapter(Context mContext) {
		this.mContext = mContext;
		Log.d(Micropublisher.LOG_TAG, "MessageAdapter contructor");
	}
	public int getCount() {
		if (messages.size() == 0)
			return 1;
		else
			return messages.size();
	}

	public Object getItem(int position) {
		return messages.get(dataObjects.get(position));
	}

	public long getItemId(int position) {
		return position;
	}
	
	public synchronized DataObject deleteMessage(int position) {
		final DataObject dObj = dataObjects.get(position);
		
		if (dObj == null)
			return null;
		
		dataObjects.remove(position);
		
		if (messages.remove(dObj) == null)
			return null;
		
		notifyDataSetChanged();
		
		return dObj;
	}
	
	public synchronized void updateMessages(DataObject dObj, PublicKey publicKey) {
	
		Log.d(Micropublisher.LOG_TAG, "data object: " + dObj);
		
		File dataFile = new File(dObj.getFilePath());
		InputStream in = null;
		try {
			in = new FileInputStream(dataFile);
			byte[] buffer = new byte[Micropublisher.MESSAGE_LENGTH_IN_BYTES];
			int bytes = in.read(buffer);
			int messageLength = bytes - Cryptography.SIGNATURE_LENGTH;
			Log.d(Micropublisher.LOG_TAG, "message length: " + messageLength);
			if (messageLength > 0) {
				String message = new String(buffer, 0, messageLength);
				String signature = new String(buffer, messageLength, Cryptography.SIGNATURE_LENGTH);
				Log.d(Micropublisher.LOG_TAG, "received message: " + message);
				
				Log.d(Micropublisher.LOG_TAG, "signature: " + signature);
				if (Cryptography.verifySignature(publicKey, message, signature)) {
					messages.put(dObj, message);
					dataObjects.add(0, dObj);
					Log.d(Micropublisher.LOG_TAG, "Signature verified!!");
				}
			}
		} catch (FileNotFoundException e) {
			Log.d(Micropublisher.LOG_TAG, "File not found");
		} catch (IOException e) {
			Log.d(Micropublisher.LOG_TAG, "Problem reading");
		} finally {
			if (in != null)
				try {
					in.close();
				} catch (IOException e) {
					Log.d(Micropublisher.LOG_TAG, "io exception");
				}	
		}

		notifyDataSetChanged();
	}

    public void refresh() {
    	notifyDataSetChanged();
    }
    
    public synchronized DataObject[] getDataObjects() {
    	return dataObjects.toArray(new DataObject[dataObjects.size()]);
    }
    
	public View getView(int position, View convertView, ViewGroup parent) {    		
		TextView tv;
		
		if (convertView == null) {
            tv = (TextView) LayoutInflater.from(mContext).inflate(R.layout.message_list_item, parent, false);
        } else {
            tv = (TextView) convertView;
        }

		if (messages.size() == 0) {
			tv.setText("none");

		} else {
			String message = (String) getItem(position);
			tv.setText(message);
		}
		return tv;
	}
}
