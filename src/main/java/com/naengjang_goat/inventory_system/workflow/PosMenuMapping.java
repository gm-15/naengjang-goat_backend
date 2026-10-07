package com.naengjang_goat.inventory_system.workflow;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
    name = "pos_menu_mapping",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_pos_mapping",
            columnNames = {"user_id", "pos_code"}))
public class PosMenuMapping {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "pos_code", nullable = false, length = 100)
  private String posCode;

  @Column(name = "menu_id", nullable = false)
  private Long menuId;
}
