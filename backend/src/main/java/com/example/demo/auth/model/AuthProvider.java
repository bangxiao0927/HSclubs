package com.example.demo.auth.model;

public class AuthProvider {

    private String id;
    private String name;
    private String authorizationUrl;

    public AuthProvider() {
    }

    public AuthProvider(String id, String name, String authorizationUrl) {
        this.id = id;
        this.name = name;
        this.authorizationUrl = authorizationUrl;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAuthorizationUrl() {
        return authorizationUrl;
    }

    public void setAuthorizationUrl(String authorizationUrl) {
        this.authorizationUrl = authorizationUrl;
    }
}
