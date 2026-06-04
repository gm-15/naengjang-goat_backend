package com.naengjang_goat.inventory_system.supplier.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * POST /suppliers / PATCH /suppliers/{id} 공통 요청.
 * 모두 선택 — PATCH 시 null 은 무시. POST 시 name 만 필수.
 *
 * @author sim
 * @since 2026-06-04
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SupplierRequest {

    @NotBlank
    private String name;
    private String phone;
    private String address;
    private String memo;
}
