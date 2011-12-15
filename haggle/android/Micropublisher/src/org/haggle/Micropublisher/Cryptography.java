package org.haggle.Micropublisher;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

import android.util.Base64;

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
	
	public static String generateSignature(String privateKeyString, String content) {
		try {
			PrivateKey privateKey = getPrivateKeyFromString(privateKeyString);
			Signature signature = Signature.getInstance("SHA1withRSA");
			signature.initSign(privateKey);
			signature.update(content.getBytes());
			byte[] signedBytes = signature.sign();
			String sig = Base64.encodeToString(signedBytes, Base64.DEFAULT);
			return sig;
		} catch (Exception e) {
		}
		
		return null;
	}
	
	public static boolean verifySignature(String publicKeyString, String content, String claim) {
		try {
			PublicKey publicKey = getPublicKeyFromString(publicKeyString);
			Signature signature = Signature.getInstance("SHA1withRSA");
			signature.initVerify(publicKey);
			signature.update(content.getBytes());
			return signature.verify(Base64.decode(claim, Base64.DEFAULT));
		} catch (Exception e) {
			return false;
		}
	}
	
	public static String getUuidFromPublicKey(String publicKeyString) {
		String uuid = null;
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA");
			digest.update(publicKeyString.getBytes());
			uuid = Base64.encodeToString(digest.digest(), Base64.DEFAULT);
		} catch (Exception e) {
		}
		return uuid;
	}
	
	private static PublicKey getPublicKeyFromString(String publicKeyString) {
		PublicKey key = null;
		try {
			byte[] keyBytes = Base64.decode(publicKeyString.getBytes(), Base64.DEFAULT);
			KeyFactory factory = KeyFactory.getInstance("RSA");
			X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
			key = factory.generatePublic(spec);
		} catch (Exception e) {
		}
		return key;
	}
	
	private static PrivateKey getPrivateKeyFromString(String privateKeyString) {
		PrivateKey key = null;
		try {
			byte[] keyBytes = Base64.decode(privateKeyString.getBytes(), Base64.DEFAULT);
			KeyFactory factory = KeyFactory.getInstance("RSA");
			PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
			key = factory.generatePrivate(spec);
		} catch (Exception e) {
		}
		return key;
	}
}