package com.naengjang_goat.inventory_system.supplier.controller;

import com.naengjang_goat.inventory_system.global.security.CustomUserDetails;
import com.naengjang_goat.inventory_system.supplier.domain.Supplier;
import com.naengjang_goat.inventory_system.supplier.dto.SupplierRequest;
import com.naengjang_goat.inventory_system.supplier.dto.SupplierResponse;
import com.naengjang_goat.inventory_system.supplier.repository.SupplierRepository;
import com.naengjang_goat.inventory_system.user.domain.User;
import com.naengjang_goat.inventory_system.user.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * 거래처 관리 컨트롤러.
 *
 *  GET    /suppliers       : 점주 거래처 목록 (이름 가나다 정렬)
 *  GET    /suppliers/{id}  : 단건 조회
 *  POST   /suppliers       : 신규 등록 (201)
 *  PATCH  /suppliers/{id}  : 부분 수정 (null 무시)
 *  DELETE /suppliers/{id}  : 삭제 (204)
 *
 * 인증: JWT Bearer Token. 모든 요청은 소유 검증 후 처리.
 *
 * @author sim
 * @since 2026-06-04
 */
@RestController
@RequestMapping("/suppliers")
@RequiredArgsConstructor
public class SupplierController {

    private final SupplierRepository supplierRepository;
    private final UserRepository     userRepository;

    @GetMapping
    public ResponseEntity<List<SupplierResponse>> list(
            @AuthenticationPrincipal CustomUserDetails principal
    ) {
        List<SupplierResponse> list = supplierRepository
                .findAllByUserIdOrderByNameAsc(principal.getId())
                .stream()
                .map(SupplierResponse::from)
                .toList();
        return ResponseEntity.ok(list);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SupplierResponse> get(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable Long id
    ) {
        Supplier s = loadOwned(principal.getId(), id);
        return ResponseEntity.ok(SupplierResponse.from(s));
    }

    @PostMapping
    public ResponseEntity<SupplierResponse> create(
            @AuthenticationPrincipal CustomUserDetails principal,
            @Valid @RequestBody SupplierRequest request
    ) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "사용자 없음"));

        Supplier saved = supplierRepository.save(new Supplier(
                user, request.getName(), request.getPhone(), request.getAddress(), request.getMemo()
        ));
        return ResponseEntity.status(HttpStatus.CREATED).body(SupplierResponse.from(saved));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<SupplierResponse> update(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable Long id,
            @RequestBody SupplierRequest request
    ) {
        Supplier s = loadOwned(principal.getId(), id);

        if (request.getName() != null)    s.setName(request.getName());
        if (request.getPhone() != null)   s.setPhone(request.getPhone());
        if (request.getAddress() != null) s.setAddress(request.getAddress());
        if (request.getMemo() != null)    s.setMemo(request.getMemo());

        Supplier saved = supplierRepository.save(s);
        return ResponseEntity.ok(SupplierResponse.from(saved));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable Long id
    ) {
        Supplier s = loadOwned(principal.getId(), id);
        supplierRepository.delete(s);
        return ResponseEntity.noContent().build();
    }

    /** 소유 검증 + 단건 조회. 없으면 404, 다른 점주 소유면 403. */
    private Supplier loadOwned(Long userId, Long id) {
        Supplier s = supplierRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "거래처 없음: " + id));
        if (!s.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "접근 권한 없음");
        }
        return s;
    }
}
