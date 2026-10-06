package com.naengjang_goat.inventory_system.workflow;

import com.naengjang_goat.inventory_system.user.domain.User;
import com.naengjang_goat.inventory_system.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Serializes stock-changing workflows for a store, including empty-stock insertion. */
@Component
@RequiredArgsConstructor
public class InventoryGate {
  private final UserRepository users;

  @Transactional(propagation = Propagation.MANDATORY)
  public User lock(Long userId) {
    return users
        .lockById(userId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "점주 없음"));
  }
}
