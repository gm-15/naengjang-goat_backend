package com.naengjang_goat.inventory_system.menu.domain;

import com.naengjang_goat.inventory_system.user.domain.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 판매 메뉴 엔티티 (v2.1)
 * 구 Recipe(메뉴) 대체 — 클래스명을 도메인 의미에 맞게 변경
 * BOM(재료 구성)은 RecipeBom 엔티티로 분리
 */
@Entity
@Table(name = "menu")
@Getter
@Setter
@NoArgsConstructor
public class Menu {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String name; // 메뉴명 (예: 제육볶음, 토마토 스파게티)

    @Column(nullable = false)
    private Integer price; // 판매가

    @OneToMany(mappedBy = "menu", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RecipeBom> bom = new ArrayList<>(); // Bill of Materials

    public Menu(User user, String name, Integer price) {
        this.user = user;
        this.name = name;
        this.price = price;
    }

    public void addBom(RecipeBom recipeBom) {
        bom.add(recipeBom);
        recipeBom.setMenu(this);
    }
}
