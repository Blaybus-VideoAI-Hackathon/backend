package com.example.hdb.service.impl;

import com.example.hdb.dto.request.LoginRequest;
import com.example.hdb.dto.response.LoginResponse;
import com.example.hdb.dto.response.MeResponse;
import com.example.hdb.dto.response.common.UserResponse;
import com.example.hdb.entity.User;
import com.example.hdb.exception.BusinessException;
import com.example.hdb.exception.ErrorCode;
import com.example.hdb.repository.UserRepository;
import com.example.hdb.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    @Override
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByLoginId(request.getLoginId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 비밀번호 검증 (BCrypt)
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_PASSWORD);
        }

        UserResponse userResponse = UserResponse.from(user);

        return LoginResponse.builder()
                .success(true)
                .message("로그인 성공")
                .user(userResponse)
                .build();
    }

    @Override
    public MeResponse getMe(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        UserResponse userResponse = UserResponse.from(user);

        return MeResponse.builder()
                .success(true)
                .message("사용자 정보 조회 성공")
                .user(userResponse)
                .build();
    }
}
