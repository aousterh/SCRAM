package org.haggle.Micropublisher;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

public class PublishView extends Activity {
	private EditText messageEntry;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

        setContentView(R.layout.publish_view);
        messageEntry = (EditText) findViewById(R.id.message_entry);
        
        final Button publishButton = (Button) findViewById(R.id.publish_button);
        publishButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
            	sendMessage();
            }
        });
	}
	
	public void sendMessage() {
		Editable ed = messageEntry.getText();
        String message = ed.toString();
        
        if (message == null || message.length() == 0)
        	return;
        
        
        message = message.trim();
        int length = Math.min(
        		Micropublisher.MESSAGE_LENGTH_IN_BYTES, message.length());
        message = message.substring(0, length).trim();
        
        Log.d(Micropublisher.LOG_TAG, "about to send message: " + message);
        ed.clear();
        
        Intent intent = new Intent();
        intent.putExtra("message", message);
        setResult(RESULT_OK, intent);
        finish();
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		switch (keyCode) {
		case KeyEvent.KEYCODE_BACK:
			Log.d(Micropublisher.LOG_TAG, "Key back");
			
			Intent intent = new Intent();
			setResult(RESULT_OK, intent);
			break;
		}
		return super.onKeyDown(keyCode, event);
	}
}
