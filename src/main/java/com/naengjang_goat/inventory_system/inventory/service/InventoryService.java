package com.naengjang_goat.inventory_system.inventory.service;

import com.naengjang_goat.inventory_system.inventory.domain.Inventory;
import com.naengjang_goat.inventory_system.inventory.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;

    public List<Inventory> getInventoriesByUser(Long userId) {
        return inventoryRepository.findAll().stream()
                .filter(inv -> inv.getRawMaterial().getUser().getId().equals(userId))
                .toList();
    }

    public Inventory updateInventory(Long rawMaterialId, double newQuantity) {
        Inventory inventory = inventoryRepository.findByRawMaterialId(rawMaterialId)
                .orElseThrow(() -> new IllegalArgumentException("재고가 존재하지 않습니다."));
        inventory.setStockQuantity(newQuantity);
        return inventoryRepository.save(inventory);
    }
}
