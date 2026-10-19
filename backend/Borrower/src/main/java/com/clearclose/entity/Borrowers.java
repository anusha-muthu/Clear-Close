package com.clearclose.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.validation.constraints.NotBlank;

@Entity
public class Borrowers {
	
	@Id
	@GeneratedValue(strategy=GenerationType.IDENTITY)
	private Long userId;
	@NotBlank(message = " Name is required")
	private String name;
	@NotBlank(message = "User Name is required")
	private String username;
	@NotBlank(message = "Password is required")
	private String passwordHash;
	@NotBlank(message = "Email is required")
	private String email;
	@NotBlank(message = "Phone number is required")
	private String phone;
	@NotBlank(message = "PanCard is required")
	private String panCard;
	
	private String location;
	private String accountStatus;
	@Column(name = "CREATED_AT", nullable = false, insertable = false, updatable = false)
	@CreationTimestamp
	private LocalDateTime createdAt;
	@Column(name = "UPDATED_AT", nullable = false, insertable = false, updatable = false)
	@CreationTimestamp
	private LocalDateTime updatedAt;
	public Borrowers() {
		super();
		// TODO Auto-generated constructor stub
	}
	
	public Borrowers(Long userId, String name, String username, String passwordHash, String email, String phone,
			String panCard, String location, String accountStatus, LocalDateTime createdAt, LocalDateTime updatedAt) {
		super();
		this.userId = userId;
		this.name = name;
		this.username = username;
		this.passwordHash = passwordHash;
		this.email = email;
		this.phone = phone;
		this.panCard = panCard;
		this.location = location;
		this.accountStatus = accountStatus;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
	}

	public Long getUserId() {
		return userId;
	}
	public void setUserId(Long userId) {
		this.userId = userId;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public String getUserName() {
		return username;
	}
	public void setUserName(String userName) {
		this.username = userName;
	}
	public String getPasswordHash() {
		return passwordHash;
	}
	public void setPasswordHash(String passwordHash) {
		this.passwordHash = passwordHash;
	}
	public String getEmail() {
		return email;
	}
	public void setEmail(String email) {
		this.email = email;
	}
	public String getPhone() {
		return phone;
	}
	public void setPhone(String phone) {
		this.phone = phone;
	}
	public String getPanCard() {
		return panCard;
	}
	public void setPanCard(String panCard) {
		this.panCard = panCard;
	}
	public String getLocation() {
		return location;
	}
	public void setLocation(String location) {
		this.location = location;
	}
	public String getAccountStatus() {
		return accountStatus;
	}
	public void setAccountStatus(String accountStatus) {
		this.accountStatus = accountStatus;
	}
	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}
	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}
	public void setUpdatedAt(LocalDateTime updatedAt) {
		this.updatedAt = updatedAt;
	}
	@Override
	public String toString() {
		return "Borrowers [userId=" + userId + ", name=" + name + ", userName=" + username + ", passwordHash="
				+ passwordHash + ", email=" + email + ", phone=" + phone + ", panCard=" + panCard + ", location="
				+ location + ", accountStatus=" + accountStatus + ", createdAt=" + createdAt + ", updatedAt="
				+ updatedAt + "]";
	}
	
	

}
