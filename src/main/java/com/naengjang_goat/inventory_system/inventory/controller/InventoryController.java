package com.naengjang_goat.inventory_system.inventory.controller;

import com.naengjang_goat.inventory_system.inventory.domain.Inventory;
import com.naengjang_goat.inventory_system.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    /**
     * 특정 유저의 재고 전체 조회
     * GET /inventory/user/{userId}
     */
    @GetMapping("/user/{userId}")
    public List<Inventory> getInventoriesByUser(@PathVariable Long userId) {
        return inventoryService.getInventoriesByUser(userId);
    }

    /**
     * 관리자/점주가 재고 직접 수정
     * PUT /inventory/update?rawMaterialId=1&newQuantity=50
     */
    @PutMapping("/update")
    public Inventory updateInventory(@RequestParam Long rawMaterialId,
                                     @RequestParam double newQuantity) {
        return inventoryService.updateInventory(rawMaterialId, newQuantity);
    }

    /**
     * 주문 발생 시 재고 차감
     * POST /inventory/decrease?rawMaterialId=1&quantity=2
     */
    @PostMapping("/decrease")
    public void decreaseStock(@RequestParam Long rawMaterialId,
                              @RequestParam double quantity) {
        inventoryService.decreaseStock(rawMaterialId, quantity);
    }
}
