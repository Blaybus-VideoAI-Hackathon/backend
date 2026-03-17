package com.example.hdb.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class PasswordGenerator {
    
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String rawPassword = "1111";
        String encodedPassword = encoder.encode(rawPassword);
        
        System.out.println("=== BCrypt Password Generator ===");
        System.out.println("Raw password: " + rawPassword);
        System.out.println("BCrypt encoded password: " + encodedPassword);
        System.out.println();
        
        // Verify encoding works
        boolean matches = encoder.matches(rawPassword, encodedPassword);
        System.out.println("Password verification: " + matches);
        System.out.println();
        
        // Generate a few more to show BCrypt randomness
        System.out.println("Multiple encodings (showing randomness):");
        for (int i = 0; i < 3; i++) {
            String encoded = encoder.encode(rawPassword);
            System.out.println("Encoding " + (i+1) + ": " + encoded);
        }
        System.out.println();
        
        // SQL update statements
        System.out.println("=== SQL Update Statements ===");
        System.out.println("Use this encoded password to update all users:");
        System.out.println("UPDATE users SET password = '" + encodedPassword + "' WHERE login_id IN ('user1', 'user2', 'user3', 'user4', 'user5');");
        System.out.println();
        System.out.println("Or update individually:");
        System.out.println("UPDATE users SET password = '" + encodedPassword + "' WHERE login_id = 'user1';");
        System.out.println("UPDATE users SET password = '" + encodedPassword + "' WHERE login_id = 'user2';");
        System.out.println("UPDATE users SET password = '" + encodedPassword + "' WHERE login_id = 'user3';");
        System.out.println("UPDATE users SET password = '" + encodedPassword + "' WHERE login_id = 'user4';");
        System.out.println("UPDATE users SET password = '" + encodedPassword + "' WHERE login_id = 'user5';");
    }
}
