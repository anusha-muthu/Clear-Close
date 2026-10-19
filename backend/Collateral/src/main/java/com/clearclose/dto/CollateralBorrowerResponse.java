package com.clearclose.dto;

public class CollateralBorrowerResponse {
	
	private Long userId;
	private String username;
	public CollateralBorrowerResponse() {
		super();
		// TODO Auto-generated constructor stub
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
	public CollateralBorrowerResponse(Long userId, String username) {
		super();
		this.userId = userId;
		this.username = username;
	}
	
	

}
