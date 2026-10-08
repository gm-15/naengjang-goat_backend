package com.naengjang_goat.inventory_system.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.naengjang_goat.inventory_system.global.security.CustomUserDetails;
import com.naengjang_goat.inventory_system.global.service.FcmService;
import com.naengjang_goat.inventory_system.user.domain.Role;
import com.naengjang_goat.inventory_system.user.domain.User;
import com.naengjang_goat.inventory_system.user.repository.UserRepository;
import com.naengjang_goat.inventory_system.user.service.UserService;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {"spring.jpa.show-sql=false", "logging.level.root=WARN"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FcmTokenIntegrationTest {
  @Autowired UserRepository users;
  @Autowired UserService service;
  @Autowired TransactionTemplate tx;
  @Autowired MockMvc mvc;
  @MockitoBean FcmService fcm;
  private User first;
  private User second;
  private String deviceToken;

  @BeforeEach
  void setup() {
    first = users.save(new User("token_first_" + UUID.randomUUID(), "test", "첫째", Role.OWNER));
    second = users.save(new User("token_second_" + UUID.randomUUID(), "test", "둘째", Role.OWNER));
    deviceToken = "test-device-" + UUID.randomUUID();
  }

  @AfterEach
  void cleanup() {
    users.deleteById(first.getId());
    users.deleteById(second.getId());
  }

  private CustomUserDetails principal(User owner) {
    return new CustomUserDetails(owner.getId(), owner.getUsername(), "test",
        List.of(new SimpleGrantedAuthority("ROLE_OWNER")));
  }

  private int request(User owner, String body) throws Exception {
    return mvc.perform(patch("/api/users/fcm-token")
            .with(user(principal(owner))).contentType("application/json").content(body))
        .andReturn().getResponse().getStatus();
  }

  private String token(User owner) {
    return users.findById(owner.getId()).orElseThrow().getFcmToken();
  }

  @Test
  void registrationReassignsDeviceEvenWhenNewOwnerHasSmallerId() throws Exception {
    service.updateFcmToken(second.getId(), deviceToken);
    mvc.perform(patch("/api/users/fcm-token").with(user(principal(first)))
            .contentType("application/json").content("{\"token\":\"" + deviceToken + "\"}"))
        .andExpect(status().isNoContent());
    assertThat(token(first)).isEqualTo(deviceToken);
    assertThat(token(second)).isNull();

    service.updateFcmToken(second.getId(), deviceToken);
    assertThat(token(first)).isNull();
    assertThat(token(second)).isEqualTo(deviceToken);
  }

  @Test
  void nullClearsOnlyCurrentAccountAndCannotDetachNewOwner() throws Exception {
    service.updateFcmToken(first.getId(), deviceToken);
    assertThat(request(first, "{\"token\":null}")).isEqualTo(204);
    assertThat(token(first)).isNull();
    service.updateFcmToken(second.getId(), deviceToken);
    assertThat(request(first, "{\"token\":null}")).isEqualTo(204);
    assertThat(token(second)).isEqualTo(deviceToken);
  }

  @Test
  void repeatingRegistrationKeepsSingleOwner() {
    service.updateFcmToken(first.getId(), deviceToken);
    service.updateFcmToken(first.getId(), deviceToken);
    assertThat(token(first)).isEqualTo(deviceToken);
    assertThat(token(second)).isNull();
  }

  @Test
  void previousDeviceLogoutCannotClearNewDeviceToken() throws Exception {
    String newDevice = "new-device-" + UUID.randomUUID();
    service.updateFcmToken(first.getId(), deviceToken);
    service.updateFcmToken(first.getId(), newDevice);
    assertThat(request(first, "{\"token\":null,\"expectedToken\":\"" + deviceToken + "\"}"))
        .isEqualTo(204);
    assertThat(token(first)).isEqualTo(newDevice);
    assertThat(request(first, "{\"token\":null,\"expectedToken\":\"" + newDevice + "\"}"))
        .isEqualTo(204);
    assertThat(token(first)).isNull();
  }

  @Test
  void failedTransactionRestoresPreviousOwnerAndExistingToken() {
    String previousToken = "other-device-" + UUID.randomUUID();
    service.updateFcmToken(first.getId(), previousToken);
    service.updateFcmToken(second.getId(), deviceToken);
    assertThatThrownBy(() -> tx.execute(status -> {
          service.updateFcmToken(first.getId(), deviceToken);
          users.flush();
          throw new IllegalStateException("test rollback");
        })).isInstanceOf(IllegalStateException.class);
    assertThat(token(first)).isEqualTo(previousToken);
    assertThat(token(second)).isEqualTo(deviceToken);
  }

  @Test
  void malformedOrMissingTokenDoesNotAccidentallyClearRegistration() throws Exception {
    service.updateFcmToken(first.getId(), deviceToken);
    for (String body : List.of("{}", "{\"token\":\"\"}", "{\"token\":\"with space\"}",
        "{\"token\":\"" + "a".repeat(256) + "\"}")) {
      assertThat(request(first, body)).isEqualTo(400);
      assertThat(token(first)).isEqualTo(deviceToken);
    }
  }

  @Test
  void tokensDifferingOnlyByCaseRemainDifferentDevices() {
    service.updateFcmToken(first.getId(), deviceToken + "AA");
    service.updateFcmToken(second.getId(), deviceToken + "aa");
    assertThat(token(first)).isEqualTo(deviceToken + "AA");
    assertThat(token(second)).isEqualTo(deviceToken + "aa");
  }

  @Test
  void concurrentRegistrationNeverLeavesSameTokenOnTwoAccounts() throws Exception {
    var start = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var requests = List.of(first, second).stream().map(owner -> pool.submit(() -> {
        start.await();
        return request(owner, "{\"token\":\"" + deviceToken + "\"}");
      })).toList();
      start.countDown();
      var statuses = List.of(requests.get(0).get(15, TimeUnit.SECONDS),
          requests.get(1).get(15, TimeUnit.SECONDS));
      assertThat(statuses).allMatch(code -> code == 204 || code == 409).contains(204);
      long count = List.of(first, second).stream().filter(owner -> deviceToken.equals(token(owner)))
          .count();
      assertThat(count).isEqualTo(1);
    }
  }
}
