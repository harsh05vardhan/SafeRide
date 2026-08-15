# SafeRide

A ride-booking platform built to solve one concrete trust problem: **a driver
cannot demand an unauthorized extra fare after the customer has confirmed the
ride.** Fare calculation runs entirely on the backend, the confirmed fare is
locked before matching begins, and any attempt to extract more money from the
rider becomes a disputable, audited event instead of a silent surcharge.

> **Status:** Phase 2 of 18 complete (auth, users, drivers, JWT security). See
> [Roadmap](#roadmap) below for what's implemented vs. planned.

## Table of contents

- [Why SafeRide](#why-saferide)
- [Architecture](#architecture)
- [Tech stack](#tech-stack)
- [Folder structure](#folder-structure)
- [Database design](#database-design)
- [API contract](#api-contract)
- [Local setup](#local-setup)
- [Environment variables](#environment-variables)
- [Docker](#docker)
- [Testing](#testing)
- [CI/CD](#cicd)
- [Deployment](#deployment)
- [Roadmap](#roadmap)

## Why SafeRide

Most ride-booking clones stop at "customer books, driver drives." SafeRide's
portfolio thesis is the trust layer around that transaction:

| Problem | SafeRide's answer |
|---|---|
| Driver demands cash on top of the app fare | **Fare Lock** — fare is computed server-side and frozen the moment the customer confirms; the driver has no code path to change it |
| "Nearest driver" isn't always the best driver | **Smart matching** — blends distance, rating, reliability, acceptance/cancellation history, ETA |
| Unreliable drivers go unnoticed | **Driver Reliability Score** — a single 0–100 number fed by acceptance, cancellation, completion, on-time and complaint history, recalculated after every ride event |
| Driver cancels and the customer has to start over | **Automatic reassignment** — the same ride request is silently re-matched to the next-best driver, excluding the one who cancelled |
| "Ride started" without the rider present | **Ride OTP** — `DRIVER_ARRIVED → RIDE_STARTED` is only reachable via a correct 4-digit OTP the driver never sees in advance |
| "The app said X, the driver says Y" | **Fare-dispute / complaint system** — customers can report extra-fare demands, admin investigates with a full audit trail |

## Architecture

**Modular monolith**, not microservices — one deployable Spring Boot
application internally partitioned by business domain (`auth`, `user`,
`driver`, `vehicle`, `ride`, `fare`, `matching`, `payment`, `rating`,
`safety`, `notification`, `complaint`, `admin`). Each domain owns its own
controller → service → repository → entity slice and talks to other domains
only through service interfaces — never through shared entities. This keeps
the operational simplicity of a monolith (one deploy, one datastore, one
transaction boundary) while leaving a clean seam to extract a domain into its
own service later, if the project ever needs to.

```
                         ┌─────────────────────────┐
                         │   React (Vite) SPA      │
                         │  customer / driver /    │
                         │  admin experiences       │
                         └────────────┬─────────────┘
                          HTTPS (REST)│  WSS (STOMP)
                                      ▼
                         ┌─────────────────────────┐
                         │   Spring Boot backend    │
                         │  (modular monolith)      │
                         │                          │
                         │  auth · user · driver    │
                         │  vehicle · ride · fare   │
                         │  matching · payment      │
                         │  rating · safety         │
                         │  notification · complaint│
                         │  admin                   │
                         └──────┬────────────┬──────┘
                                │            │
                 source of truth│            │hot / ephemeral data
                                ▼            ▼
                       ┌───────────────┐ ┌──────────────┐
                       │   MySQL 8     │ │    Redis      │
                       │ (all durable  │ │ (driver geo,  │
                       │  entities)    │ │  availability,│
                       └───────────────┘ │  matching,    │
                                          │  rate limits) │
                                          └──────────────┘
                                      │
                         (Phase 13, optional) Kafka
                         RideRequested / DriverAssigned / ...
```

Cross-cutting design decisions baked in from Phase 1:

- **DTOs at every boundary** — JPA entities never leave the service layer.
- **`ApiResponse<T>` / `ApiErrorResponse`** — one success envelope, one error
  envelope, used by every controller (see
  [`common/ApiResponse.java`](saferide-backend/src/main/java/com/saferide/common/ApiResponse.java)).
- **Provider abstraction for maps and payments** — `MapService` and
  `PaymentService` interfaces ship with `MockMapService` /
  `MockPaymentService` implementations first; a real provider (Google
  Maps/Mapbox, Razorpay) becomes a second implementation, never a rewrite.
- **Config over hardcoding** — every secret and connection string comes from
  an environment variable (see [`.env.example`](.env.example)); nothing
  sensitive is committed.

## Tech stack

**Backend:** Java 21, Spring Boot 3, Spring Web, Spring Data JPA/Hibernate,
Spring Security + JWT (Phase 2), Spring WebSocket/STOMP (Phase 10), Spring
Actuator, Maven, Lombok, springdoc-openapi/Swagger, JUnit 5, Mockito,
Testcontainers (Phase 14).

**Database:** MySQL 8 (source of truth) + Redis (hot/ephemeral data — driver
geolocation, availability, matching cache, rate limiting).

**Messaging:** Apache Kafka (Phase 13, optional/additive — domain events like
`RideRequested`, `DriverAssigned`, `PaymentCompleted`).

**Frontend:** React + TypeScript + Vite, Tailwind CSS, shadcn/ui (Radix +
CVA), React Router, Axios, TanStack Query, React Hook Form + Zod, Lucide
icons, Framer Motion, React Leaflet + OpenStreetMap.

**DevOps:** Docker, Docker Compose, GitHub Actions (CI + CD), container
registry.

## Folder structure

```
safeRide/
├── saferide-backend/
│   └── src/main/java/com/saferide/
│       ├── SafeRideApplication.java
│       ├── auth/            JWT issuance/refresh/logout, register/login, security filter
│       ├── user/            User, Role, CustomerController, profile self-service
│       ├── driver/          Driver profile, driver self-registration
│       ├── vehicle/         (Phase 3)
│       ├── ride/            (Phase 4)
│       ├── fare/            (Phase 5)
│       ├── matching/        (Phase 6)
│       ├── payment/         (Phase 12)
│       ├── rating/          (Phase 12)
│       ├── safety/          (Phase 12)
│       ├── notification/    (Phase 12)
│       ├── complaint/       (Phase 12)
│       ├── admin/           (Phase 9)
│       ├── common/          ApiResponse, ApiErrorResponse, PingController, Auditable
│       ├── config/          CorsConfig, OpenApiConfig, SecurityConfig, JwtAuthenticationEntryPoint,
│       │                    JwtAccessDeniedHandler, JpaAuditingConfig, DevDataSeeder, ProdSafetyChecks
│       └── exception/       ApiException + GlobalExceptionHandler (extended per-phase with new exceptions)
│   └── src/main/resources/
│       ├── application.yml
│       ├── application-dev.yml
│       ├── application-test.yml
│       └── application-prod.yml
├── saferide-frontend/
│   └── src/
│       ├── assets/
│       ├── components/{common,layout,map,ride,driver,admin,auth,safety}/
│       ├── pages/{auth,customer,driver,admin}/
│       ├── services/        api.ts (Axios instance)
│       ├── hooks/
│       ├── contexts/
│       ├── types/
│       ├── utils/
│       ├── lib/              utils.ts (shadcn `cn` helper)
│       ├── routes/           AppRoutes.tsx
│       ├── App.tsx
│       └── main.tsx
├── docker-compose.dev.yml     local MySQL + Redis for native `mvn`/`npm` dev
├── docker-compose.yml         full stack (Phase 15)
├── .github/workflows/         ci.yml / cd.yml (Phase 16)
├── .env.example
└── README.md
```

Every package/folder above already exists in the repo (most with a
`.gitkeep` today) so the intended shape of the system is visible from Phase
1, even though most domains are implemented incrementally in later phases.

## Database design

Full entity list, columns and constraints are specified in the project brief
this README accompanies; they land in the codebase starting Phase 2 (`USER`,
`ROLE`) and Phase 4 (`RIDE`, `FARE`, `FARE_LOCK`, ...). Summary of the core
relationships:

- One `USER` has at most one `DRIVER` profile (1:0..1) and can place many
  `RIDE`s as a customer.
- One `DRIVER` has one or more `VEHICLE`s and exactly one
  `DRIVER_RELIABILITY` record.
- One `RIDE` has exactly one `FARE`, at most one `FARE_LOCK`, at most one
  `PAYMENT`, at most one `RATING`, and zero or more `COMPLAINT`s.
- `RIDE_LOCATION` (high-frequency GPS points) is **not** written to MySQL —
  live location lives in Redis; MySQL only ever sees the ride's key
  lifecycle timestamps (`createdAt`, `acceptedAt`, `startedAt`,
  `completedAt`, `cancelledAt`).
- `RIDE.version` is an `@Version` optimistic-lock column — it's what stops
  two drivers from accepting the same ride under concurrent requests.

Foreign keys, unique constraints (e.g. one `RATING` per ride) and indexes
(e.g. `ride.status`, `ride.customer_id`, `ride.driver_id`,
`driver.online_status`) are added alongside the entities that need them so
each migration is reviewable against real query patterns instead of being
guessed upfront.

## API contract

Every response uses one of two envelopes:

```jsonc
// success
{ "success": true, "message": "Ride created successfully", "data": { } }

// error
{
  "success": false,
  "message": "Fare is already locked",
  "code": "FARE_LOCKED",
  "timestamp": "2026-08-15T10:15:30Z"
}
```

Endpoint groups not listed below (`/api/rides`, `/api/fare`, `/api/payments`,
`/api/ratings`, `/api/complaints`, `/api/admin`, ...) are introduced with the
phase that implements them and documented live via Swagger UI once the app
is running:

- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs

### Live endpoints (Phase 1 + 2)

| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/api/ping` | Public | Liveness check |
| POST | `/api/auth/register` | Public | `role: CUSTOMER \| DRIVER`; `licenseNumber` required when `role=DRIVER` |
| POST | `/api/auth/login` | Public | `identifier` accepts email or phone |
| POST | `/api/auth/refresh` | Public | Rotates the refresh token; the old one is revoked |
| POST | `/api/auth/logout` | Public | Revokes the given refresh token (idempotent) |
| GET / PUT | `/api/customers/profile` | `CUSTOMER` | Always operates on the caller's own account |
| POST | `/api/drivers/register` | Any authenticated user | Adds the DRIVER role + driver profile to the current account; re-login/refresh needed to pick up the new role |
| GET | `/api/drivers/profile` | `DRIVER` | |

Access tokens are short-lived (15 min default, `JWT_EXPIRATION`) self-contained
JWTs carrying `sub` (user id), `email` and `roles` claims - no DB hit per
request. Refresh tokens (7 days default, `JWT_REFRESH_EXPIRATION`) are opaque
random values stored only as a SHA-256 hash, rotated on every use, and
revocable server-side (what makes logout actually work). See
[`AuthService`](saferide-backend/src/main/java/com/saferide/auth/AuthService.java)
for the full flow.

**Dev-only seed accounts** (see [Seed data](#seed--demo-credentials-phase-2)
below) let you log in immediately without registering.

## Local setup

Prerequisites: **Java 21+** (JDK 24 with `--release 21` also works), **Maven
3.9+**, **Node 20+**, **Docker Desktop** (for local MySQL/Redis).

### 1. Start local infrastructure (MySQL + Redis)

```bash
docker compose -f docker-compose.dev.yml up -d
```

### 2. Backend

```bash
cd saferide-backend
mvn spring-boot:run
```

Runs on `http://localhost:8080` with the `dev` profile active by default
(`SPRING_PROFILES_ACTIVE` defaults to `dev`). Verify:

```bash
curl http://localhost:8080/api/ping
curl http://localhost:8080/actuator/health
```

Swagger UI: http://localhost:8080/swagger-ui.html

### 3. Frontend

```bash
cd saferide-frontend
npm install
npm run dev
```

Runs on `http://localhost:5173`. The landing page pings the backend and
shows the connection status — a green line means the full stack (Vite →
Axios → Spring Boot → `ApiResponse` envelope → back to React) is wired
correctly.

## Environment variables

See [`.env.example`](.env.example) for the full annotated list (database,
JWT, Redis, map/payment provider keys, CORS). Copy it to `.env` and adjust
for your machine; `.env` is git-ignored so real secrets never get committed.
The frontend reads its own `VITE_`-prefixed subset from
`saferide-frontend/.env` (see
[`saferide-frontend/.env.example`](saferide-frontend/.env.example)).

Every backend default in `application.yml` falls back to a safe local value
(`saferide`/`saferide` for MySQL, `localhost` for Redis) so `mvn
spring-boot:run` works out of the box against `docker-compose.dev.yml`
without any `.env` file at all — env vars only need to be set to *override*
those defaults (required in `prod`).

## Docker

- `docker-compose.dev.yml` — **today.** MySQL 8.4 + Redis 7.4 only, so the
  backend and frontend can run natively for fast iteration
  (`mvn spring-boot:run`, `npm run dev`).
- `docker-compose.yml` — **Phase 15.** The full containerized stack
  (`mysql`, `redis`, `backend`, `frontend`, optionally `kafka`), started
  with a single `docker compose up --build`.

## Testing

```bash
cd saferide-backend
mvn test
```

All tests run against an in-memory H2 database (`application-test.yml`), so
none of them require Docker/MySQL to be running:

- [`SafeRideApplicationTests`](saferide-backend/src/test/java/com/saferide/SafeRideApplicationTests.java) — Spring context smoke test.
- [`JwtServiceTest`](saferide-backend/src/test/java/com/saferide/auth/JwtServiceTest.java) — token round-trip, expiry, tamper rejection.
- [`AuthServiceTest`](saferide-backend/src/test/java/com/saferide/auth/AuthServiceTest.java) — register/login/refresh-rotation/logout business logic (Mockito).
- [`AuthIntegrationTest`](saferide-backend/src/test/java/com/saferide/auth/AuthIntegrationTest.java) — full HTTP-shaped flow: register → login → call a protected endpoint → cross-role 403 → refresh rotation → logout revocation → validation errors, all through real MockMvc requests (no `@WithMockUser` shortcuts).

Domain-level tests (fare calculation, fare locking, matching, reliability
scoring, OTP, concurrency) are added alongside each phase that introduces
the behavior; Testcontainers (real MySQL in tests) arrives in Phase 14.

## CI/CD

`.github/workflows/ci.yml` / `cd.yml` are introduced in Phase 16
(checkout → setup Java/Node → backend tests → frontend tests → build → build
Docker images → push to a container registry → deploy).

## Deployment

Local Docker first, free-tier MVP hosting next, AWS later — never required
for local development. Target AWS shape (Phase 17+): Route 53 → CloudFront →
ALB → Spring Boot containers → RDS MySQL + ElastiCache Redis, S3 for static
assets, CloudWatch for logs/metrics, MSK (or a managed Kafka) once Phase 13
events are in place.

## Roadmap

| Phase | Scope | Status |
|---|---|---|
| 1 | Project setup — Spring Boot, React, MySQL, Git, basic structure | ✅ Done |
| 2 | User/Customer/Driver/Admin, JWT auth, Spring Security | ✅ Done |
| 3 | Vehicle, driver verification, driver status | ⏳ Next |
| 4 | Ride booking, ride state machine, ride APIs | Planned |
| 5 | Fare engine, fare breakdown, **Fare Lock**, destination changes | Planned |
| 6 | Driver matching, reliability score, automatic reassignment | Planned |
| 7 | Customer frontend (login → tracking) | Planned |
| 8 | Driver frontend (dashboard → earnings) | Planned |
| 9 | Admin frontend | Planned |
| 10 | WebSocket/STOMP — real-time status & location | Planned |
| 11 | Redis — driver locations, matching, caching | Planned |
| 12 | Payment, rating, complaints, safety | Planned |
| 13 | Kafka events, notifications, analytics | Planned |
| 14 | Testing — JUnit, Mockito, Testcontainers | Planned |
| 15 | Docker / Docker Compose (full stack) | Planned |
| 16 | GitHub Actions CI/CD | Planned |
| 17 | Deployment (free-tier MVP → AWS) | Planned |
| 18 | Production optimization | Planned |

---

### Seed / demo credentials (Phase 2)

Seeded automatically on startup under the `dev` profile only (never `test`
or `prod`) by
[`DevDataSeeder`](saferide-backend/src/main/java/com/saferide/config/DevDataSeeder.java) —
log in with these immediately, no registration needed:

| Email | Password | Role | Notes |
|---|---|---|---|
| `customer@example.com` | `Password123!` | CUSTOMER | |
| `driver@example.com` | `Password123!` | DRIVER | Pre-verified, so it can be used end-to-end without the Phase 3 admin-verification step |
| `admin@example.com` | `Password123!` | ADMIN | |

These are intentionally public, dev-only credentials — never reachable in
`prod` (`ProdSafetyChecks` also refuses to boot the `prod` profile if
`JWT_SECRET` was left at its dev default).
