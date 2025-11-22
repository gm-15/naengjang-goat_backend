package com.naengjang_goat.inventory_system;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class InventorySystemApplication {
	public static void main(String[] args) {
		SpringApplication.run(InventorySystemApplication.class, args);
	}
}