package com.dietdiary.auth.entity;

/**
 * Defines user roles.
 */
public enum UserRole {
    USER("일반 사용자"),
    ADMIN("관리자"),
    SUPER_ADMIN("최고 관리자");
    
    private final String description;
    
    UserRole(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
    
    public boolean isAdmin() {
        return this == ADMIN || this == SUPER_ADMIN;
    }
    
    public boolean isSuperAdmin() {
        return this == SUPER_ADMIN;
    }
}
