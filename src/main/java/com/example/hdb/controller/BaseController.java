package com.example.hdb.controller;

import org.springframework.security.core.Authentication;

public class BaseController {
    
    protected static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(BaseController.class);
    
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
