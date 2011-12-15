package org.haggle.Micropublisher;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;


public class UserDbAdapter {
	// Database fields
	public static final String KEY_PUBLICKEY = "publickey";
	public static final String KEY_DISTANCE = "distance";
	public static final String KEY_UUID = "uuid";
	public static final String KEY_NAME = "name";
	
	private static final String DATABASE_TABLE = "users";
	
	private Context context;
	private SQLiteDatabase database;
	private UserDbHelper dbHelper;
	
	public UserDbAdapter(Context context) {
		this.context = context;
	}
	
	public UserDbAdapter open() throws SQLException {
		dbHelper = new UserDbHelper(context);
		database = dbHelper.getWritableDatabase();
		return this;
	}
	
	public boolean isOpen() {
		return database.isOpen();
	}
	
	public void close() {
		dbHelper.close();
	}
	
	public long createUserEntry(String publicKey, int distance, String uuid, String name) {
		ContentValues initialValues = createContentValues(publicKey, distance, uuid, name);
		return database.insert(DATABASE_TABLE, null, initialValues);
	}
	
	// assume that Mike sends valid stuff. check this?!!
	public boolean updateTrustEntry(String publicKey, int distance, String uuid, String name) {
		ContentValues updateValues = createContentValues(publicKey, distance, uuid, name);
		publicKey = DatabaseUtils.sqlEscapeString(publicKey);
		return (database.update(DATABASE_TABLE, updateValues, KEY_PUBLICKEY + " = " + publicKey, null) > 0);
	}
	
	public Cursor selectEntryById(String uuid) throws SQLException {
		if (uuid == null)
			return null;
		Log.d(Micropublisher.LOG_TAG, "uuid: " + uuid);
		String query = "SELECT * FROM " + DATABASE_TABLE + " WHERE " + KEY_UUID + " = ?";
		Cursor cursor = database.rawQuery(query, new String[] { uuid });
		return cursor;
	}
	
	private ContentValues createContentValues(String publicKey, int distance, String uuid, String name) {
		ContentValues contentValues = new ContentValues();
		contentValues.put(KEY_PUBLICKEY, publicKey);
		contentValues.put(KEY_DISTANCE, distance);
		contentValues.put(KEY_UUID, uuid);
		contentValues.put(KEY_NAME, name);
		return contentValues;
	}
	
}