package org.haggle.Micropublisher;

import java.util.ArrayList;
import java.util.HashMap;

import org.haggle.DataObject;

import android.content.Context;
import android.graphics.Color;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListAdapter;
import android.widget.TextView;

class MessageAdapter extends BaseAdapter implements ListAdapter {
	private Context mContext;
	private final HashMap<DataObject,MessageData> data = new HashMap<DataObject,MessageData>();
	private final ArrayList<DataObject> dataObjects = new ArrayList<DataObject>();
	
	public MessageAdapter(Context mContext) {
		this.mContext = mContext;
		Log.d(Micropublisher.LOG_TAG, "MessageAdapter contructor");
	}
	
	public int getCount() {
		if (data.size() == 0)
			return 1;
		else
			return data.size();
	}

	public Object getItem(int position) {
		return data.get(dataObjects.get(position));
	}

	public long getItemId(int position) {
		return position;
	}
	
	public synchronized DataObject deleteMessage(int position) {
		final DataObject dObj = dataObjects.get(position);
		
		if (dObj == null)
			return null;
		
		dataObjects.remove(position);
		
		if (data.remove(dObj) == null)
			return null;
		
		notifyDataSetChanged();
		
		return dObj;
	}
	
	public synchronized void updateMessages(DataObject dObj, MessageData messageData) {
		data.put(dObj, messageData);
		dataObjects.add(0, dObj);

		notifyDataSetChanged();
	}

    public void refresh() {
    	notifyDataSetChanged();
    }
    
    public synchronized DataObject[] getDataObjects() {
    	return dataObjects.toArray(new DataObject[dataObjects.size()]);
    }
    
	public View getView(int position, View convertView, ViewGroup parent) {    		
		View view = convertView;
		
		if (data.size() == 0) {
			view = LayoutInflater.from(mContext).inflate(R.layout.empty_message_list_item, parent, false);
			TextView text = (TextView) view.findViewById(R.id.text_item);
			text.setText("none");
			return view;
		}
		
		ViewHolder viewHolder;
		
		if (view == null || view.getTag() == null) {
            view = LayoutInflater.from(mContext).inflate(R.layout.message_list_item, parent, false);
            viewHolder = new ViewHolder();
            viewHolder.message = (TextView) view.findViewById(R.id.message_item);
            viewHolder.name = (TextView) view.findViewById(R.id.name_item);
            view.setTag(viewHolder);
        } else {
        	viewHolder = (ViewHolder) view.getTag();
        }
		
		MessageData messageData = (MessageData) getItem(position);
		
		if (messageData != null) {
			viewHolder.message.setText(messageData.getMessage());
			
			switch (messageData.getStatus()) {
				case MessageData.STATUS_FORGERY:
					viewHolder.name.setText("forged");
					viewHolder.name.setTextColor(Color.RED);
					break;
				case MessageData.STATUS_FROM_ME:
					viewHolder.name.setText("me");
					viewHolder.name.setTextColor(Color.CYAN);
					break;
				case MessageData.STATUS_UNVERIFIED:
					viewHolder.name.setText("unverified");
					viewHolder.name.setTextColor(Color.YELLOW);
					break;
				case MessageData.STATUS_VERIFIED:
					String name = messageData.getName();
					if (name != null)
						viewHolder.name.setText(name);
					else
						viewHolder.name.setText("unknown");
					viewHolder.name.setTextColor(Color.GREEN);
					break;
			}
		}
		
		return view;
	}
	
	static class ViewHolder {
		private TextView message;
		private TextView name;
	}
	
}
