package com.naengjang_goat.inventory_system.workflow;

import jakarta.persistence.*;
import java.time.*;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
    name = "pos_upload",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_pos_user_date",
          columnNames = {"user_id", "business_date"}),
      @UniqueConstraint(
          name = "uk_pos_user_hash",
          columnNames = {"user_id", "file_hash"})
    })
public class PosUpload {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "business_date", nullable = false)
  private LocalDate businessDate;

  @Column(name = "file_hash", nullable = false, length = 64)
  private String fileHash;

  @Column(name = "original_filename", nullable = false)
  private String originalFilename;

  @Column(name = "sales_json", nullable = false, columnDefinition = "LONGTEXT")
  private String salesJson;

  @Column(name = "consumption_json", nullable = false, columnDefinition = "LONGTEXT")
  private String consumptionJson;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;
}
