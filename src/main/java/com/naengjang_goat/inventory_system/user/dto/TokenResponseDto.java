package com.naengjang_goat.inventory_system.user.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor // 모든 필드를 인자로 받는 생성자
public class TokenResponseDto {
    private String accessToken;
    private String refreshToken;
}
