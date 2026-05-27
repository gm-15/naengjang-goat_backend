# NaengjangGOAT — Food Inventory & Order Decision Support Backend

A Spring Boot 3.5 / Java 21 backend that supports procurement decisions for restaurant owners.
Integrates 4 external price sources and automatically determines buy-signals based on thresholds derived from 6 years of measured data.

🇰🇷 Korean master: [README.md](README.md) · 📜 v1 archive: [README_0315.md](README_0315.md)

---

## At a Glance

| Item | Detail |
|---|---|
| Period | Oct 2025 ~ ongoing (Capstone project) |
| Role | Backend lead (1 teammate: sim) |
| Stack | Spring Boot 3.5 · Java 21 · MySQL 8 · Redis · Spring Batch · Flyway · Resilience4j · FCM |
| Verified | 4-strategy lock comparison (500-thread chaos) · 4-source price integration · KAMIS 6-year buy-signal threshold |
| Status | Concurrency · price integration · order history CRUD implemented / UC-CORE-3 order timing prediction not yet implemented |

![NaengjangGOAT Data Flow](docs/data-flow.png)

---

## Background

The Korean food ingredient B2B market is estimated at KRW 62 trillion in 2023 (KFDA), growing ~4.7% annually.
Interviews with two restaurant owners (Songtan and Yongin locations) revealed the same pattern: SMS-based ordering to 5 suppliers, 3–6 unit-price errors per month, and 3–5 hours per week spent on manual stock checks.

Three pain points confirmed on-site:

1. **Inventory tracking fatigue** — handwritten stock checks, no real-time visibility
2. **Cost estimation by instinct** — no tool to compare order unit prices
3. **Missing optimal order timing** — no way to detect when prices are low

Existing apps (Baljugo, Sikjajaewang, Oneul-eolma) each have strengths, but none simultaneously provide inventory estimation + order timing alerts + market-price comparison.

This project's goal: automate procurement decisions by detecting stockout predictions and lowest prices simultaneously.
Current state: price integration and buy-signal detection implemented. Order timing alerts (UC-CORE-3) are not yet implemented.

---

## v0 → v2 Pivot — "The engine is a Lamborghini, but there's no steering wheel"

v0 was a concurrency lock strategy demo. A self-critique was documented in plan.md on 2026-04-05.

> *Restaurant owners pay KRW 30,000/month not for data consistency, but to reduce worry and manual effort.*

After the pivot, all features were aligned to a single goal: **procurement decision support**.

---

## Order Decision Framework: 3 Axes vs. Current Implementation

| Axis | Use Case | Status | Code Truth |
|---|---|:---:|---|
| Priority | UC-CORE-1: Top 5 low-stock items | 🟡 | FIFO query exists. Dedicated API · depletion ETA not done |
| Price | UC-CORE-2: Top 5 lowest price | 🟡 | `pricing/` 17 files build pass, 0 integration tests |
| Timing | UC-CORE-3: Order timing prediction · alerts | ❌ | `/orders/forecast` · prediction logic: 0 lines of code |
| History | UC-SUP-8: Order history CRUD | ✅ | create/list/summary + Excel export implemented |

"Time saved on ordering" expressions appear nowhere in this repository — UC-CORE-3 is not implemented.

---

## Key Measured Results

### 4-Strategy Lock Comparison — 500 threads, 100g initial stock, 1g/request

| Strategy | Remaining | Consistency | Notes |
|---|:---:|:---:|---|
| NONE (control) | **10g** | ❌ | Lost Update fingerprint — intentionally preserved as regression ground truth |
| SPIN (Redis SETNX) | 0g | ✅ | TTL 30s, polling 1ms |
| REDISSON + CB | 0g | ✅ | tryLock(5s wait, 10s lease), **1st choice** |
| PESSIMISTIC | 0g | ✅ | Auto-fallback via CB when REDISSON fails |

NONE remaining 10g: empirical fingerprint of MySQL REPEATABLE READ's Last-Writer-Wins.
"A test that always passes has lost its ground truth" — NONE preserved intentionally.

ChaosTest (`docker stop redis-test`): CB (slidingWindow=10, failureRate=50%) triggers after 3 failures, automatic PESSIMISTIC fallback confirmed.

### KAMIS 6-Year Buy-Signal Thresholds (2019–2024, outliers removed at z-score ≤ 1.3)

| Category | Products & CV | Threshold |
|---|---|:---:|
| Vegetables | Cabbage 16.1% · Onion 5.0% · Radish 16.8% · Green Onion 28.4% → avg 16.6% | **0.17** |
| Livestock | EKAPE consumer price 4 items avg 4.9%, conservative B2B upward adjustment | **0.08** |
| Seafood | Mackerel 7.7% · Pollack 5.6% → avg 6.7% | **0.07** |
| Fruits | Pear 12.3% · Fuji Apple 12.9% → avg 12.6% | **0.13** |
| Grains | Rice 4.5% | **0.05** |
| Processed | Low volatility, fixed without separate analysis | **0.03** |

Buy-signal condition: `currentPricePerKg < monthAvg × (1 − threshold)`

Live verification: Cabbage buySignal=true for 8 consecutive days from 2026-05-03.
Wholesale 15,725 KRW/kg vs. 30-day avg 20,698 KRW/kg (24.0% drop).

---

## Architecture

```
[4-Source Price Collection + 2-Table Split]
  KAMIS (wholesale market XML)  ─┐
  EKAPE (livestock HTML)        ─┴─▶ market_price   ← KamisPriceCalculator Java runtime (KRW/kg)

  Naver Shopping API            ─┐
  Sikjajaewang B2B crawl        ─┴─▶ price_records  ← GENERATED ALWAYS AS (price×1000/weight_grams) STORED

[Spring Batch Pipeline]
  kamisPriceStep (chunk=50)
    KamisApiReader → KamisPriceProcessor → KamisPriceWriter
        ↓
  buySignalNotifyStep (tasklet)
    BuySignalNotifyTasklet → per-user aggregation → FCM alert

[Concurrency Control]
  REDISSON (1st choice) ──▶ CircuitBreaker ──▶ PESSIMISTIC (fallback)
```

Detailed ADR: [`MD/ADR-001-lock-strategy.md`](MD/ADR-001-lock-strategy.md) · [`MD/ADR-002-generated-column.md`](MD/ADR-002-generated-column.md) *(to be written)*

---

## Implemented

- ✅ Concurrency control — 4-strategy lock + Circuit Breaker fallback
- ✅ Price integration — 4 sources · 2-table split · GENERATED COLUMN unit normalization
- ✅ Buy-signal — KAMIS 6-year threshold · BuySignalNotifyTasklet · FCM alerts
- ✅ Order history — UC-SUP-8 CRUD (create/list/summary) + Excel export
- ✅ Auth — JWT (Access 1h / Refresh 7d) · Spring Security
- ✅ Onboarding API · single-item inventory input · batch admin endpoint · Flyway V001~V008

---

## In Progress

- 🟡 UC-CORE-2 Top 5 Lowest Price — `pricing/` builds, 0 integration tests
- 🟡 UC-CORE-1 Top 5 Low Stock — FIFO query exists, dedicated API · depletion ETA incomplete
- 🟡 KAMIS item_code mapping — V007 column added, initial mapping data not yet populated

---

## Roadmap

- [ ] UC-CORE-3: Order timing prediction + alerts (`OrderForecastScheduler`, `GET /orders/forecast`)
- [ ] UC-CORE-1 complete: Top 5 endpoint + status color + depletion ETA
- [ ] UC-CORE-2 integration tests
- [ ] Validate sim crawler data loading to dev DB
- [ ] Firebase service account JSON setup → enable FCM live dispatch
- [ ] Write ADR documents (MD/ADR-001, ADR-002)

---

## Tech Stack

| Area | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.5.7 · Spring Batch · Spring Security · Spring Data JPA |
| Database | MySQL 8.0+ (GENERATED COLUMN STORED) |
| Migration | Flyway V001~V008 |
| Cache / Distributed Lock | Redis 7 · Redisson 3.23.2 |
| Reliability | Resilience4j 2.1.0 (CircuitBreaker) |
| Notifications | Firebase Cloud Messaging (FCM) |
| Testing | JUnit 5 · CountDownLatch |
| External APIs | KAMIS (XML) · EKAPE · Naver Shopping API (JSON) |
| Crawler (sim) | Python 3 · Selenium · webdriver-manager |

---

## Role & Responsibilities

| Area | Park (me) | sim |
|---|---|---|
| Domain design | 3-axis decision framework · 6-year threshold derivation · pivot decision | |
| Concurrency | 4-strategy lock + CB fallback · 500-thread chaos test | |
| Price integration | `pricing/` module · EKAPE integration · GENERATED COLUMN agreement | |
| Order history | UC-SUP-8 CRUD + Excel export | |
| Notifications | FCM infrastructure · BuySignalNotifyTasklet | |
| External crawler | | Sikjajaewang Python crawler · recipe templates |

---

## Local Setup

```bash
# 1. Create MySQL schema
mysql -u root -p -e "CREATE DATABASE naengjang_goat_db;"

# 2. Set environment variables (see application.properties)
export SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/naengjang_goat_db
export KAMIS_CERT_KEY=<your-key>

# 3. Run — Flyway auto-applies V001~V008
./gradlew bootRun
```

Demo account: `username=demo / password=demo1234` (auto-created by DataInitializer)

Get JWT:
```
POST /api/users/login
{ "username": "demo", "password": "demo1234" }
```

---

## References

- `MD/plan_park_*` — decision logs (v0.3 → v0.4 pivot, teammate sync)
- `MD/ADR-001-lock-strategy.md` — lock strategy rationale *(to be written)*
- `MD/ADR-002-generated-column.md` — GENERATED COLUMN rationale *(to be written)*
- `src/main/resources/db/migration/` — Flyway V001~V008
- [README.md](README.md) · [README_0315.md](README_0315.md)

---

## Contact

**Park Gunwoo | Backend Engineer**

- Email: gunwoo363@gmail.com
- GitHub: [github.com/gm-15](https://github.com/gm-15)
- Blog: [velog.io/@gm-15](https://velog.io/@gm-15)
