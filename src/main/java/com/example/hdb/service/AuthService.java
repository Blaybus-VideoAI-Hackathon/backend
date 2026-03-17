package com.example.hdb.service;

import com.example.hdb.dto.request.LoginRequest;
import com.example.hdb.dto.response.LoginResponse;
import com.example.hdb.dto.response.MeResponse;

public interface AuthService {

    LoginResponse login(LoginRequest request);

    MeResponse getMe(Long userId);
}
