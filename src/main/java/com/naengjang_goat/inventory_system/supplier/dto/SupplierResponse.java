package com.naengjang_goat.inventory_system.supplier.dto;

import com.naengjang_goat.inventory_system.supplier.domain.Supplier;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * GET /suppliers 응답 한 항목.
 *
 * @author sim
 * @since 2026-06-04
 */
@Getter
@Builder
@AllArgsConstructor
public class SupplierResponse {

    private final Long id;
    private final String name;
    private final String phone;
    private final String address;
    private final String memo;
    private final LocalDateTime createdAt;

    public static SupplierResponse from(Supplier s) {
        return SupplierResponse.builder()
                .id(s.getId())
                .name(s.getName())
                .phone(s.getPhone())
                .address(s.getAddress())
                .memo(s.getMemo())
                .createdAt(s.getCreatedAt())
                .build();
    }
}
