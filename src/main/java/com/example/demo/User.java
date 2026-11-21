package com.example.demo;

import jakarta.persistence.*;

@Entity
@Table(name = "users")   // "user" is a reserved word in PostgreSQL
@Inheritance(strategy = InheritanceType.JOINED)
public abstract class User {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	private String name;
	@Column(unique = true)
	private String email;
	private String passwordHash;
	@Enumerated(EnumType.STRING)
	@Column(name = "account_role")
	private Role role;
	public String passwordHash() { return passwordHash; }
	public void changePassword(String encodedHash) { this.passwordHash = encodedHash; }

	protected User() {}
	protected User(String name, String email) { this.name = name; this.email = email; }

	public Role getRole() { return role != null ? role : defaultRole(); }
	public void changeRole(Role role) { this.role = role; }
	protected abstract Role defaultRole();
	public Long getId() { return id; }
	public String getName() { return name; }
	public String getEmail() { return email; }
}
