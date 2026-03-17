package com.example.hdb.dto.response.common;

import com.example.hdb.entity.User;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserResponse {

    private Long id;
    private String loginId;
    private String name;
    private String role;

    public static UserResponse from(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .loginId(user.getLoginId())
                .name(user.getName())
                .role(user.getRole().name())
                .build();
    }
}
