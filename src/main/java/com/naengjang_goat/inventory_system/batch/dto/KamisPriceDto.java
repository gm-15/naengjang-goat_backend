package com.naengjang_goat.inventory_system.batch.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;

@Data
@AllArgsConstructor
public class KamisPriceDto {
    private String name;
    private LocalDate priceDate;
    private double price;
    private String unit;
}
