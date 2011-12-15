package org.haggle.Micropublisher;

import android.util.Log;


public class UserNode {
	
	private UserDbAdapter db;	// database
	private String publicKey;	// string version of public key
	private int distance;		// trust distance
	private String uuid;		// unique user id
	private String name;		// human-readable name
	
	public UserNode(UserDbAdapter db) {
		this.db = db;
		publicKey = null;
		distance = Integer.MAX_VALUE;
		uuid = null;
		name = null;
	}
	
	public void setPublicKey(String publicKey) {
		this.publicKey = publicKey;
		this.uuid = Cryptography.getUuidFromPublicKey(publicKey);
		Log.d(Micropublisher.LOG_TAG, "UUID: " + uuid);
	}
	
	public void setDistance(int distance) {
		this.distance = distance;
	}
	
	public void setName(String name) {
		this.name = name;
	}
	
	public boolean commitUserNode() {
		Log.e(Micropublisher.LOG_TAG, "Node commit, name: " + name + " uuid: " + uuid);
		if (publicKey != null && uuid != null) {
			if (!db.updateTrustEntry(publicKey, distance, uuid, name))
				return (db.createUserEntry(publicKey, distance, uuid, name) != -1);
			return true;
		}
		return false;
	}
}