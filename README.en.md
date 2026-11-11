# NaengjangGOAT — Food Procurement Decision Support Platform

A Spring Boot 3.5 / Java 21 backend that connects a restaurant's existing POS sales data with public market prices to support three decisions: **what is in stock, when to buy, and how much to buy.**

**What this service does and does not do**

- The system calculates and **recommends** order quantities and shortage forecasts
- The owner buys **directly** on an external shopping site
- Purchase records and actual goods receipt are tracked **separately**
- It does not place orders or process payments. Stock is **estimated from sales data**, not sensed automatically

**Layout:** the repository root is the Spring backend, `frontend/` is the web client, `mobile/` is the React Native app.

**Run & demo:** [Integrated service runbook](docs/integration/RUNBOOK.md) · [Demo runbook](docs/handoff/DEMO_RUNBOOK.md) · [API contract](docs/handoff/API_CONTRACT.md)

**Data verification:** [KAMIS observation date / wholesale-retail / duplicate ingestion fixes](docs/integration/KAMIS_PRICE_FIX.md) · [Historical price CSV seeding](docs/integration/CSV_PRICE_SEED.md)

🇰🇷 Korean master: [README.md](README.md) · 📜 v1 archive: [README_0315.md](README_0315.md)

---

## At a Glance

| Item | Detail |
|---|---|
| Period | Mar 2 – Oct 16, 2026 (Software Capstone Design, 3-person team) |
| Role | Backend lead Gunwoo Park · Data Sangmuk Sim · Frontend Gunwoo Kim |
| Stack | Spring Boot 3.5.7 · Java 21 · MySQL 8 · Redis 7 · Spring Batch · Flyway · Resilience4j · FCM |
| Core flow | POS sales file → stock deduction → price comparison → order recommendation → purchase record → goods receipt |
| Verification | **75 tests passing / 0 failures or errors** (13 classes) |
| Field validation | 2 restaurants, usefulness for stock & order planning averaged 4.5 / 5, both rated 5 / 5 on willingness to adopt |
| Final state | As of the Nov 11, 2026 merge into main. **No deployed production server — local demo environment** |
| Capstone | Passed faculty review |

![NaengjangGOAT Data Flow](docs/data-flow.png)

---

## Background

The Korean B2B food ingredient market was estimated at KRW 62 trillion in 2023, growing roughly 4.7% annually.
Yet interviews with two restaurant owners showed they still sent orders by SMS to five separate suppliers, cancelled 3–6 orders a month because an ingredient had run out, and spent 3–5 hours a week checking stock by hand.

Three problems observed on site:

1. **The burden of checking stock** — owners open the fridge and count; no real-time view
2. **No basis for price judgment** — no way to tell whether today's price is good
3. **Missed order timing** — the owner does not notice stock running out until it is late

Existing apps each solve part of this, but none combined stock estimation, order timing, and market-price comparison.

---

## v0 → v2 Pivot — "The engine is a Lamborghini, but there's no steering wheel"

v0 was a concurrency-lock demo. A self-critique landed in `plan.md` on 2026-04-05.

> *An owner does not pay KRW 30,000 a month for data consistency. They pay to have worry and effort taken off their hands.*

From then on every feature was aligned to one goal: **supporting the order decision.**

A second pivot changed the input unit. The initial design deducted stock on every order, but owner interviews showed that during service hours they use their existing POS and never re-enter sales into the app. The input unit became a **daily sales file upload**, and the consistency mechanism moved with it — from a distributed lock to a per-owner DB lock plus file-hash idempotency inside a single transaction.

---

## An Owner's Day — Before, During, After Service

| Phase | Owner's situation | Feature |
|---|---|---|
| Before | "We're short on onions today." | Current stock and expiry, shortage alert, lowest price |
| During | "Sales go through the existing POS, as usual." | Stock and price lookup, goods receipt for deliveries |
| After | "How much did we use, and what should I buy tomorrow?" | POS file upload → consumption and remaining stock, daily report, price comparison, order recommendation |

Not re-entering sales into the app during service is a deliberate design choice.

---

## Three Axes of the Purchase Decision

The three axes use **different data and different criteria**. This is not one price-recommendation algorithm.

| Question | Mechanism | Underlying data | Status |
|---|---|---|:---:|
| Where to buy | Online lowest price | Shopping listings normalised to price per kg | ✅ |
| When to buy | Public-price buy-signal | KAMIS / EKAPE trends, per-category coefficient of variation | ✅ |
| How much to buy | Stock-based recommendation | Daily average consumption × days until next order day − available stock | ✅ |

Actual time saved for owners is a separate user-validation task and is kept distinct from "implemented".

---

## Key Measured Results

### [Initial design] 4-Strategy Lock Comparison — 500 threads, 100g initial stock, 1g/request

This experiment targeted the **initial structure**, which deducted stock per order. It is not a load test of the final implementation.

| Strategy | Remaining stock | Consistent | Notes |
|---|:---:|:---:|---|
| NONE (control) | **10g** | ❌ | Lost Update fingerprint — deliberately kept as regression ground truth |
| SPIN (Redis SETNX) | 0g | ✅ | TTL 30s, polling 1ms |
| REDISSON + CB | 0g | ✅ | tryLock(5s wait, 10s lease), **primary** |
| PESSIMISTIC | 0g | ✅ | Automatic CB fallback when REDISSON fails |

All three preserved consistency, so the deciding factor was **behaviour while waiting**.
A spin lock keeps retrying against Redis, so load grows with wait time, and if the work outlives the TTL it can delete a lock another request now holds. Redisson waits on a release notification and only unlocks when the current thread is the holder.

The 10g left by NONE is a measured fingerprint of Last-Writer-Wins under MySQL REPEATABLE READ.
NONE was kept in the codebase on the principle that *a test that always passes has lost its ground truth.*

ChaosTest (`docker stop redis-test`): after repeated failures the circuit breaker (slidingWindow=10, failureRate=50%) switched to PESSIMISTIC as expected.

### [Final implementation] Batch POS Ingestion · Purchase vs. Receipt Separation

| Check | Result |
|---|---|
| Same file uploaded 6× concurrently | **1** new ingestion, stock 6,000g → 2,000g deducted **once**, 1 alert |
| 6 concurrent goods receipts | **1** success (others 409), stock 2,000g → 4,000g |
| On error | If format, menu matching, unit conversion, or stock sufficiency fails, **nothing is applied** |

The upload path is serialised with **pessimistic locks on the owner row and the stock batches**, not a distributed lock.
A SHA-256 of the file bytes plus `UNIQUE(user_id, file_hash)` makes re-uploading the same file a no-op, and `UNIQUE(user_id, business_date)` rejects a different file for the same business day with 409.

### [Final implementation] 11 Regression Tests for Order Judgment

Sales volume, stock level, and order weekday are varied to assert daily average consumption, stock grade, estimated depletion date, and order alert.

- With the same sales and stock, moving the next order day from 3 to 6 days out changes the grade from sufficient to normal
- Reverse check: shifting the grade boundary 0.60 → 0.50 fails that boundary case; removing the order-day distance from the formula fails 8 of 11

### KAMIS 6-Year Buy-Signal Thresholds (2019–2024, outliers removed at z-score ≤ 1.3)

| Category | Items and CV | Threshold |
|---|---|:---:|
| Vegetables | Napa cabbage 16.1% · onion 5.0% · radish 16.8% · green onion 28.4% → avg 16.6% | **0.17** |
| Livestock | EKAPE consumer prices, 4 items avg 4.9% (pork belly 8.3%) | **0.08** |
| Seafood | Mackerel 7.7% · pollock 5.6% → avg 6.7% | **0.07** |
| Fruit | Pear 12.3% · Fuji apple 12.9% → avg 12.6% | **0.13** |
| Grain | Rice 4.5% | **0.05** |
| Processed | Low volatility, fixed without separate analysis | **0.03** |

Buy-signal condition: `currentPricePerKg < monthAvg × (1 − threshold)`

CV measures how widely a price is normally dispersed. A plain month-over-month change does not tell you whether that move is unusual *for this item*, so per-category volatility became the yardstick for the threshold.

> **The livestock 0.08 was not derived from the measured average.** It was fixed as an industry reference value at design time and kept after EKAPE measurement (4-item average 4.9%), on the assumption that B2B wholesale prices swing more than consumer prices. It happens to match pork belly (8.3%), the most volatile item measured.

### Price Data Integrity

- Fixed three storage defects: wrong observation date, double kg conversion, duplicate rows per grade
- Made price basis (wholesale/retail), observation date, and observation key explicit in the schema, with `UNIQUE(ingredient_id, source, price_basis, reported_date, observation_key)`
- Running the same batch twice left 20 stored rows with 0 duplicate combinations (verified locally 2026-10-19)
- Pre-existing rows cannot have their channel or observation date recovered, so they are preserved as `LEGACY` rather than converted by guesswork

---

## Architecture

```
[4-source price ingestion + 2-table separation]
  KAMIS  (produce wholesale XML)   ─┐
  EKAPE  (livestock public API)    ─┴─▶ market_price   ← Java runtime normalisation (KRW/kg)
                                         + price_basis · observation_key unique index

  Naver Shopping API               ─┐
  Sikjajaewang B2B crawler         ─┴─▶ price_records  ← GENERATED ALWAYS AS (price×1000/weight_grams) STORED

[Spring Batch pipeline]
  kamisPriceStep (chunk=50)
    KamisApiReader → KamisPriceProcessor → KamisPriceWriter
        ↓
  buySignalNotifyStep (tasklet)
    BuySignalNotifyTasklet → per-user aggregation → FCM notification

[Concurrency control]
  Order path        : REDISSON ──▶ CircuitBreaker ──▶ PESSIMISTIC (fallback)
  POS / receipt path: serialised by pessimistic locks on the owner row and stock batches
```

Public prices are ingested on a Spring Batch schedule; online retail prices are fetched **when the screen is opened** and cached for 30 minutes.

---

## Implemented

- ✅ Batch POS ingestion — daily sales file → recipe conversion → stock deduction, with hash idempotency, a single transaction, and per-owner serialisation
- ✅ Order judgment — recommended quantity and estimated depletion date from daily consumption, available stock, and order cycle; 11 regression tests
- ✅ Concurrency control — 4-strategy comparison with circuit-breaker fallback
- ✅ Price integration — 4 sources · 2-table separation · GENERATED COLUMN unit normalisation · observation-key unique index
- ✅ Buy-signal — thresholds from 6 years of measured data, applied per category
- ✅ Purchase record vs. goods receipt — stock is unchanged at purchase and only increases on receipt
- ✅ Notifications — UPLOAD (after sales ingestion) and OPENING (3 hours before service) events, with deduplication, 3 retries, and 24-hour expiry
- ✅ Order history — CRUD plus Excel export
- ✅ Auth — JWT (access 1h / refresh 7d) · Spring Security · per-owner ownership checks
- ✅ Schema — Flyway V001~V011, verified to apply end to end on an empty database

---

## Not Implemented

| Item | State |
|---|---|
| KakaoTalk notifications | Not started. Both surveyed restaurants ranked it first |
| In-app payment | Not started. The app currently links out to an external shopping site |
| Sales cancellation and stock restore | Only the `OrderStatus.CANCELED` enum exists; the implementation lives on a separate branch |
| Physical stock reconciliation | Only the `inventoryDay` setting exists. Waste and staff meals have no entry path, so the ledger drifts from reality |
| Automatic disposal of expired stock | Expired batches are excluded from available stock but remain in the list |
| Per-vendor POS file formats | Headers are fixed to five columns |
| Real-time POS integration | Replaced by daily file upload |

---

## Field Validation

After implementation the result was shown to two restaurants and feedback collected (late Oct 2026).

**Samsan Hoegwan Soraepogu · Daegu Banyawol Makchang**

| Item | Result |
|---|---|
| Usefulness for stock & order planning | 4 / 5 and 5 / 5 (avg 4.5) |
| Willingness to adopt at launch | 5 / 5 from both |
| Device used | Mobile phone, both |
| First item checked in the report | Sales amount / forecast shortages |

> "I don't see a downside yet." · "With reliable real-time visibility and alerts, it would be worth using."

**Where this differed from the initial requirements.** Neither restaurant used fixed suppliers; both bought vegetables at a market, and price swings by weekday and weather were their biggest pain point. That is a different operating model from the "five suppliers, orders by SMS" pattern found in the first interviews.

**Additional requests:** KakaoTalk notifications (first choice for both) · in-app payment · recording the market price at the time of each order

> **n = 2. These are cases, not statistics, and are not generalised.**

---

## Final State and Limitations

- As of the Nov 11, 2026 merge into main. **There is no deployed production server; behaviour was verified in a local demo environment.**
- Passed capstone faculty review. Review comment: **recipe-based consumption estimates have limited precision, so the owner's own management is still required.**
- That is both a weakness and a design premise. The system does not claim to know real stock exactly. It **estimates** consumption from sales data and recipes and helps the owner manage receipts and stock.
- Per-serving ingredient amounts (BOM) are entered by the owner. Template defaults are not verified recipes.
- Historical demo prices were loaded into a local database only; cloning the repository does not produce them ([seeding procedure](docs/integration/CSV_PRICE_SEED.md)).
- **Not measured:** throughput (TPS), API latency, time saved on ordering, reduction in stockouts or waste, cost savings, number of real users.

---

## Tech Stack

| Area | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.5.7 · Spring Batch · Spring Security · Spring Data JPA |
| Database | MySQL 8.0+ (GENERATED COLUMN STORED) |
| Migration | Flyway V001~V011 |
| Cache · distributed lock | Redis 7 · Redisson 3.23.2 |
| Reliability | Resilience4j 2.1.0 (CircuitBreaker) |
| Notifications | Firebase Cloud Messaging (FCM) |
| Testing | JUnit 5 · CountDownLatch · local Redis container (no Testcontainers) |
| External APIs | KAMIS (XML) · EKAPE public data portal (XML) · Naver Shopping (JSON) |
| Web (`frontend/`) | Vite 6.3 · TypeScript 5.9 · React — 73 screen components |
| Mobile (`mobile/`) | React Native 0.86 · Expo 57 · expo-router — 26 screen components |
| Crawler (`crawler/`) | Python 3 · Selenium |

---

## Role & Responsibilities

| Area | Gunwoo Park | Sangmuk Sim | Gunwoo Kim |
|---|---|---|---|
| Domain design | Order judgment criteria · pivot decisions | | |
| Concurrency | 4-strategy locks + CB · 500-thread test | | |
| POS ingestion | Upload atomicity, idempotency, serialisation | | |
| Price integration | `pricing/` module · 6-year thresholds · GENERATED COLUMN · observation integrity | EKAPE public API migration · KAMIS call fixes | |
| Purchase & receipt | Separation of purchase record and goods receipt | | |
| Notifications | FCM infrastructure · closing and pre-opening alerts | | |
| External crawler | | Sikjajaewang crawler · recipe templates | |
| Clients | | | Web and mobile screens |

---

## Local Setup

> **Start Redis first.** The `RedissonClient` bean is created at startup, so without Redis the context fails *after* Flyway has already migrated.

```bash
# 1. Redis
docker run -d --name redis-test -p 6379:6379 redis:7-alpine
docker exec redis-test redis-cli ping   # wait for PONG

# 2. Create MySQL schema
mysql -u root -p -e "CREATE DATABASE naengjang_goat_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 3. Environment variables (defaults in application.properties)
export DB_URL=jdbc:mysql://localhost:3306/naengjang_goat_db
export DB_PASSWORD=<your-password>
export KAMIS_API_KEY=<your-key>
export EKAPE_API_KEY=<your-key>      # if unset, only livestock ingestion is skipped

# 4. Run — Flyway applies V001~V011 automatically
./gradlew bootRun
```

For demo data, start with `SPRING_PROFILES_ACTIVE=demo` and call `POST /demo/setup`.
See the [demo runbook](docs/handoff/DEMO_RUNBOOK.md) for the full procedure.

Demo account: `username=demo / password=demo1234`

Obtain a JWT:
```
POST /api/users/login
{ "username": "demo", "password": "demo1234" }
```

**Note when running tests.** Some tests truncate tables, so never point them at a development database.

```bash
TEST_DB_URL=jdbc:mysql://localhost:3306/naengjang_goat_test ./gradlew test
```

---

## References

- [`docs/integration/RUNBOOK.md`](docs/integration/RUNBOOK.md) — running the integrated service
- [`docs/handoff/DEMO_RUNBOOK.md`](docs/handoff/DEMO_RUNBOOK.md) — demo procedure and fixture figures
- [`docs/handoff/API_CONTRACT.md`](docs/handoff/API_CONTRACT.md) — API contract
- [`docs/integration/KAMIS_PRICE_FIX.md`](docs/integration/KAMIS_PRICE_FIX.md) — price ingestion integrity fixes
- [`docs/integration/CSV_PRICE_SEED.md`](docs/integration/CSV_PRICE_SEED.md) — historical price seeding and its limits
- `src/main/resources/db/migration/` — Flyway V001~V011
- [README.md](README.md) · [README_0315.md](README_0315.md)

---

## Contact

**Gunwoo Park | Backend Engineer**

- Email: gunwoo363@gmail.com
- GitHub: [github.com/gm-15](https://github.com/gm-15)
- Blog: [velog.io/@gm-15](https://velog.io/@gm-15)
