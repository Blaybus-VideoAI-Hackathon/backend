package com.example.hdb.dto.response;

import com.example.hdb.dto.response.common.UserResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class MeResponse {

    private boolean success;
    private String message;
    private UserResponse user;
}
