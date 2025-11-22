package com.naengjang_goat.inventory_system.batch.service;

import com.naengjang_goat.inventory_system.batch.dto.KamisPriceDto;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class KamisApiClient {

    public List<KamisPriceDto> fetchPrices() {
        return List.of(
                new KamisPriceDto("깐마늘", LocalDate.now(), 3500, "kg"),
                new KamisPriceDto("양파", LocalDate.now(), 1200, "kg")
        );
    }
}
