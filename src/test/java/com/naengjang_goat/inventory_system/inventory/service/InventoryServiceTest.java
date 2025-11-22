package com.naengjang_goat.inventory_system.inventory.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest
class InventoryServiceTest {

    @Autowired
    private InventoryService inventoryService;

    @Test
    void 동시에_재고차감_테스트() throws InterruptedException {

        Long rawMaterialId = 1L;  // 테스트용 재고 ID (DB에 존재해야 함)
        int threadCount = 10;

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    inventoryService.decreaseStock(rawMaterialId, 1.0);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
    }
}
