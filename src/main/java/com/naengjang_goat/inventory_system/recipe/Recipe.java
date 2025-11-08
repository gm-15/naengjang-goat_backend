package com.naengjang_goat.inventory_system.recipe;

import com.naengjang_goat.inventory_system.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.List;
import java.util.ArrayList;

/**
 * 메뉴를 정의하는 엔티티
 * 예: 토마토 스파게티
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Recipe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name; // 메뉴명 (예: 토마토 스파게티)

    // Recipe 1개가 여러개의 RecipeItem을 가짐 (1:N)
    // CascadeType.ALL: 레시피 저장 시 재료 구성도 함께 저장
    // orphanRemoval = true: 레시피에서 재료를 빼면 DB에서도 삭제
    @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RecipeItem> items = new ArrayList<>();

    //== 연관관계 편의 메서드 ==//
    public void addItem(RecipeItem item) {
        items.add(item);
        item.setRecipe(this);
    }

    // ✅ 점주와 N:1 관계 설정
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;
}
