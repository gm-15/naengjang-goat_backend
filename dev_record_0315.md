# 냉장GOAT v2.1 — 개발 실행 기록

> 작성일: 2026-03-16
> 기준 계획서: `plan_0315.md`
> 결과: Phase 1 ~ Phase 4 전 항목 완료, 동시성 테스트 의도대로 통과

---

## 최종 테스트 결과 (Phase 4)

```
전략          성공   실패   남은재고   판정
────────────────────────────────────────────
NONE          500    0     10.000g   ✅ 동시성 붕괴 재현 (Lost Update)
SPIN          100    400   0.000g    ✅ 정합성 보장
REDISSON      100    400   0.000g    ✅ 정합성 보장
PESSIMISTIC   100    400   0.000g    ✅ 정합성 보장
```

초기 재고 100g, 500 스레드 각 1g 차감 동시 요청.
NONE은 락 없이 500건 전부 통과해 재고 오차 발생.
나머지 3전략은 정확히 100건 성공, 400건 재고 부족 실패, 잔여 재고 0g.

---

## Phase 1 — 도메인 재설계 + 기반 정리

### 1-1. 비활성화 처리 (삭제 아닌 주석 처리)

Spring 빈 등록 어노테이션을 제거하고 `[v2.1 비활성화]` Javadoc 헤더 추가.
Repository는 `@NoRepositoryBean` 추가로 JPA 자동 구현 차단.

**비활성화 파일 목록**

| 파일 | 처리 | 비활성화 사유 |
|------|------|------------|
| `SecurityConfig.java` | `@Configuration` 제거 | MockAuthFilter로 대체 |
| `JwtAuthenticationFilter.java` | `@Component` 제거 | MockAuthFilter로 대체 |
| `TokenProvider.java` | `@Component` 제거 | MockAuthFilter로 대체 |
| `UserDetailsServiceImpl.java` | `@Service` 제거 | MockAuthFilter로 대체 |
| `Inventory.java` | `@Entity` 제거 | InventoryBatch로 대체 |
| `SaleHistory.java` | `@Entity` 제거 | Order로 대체 |
| `SaleItem.java` | `@Entity` 제거 | OrderItem으로 재설계 |
| `RawMaterial.java` | `@Entity` 제거 | Ingredient로 대체 |
| `Recipe.java` (구 메뉴 엔티티) | `@Entity` 제거 | Menu로 대체 |
| `RecipeItem.java` | `@Entity` 제거 | RecipeBom으로 대체 |
| `PriceHistory.java` | `@Entity` 제거 | MarketPrice로 대체 |
| `InventoryRepository.java` | `@NoRepositoryBean` 추가 | InventoryBatchRepository로 대체 |
| `SaleHistoryRepository.java` | `@NoRepositoryBean` 추가 | OrderRepository로 대체 |
| `SaleItemRepository.java` | `@NoRepositoryBean` 추가 | OrderItemRepository로 대체 |
| `RawMaterialRepository.java` | `@NoRepositoryBean` 추가 | IngredientRepository로 대체 |
| `RecipeRepository.java` | `@NoRepositoryBean` 추가 | MenuRepository로 대체 |
| `RecipeItemRepository.java` | `@NoRepositoryBean` 추가 | RecipeBomRepository로 대체 |
| `PriceHistoryRepository.java` | `@NoRepositoryBean` 추가 | MarketPriceRepository로 대체 |
| `InventoryService.java` | `@Service` 제거 | OrderService + StockDeductionService로 대체 |
| `SaleService.java` | `@Service` 제거 | OrderService로 대체 |
| `InventoryController.java` | `@RestController` 제거 | OrderController로 대체 |
| `RecipeService.java` | `@Service` 제거 | 메뉴 관련 로직 분리 |
| `RecipeController.java` | `@RestController` 제거 | 메뉴 관련 분리 |
| `AnalysisService.java` | `@Service` 제거 | MarketPrice 기반으로 재작성 예정 |
| `AnalysisController.java` | `@RestController` 제거 | 위와 동일 |
| `PriceHistoryService.java` | `@Service` 제거 | MarketPriceRepository 기반으로 재작성 예정 |
| `KamisPriceBatchJobConfig.java` | `@Configuration` 제거 | MarketPrice 엔티티 전환 후 복구 예정 |
| `KamisPriceProcessor.java` | `@Component` 제거 | 위와 동일 |
| `KamisPriceWriter.java` | `@Component` 제거 | 위와 동일 |
| `BatchScheduler.java` | `@Component` 제거 | Job 빈 없음 (KamisPriceBatchJobConfig 비활성화) |
| `UserService.java` | `@Service` 제거 | MockAuth 환경에서 불필요 |
| `UserController.java` | `@RestController` 제거 | MockAuth 환경에서 불필요 |

### 1-2. 신규 엔티티 생성

| 클래스 | 테이블 | 역할 |
|--------|--------|------|
| `Ingredient` | `ingredient` | 원재료 마스터 (base_unit, warning_threshold) |
| `InventoryBatch` | `inventory_batch` | FIFO 재고 배치 (expiration_date, quantity: BigDecimal) |
| `Menu` | `menu` | 판매 메뉴 |
| `RecipeBom` | `recipe` | 메뉴-재료 BOM (required_quantity: BigDecimal, unit) |
| `Order` | `orders` | 주문 이력 (channel_type: POS/DELIVERY/KIOSK) |
| `OrderItem` | `order_item` | 주문 상세 (unit_price 스냅샷) |
| `MarketPrice` | `market_price` | KAMIS 시세 (retail_price, wholesale_price) |

**주요 설계 결정**
- 수량 연산 전체 `Double → BigDecimal` 전환 (부동소수점 오차 제거)
- `InventoryBatch.expiration_date ASC` 정렬로 FIFO 구현
- `OrderItem.unit_price`: 판매 시점 가격 스냅샷 (이후 가격 변동과 독립)
- `Order.channel_type`: POS/DELIVERY/KIOSK 다채널 동시성 시나리오의 핵심

### 1-3. 신규 Repository 생성

| Repository | 핵심 쿼리 |
|-----------|---------|
| `IngredientRepository` | Fetch Join으로 N+1 제거 |
| `InventoryBatchRepository` | FIFO 정렬, PESSIMISTIC_WRITE 쿼리 분리 |
| `MenuRepository` | BOM Fetch Join |
| `RecipeBomRepository` | menuId 기준 조회 |
| `OrderRepository` | userId + 기간 조회, Fetch Join |
| `OrderItemRepository` | orderId 기준 조회 |
| `MarketPriceRepository` | 최근 30건, 기간 조회 |

### 1-4. 신규 유틸리티 생성

**`MockAuthFilter`** (`global/filter/`)
- Spring Security/JWT 전체 비활성화 후 개발 편의를 위해 대체
- `X-User-Id` 헤더를 읽어 `request.setAttribute("userId", ...)`로 주입
- 헤더 없으면 401, 숫자 아닌 값이면 400 반환

**`UnitConverter`** (`global/util/`)
- g↔kg, ml↔L 단위 변환 (×1000)
- 전 연산 `BigDecimal` + `HALF_UP` 반올림
- 단위 그룹 불일치 시 `IllegalArgumentException` (g→ml 같은 경우)
- BOM의 `unit` 필드와 재고 `base_unit` 사이를 정규화

---

## Phase 2 — LockStrategy 아키텍처

### 2-1. 설계 의도

4가지 락 전략을 런타임에 전환 가능하도록 전략 패턴으로 구현.
테스트 시 `LockStrategyHolder`에서 `AtomicReference`로 교체, 운영 시 REDISSON 고정.

```
global/lock/
├── LockType.java            (enum: NONE, SPIN, REDISSON, PESSIMISTIC)
├── LockStrategy.java        (interface: executeWithLock(key, Callable))
├── LockStrategyHolder.java  (AtomicReference<LockType>, 기본값: REDISSON)
├── LockStrategyFactory.java (LockType → 구현체 매핑)
└── impl/
    ├── NoLockStrategy.java           즉시 실행 (락 없음)
    ├── SpinLockStrategy.java         Redis SETNX 스핀 루프
    ├── RedissonLockStrategy.java     @CircuitBreaker + Fallback
    └── PessimisticLockStrategy.java  @Transactional + SELECT FOR UPDATE
```

### 2-2. 전략별 특성

| 전략 | 방식 | 용도 |
|------|------|------|
| NONE | 락 없음 | 붕괴 재현 (테스트 전용) |
| SPIN | Redis SETNX 반복 획득 | 단순 구현 비교용 |
| REDISSON | Pub/Sub 대기 + 서킷 브레이커 | **운영 기본** |
| PESSIMISTIC | DB `SELECT FOR UPDATE` | Redis 장애 시 Fallback |

### 2-3. Resilience4j 서킷 브레이커

`RedissonLockStrategy`에 `@CircuitBreaker(name="redisLock")` 적용.
Redis 장애 시 `fallbackToPessimistic()`이 자동 호출돼 PESSIMISTIC으로 전환.

```properties
resilience4j.circuitbreaker.instances.redisLock.slidingWindowSize=10
resilience4j.circuitbreaker.instances.redisLock.failureRateThreshold=50
resilience4j.circuitbreaker.instances.redisLock.waitDurationInOpenState=10s
resilience4j.circuitbreaker.instances.redisLock.permittedNumberOfCallsInHalfOpenState=3
```

### 2-4. OrderService + StockDeductionService 설계

핵심 트랜잭션 설계 결정:

```
processOrder (OrderService)
  → @Transactional 없음

    executeWithLock(key, () -> {
        stockDeductionService.deductFifo(...)   ← @Transactional (REQUIRED)
    })
    → 락 범위 안에서 독립 트랜잭션이 즉시 커밋
    → 다음 스레드가 락을 획득하면 항상 최신 커밋 값 읽음
```

**왜 @Transactional을 제거했는가?**
processOrder에 @Transactional이 있으면 각 스레드가 외부 트랜잭션(C1)을 보유한 채
StockDeductionService의 REQUIRES_NEW가 C2를 추가로 요구.
500 스레드 × 2커넥션 = HikariCP 풀 소진 → 커넥션 데드락 발생.

**PESSIMISTIC 전략은 어떻게 동작하는가?**
PessimisticLockStrategy.executeWithLock이 @Transactional 컨텍스트를 제공하면
StockDeductionService.deductFifo(REQUIRED)가 해당 트랜잭션에 합류.
findAllByIngredientIdWithPessimisticLock → SELECT FOR UPDATE가 DB 레벨 직렬화.

---

## Phase 3 — 프론트엔드

프론트 팀원이 담당. plan_0315.md의 화면 명세 전달 완료.
(POS 주문 화면 / 재고 대시보드 / 판매 이력 화면)

---

## Phase 4 — 동시성 부하 테스트

### 테스트 구성

```java
// ConcurrencyTest.java
@SpringBootTest
@ActiveProfiles("test")
@ParameterizedTest
@EnumSource(LockType.class)
// 500 스레드, CountDownLatch로 동시 출발
// 초기 재고: 100g (1 배치)
// 주문 1건당 차감: 1g
// 예상: 100건 성공, 400건 재고 부족 실패, 남은재고 0
```

### 발생한 문제 및 해결 과정

| 문제 | 원인 | 해결 |
|------|------|------|
| ApplicationContext 로드 실패 | BatchScheduler가 비활성화된 Job 빈 주입 시도 | BatchScheduler `@Component` 제거 |
| `Not a managed type: RawMaterial` | 비활성화 Repository가 @Entity 없는 엔티티 참조 | 7개 Repository에 `@NoRepositoryBean` 추가 |
| `Field 'price' doesn't have a default value` | 구 Recipe 테이블에 price 컬럼 잔존 | MySQL에서 직접 `ALTER TABLE recipe DROP COLUMN price/name/user_id` |
| SPIN/REDISSON 정합성 실패 (2차) | REQUIRES_NEW + 외부 트랜잭션 → 커넥션 풀 데드락 | `processOrder`에서 `@Transactional` 제거, `StockDeductionService.deductFifo`를 일반 `@Transactional`로 변경 |
| SPIN/REDISSON 정합성 실패 (1차) | 락 해제 후 트랜잭션 미커밋 → MVCC 스냅샷 문제 | 별도 빈(`StockDeductionService`) 분리로 트랜잭션 범위 조정 |

### 최종 수치

```
전략          스레드  성공   실패   남은재고   정합성
────────────────────────────────────────────────────
NONE          500    500    0     10g        ❌ 붕괴 (Lost Update 재현)
SPIN          500    100    400   0g         ✅ 보장
REDISSON      500    100    400   0g         ✅ 보장
PESSIMISTIC   500    100    400   0g         ✅ 보장
```

> NONE 남은재고가 -400g이 아닌 10g인 이유:
> 500 스레드가 동시에 quantity=100을 읽고 각각 -1씩 저장.
> MySQL REPEATABLE READ에서 Last-Writer-Wins → 최종값이 100-N개의 실제 커밋만 반영.
> 붕괴는 수치(10g ≠ 0g)로 입증됨.

---

## 주요 아키텍처 결정 요약

```
1. 비활성화 = 삭제 아님
   → Spring 어노테이션만 제거, 코드 보존
   → 실 배포 시 Security/JWT 복구 가능

2. 재고 단위 = BigDecimal
   → Double 사용 금지 (부동소수점 오차)
   → UnitConverter로 g/kg/ml/L 정규화 후 차감

3. FIFO = expiration_date ASC 정렬
   → InventoryBatch를 유통기한 오름차순으로 소진

4. 운영 락 전략 = REDISSON + Circuit Breaker → PESSIMISTIC Fallback
   → Redis 장애 시 자동 전환, 무중단 운영

5. 트랜잭션 설계
   → processOrder: @Transactional 없음
   → StockDeductionService.deductFifo: @Transactional (REQUIRED)
   → 락 안에서 독립 트랜잭션 즉시 커밋 → 다음 스레드가 최신값 읽음
```
