# 냉장GOAT — Procurement-Centered Inventory Backend for Independent Restaurants

> A Spring Boot 3.5 / Java 21 backend designed around a single interview-derived insight: **for an independent restaurant owner, procurement is the largest single consumer of time** (per-week phone/text orders to 5+ vendors, weekly cost-rate calculations, lost sales from stockouts, ad-hoc borrowing from sister restaurants when an order is missed). Every architectural decision in this project is aligned to one of three **procurement-decision axes** — *priority* (what runs out first), *price* (where to buy at what cost), and *timing* (when to order). The engineering pillars below — concurrency control and multi-source price integration — are not separate showcases; they are the technical means to those procurement-decision ends.

🇰🇷 한국어 버전: [README.ko.md](README.ko.md)
📜 v1 시점 README (역사적 보존): [README_0315.md](README_0315.md)

---

## 🥩 The Pain Point (Where the Project Started)

Two field interviews — a barbecue place in Songtan and a 막창 (grilled-tripe) restaurant in Yongin — surfaced a remarkably consistent pattern. The owner's most expensive recurring activity, week after week, was procurement:

| Pain | Source | Frequency |
|---|---|---|
| Sending individual texts/calls to ~5 vendors per ordering cycle | Songtan | weekly |
| Stockouts that block sales | Songtan | 3–6 / month |
| Borrowing missing ingredients from a sister restaurant after a missed order | Yongin | confirmed |
| Manual cost-rate calculation (40–50% margin tracking) | Yongin | continuous |
| Hand-counted inventory → manual order-quantity arithmetic | both | 3–5 hours / week |

A 2026-04-05 reframing (`plan.md`) made this explicit: "the engine is a Lamborghini but there is no driver's seat." The engineering invariants this project was already building — concurrency, integrity, fallback — were correct, but the *user* (the owner) had no reason to pay for any of them. The project was repositioned around the owner's procurement workflow, and the three core use cases were aligned accordingly:

| Procurement-decision axis | Use case | Question it answers |
|---|---|---|
| Priority | UC-CORE-1 — top 5 inventory at risk | *"What is about to run out?"* |
| Price | UC-CORE-2 — top 5 lowest unit-price across sources | *"Where, at what price, should I buy it?"* |
| Timing | UC-CORE-3 — order-timing forecast + alert | *"When should I place the order?"* |
| (history) | UC-SUP-8 — purchase-order ledger | (data substrate for V1 trend / forecast features) |

---

## ✨ At a Glance

- **Capstone team project** — backend lead role
- **Two engineering pillars, one business goal**
  - Pillar 1 (v1, verified): **Concurrency control** — four runtime-swappable lock strategies + Resilience4j circuit-breaker fallback. Supports the multi-channel side of procurement (POS · delivery apps · kiosk all touching the same inventory) and proves data integrity before any of the higher-level procurement features can rely on it.
  - Pillar 2 (v2, designed & compiling): **Multi-source price integration** — KAMIS public-market data + Naver Shopping API + a B2B Selenium crawler unified through MySQL `GENERATED COLUMN STORED`. Implements the *price* axis (UC-CORE-2).
- **🚧 Honest gap** — the *timing* axis (UC-CORE-3) and the purchase-order ledger (UC-SUP-8) — i.e. the parts most directly tied to "saving the owner's time" — **are not yet implemented**. Roadmap below; English-portfolio claims are scoped accordingly.

---

## 🎯 Three Procurement-Decision Axes vs. Current State

| Axis | Use case | Current state | Code reality |
|---|---|---|---|
| Priority | UC-CORE-1 — top 5 inventory at risk | 🟡 **Partial** | `InventoryBatch` FIFO query exists and is used by `StockDeductionService`; the dedicated top-5 endpoint, color-grade response, and depletion-day estimate are not yet implemented |
| **Price** | UC-CORE-2 — top 5 lowest unit-price | 🟡 **v2 designed** | `pricing/` module: 17 new files; `compileJava` passes; V001 migration SQL written; **integration tests = 0**, V001 not yet applied to dev DB |
| **Timing** | **UC-CORE-3 — order-timing forecast + alert** | ❌ **Not implemented** | No `PurchaseOrder` entity, no `/orders/forecast` endpoint, no alert dispatcher, no order-day configuration model — zero lines of code yet |
| (history) | **UC-SUP-8 — purchase-order ledger** | ❌ **Not implemented** | No `PurchaseOrder` entity, no `/purchase-orders` endpoint, no order-confirmation flow |

> **Implication for portfolio framing.** This project's headline business promise — *helping the owner spend less time on procurement* — depends on UC-CORE-3 + UC-SUP-8, which are still on the roadmap. What is *built* and verifiable today is **the technical substrate that those features will sit on**: concurrency-correct order processing (Pillar 1) and the price-comparison data plane (Pillar 2). Claims like "reduced procurement time" or "saved owners 30 minutes" are not made anywhere in this README and should never appear in any derived portfolio material — they describe a promise the code does not yet keep.

---

## 🛡️ Pillar 1 — Concurrency Control (v1, Verified)

The setup. Modern restaurants take orders through three channels at once — delivery apps, in-store table-order tablets, and counter POS. With one steak left at peak time and a 0.1-second gap between two channels firing at the same row, a system without locks oversells the last unit, the kitchen makes a cancellation phone call, and procurement assumptions for the next day are silently wrong (you "sold" something you couldn't make). Procurement decisions can only be trusted if the inventory number they are based on is trusted.

```
LockStrategy (interface)
  ├─ NONE          (no lock — preserved deliberately to reproduce the failure)
  ├─ SPIN          (Redis SETNX spin lock)
  ├─ REDISSON      (Redis distributed lock — production default)
  └─ PESSIMISTIC   (MySQL SELECT FOR UPDATE)

LockStrategyHolder ── AtomicReference<LockStrategy>  (runtime-swappable)
LockStrategyFactory ── LockType enum → strategy implementation

Resilience4j CircuitBreaker — wraps REDISSON
   ↓ failures cross threshold
   ↓
Automatic fallback → PESSIMISTIC (DB-only path keeps the same correctness invariants)
```

### Verified Metrics

Initial inventory 100 g, 500 threads with `CountDownLatch`, each deducts 1 g. Theoretically correct outcome: 100 successes / 400 stock-shortage failures / 0 g remaining.

#### Integrity comparison (`ConcurrencyTest`, parameterized on `LockType`)

| Strategy | Successes | Failures | Remaining | Integrity |
|---|:---:|:---:|:---:|:---:|
| NONE | 500 | 0 | **10 g** | ❌ broken |
| SPIN | 100 | 400 | 0 g | ✅ |
| REDISSON | 100 | 400 | 0 g | ✅ |
| PESSIMISTIC | 100 | 400 | 0 g | ✅ |

The `NONE` outcome is **10 g, not −400 g**. Five hundred threads each read `quantity=100`, each writes `99`, and MySQL `REPEATABLE READ`'s Last-Writer-Wins keeps only one of those writes. **The number 10 is the empirical fingerprint of Lost Update** — and the entire reason the `NONE` strategy is preserved rather than deleted.

#### Failure-mode comparison (`ChaosTest` — `docker stop redis-test`)

| Strategy | Behaviour on Redis outage | Result |
|---|---|:---:|
| SPIN | `RedisConnectionFailureException` propagates | ❌ outage |
| REDISSON | 3 failures → CircuitBreaker `OPEN` → automatic fallback to PESSIMISTIC | ✅ uninterrupted |

Live trace from `ChaosTest`:
```
[REDISSON] Redis up: order #1 succeeded
[REDISSON] docker stop redis-test
[REDISSON] failure 1/3 → 2/3 → 3/3
[REDISSON] CircuitBreaker = OPEN
[REDISSON] order attempt → fallback to PESSIMISTIC
[REDISSON] PESSIMISTIC succeeded ✅ — service uninterrupted
```

CircuitBreaker configuration (production / test):
- Production: `slidingWindowSize=10`, `failureRateThreshold=50%`, `waitDurationInOpenState=10s`, `permittedNumberOfCallsInHalfOpenState=3`
- Test profile: `slidingWindowSize=3`, `minimumNumberOfCalls=3`, `waitDurationInOpenState=5s`

### Deliberately Excluded Numbers

This README does **not** claim TPS, P95/P99 latency, or "tens-of-thousands-transaction durability." The codebase has no measurement scripts that would back those numbers, and putting them in here would be fabrication. The integrity result above is the only thing this pillar measures, and it is reproducible from the test suite alone.

---

## 💰 Pillar 2 — Multi-Source Price Integration (v2, Designed)

The procurement decision *"where, at what price, should I buy it"* requires comparable unit prices across heterogeneous sources. The system unifies three:

```
                 ┌─────────────────────────────────────────────┐
                 │  KAMIS  (public-market wholesale data)      │
                 │  Naver Shopping API  (on-demand B2C)        │
                 │  Sikjajaewang  (B2B vertical, Selenium)     │
                 └────────────────────┬────────────────────────┘
                                      ▼
                 ┌─────────────────────────────────────────────┐
                 │   price_records  (single canonical table)   │
                 │   UNIQUE (source, raw_product_id,           │
                 │           DATE(fetched_at))   ← append-only │
                 │                                             │
                 │   unit_price_per_kg                         │
                 │     GENERATED ALWAYS AS                     │
                 │     (price * 1000 / weight_grams)           │
                 │     STORED                                  │
                 │   ← computed by the DB engine,              │
                 │     materialized, INDEX-able                │
                 └─────────────────────────────────────────────┘
                                      ▼
                 IngredientMatcher
                   @Scheduled(fixedDelay = 10 min)  + on-demand fallback
                 OnlinePriceAggregator
                   multi-source merge → lowest unit price (ties allowed)
                 BR2-11 fallback
                   when all sources empty → return search-URL CTA
```

### The B2C / B2B Mismatch That Forced a Source Change

The original v0.3 plan used Coupang and MarketKurly. Both turned out to be B2C-priced — owners actually buy from B2B vertical channels, and the price displayed on a B2C site is misleading. The decision to switch to Naver Shopping (general-purpose) + Sikjajaewang (B2B) wasn't a tooling choice, it was a self-criticism of the original assumption: *we were comparing prices the owner would never pay.* Documented in `plan_park_0423_*` series.

### `GENERATED ALWAYS AS ... STORED` — A Real Disagreement Resolved at the DB Layer

Two valid concerns collided:

- *Teammate (sim):* "Storing a derived value (`unit_price_per_kg`) violates normalization and risks divergence from the raw `price` and `weight_grams` it was computed from."
- *Park:* "I need `unit_price_per_kg` to be **indexable** so the lowest-price-across-sources query stays fast."

`GENERATED ALWAYS AS (...) STORED` resolves both at the database layer:
- The DB engine *guarantees* the derivation — application code cannot produce a divergent value
- The column is materialized on disk, so `INDEX (unit_price_per_kg)` and `ORDER BY unit_price_per_kg` are cheap
- The integrity concern is honored by MySQL's contract, not by application discipline

### Domain Precision: Liquid Density Constants

Volume-priced staples (soy sauce, fish sauce, cooking oil, sesame oil, milk, honey) skew the unit-price-per-kg calculation by ~20–40% if you naively assume 1 mL = 1 g. A six-entry density table closes that hole:

| Ingredient | Density (g/mL) |
|---|:---:|
| Soy sauce (간장) | 1.20 |
| Fish sauce (액젓) | 1.25 |
| Cooking oil (식용유) | 0.92 |
| Sesame oil (참기름) | 0.92 |
| Milk (우유) | 1.03 |
| Honey (꿀) | 1.40 |

This is five minutes of code that prevents an embarrassing demo number — the kind of thing that would make a kitchen owner say "your app says soy sauce is the cheapest, but it isn't."

### Honest Status of Pillar 2

```
✅ compileJava passes
✅ V001 migration SQL written
✅ 17 source files in pricing/ module
❌ V001 has not been applied to the dev DB
❌ Integration tests have not been written
❌ Three teammate sign-offs are pending (GENERATED STORED policy,
                                          raw_product_id NOT NULL,
                                          10-min matching cadence)
❌ Sikjajaewang crawler branch not merged
❌ DB-name unification (sim's `nangjanggoat` vs our `inventory_db`) pending
```

In English-portfolio terms: this pillar is **designed and implemented**, not **measured or production-tested**. The portfolio one-liner reflects that distinction.

---

## 👤 My Role & Responsibilities (capstone team project)

### Owned (interview-defendable in depth)

#### Pillar 1 — Concurrency
- `LockStrategy` interface, `LockType` enum, `LockStrategyHolder` (`AtomicReference`-backed runtime swap), `LockStrategyFactory`, four `*LockStrategy` implementations
- Resilience4j wiring on REDISSON with REDISSON → PESSIMISTIC fallback (production + test profiles)
- `ConcurrencyTest` (parameterized JUnit 5, 500-thread `CountDownLatch`, three integrity assertions per strategy)
- `ChaosTest` (Testcontainers `docker stop redis-test`, asserts circuit-OPEN transition)
- `processOrder` `@Transactional` decomposition — outer transaction removed; `StockDeductionService.deductFifo` carries its own `@Transactional REQUIRED` so the lock can release per thread without holding two HikariCP connections

#### Domain
- `UnitConverter` — `BigDecimal` with `HALF_UP` rounding, scale 3, full g↔kg / ml↔L matrix; rejects cross-group conversions (e.g. g→ml without density)
- `InventoryBatch` FIFO model — `expiration_date ASC` ordering, batch-level Pessimistic-lock branching via a `ThreadLocal` `ACTIVE` flag
- v2.1 entity refresh — `Ingredient`, `InventoryBatch`, `Menu`, `RecipeBom`, `Order`, `OrderItem`, `MarketPrice`; the v1 layer is **deactivated rather than deleted** (Spring annotations stripped, Javadoc-tagged `[v2.1 비활성화]`, `@NoRepositoryBean` on stale repositories) so it can be reactivated with security in V2

#### Pillar 2 — Pricing module (17 files in `pricing/`)
- `PriceRecord` entity (with the `GENERATED` column annotation contract: `@Column(insertable=false, updatable=false)`)
- `WeightParser` — single-match regex over package strings, blocks "box/set/mixed" SKUs that would defeat the unit-price calculation
- `LiquidDensity` — six-entry constants table (above)
- `IngredientMatcher` — `@Scheduled(fixedDelay = 10 min)` batch + on-demand fallback for cache misses
- `NaverOnlinePriceProvider` — on-demand external call + 30-minute DB-level cache via `price_records.fetched_at`
- `OnlinePriceAggregator` — multi-source merge with lowest-unit-price selection (ties preserved)
- `PriceController` — `/prices/lowest-top`, `/prices/{ingredientId}`
- `BR2-11` fallback CTA — when all sources return empty for an ingredient, return a search-URL link instead of a blank screen

#### Migration & docs
- `V001__price_records_extension.sql` — GENERATED column DDL, append-only UNIQUE on `(source, raw_product_id, DATE(fetched_at))`
- `plan_park_0423_01`, `plan_park_0423_02 (v2)`, `v04_diff_proposal_park_0423`, `plan_park_0426_01`

### Team-led (boundary acknowledged)

- **Sikjajaewang B2B Python crawler** (Selenium + webdriver-manager, 13 categories) — separate branch (`origin/feat/crawler-ewangmart`), teammate-owned. Cross-DB merge into `inventory_db` is pending.
- **v0.3 → v0.4 use-case document integration** — teammate consolidated the spec; my contribution was the diff proposal and the pricing-side schema agreement.

### Pivots tracked in `plan.md`

The user-centric repositioning (procurement-as-the-pain-point) is documented in [plan.md](plan.md), which lists the Demo / V1 / V2 release scopes. The headline V1 features (KakaoTalk inventory alerts, automatic cost-rate calculation, ordering message helper, simplified inbound entry, sales dashboard) trace one-to-one back to specific interview pain points; status of each is in the roadmap section below.

---

## 🏛️ Architecture

```
                           ┌──────────────────────────────┐
                           │  POS / Delivery App / Kiosk  │
                           └──────────────┬───────────────┘
                                          │ X-User-Id (MockAuthFilter — JWT
                                          │            intentionally disabled
                                          │            during the v2.1 refactor)
                                          ▼
            ┌──────────────────────────────────────────────────────────┐
            │  Spring Boot 3.5.7 (Java 21)                             │
            │                                                          │
            │  ┌─── OrderService.processOrder() (no @Transactional) ──┐│
            │  │                                                      ││
            │  │  LockStrategyHolder.get()  →  REDISSON (default)     ││
            │  │      │ executeWithLock()                              ││
            │  │      ▼                                                ││
            │  │  StockDeductionService.deductFifo()                  ││
            │  │      └─ @Transactional REQUIRED                      ││
            │  │      └─ FIFO over InventoryBatch (expiration ASC)    ││
            │  │      └─ BigDecimal + HALF_UP unit normalization      ││
            │  │                                                      ││
            │  │  if Circuit Breaker OPEN:                            ││
            │  │      fallback → PessimisticLockStrategy              ││
            │  │      └─ SELECT FOR UPDATE                            ││
            │  └──────────────────────────────────────────────────────┘│
            │                                                          │
            │  ┌─── pricing/ module (v2, designed) ──────────────────┐ │
            │  │  IngredientMatcher  @Scheduled(10 min) + on-demand  │ │
            │  │  OnlinePriceAggregator  (multi-source merge)        │ │
            │  │  NaverOnlinePriceProvider  (30-min DB cache)        │ │
            │  │  PriceController  /prices/lowest-top                │ │
            │  └─────────────────────────────────────────────────────┘ │
            │                                                          │
            │  🚧 UC-CORE-3 (timing) + UC-SUP-8 (history): NOT YET     │
            └──────────────────┬─────────────────────────┬─────────────┘
                               │                         │
                               ▼                         ▼
            ┌──────────────────────────────┐  ┌────────────────────────┐
            │  MySQL  inventory_db         │  │  Redis 7               │
            │  • InventoryBatch (FIFO)     │  │  • Redisson lock       │
            │  • Ingredient / Menu /       │  │  • SETNX spin lock     │
            │    RecipeBom                 │  │                        │
            │  • Order / OrderItem         │  │                        │
            │  • MarketPrice (KAMIS)       │  │                        │
            │  • price_records             │  │                        │
            │      └ unit_price_per_kg     │  │                        │
            │        GENERATED STORED      │  │                        │
            └──────────────────────────────┘  └────────────────────────┘
```

> **The `processOrder` no-`@Transactional` decision** is the most concentrated piece of design in Pillar 1. With 500 threads × `REQUIRES_NEW`, HikariCP enters deadlock because each thread asks for two connections (the outer transaction + the inner deduction transaction). Removing the outer `@Transactional` and letting the inner `StockDeductionService.deductFifo` carry its own short-lived transaction inside the lock — that one change is what makes the entire 500-thread test survive.

---

## 🔧 Tech Stack

| Layer | Technology | Source-verified at |
|---|---|---|
| Language | Java 21 | `build.gradle:13` (`JavaLanguageVersion.of(21)`) |
| Framework | Spring Boot 3.5.7 | `build.gradle:3` |
| ORM | Spring Data JPA + Hibernate | `build.gradle:28` |
| DB | MySQL 8 (`inventory_db`) | `application.properties:7` |
| **DB GENERATED column** | `STORED` derived `unit_price_per_kg` | `V001__price_records_extension.sql` |
| Scheduler | `@EnableScheduling` (active) | `InventorySystemApplication.java:7` |
| In-memory store | Redis (`spring-boot-starter-data-redis`) | `build.gradle:29` |
| Distributed lock | Redisson 3.23.2 | `build.gradle:43` |
| Circuit Breaker | Resilience4j 2.1.0 (Spring Boot 3 starter) | `build.gradle:46` |
| Security | Spring Security + jjwt 0.12.3 (code present, intentionally **disabled** during v2.1 refactor; `MockAuthFilter` parses `X-User-Id` header) | `SecurityConfig.java:28` (`// @Configuration`) |
| Batch (planned) | Spring Batch (dependencies present, Job/Reader/Writer disabled) | `KamisPriceBatchJobConfig.java:23` |
| Testing | JUnit 5 + `spring-boot-starter-test` | `build.gradle:37,52` |
| AOP | `spring-boot-starter-aop` | `build.gradle:47` |
| External price source #1 | Naver Shopping API (on-demand + 30-min cache via `price_records.fetched_at`) | `NaverOnlinePriceProvider.java` |
| External price source #2 | Sikjajaewang B2B (Selenium, separate branch) | `crawler/ewangmart/main.py` |

> **Honestly missing.** No `Dockerfile`, no `docker-compose.yml`, no Kubernetes manifests, no Terraform, no CI/CD workflow. This is intentional: the project is a *backend domain* showcase, and the infra showcase lives in a separate portfolio piece.

---

## 🚦 Backend Deep Dives

### 1. Reproducing the Lost-Update Fingerprint

```
500 threads each: read quantity → write (quantity − 1)
                 │
                 │ no lock                            with lock
                 ▼                                     ▼
           Last-Writer-Wins                       serialised access
                 │                                     │
                 ▼                                     ▼
        100 g → 10 g (after 500 races)          100 g → 0 g (after 100 successes)
```

The `NONE` strategy is **deliberately preserved** because reproducing the failure mode is the proof of the entire pillar. The number 10 g is an empirical fingerprint of MySQL `REPEATABLE READ`'s Last-Writer-Wins: 500 threads each read `quantity=100`, each writes `99`, and only some of those writes survive serialization.

### 2. The HikariCP Deadlock Resolution

```
Before:                                      After:
processOrder      @Transactional             processOrder      (no transaction)
  └─ deductFifo   @Transactional REQUIRES_NEW   └─ deductFifo   @Transactional REQUIRED
        │                                            │
        ▼                                            ▼
   thread × 2 conns                              thread × 1 conn
        │                                            │
        ▼                                            ▼
   500 × 2 = 1000 conn  →  pool deadlock         500 × 1 = 500 conn  →  fits, integrity holds
```

Three rounds of test failures pointed to this. Round 1: integrity held only on `PESSIMISTIC`, the other strategies failed. Round 2: extracting `StockDeductionService` as a separate bean partially helped (transaction-scope isolation). Round 3: removing the *outer* `@Transactional` on `processOrder` was the actual fix. Documented in [`dev_record_0315.md`](dev_record_0315.md).

### 3. Resilience4j as the Right Tool for the Right Job

Most Resilience4j tutorials wrap an HTTP client. Here it wraps the **distributed-lock acquisition** — a deliberate decision because the failure surface that matters in this system is "Redis goes down," not "another microservice times out." The fallback isn't a static error response either; it's a **strategy swap** to the DB-only Pessimistic path that preserves the same correctness invariants. Service stays up; the locking mechanism degrades.

### 4. Why `BigDecimal`, Not `Double`

The unit-conversion matrix (g ↔ kg, ml ↔ L) multiplies by 1000 routinely. `0.1 + 0.2 ≠ 0.3` in IEEE 754; over tens of thousands of inventory deductions that error accumulates and quietly desynchronizes recorded inventory from physical reality. Once the inventory number is wrong, every downstream procurement signal — low-stock alert (UC-CORE-1), depletion-day forecast (UC-CORE-3) — is also wrong. `BigDecimal` with `HALF_UP` rounding at scale 3 is the single point where this entire class of bug is closed off.

### 5. FIFO via `InventoryBatch`

Total-quantity inventory tables can't answer *"which lot of this ingredient should be used first."* `InventoryBatch` represents one inbound shipment, with `expiration_date`, `lot_unit_price`, and quantity. Deduction sorts by `expiration_date ASC` so the closest-to-expiry lot is consumed first. This single design choice unlocks three downstream procurement features at once: D-3 expiration warnings, cost calculation against the actual lot price (not an average), and theoretical-vs-actual variance analysis.

### 6. Pillar 2 — `GENERATED COLUMN STORED` as a Resolution

(See *Pillar 2* section above for the full story.) The compressed version: a teammate's normalization concern and my indexability requirement looked like a tradeoff but weren't — `GENERATED ALWAYS AS (...) STORED` honors both, with the DB engine enforcing derivation correctness while still letting the materialized column be indexed.

---

## 📐 Architecture Decisions (ADRs)

The five load-bearing decisions:

1. **`LockStrategy` interface + runtime swap.** Locks are infrastructure, not business code; they should be replaceable without touching call sites. The strategy pattern also makes the comparison study (one-size-fits-all vs. context-appropriate) literally a parameterized test.
2. **Pessimistic Lock as the *fallback*, not the primary.** Pessimistic locking has a connection-hold cost; the system pays that cost only when Redis is down, not in the steady state.
3. **`BigDecimal` over `Double` for all quantity fields.** Catastrophic procurement-decision errors hide in floating-point drift.
4. **`InventoryBatch` over a single total-quantity column.** FIFO, per-lot cost, and expiration-driven alerts are all untenable without per-shipment rows.
5. **`GENERATED COLUMN STORED` for `unit_price_per_kg`.** Resolved an integrity-vs-performance disagreement at the DB layer instead of the application layer.

---

## 🚧 Status & Roadmap

### Pillar 1 (Concurrency) — verified

| | Status |
|---|---|
| 4 lock strategies | ✅ implemented |
| `ConcurrencyTest` (500 threads, parameterized) | ✅ passing under all four strategies |
| `ChaosTest` (`docker stop redis-test`) | ✅ passing |
| Resilience4j REDISSON → PESSIMISTIC fallback | ✅ wired |

### Pillar 2 (Pricing) — designed, awaiting integration tests + team sign-off

| | Status |
|---|---|
| `pricing/` module (17 files) | ✅ `compileJava` passes |
| V001 SQL migration | ✅ written, ⏳ not yet applied to dev DB |
| Integration tests | ❌ none yet |
| Three teammate sign-offs | ⏳ pending (`GENERATED STORED` policy, `raw_product_id NOT NULL`, 10-min matching cadence) |
| Sikjajaewang crawler branch merge | ⏳ pending |
| DB-name unification (`nangjanggoat` ↔ `inventory_db`) | ⏳ pending |

### Procurement-decision axes — current vs. planned

| Axis | Use case | Status |
|---|---|---|
| Priority | UC-CORE-1 — top 5 inventory at risk | 🟡 partial — FIFO query exists, dedicated endpoint not yet |
| Price | UC-CORE-2 — top 5 lowest unit-price | 🟡 v2 designed (Pillar 2) |
| **Timing** | **UC-CORE-3 — order-timing forecast + alert** | ❌ **roadmap** — `PurchaseOrder` entity, `/orders/forecast`, alert dispatcher all unwritten |
| (history) | **UC-SUP-8 — purchase-order ledger** | ❌ **roadmap** — `PurchaseOrder` entity, `/purchase-orders` endpoints all unwritten |

### Demo / V1 / V2 phases (from `plan.md`)

| Phase | Scope |
|---|---|
| **Demo** (front-end pending) | Four `POST` endpoints — `/ingredients`, `/menus`, `/ingredients/{id}/batches`, `/menus/{id}/bom` |
| **V1** (post-demo) | KakaoTalk inventory alerts · automatic cost-rate calculation · ordering message helper · simplified inbound entry · daily/weekly sales dashboard |
| **V2** (post-validation) | Receipt OCR auto-inbound · AI ordering suggestions · ROI simulation · JWT re-enabled · nGrinder load testing |

### Honest deferrals (intentional, scope-out)

- Docker / Docker Compose / Kubernetes / Terraform — out of scope for this project; covered by a different portfolio piece
- CI/CD pipeline — out of scope
- Production monitoring (Actuator / Micrometer / Prometheus) — V2 scope, currently SLF4J only
- KAMIS scheduled batch — code present; the scheduler `@Component` annotation is currently disabled and will be restored when the `MarketPrice` entity refresh stabilizes

---

## 🚀 Local Development

```bash
# Redis
docker run -d --name redis-test -p 6379:6379 redis:7-alpine

# Build & run
./gradlew bootRun

# Concurrency integrity test (4 strategies, 500 threads)
./gradlew cleanTest test --tests "*.ConcurrencyTest" --info

# Chaos test (Redis outage → CircuitBreaker fallback)
./gradlew test --tests "*.ChaosTest" --info
```

All endpoints currently require the `X-User-Id` header. The production JWT layer is intentionally disabled during the v2.1 refactor; see `MockAuthFilter` and the deactivation log in [`dev_record_0315.md`](dev_record_0315.md).

---

## 📚 Reference Documents

- [README_0315.md](README_0315.md) — v1 snapshot with detailed concurrency narrative + interview Q&A
- [plan.md](plan.md) — User-centric pivot plan (Demo / V1 / V2 phases) with interview-derived pain points
- [strategy.md](strategy.md) — Initial strategy document (project-identity statement, 4-strategy architecture, AI-suggestion 3-phase approach)
- [dev_record_0315.md](dev_record_0315.md) — v1 development log including the three-round HikariCP-deadlock debugging
- [HELP.md](HELP.md) — Spring Boot scaffold help

The `plan_park_*.md` series and `v04_diff_proposal_park_0423.md` are working documents kept in project memory; they capture the four-day compressed progress (2026-04-23 → 2026-04-26) that produced the v2 pricing module.

---

## 👤 Author & Team

**Park, Gunwoo (gm-15)** — Backend Lead (capstone team project)
- Sangmyung University, Software Engineering
- GitHub: [github.com/gm-15](https://github.com/gm-15)
- Blog: [velog.io/@gm-15](https://velog.io/@gm-15)
- Email: gunwoo363@gmail.com

The capstone teammate is responsible for the Sikjajaewang B2B Python crawler (separate branch) and the v0.3 → v0.4 use-case document integration.
