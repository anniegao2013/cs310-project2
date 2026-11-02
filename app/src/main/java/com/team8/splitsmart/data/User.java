package com.team8.splitsmart.data;

public class User {
    public long userId;
    public String name;
    public String email;
    public String uscId;
    public String passwordHash;

    public User() {
    }

    public User(String name, String email, String uscId, String passwordHash) {
        this.name = name;
        this.email = email;
        this.uscId = uscId;
        this.passwordHash = passwordHash;
    }
}
