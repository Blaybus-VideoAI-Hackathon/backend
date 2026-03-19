package com.example.hdb.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;

@Slf4j
public class BaseController {
    
    protected String resolveLoginId(Authentication authentication) {
        log.info("authentication = {}", authentication);
        
        if (authentication == null) {
            log.info("Authentication is null, using fallback user1");
            return "user1";
        }
        
        String name = authentication.getName();
        log.info("authentication.getName() = {}", name);
        
        if (name == null || "anonymousUser".equals(name)) {
            log.info("Authentication name is null or anonymousUser, using fallback user1");
            return "user1";
        }
        
        log.info("resolved loginId = {}", name);
        return name;
    }
}
