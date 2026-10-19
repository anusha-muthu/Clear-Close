package com.clearclose.dto;

public class BorrowerResponse {
	private Long userId;
	private String username;
	public BorrowerResponse() {
		super();
		// TODO Auto-generated constructor stub
	}
	public BorrowerResponse(Long userId, String username) {
		super();
		this.userId = userId;
		this.username = username;
	}
	public Long getUserId() {
		return userId;
	}
	public void setUserId(Long userId) {
		this.userId = userId;
	}
	public String getUsername() {
		return username;
	}
	public void setUsername(String username) {
		this.username = username;
	}
	@Override
	public String toString() {
		return "BorrowerResponse [userId=" + userId + ", username=" + username + "]";
	}
	
}
