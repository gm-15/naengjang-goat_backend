package com.naengjang_goat.inventory_system.user.dto;

import com.naengjang_goat.inventory_system.user.Role;
import com.naengjang_goat.inventory_system.user.User;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserSignupRequestDto {

    @NotBlank(message = "로그인 ID는 필수입니다.")
    private String username;

    @NotBlank(message = "비밀번호는 필수입니다.")
    private String password;

    @NotBlank(message = "점주 이름은 필수입니다.")
    private String ownerName;

    // DTO를 Entity로 변환하는 메서드
    public User toEntity(String encodedPassword) {
        User user = new User();
        user.setUsername(this.username);
        user.setPassword(encodedPassword); // 암호화된 비밀번호 저장
        user.setOwnerName(this.ownerName);
        user.setRole(Role.OWNER); // 회원가입 시 기본 권한은 '점주'
        return user;
    }
}
