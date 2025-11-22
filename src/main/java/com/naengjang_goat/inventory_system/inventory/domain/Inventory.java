package com.naengjang_goat.inventory_system.inventory.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Inventory {

    @Id
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "raw_material_id")
    private RawMaterial rawMaterial;

    @Column(nullable = false)
    private Double stockQuantity;

    @Column(nullable = false)
    private String stockUnit;

    public Inventory(RawMaterial rawMaterial, double stockQuantity) {
        this.rawMaterial = rawMaterial;
        this.stockQuantity = stockQuantity;
    }
}
