package com.example.hdb.controller;

import com.example.hdb.dto.request.LoginRequest;
import com.example.hdb.dto.response.LoginResponse;
import com.example.hdb.dto.response.MeResponse;
import com.example.hdb.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "인증 API", description = "로그인 및 사용자 정보 조회 API")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "로그인", description = "아이디와 비밀번호로 로그인합니다.")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        try {
            LoginResponse response = authService.login(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            LoginResponse errorResponse = LoginResponse.builder()
                    .success(false)
                    .message("아이디 또는 비밀번호가 올바르지 않습니다.")
                    .user(null)
                    .build();
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @GetMapping("/me")
    @Operation(summary = "내 정보 조회", description = "로그인한 사용자의 정보를 조회합니다.")
    public ResponseEntity<MeResponse> getMe(@RequestParam Long userId) {
        try {
            MeResponse response = authService.getMe(userId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            MeResponse errorResponse = MeResponse.builder()
                    .success(false)
                    .message("사용자 정보를 찾을 수 없습니다.")
                    .user(null)
                    .build();
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
}
