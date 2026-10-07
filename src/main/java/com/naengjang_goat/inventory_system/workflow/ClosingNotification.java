package com.naengjang_goat.inventory_system.workflow;

import jakarta.persistence.*;
import java.time.*;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
    name = "closing_notification",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_closing_event",
            columnNames = {"user_id", "event_key"}))
public class ClosingNotification {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "event_key", nullable = false, length = 100)
  private String eventKey;

  @Column(nullable = false, length = 30)
  private String type;

  @Column(name = "business_date", nullable = false)
  private LocalDate businessDate;

  @Column(nullable = false)
  private String title;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String body;

  @Column(name = "recommendations_json", nullable = false, columnDefinition = "LONGTEXT")
  private String recommendationsJson;

  @Column(name = "delivery_status", nullable = false, length = 30)
  private String deliveryStatus = "PENDING";

  @Column(nullable = false)
  private int attempts;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "next_attempt_at", nullable = false)
  private LocalDateTime nextAttemptAt;

  private LocalDateTime sentAt;
}
