# SafeRide

A ride-booking platform built to solve one concrete trust problem: **a driver
cannot demand an unauthorized extra fare after the customer has confirmed the
ride.** Fare calculation runs entirely on the backend, the confirmed fare is
locked before matching begins, and any attempt to extract more money from the
rider becomes a disputable, audited event instead of a silent surcharge.

> **Status:** Phase 5 of 18 complete (fare engine, fare breakdown, **Fare
> Lock**, destination changes). See [Roadmap](#roadmap) below for what's
> implemented vs. planned.

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
│       ├── driver/          Driver profile, status toggle, documents, self-registration
│       ├── vehicle/         Vehicle entity, driver self-service registration/activation
│       ├── ride/            Ride entity/state machine, booking, accept/arrive/start/complete/cancel
│       ├── map/             MapService abstraction - MockMapService today, Google/Mapbox later
│       ├── fare/            FareCalculator, Fare + FareLock, the Fare Lock guarantee itself
│       ├── matching/        (Phase 6)
│       ├── payment/         (Phase 12)
│       ├── rating/          (Phase 12)
│       ├── safety/          (Phase 12)
│       ├── notification/    (Phase 12)
│       ├── complaint/       (Phase 12)
│       ├── admin/           Driver/vehicle/document verification review (full dashboard: Phase 9)
│       ├── common/          ApiResponse, ApiErrorResponse, PingController, Auditable, AuditLog(Service)
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
`ROLE`), Phase 3 (`VEHICLE`, `DRIVER_DOCUMENT`, `AUDIT_LOG`), Phase 4
(`RIDE`) and Phase 5 (`FARE`, `FARE_LOCK`). Summary of the core
relationships:

- One `USER` has at most one `DRIVER` profile (1:0..1), can place many
  `RIDE`s as a customer, and can have many `RIDE`s as a driver.
- One `DRIVER` has **one or more** `VEHICLE`s (added in Phase 3 as a proper
  1:N - a driver can register more than one and switch which is `active`,
  but only the active one is ever visible to matching) and, eventually, one
  `DRIVER_RELIABILITY` record.
- `VerificationStatus` (`PENDING`/`VERIFIED`/`REJECTED`) is one shared enum
  used by `DRIVER`, `VEHICLE` and `DRIVER_DOCUMENT` alike - an admin reviews
  the person, each vehicle, and each document as independent decisions, not
  one combined flag.
- One `RIDE` has exactly one `FARE` and at most one `FARE_LOCK` - both are
  single rows **updated in place** (not appended) when a destination change
  forces a recalculation, matching the "one fare, one lock, per ride" shape
  the entity list implies; a ride also has at most one `PAYMENT`, at most
  one `RATING`, and zero or more `COMPLAINT`s.
- `RIDE.status` moves through the full lifecycle
  (`REQUESTED → SEARCHING_DRIVER → DRIVER_ASSIGNED → DRIVER_ACCEPTED →
  DRIVER_ARRIVED → RIDE_STARTED → RIDE_COMPLETED → PAYMENT_PENDING → ...`)
  under `RideStateMachine`'s validation - no controller or service sets
  `status` directly.
- `RIDE_LOCATION` (high-frequency GPS points) is **not** written to MySQL —
  live location lives in Redis; MySQL only ever sees the ride's key
  lifecycle timestamps (`createdAt`, `acceptedAt`, `startedAt`,
  `completedAt`, `cancelledAt`).
- `RIDE.version` is an `@Version` optimistic-lock column — it's what stops
  two drivers from accepting the same ride under concurrent requests (see
  [Concurrency](#concurrency--optimistic-locking) below).
- `AUDIT_LOG` deliberately holds plain `userId`/`rideId` longs rather than
  foreign-key relations, so the trail keeps meaning what it said at the time
  even if the referenced row later changes.

Foreign keys, unique constraints (e.g. one `RATING` per ride, one active
vehicle enforced at the service layer since "at most one" isn't expressible
as a plain column constraint) and indexes (e.g. `ride.status`,
`ride.customer_id`, `ride.driver_id`, `driver.online_status`,
`driver.verification_status`, `vehicle.verification_status`) are added
alongside the entities that need them so each migration is reviewable
against real query patterns instead of being guessed upfront.

### Concurrency / optimistic locking

`RIDE.version` backs a JPA `@Version` column. When two drivers call
`POST /api/rides/{id}/accept` on the same ride at nearly the same instant,
both read the row at the same version; whichever transaction commits first
wins and increments it, and the second one's `UPDATE ... WHERE version = N`
matches zero rows, so Hibernate throws
`ObjectOptimisticLockingFailureException` - translated by
`GlobalExceptionHandler` into a normal `409 CONCURRENT_MODIFICATION`
response, not a 500. No `Thread.sleep`, no manual locking - see
`RideService.acceptRide` and the accompanying handler.

### Fare Lock — how the guarantee actually holds

The core promise (see [Why SafeRide](#why-saferide)) isn't a UI badge, it's
a sequence of structural facts:

1. **The formula only runs on the backend.** `FareCalculator` is the one
   place `baseFare + distanceKm×perKmRate + durationMinutes×perMinuteRate +
   platformFee − discount` is computed - both `POST /api/fare/calculate`
   (a preview) and every fare actually attached to a ride call through it.
   Nothing ever accepts a fare number from the client.
2. **A ride starts invisible to drivers.** `POST /api/rides` leaves the
   ride at `REQUESTED` and creates its `Fare` row, but `RideStateMachine`
   has no `REQUESTED -> DRIVER_ASSIGNED` edge - a ride can only ever
   become visible to matching via `SEARCHING_DRIVER`.
3. **Confirming the fare is what unlocks matching.**
   `POST /api/rides/{id}/fare/confirm` locks the fare (creates/updates
   `FareLock` with `status = LOCKED`) and, only as a consequence, advances
   `REQUESTED -> SEARCHING_DRIVER`. There is no other way to reach
   `SEARCHING_DRIVER`. A driver therefore can never see, let alone accept,
   a ride whose fare isn't currently locked - by construction, not a
   separate runtime check bolted onto `acceptRide`.
4. **The driver has no fare-mutating endpoint, full stop.** Every write to
   `Fare`/`FareLock` requires `hasRole('CUSTOMER')` and an ownership check
   against the ride. `FareIntegrationTest.driverCanSeeTheLockedFareButHasNoEndpointToChangeIt`
   asserts exactly this: the driver can `GET` the same locked amount the
   customer confirmed, and gets `403 ACCESS_DENIED` on the one endpoint
   that changes it.
5. **A destination change can't silently raise the price.**
   `PUT /api/rides/{id}/destination` (customer only, and only before a
   driver has been assigned) recalculates the fare and **unlocks** it
   (`FareLock.status = UNLOCKED`, `unlockedAt` recorded) rather than
   updating the locked amount in place. If the ride was already
   `SEARCHING_DRIVER`, it's pulled back to `REQUESTED` - removed from
   `GET /api/rides/open` - until the customer confirms the *new* amount
   through the exact same locking path as before. Confirming twice without
   a destination change in between is rejected with
   `409 FARE_LOCKED` ("Fare is already locked").

See [`FareService`](saferide-backend/src/main/java/com/saferide/fare/FareService.java),
[`RideService`](saferide-backend/src/main/java/com/saferide/ride/RideService.java)
(search for "Fare Lock" in its class doc), and
[`FareIntegrationTest`](saferide-backend/src/test/java/com/saferide/fare/FareIntegrationTest.java)
for the full mechanism and its test coverage.

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

Endpoint groups not listed below (`/api/payments`, `/api/ratings`,
`/api/complaints`, `/api/admin/{users,rides,payments,complaints,reports}`,
...) are introduced with the phase that implements them and documented
live via Swagger UI once the app is running:

- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs

### Live endpoints (Phase 1–5)

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
| PUT | `/api/drivers/status` | `DRIVER` | `{onlineStatus: ONLINE\|OFFLINE}`; going `ONLINE` requires an admin-verified driver |
| POST / GET | `/api/drivers/documents` | `DRIVER` | Submit/list documents for review (`documentUrl` references external storage - no upload endpoint yet) |
| GET | `/api/drivers/rides` | `DRIVER` | Own ride history, paginated |
| POST / GET | `/api/vehicles` | `DRIVER` | Register a vehicle (auto-becomes the active one) / list own vehicles |
| PUT | `/api/vehicles/{id}/activate` | `DRIVER` | Switch which owned vehicle is active |
| GET | `/api/admin/drivers` | `ADMIN` | `?status=PENDING\|VERIFIED\|REJECTED&page=&size=` |
| GET | `/api/admin/drivers/{id}` | `ADMIN` | Driver + vehicles + documents in one call |
| PUT | `/api/admin/drivers/{id}/approve` \| `/block` \| `/unblock` | `ADMIN` | `/block` also forces the driver offline |
| PUT | `/api/admin/drivers/vehicles/{id}/approve` \| `/reject` | `ADMIN` | |
| PUT | `/api/admin/drivers/documents/{id}/approve` \| `/reject` | `ADMIN` | |
| POST | `/api/rides/estimate` | `CUSTOMER` | Distance/ETA preview via `MapService` - no ride created |
| POST | `/api/rides` | `CUSTOMER` | Creates a ride at `REQUESTED` (not yet driver-visible - see [Fare Lock](#fare-lock--how-the-guarantee-actually-holds)); rejects a second active ride per customer (409 `ACTIVE_RIDE_EXISTS`) |
| GET | `/api/rides` | `CUSTOMER` | Own ride history, paginated |
| GET | `/api/rides/open` | `DRIVER` | Phase 4's naive "browse open requests" (filtered by the driver's active vehicle type) - only ever contains fare-locked rides |
| GET | `/api/rides/{id}` | Customer, assigned driver, or `ADMIN` | `otp` is only included for the ride's own customer |
| GET | `/api/rides/{id}/fare` | Customer, assigned driver, or `ADMIN` | Current breakdown + lock status; `lockStatus` is absent until first confirmed |
| POST | `/api/rides/{id}/fare/confirm` | `CUSTOMER` | **Locks the fare** and is what actually advances `REQUESTED → SEARCHING_DRIVER`; confirming an already-locked fare → `409 FARE_LOCKED` |
| PUT | `/api/rides/{id}/destination` | `CUSTOMER` | Recalculates distance/fare and **unlocks** the fare (never edits the locked amount in place); only before a driver is assigned; pulls a searching ride back to `REQUESTED` until re-confirmed |
| POST | `/api/rides/{id}/accept` | `DRIVER` | Self-claim (Phase 4); requires `ONLINE` + verified driver + verified, type-matching active vehicle; `409 CONCURRENT_MODIFICATION` if another driver won the race |
| POST | `/api/rides/{id}/arrive` | `DRIVER` (assigned) | Generates the 4-digit OTP |
| POST | `/api/rides/{id}/start` | `DRIVER` (assigned) | `{otp}`; wrong code → `400 INVALID_OTP` |
| POST | `/api/rides/{id}/complete` | `DRIVER` (assigned) | Auto-advances straight to `PAYMENT_PENDING` |
| POST | `/api/rides/{id}/cancel` | Customer or assigned driver | Customer → `CANCELLED`; driver → `DRIVER_CANCELLED`; blocked once `RIDE_STARTED` |
| POST | `/api/fare/calculate` | `CUSTOMER` | Standalone preview (e.g. one call per vehicle card on the ride-options page) - computes but never persists anything |

Access tokens are short-lived (15 min default, `JWT_EXPIRATION`) self-contained
JWTs carrying `sub` (user id), `email` and `roles` claims - no DB hit per
request. Refresh tokens (7 days default, `JWT_REFRESH_EXPIRATION`) are opaque
random values stored only as a SHA-256 hash, rotated on every use, and
revocable server-side (what makes logout actually work). See
[`AuthService`](saferide-backend/src/main/java/com/saferide/auth/AuthService.java)
for the full flow.

**Dev-only seed accounts** (see [Seed data](#seed--demo-credentials-phase-2)
below) let you log in immediately without registering - the seeded driver
is pre-verified with a pre-verified active vehicle, so it's ready to go
online immediately.

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

Fare rates (`app.fare.*` - base fare/per-km/per-minute per vehicle type,
platform fee, currency) are **not** environment variables - they live
directly in `application.yml` since a fare change is a pricing decision,
not a per-environment secret. See
[`FareProperties`](saferide-backend/src/main/java/com/saferide/fare/FareProperties.java).

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
- [`VehicleServiceTest`](saferide-backend/src/test/java/com/saferide/vehicle/VehicleServiceTest.java) — registering a new vehicle correctly deactivates whichever one was previously active (Mockito).
- [`DriverVerificationIntegrationTest`](saferide-backend/src/test/java/com/saferide/admin/DriverVerificationIntegrationTest.java) — the full Phase 3 pipeline through real HTTP requests: driver signs up → blocked from going online while PENDING → admin lists/approves them → driver can go online → vehicle and document verified/rejected independently → admin blocks the account (login then fails) → admin unblocks (login works again) → ownership checks (can't activate another driver's vehicle) and duplicate-vehicle-number rejection.
- [`RideStateMachineTest`](saferide-backend/src/test/java/com/saferide/ride/RideStateMachineTest.java) — every legal transition in the spec's flowchart is allowed, every illegal one (skipping a state, going backwards, cancelling a ride already in progress, acting on a terminal state) is rejected; every non-terminal status has at least one outgoing edge (32 cases via `@ParameterizedTest`).
- [`RideIntegrationTest`](saferide-backend/src/test/java/com/saferide/ride/RideIntegrationTest.java) — full HTTP-shaped ride lifecycle: estimate → book → confirm fare → a driver with the wrong vehicle type never sees it in `/api/rides/open` → the right driver accepts → a second driver is too late (`409 INVALID_RIDE_STATE`) → arriving generates an OTP visible only to the customer → wrong OTP rejected → correct OTP starts the ride → completing auto-advances to `PAYMENT_PENDING` → a ride that far along can no longer be cancelled → a stranger can't view or touch the ride → a customer can't book a second active ride → customer-cancel vs. driver-cancel produce `CANCELLED` vs. `DRIVER_CANCELLED` respectively.
- [`FareCalculatorTest`](saferide-backend/src/test/java/com/saferide/fare/FareCalculatorTest.java) — matches the spec's worked example (8km/20min → ₹210) exactly, discount reduces the total by exactly its amount, a discount larger than the subtotal is clamped rather than producing a negative fare, different vehicle types produce different totals for an identical trip, and negative distance/duration/an unconfigured vehicle type are all rejected.
- [`FareIntegrationTest`](saferide-backend/src/test/java/com/saferide/fare/FareIntegrationTest.java) — the Fare Lock guarantee end to end: a preview differs by vehicle type; a new ride's fare starts unlocked; confirming locks it (and confirming twice fails with `409 FARE_LOCKED`); a driver can view the locked amount but has no endpoint that can change it (`403` on the one write path, which is customer-only); changing the destination unlocks the fare, recalculates it, and pulls an already-searching ride back out of `/api/rides/open` until the customer re-confirms the *new* amount; a destination change is rejected outright once a driver has accepted.

Domain-level tests (matching, reliability scoring, concurrency stress) are
added alongside each phase that introduces the behavior; Testcontainers
(real MySQL in tests) arrives in Phase 14.

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
| 3 | Vehicle, driver verification, driver status | ✅ Done |
| 4 | Ride booking, ride state machine, ride APIs | ✅ Done |
| 5 | Fare engine, fare breakdown, **Fare Lock**, destination changes | ✅ Done |
| 6 | Driver matching, reliability score, automatic reassignment | ⏳ Next |
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

### Seed / demo credentials (Phase 2–3)

Seeded automatically on startup under the `dev` profile only (never `test`
or `prod`) by
[`DevDataSeeder`](saferide-backend/src/main/java/com/saferide/config/DevDataSeeder.java) —
log in with these immediately, no registration needed:

| Email | Password | Role | Notes |
|---|---|---|---|
| `customer@example.com` | `Password123!` | CUSTOMER | |
| `driver@example.com` | `Password123!` | DRIVER | Pre-verified, with a pre-verified active vehicle (2020 Maruti Suzuki Dzire) - ready to go `ONLINE` immediately, skipping the admin-verification step below |
| `admin@example.com` | `Password123!` | ADMIN | Use this to review other drivers via `GET/PUT /api/admin/drivers/**` |

These are intentionally public, dev-only credentials — never reachable in
`prod` (`ProdSafetyChecks` also refuses to boot the `prod` profile if
`JWT_SECRET` was left at its dev default).
