package com.naengjang_goat.inventory_system.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** null은 현재 계정의 기기 연결 해제이며, 토큰 값은 로그·응답에 포함하지 않는다. */
public record FcmTokenRequest(
    @JsonProperty(required = true)
        @Size(min = 1, max = 255, message = "기기 토큰은 1~255자여야 합니다.")
        @Pattern(regexp = "[!-~]+", message = "기기 토큰에 공백이나 지원하지 않는 문자가 있습니다.")
        String token,
    @Size(min = 1, max = 255, message = "기기 토큰은 1~255자여야 합니다.")
        @Pattern(regexp = "[!-~]+", message = "기기 토큰에 공백이나 지원하지 않는 문자가 있습니다.")
        String expectedToken) {}
