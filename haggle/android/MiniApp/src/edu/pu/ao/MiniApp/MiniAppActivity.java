package edu.pu.ao.MiniApp;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;

public class MiniAppActivity extends Activity {
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        // generate keys
        KeyPair keys = null;
        try {
			KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
			kpg.initialize(2048);
			keys = kpg.generateKeyPair();
		} catch (Exception e) {
		}
        PrivateKey privateKey = keys.getPrivate();
        PublicKey publicKey = keys.getPublic();
        byte[] encodedPublic = publicKey.getEncoded();
		byte[] encodedPrivate = privateKey.getEncoded();
		X509EncodedKeySpec publicSpec = new X509EncodedKeySpec(encodedPublic);
		PKCS8EncodedKeySpec privateSpec = new PKCS8EncodedKeySpec(encodedPrivate);
		byte[] encodedX509 = publicSpec.getEncoded();
		byte[] encodedPKCS8 = privateSpec.getEncoded();
		String publicKeyString = Base64.encodeToString(encodedX509,  Base64.DEFAULT);
		String privateKeyString = Base64.encodeToString(encodedPKCS8,  Base64.DEFAULT);
        
        Intent intent = new Intent("edu.pu.ao.MiniApp.KEYS");
        intent.putExtra("publickey", publicKeyString);
        intent.putExtra("privatekey", privateKeyString);
        sendBroadcast(intent);
        
   /*     // generate keys 2
        keys = null;
        try {
			KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
			kpg.initialize(2048);
			keys = kpg.generateKeyPair();
		} catch (Exception e) {
		}
        privateKey = keys.getPrivate();
        publicKey = keys.getPublic();
        encodedPublic = publicKey.getEncoded();
		encodedPrivate = privateKey.getEncoded();
		publicSpec = new X509EncodedKeySpec(encodedPublic);
		privateSpec = new PKCS8EncodedKeySpec(encodedPrivate);
		encodedX509 = publicSpec.getEncoded();
		encodedPKCS8 = privateSpec.getEncoded();
		publicKeyString = Base64.encodeToString(encodedX509,  Base64.DEFAULT);
		privateKeyString = Base64.encodeToString(encodedPKCS8,  Base64.DEFAULT);
		
		intent = new Intent("edu.pu.ao.MiniApp.KEYS");
        intent.putExtra("publickey", publicKeyString);
        intent.putExtra("privatekey", privateKeyString);
        sendBroadcast(intent);*/
        
        Intent intent2 = new Intent("edu.pu.ao.MiniApp.USER_DATA");
        intent2.putExtra("publickey", publicKeyString);
        intent2.putExtra("distance", 12);
        // intent2.putExtra("name", "Dr. Evil");
        sendBroadcast(intent2);
        Log.d("MiniApp", "SENT intents with data");
    }
    
}