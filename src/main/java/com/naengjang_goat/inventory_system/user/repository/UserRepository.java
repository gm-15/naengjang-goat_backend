package com.naengjang_goat.inventory_system.user.repository;

import com.naengjang_goat.inventory_system.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * User 엔티티에 접근하기 위한 Spring Data JPA 리포지토리입니다.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> { // <관리할 엔티티, 엔티티의 ID 타입>

    /**
     * username(로그인 ID)을 기준으로 사용자를 조회합니다.
     * Spring Data JPA가 메서드 이름을 분석하여 쿼리를 자동으로 생성합니다.
     * "SELECT * FROM users WHERE username = ?"
     *
     * @param username 조회할 사용자의 로그인 ID
     * @return 사용자가 존재하면 Optional<User>를, 없으면 Optional.empty()를 반환
     */
    Optional<User> findByUsername(String username);

    // save() 메서드는 JpaRepository가 이미 구현하여 제공.

}

