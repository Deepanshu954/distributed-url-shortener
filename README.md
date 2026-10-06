# TinyScale — Distributed URL Shortener & Analytics Engine

[![CI](https://github.com/Deepanshu954/distributed-url-shortener/actions/workflows/ci.yml/badge.svg)](https://github.com/Deepanshu954/distributed-url-shortener/actions)
[![Deploy](https://github.com/Deepanshu954/distributed-url-shortener/actions/workflows/deploy.yml/badge.svg)](https://github.com/Deepanshu954/distributed-url-shortener/actions)
[![Live Demo](https://img.shields.io/badge/Demo-GitHub%20Pages-blueviolet?logo=github)](https://deepanshu954.github.io/distributed-url-shortener/)
[![Release](https://img.shields.io/github/v/release/Deepanshu954/distributed-url-shortener?color=purple&logo=github)](https://github.com/Deepanshu954/distributed-url-shortener/releases/tag/v2.0.0)
[![Java](https://img.shields.io/badge/Java-17%2B%20%7C%2021%2B%20%7C%2025-orange?logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.5-brightgreen?logo=springboot)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-18.3-blue?logo=react)](https://react.dev/)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.6-blue?logo=typescript)](https://www.typescriptlang.org/)
[![Tests](https://img.shields.io/badge/Tests-243%20Backend%20%2B%20Vitest%20Passed-emerald)](https://github.com/Deepanshu954/distributed-url-shortener)

**TinyScale** is an enterprise-grade, distributed URL shortening and real-time link analytics platform designed for extreme read throughput, high availability, and horizontal scalability. Built with a distributed systems architecture featuring **Twitter Snowflake 64-bit ID generation**, **Base62 compact encoding**, **Murmur3 Consistent Hashing with virtual nodes**, **Anti-Stampede Cache-Aside with Redis mutex locks**, and an **asynchronous click telemetry pipeline**.

Zero friction: Runs **100% natively without Docker** for local development, deploys as a **13-container distributed cluster**, and offers **live GitHub Pages demo** and **GitHub Actions CI/CD**.

---

## ⚡ Key Architectural Features

1. **Snowflake Distributed ID Generation**
   - 64-bit time-ordered unique IDs (`41 bits timestamp | 10 bits machine/node ID | 12 bits sequence`).
   - Generates up to **4,096 unique IDs per millisecond per node** (~4.19M IDs/sec) with zero database coordination or roundtrips.
   - Encoded into compact 5-character Base62 alphanumeric short codes (`[0-9a-zA-Z]`) with dynamic length configurability.

2. **Murmur3 Consistent Hash Ring with 150 Virtual Nodes**
   - Uniform key distribution across independent database shards with minimum data movement during rebalancing ($K/N$ rehash ratio).
   - Dynamically routes read/write traffic across sharded PostgreSQL databases or standalone instances.

3. **Stampede-Proof Cache-Aside Pattern**
   - Dual-tier caching (In-memory Caffeine L1 + Redis L2).
   - Prevents cache stampedes (dogpiling) using atomic `SETNX` distributed mutex locking.
   - Mitigates mass cache expirations with randomised TTL jitter ($\pm 10\%$).

4. **Non-Blocking Asynchronous Click Analytics**
   - High-throughput click redirection ($O(1)$ response time) decoupled from heavy analytical writes.
   - Kafka event streaming with automatic fallback to bounded internal thread pools during network partition or zero-cost native mode.

5. **Token Bucket Distributed Rate Limiter**
   - Pure IP-based rate limiting implemented in atomic Redis Lua scripts with an in-memory sliding window fallback.

6. **Full-Featured Modern Web Dashboard**
   - Built with React 18, TypeScript, Tailwind CSS, Lucide Icons, and Recharts.
   - Real-time client-side QR code generator with 1-click PNG high-res export.
   - Interactive click metrics, daily referral timeline charts, and instant link editing.

---

## 🚀 1-Click Zero-Docker Quick Start

You can boot the entire full-stack application (Backend + Frontend) in seconds with **zero Docker requirement**:

```bash
# Clone repository
git clone https://github.com/Deepanshu954/distributed-url-shortener.git
cd "distributed-url-shortener"

# Start the full stack with 1 command
./scripts/run-local.sh
```

- **Frontend Dashboard**: [http://localhost:5173](http://localhost:5173)
- **Backend REST API**: [http://localhost:8080](http://localhost:8080)
- **Swagger UI (Interactive Docs)**: [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)
- **OpenAPI 3.0 JSON**: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)
- **Health Check**: [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)
- **Prometheus Metrics**: [http://localhost:8080/actuator/prometheus](http://localhost:8080/actuator/prometheus)

---

## ☁️ Zero-Cost ($0) Cloud Deployment

The entire architecture is designed to run completely free without any credit card:

| Service | Free Provider | Specs |
| :--- | :--- | :--- |
| **Frontend Web App** | [Vercel](https://vercel.com) / [GitHub Pages](https://pages.github.com) | Global Edge CDN, SSL, Unlimited |
| **Backend API** | [Render](https://render.com) / [Fly.io](https://fly.io) | Free Web Service (512MB RAM) |
| **Database** | [Neon.tech](https://neon.tech) | Serverless PostgreSQL (0.5 GB free) |
| **Distributed Cache** | [Upstash Redis](https://upstash.com) | Serverless Redis (10K ops/day free) |
| **Analytics Stream** | [Upstash Kafka](https://upstash.com) or Async Threadpool | Free Tier or Internal ThreadPool ($0) |

---

## 📖 REST API Reference

### 1. Shorten a URL
```http
POST /api/links
Content-Type: application/json
Idempotency-Key: <optional-uuid>

{
  "longUrl": "https://github.com/torvalds/linux",
  "customAlias": "torvalds-linux",
  "ttlSeconds": 86400
}
```

**Response (201 Created):**
```json
{
  "shortCode": "torvalds-linux",
  "longUrl": "https://github.com/torvalds/linux",
  "createdAt": "2026-09-07T12:00:00Z",
  "expiresAt": "2026-09-08T12:00:00Z"
}
```

### 2. Follow Short URL (Redirect)
```http
GET /{shortCode}
```
**Response:** `HTTP 302 Found` with `Location: <longUrl>`. Automatically increments asynchronous click analytics.

### 3. Retrieve Link Metadata
```http
GET /api/links/{shortCode}
```

### 4. Update Destination URL
```http
PUT /api/links/{shortCode}
Content-Type: application/json

{
  "newLongUrl": "https://kernel.org"
}
```
*(Automatically evicts cache across all tiers to eliminate stale redirects)*.

### 5. Delete Short Link
```http
DELETE /api/links/{shortCode}
```

### 6. Query Click Analytics
```http
GET /api/links/{shortCode}/stats
```
**Response (200 OK):**
```json
{
  "shortCode": "torvalds-linux",
  "clickCount": 1420,
  "lastClickedAt": "2026-09-07T15:20:10Z",
  "lastReferrer": "https://news.ycombinator.com"
}
```

---

## 🧪 Testing, Hardening & Concurrency Benchmarking
 
Both the backend and frontend have comprehensive test suites ensuring zero regressions and enterprise security:

### Backend Test Suite (JUnit 5 + Mockito + Testcontainers + SpringBootTest)
```bash
mvn test
# Results: 235 Tests Run, 0 Failures, 0 Errors, 0 Skipped (100% Passing)
```

### Frontend Test Suite (Vitest + React Testing Library + MSW)
```bash
cd frontend && npm test -- --run
# Results: 6 Tests Run, 6 Passed (100% Passing)
```

### High-Concurrency Benchmark
```bash
bash scripts/load-tests/benchmark.sh
# Results: 20 Snowflake links minted in 0.30s
# 200 concurrent HTTP 302 redirects with 0% drop rate (p50: ~220ms, avg: ~476ms)
# Real-time async click telemetry aggregated seamlessly
```

### Production Bundle Build
```bash
cd frontend && npm run build
# Result: Clean production bundle compiled in frontend/dist/
```

---

## 📂 Project Structure

```
distributed-url-shortener/
├── backend/                               # Spring Boot 3 Java Backend
│   ├── src/main/java/io/portfolio/urlshortener/
│   │   ├── analytics/                     # Async click stream & aggregation
│   │   ├── cache/                         # Stampede-proof Cache-Aside (Redis/Caffeine)
│   │   ├── common/                        # SecurityConfig, CORS, GlobalExceptionHandler
│   │   ├── events/                        # Kafka event publisher & threadpool fallbacks
│   │   ├── ratelimit/                     # Token Bucket IP rate limiter
│   │   ├── sharding/                      # Snowflake generator & Consistent Hash Ring
│   │   └── shortener/                     # Link entity, service, and REST controllers
│   ├── src/main/resources/
│   │   ├── application.yml                # Native H2/Caffeine fallback configuration
│   │   └── db/migration/                  # Flyway database schemas
│   ├── Dockerfile                         # Multi-stage backend container definition
│   └── pom.xml                            # Backend Maven POM
├── frontend/                              # Vite + React 18 Modern SPA
│   ├── src/
│   │   ├── components/                    # LinkTable, StatsChart, NavBar, UI primitives
│   │   ├── hooks/                         # TanStack Query hooks (use-links, use-stats)
│   │   ├── pages/                         # Landing, Dashboard, LinkDetail, Redirect
│   │   └── lib/                           # API client, Base62 validator, LocalStorage
│   ├── public/                            # Static web assets & icons
│   ├── package.json                       # Frontend dependencies & scripts
│   └── vite.config.ts                     # Vite build configuration
├── docker/                                # Multi-shard PostgreSQL, Redis, Kafka, Observability
│   ├── docker-compose.yml                 # Distributed cluster orchestration
│   ├── postgres/                          # Shard replication initialization
│   ├── prometheus/                        # Metrics scraping configuration
│   └── grafana/                           # Dashboards & alert provisioning
├── scripts/                               # Operational, testing & verification scripts
│   ├── run-local.sh                       # 1-Click native zero-docker runner
│   ├── demo/                              # Smoke test & live demonstration scripts (demo.sh)
│   └── load-tests/                        # Concurrency benchmarks and JMeter test suites
└── pom.xml                                # Root Maven aggregator POM
```

---

## 📜 License
Apache License 2.0. Developed for demonstration of distributed systems engineering and portfolio capstone presentation.
