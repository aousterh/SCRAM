package org.haggle.Micropublisher;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import android.util.Base64;

import android.util.Log;


public class Cryptography {
	public static final int SIGNATURE_LENGTH = 256;
	
	public static KeyPair initKeys() {
		KeyPair keys = null;
		
		/* Replace this with a real key to be passed in */
		try {
			KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
			kpg.initialize(2048);
			keys = kpg.generateKeyPair();
		} catch (Exception e) {
		}
		return keys;
	}
	
	public static String generateSignature(PrivateKey privateKey, String content) {
		try {
			Signature signature = Signature.getInstance("SHA1withRSA");
			signature.initSign(privateKey);
			signature.update(Base64.decode(content, Base64.DEFAULT));
			byte[] signedBytes = signature.sign();
			
			String sig = Base64.encodeToString(signedBytes, Base64.DEFAULT);
			Log.d(Micropublisher.LOG_TAG, "sig length: " + sig.length());
			return sig;
		} catch (Exception e) {
		}
		
		return null;
	}
	
	public static boolean verifySignature(PublicKey publicKey, String content, String claim) {
		try {
			Signature signature = Signature.getInstance("SHA1withRSA");
			signature.initVerify(publicKey);
			signature.update(content.getBytes());
			return signature.verify(claim.getBytes());
		} catch (Exception e) {
			return false;
		}
	}
}