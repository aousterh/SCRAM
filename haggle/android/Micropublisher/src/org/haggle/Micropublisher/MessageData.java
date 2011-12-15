package org.haggle.Micropublisher;


public class MessageData {
	private String message;
	private String name;
	private int status;
	
	public static final int STATUS_VERIFIED = 0;
	public static final int STATUS_UNVERIFIED = 1;
	public static final int STATUS_FORGERY = 2;
	public static final int STATUS_FROM_ME = 3;
	
	public MessageData(String message) {
		this.message = message;
		this.name = null;
	}
	
	public MessageData(String message, String name) {
		this.message = message;
		this.name = name;
	}
	
	public String getMessage() {
		return message;
	}
	
	// might want to be able to set the name on past
	// messages if more info becomes available
	public String getName() {
		return name;
	}
	
	public void setName(String name) {
		this.name = name;
	}
	
	public int getStatus() {
		return status;
	}
	
	public void setStatus(int status) {
		this.status = status;
	}
	
}