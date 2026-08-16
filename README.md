# SafeRide

A ride-booking platform built to solve one concrete trust problem: **a driver
cannot demand an unauthorized extra fare after the customer has confirmed the
ride.** Fare calculation runs entirely on the backend, the confirmed fare is
locked before matching begins, and any attempt to extract more money from the
rider becomes a disputable, audited event instead of a silent surcharge.

> **Status:** Phase 16 of 18 complete (GitHub Actions CI/CD - test+lint on
> every push, Docker images published to GHCR on `main`). See
> [Roadmap](#roadmap) below for what's implemented vs. planned.

## Table of contents

- [Why SafeRide](#why-saferide)
- [Architecture](#architecture)
- [Tech stack](#tech-stack)
- [Folder structure](#folder-structure)
- [Database design](#database-design)
- [Customer frontend](#customer-frontend)
- [Driver frontend](#driver-frontend)
- [Admin frontend](#admin-frontend)
- [Real-time (WebSocket)](#real-time-websocket)
- [Redis (driver geo-matching, rate limiting)](#redis-driver-geo-matching-rate-limiting)
- [Payment, rating, complaints and safety](#payment-rating-complaints-and-safety)
- [Domain events, notifications, and analytics](#domain-events-notifications-and-analytics)
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
                              optional, additive
                                      ▼
                              ┌──────────────┐
                              │    Kafka      │
                              │ RideRequested │
                              │ DriverAssigned│
                              │ PaymentDone...│
                              └──────────────┘
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
Data Redis (Phase 11), Spring Kafka (Phase 13, optional/additive), Spring
Actuator, Maven, Lombok, springdoc-openapi/Swagger, JUnit 5, Mockito,
Testcontainers (Phase 14 — real MySQL + Redis in every integration test,
see [Testing](#testing)).

**Database:** MySQL 8 (source of truth) + Redis 7 (hot/ephemeral data —
driver geo-index for matching, login rate limiting; see
[Redis](#redis-driver-geo-matching-rate-limiting) below).

**Messaging:** Apache Kafka (Phase 13, optional/additive — every domain
event (`RideRequestedEvent`, `DriverAssignedEvent`, `RideCompletedEvent`,
`PaymentCompletedEvent`, `ComplaintFiledEvent`, `ComplaintResolvedEvent`) is
mirrored onto `saferide.domain-events` for consumers outside this JVM; the
app itself never depends on Kafka being reachable - see
[Domain events, notifications, and analytics](#domain-events-notifications-and-analytics)).

**Frontend:** React + TypeScript + Vite, Tailwind CSS, shadcn/ui (Radix +
CVA), React Router, Axios, TanStack Query, Recharts, React Hook Form + Zod,
Lucide icons, Framer Motion, React Leaflet + OpenStreetMap, `@stomp/stompjs`
(Phase 10 — plain STOMP over native `WebSocket`, no SockJS), Vitest +
React Testing Library (Phase 14, see [Testing](#testing)).

**DevOps:** Docker, Docker Compose, GitHub Actions (CI + CD), container
registry.

## Folder structure

```
safeRide/
├── saferide-backend/
│   ├── Dockerfile             (Phase 15) multi-stage - maven:3.9-eclipse-temurin-21 to build,
│   │                          eclipse-temurin:21-jre-alpine to run, non-root, /actuator/health check
│   ├── .dockerignore
│   └── src/main/java/com/saferide/
│       ├── SafeRideApplication.java
│       ├── auth/            JWT issuance/refresh/logout, register/login, security filter,
│       │                    LoginRateLimiter (Redis/in-memory, Phase 11)
│       ├── user/            User, Role, CustomerController, profile self-service
│       ├── driver/          Driver profile, status/location, documents, self-registration,
│       │                    ReliabilityCalculator + DriverReliabilityService
│       ├── vehicle/         Vehicle entity, driver self-service registration/activation
│       ├── ride/            Ride entity/state machine, booking, accept/decline/arrive/start/
│       │                    complete/cancel, ActiveRideStatuses (shared with matching),
│       │                    RideEventPublisher (Phase 10 - the one place every WS broadcast goes through)
│       ├── map/             MapService abstraction + GeoUtils - MockMapService today, Google/Mapbox later
│       ├── fare/            FareCalculator, Fare + FareLock, the Fare Lock guarantee itself
│       ├── matching/        DriverMatchScorer, DriverMatchingService, RideDriverExclusion -
│       │                    proactive assignment + automatic reassignment; DriverGeoIndex
│       │                    (Redis/SQL, Phase 11) - matching's candidate lookup
│       ├── payment/         (Phase 12) Payment entity, PaymentGateway abstraction - MockPaymentGateway
│       │                    today, a real provider later; charges only the ride's locked fare
│       ├── rating/          (Phase 12) Rating entity/service - feeds Driver.rating via the same EMA
│       │                    DriverReliabilityService already uses for every other driver stat
│       ├── safety/          (Phase 12) SafetyAlert (SOS) + TripShare (public read-only tracking link),
│       │                    SafetyService - the Safety Center's backend
│       ├── event/           (Phase 13) DomainEvent + its six records (RideRequestedEvent, ...),
│       │                    KafkaEventForwarder - the one place any event reaches Kafka, optional/additive
│       ├── notification/    (Phase 13) Notification entity/service, NotificationEventListener - the one
│       │                    place a DomainEvent becomes an in-app notification, entirely Kafka-independent
│       ├── complaint/       (Phase 12) Complaint entity/service - the dispute system behind "the driver
│       │                    cannot demand an unauthorized extra fare"; a customer-filed complaint feeds
│       │                    the driver's complaintCount/reliabilityScore the same way Phase 6 designed for
│       ├── admin/           Driver/vehicle/document verification review (frontend console: Phase 9);
│       │                    AdminComplaintService/AdminSafetyService - complaint + SOS review (Phase 12);
│       │                    AdminAnalyticsService - platform-wide COUNT/SUM/AVG summary (Phase 13)
│       ├── websocket/       (Phase 10) WebSocketConfig, StompAuthChannelInterceptor, StompPrincipal -
│       │                    STOMP CONNECT auth + per-ride SUBSCRIBE authorization
│       ├── common/          ApiResponse, ApiErrorResponse, PingController, Auditable, AuditLog(Service)
│       ├── config/          CorsConfig, OpenApiConfig, SecurityConfig, JwtAuthenticationEntryPoint,
│       │                    JwtAccessDeniedHandler, JpaAuditingConfig, DevDataSeeder, ProdSafetyChecks
│       └── exception/       ApiException + GlobalExceptionHandler (extended per-phase with new exceptions)
│   └── src/main/resources/
│       ├── application.yml
│       ├── application-dev.yml
│       ├── application-test.yml
│       └── application-prod.yml
│   └── src/test/java/com/saferide/
│       │  (mirrors the main tree above, package by package - *Test for Mockito/plain unit
│       │   tests, *IntegrationTest for real HTTP via MockMvc)
│       └── testsupport/      (Phase 14) AbstractIntegrationTest - the shared singleton
│                              MySQL + Redis Testcontainers every @SpringBootTest class extends
├── saferide-frontend/
│   ├── Dockerfile             (Phase 15) multi-stage - node:20-alpine to build (VITE_API_BASE_URL/
│   │                          VITE_WS_BASE_URL baked in as build args), nginx:1.27-alpine to serve
│   ├── nginx.conf             (Phase 15) SPA fallback routing + long-cache immutable assets/
│   ├── .dockerignore
│   └── src/
│       ├── assets/
│       ├── components/
│       │   ├── common/       Button, Input, Card, Badge, Spinner, Skeleton,
│       │   │                 ConfirmDialog, StateViews, ProtectedRoute (role-gated),
│       │   │                 NotificationBell (Phase 13, shared by all three navbars)
│       │   ├── layout/       Navbar/MobileBottomNav/CustomerLayout,
│       │   │                 DriverNavbar/DriverMobileBottomNav/DriverLayout (Phase 8),
│       │   │                 AdminNavbar/AdminLayout (Phase 9, no mobile bottom nav)
│       │   ├── map/          MapView, Pickup/Destination/DriverMarker, RouteLine
│       │   ├── ride/         LocationPicker; RatingStars, ReportIssueDialog, ShareRideDialog
│       │   │                 (Phase 12) - shared by both the customer and driver ride screens
│       │   ├── driver/       (Phase 8) StatCard, IncomingRequestCard
│       │   └── admin/        (Phase 9) StatusBadges, ReasonConfirmDialog
│       ├── pages/
│       │   ├── auth/         LoginPage, RegisterPage (role picker built in)
│       │   ├── RoleLandingPage.tsx  "/" - routes single-role accounts straight
│       │   │                 through, shows a chooser for multi-role accounts
│       │   ├── PublicTrackingPage.tsx  (Phase 12) "/track/:token" - unauthenticated,
│       │   │                 outside every ProtectedRoute; the "Share Ride" landing page
│       │   ├── customer/     HomePage, RideOptionsPage, FareBreakdownPage,
│       │   │                 FindingDriverPage, RideTrackingPage, MyRidesPage, ProfilePage,
│       │   │                 MyComplaintsPage (Phase 12, also mounted at /driver/complaints)
│       │   ├── driver/       (Phase 8) DriverDashboardPage, DriverRidesPage,
│       │   │                 DriverRideDetailPage, DriverEarningsPage, DriverProfilePage
│       │   └── admin/        (Phase 9) AdminDashboardPage, AdminDriversPage,
│       │                     AdminDriverDetailPage; AdminComplaintsPage,
│       │                     AdminComplaintDetailPage, AdminSafetyPage (Phase 12);
│       │                     AdminAnalyticsPage (Phase 13)
│       ├── services/         api.ts (Axios + JWT/refresh interceptors), authService,
│       │                     rideService (customer + driver actions), fareService,
│       │                     customerService, driverService, vehicleService (Phase 8),
│       │                     adminService (Phase 9), paymentService, ratingService,
│       │                     complaintService, safetyService, adminComplaintService,
│       │                     adminSafetyService (Phase 12), notificationService,
│       │                     adminAnalyticsService (Phase 13)
│       ├── hooks/            useDriverProfile, useCurrentDriverRide (Phase 8),
│       │                     useRideChannel (Phase 10 - useRideStatusChannel/
│       │                     useRideLocationChannel/useDriverAssignmentChannel)
│       ├── contexts/         AuthContext (isCustomer/isDriver/isAdmin), BookingContext, ToastContext,
│       │                     WebSocketContext (Phase 10 - one STOMP connection per session)
│       ├── types/            auth.ts, ride.ts, fare.ts, common.ts, driver.ts, vehicle.ts,
│       │                     admin.ts, ws.ts (Phase 10), payment.ts, rating.ts, complaint.ts,
│       │                     safety.ts (Phase 12), notification.ts, analytics.ts (Phase 13) -
│       │                     mirror the backend DTOs exactly
│       ├── lib/               utils.ts (`cn`), tokenStorage.ts, validation.ts (Zod),
│       │                     suggestedLocations.ts
│       ├── routes/           AppRoutes.tsx (every page lazy-loaded)
│       ├── testSetup.ts      (Phase 14) jest-dom matchers for Vitest - see vite.config.ts's
│       │                     `test` block; `*.test.ts(x)` files sit next to what they cover
│       │                     (e.g. components/common/Badge.test.tsx), not in a parallel tree
│       ├── App.tsx
│       └── main.tsx
├── docker-compose.dev.yml     local MySQL + Redis for native `mvn`/`npm` dev
├── docker-compose.yml         (Phase 15) full containerized stack - mysql, redis, backend,
│                              frontend, optionally kafka (`--profile kafka`)
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
(`RIDE`), Phase 5 (`FARE`, `FARE_LOCK`) and Phase 6 (`RIDE_DRIVER_EXCLUSIONS`
- see below). Summary of the core relationships:

- One `USER` has at most one `DRIVER` profile (1:0..1), can place many
  `RIDE`s as a customer, and can have many `RIDE`s as a driver.
- One `DRIVER` has **one or more** `VEHICLE`s (added in Phase 3 as a proper
  1:N - a driver can register more than one and switch which is `active`,
  but only the active one is ever visible to matching).
- The spec's separate `DRIVER_RELIABILITY` entity is **not** a second
  table - its columns (`acceptanceRate`, `cancellationRate`,
  `completionRate`, `onTimeRate`, `complaintCount`, `reliabilityScore`)
  already live directly on `DRIVER` (most since Phase 2), updated in place
  after every ride event by `DriverReliabilityService` rather than
  duplicated into a row that could drift out of sync with the "current"
  numbers everything else reads. `averageRating` in the spec is just
  `Driver.rating` by another name.
- `RIDE_DRIVER_EXCLUSIONS` (Phase 6, `matching` package) records "driver X
  is permanently ruled out for ride Y" whenever they decline an assignment
  or cancel after accepting - the mechanism behind "a previously
  cancelled/declined driver must not receive the same ride again", checked
  by both the proactive matching engine and the `/api/rides/open`
  fallback.
- `VerificationStatus` (`PENDING`/`VERIFIED`/`REJECTED`) is one shared enum
  used by `DRIVER`, `VEHICLE` and `DRIVER_DOCUMENT` alike - an admin reviews
  the person, each vehicle, and each document as independent decisions, not
  one combined flag.
- One `RIDE` has exactly one `FARE` and at most one `FARE_LOCK` - both are
  single rows **updated in place** (not appended) when a destination change
  forces a recalculation, matching the "one fare, one lock, per ride" shape
  the entity list implies; a ride also has at most one `PAYMENT` (Phase 12
  - created only once `POST /api/rides/{id}/pay` succeeds, never before),
  at most one `RATING` (Phase 12), and zero or more `COMPLAINT`s (Phase 12
  - the only one of the three where "zero or more" actually matters: a
  rider could file separate reports for an extra-fare demand *and* unsafe
  driving on the same trip). A ride also has zero or more `SAFETY_ALERT`s
  (one per SOS press) and at most one `TRIP_SHARE` (Phase 12) - see
  [Safety Center](#safety-center).
- `RIDE.status` moves through the full lifecycle
  (`REQUESTED → SEARCHING_DRIVER → DRIVER_ASSIGNED → DRIVER_ACCEPTED →
  DRIVER_ARRIVED → RIDE_STARTED → RIDE_COMPLETED → PAYMENT_PENDING →
  PAYMENT_COMPLETED → RATED`) under `RideStateMachine`'s validation - no
  controller or service sets `status` directly.
- `NOTIFICATION` (Phase 13) belongs to one `USER`, not one `RIDE` - a
  single ride event (e.g. `DriverAssignedEvent`) produces one row per
  recipient (customer *and* driver), so a ride with an eventful lifecycle
  can have several. `rideId` on it is a bare column, not an FK, same
  reasoning `AUDIT_LOG` already established.
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

### Matching, reliability, and automatic reassignment

Phase 4's "driver browses open requests" model is now the *fallback*, not
the primary path:

1. **Confirming a fare triggers matching.** The instant a ride reaches
   `SEARCHING_DRIVER` (fresh confirm, a decline, or a driver cancellation),
   `RideService.attemptAutoAssign` asks `DriverMatchingService` for the
   best eligible driver and, if one exists, proactively assigns them
   (`DRIVER_ASSIGNED`) - no driver ever had to browse anything.
2. **The scoring algorithm balances, it doesn't just pick nearest.**
   `DriverMatchScorer` combines distance, rating, reliability, acceptance
   rate and ETA as weighted, configurable (`app.matching.*`) components,
   and subtracts a cancellation-rate penalty. `DriverMatchScorerTest`
   reproduces the spec's own example verbatim: a driver 1.2km away with a
   4.8 rating and 3% cancellation rate outscores one 0.5km away with a 3.9
   rating and 20% cancellation rate.
3. **A decline permanently excludes that driver from that ride.**
   `POST /api/rides/{id}/decline` records a `RideDriverExclusion` row and
   immediately re-runs matching for the next-best candidate. That
   exclusion is checked by *both* the proactive path and the
   `/api/rides/open` fallback, so a declined driver can never claw the
   ride back either way.
4. **A driver cancellation auto-reassigns instead of dead-ending.**
   `RideStateMachine`'s `DRIVER_CANCELLED -> SEARCHING_DRIVER` edge
   (added in Phase 5, used for the first time here) is exactly for this:
   cancelling after accepting excludes that driver, updates their
   reliability, and immediately tries the next-best one - the customer's
   original request continues rather than dying.
5. **Reliability updates after every relevant event, no full history
   scan.** `DriverReliabilityService` nudges the relevant rate
   (`acceptanceRate`, `completionRate`, `cancellationRate`) with an
   exponential moving average on acceptance/decline/completion/
   cancellation, then `ReliabilityCalculator` recomputes the single 0-100
   `reliabilityScore` other drivers are compared against - both are
   configurable via `app.reliability.*`. `rating` and `complaintCount`
   moved the same way from their seeded defaults starting Phase 12, once
   ratings and complaints existed to feed them - see
   [Payment, rating, complaints and safety](#payment-rating-complaints-and-safety).
6. **If nobody qualifies, the ride waits - it doesn't fail.** No eligible
   driver (none within `maxRadiusKm`, none with a verified matching
   vehicle) just leaves the ride at `SEARCHING_DRIVER`, discoverable via
   the `/api/rides/open` fallback the moment a suitable driver comes
   online - a fallback that never touches Redis at all, so it works even
   if Redis is down (see [Redis](#redis-driver-geo-matching-rate-limiting)).

Candidate retrieval itself is `DriverGeoIndex` (Phase 11) - Redis by
default, a real geo-index instead of the full-table-scan-then-filter this
did through Phase 6-10 - but `DriverMatchingService.findBestDriver`'s own
signature (a ride in, a best driver out) never changed; only how it finds
candidates did, exactly as planned when this section was first written.
Everything above - exclusions, the busy check, vehicle/verification
eligibility, scoring - stays in `DriverMatchingService` regardless of
which `DriverGeoIndex` is active, since neither implementation knows
anything about rides, vehicles, or exclusions.

See [`DriverMatchingService`](saferide-backend/src/main/java/com/saferide/matching/DriverMatchingService.java),
[`DriverMatchScorer`](saferide-backend/src/main/java/com/saferide/matching/DriverMatchScorer.java),
[`DriverReliabilityService`](saferide-backend/src/main/java/com/saferide/driver/DriverReliabilityService.java),
and [`MatchingIntegrationTest`](saferide-backend/src/test/java/com/saferide/matching/MatchingIntegrationTest.java)
for the full mechanism and its test coverage.

## Customer frontend

The full customer journey - login through live tracking - against the real
backend, no mocked data:

**Login → Home → Ride Options → Fare Breakdown → Finding Driver → Ride
Tracking → My Rides / Profile.**

A few design decisions worth calling out:

- **No fake network calls.** Every page hits the real API through a typed
  service layer (`authService`, `rideService`, `fareService`,
  `customerService`) whose request/response types
  ([`types/auth.ts`](saferide-frontend/src/types/auth.ts),
  [`types/ride.ts`](saferide-frontend/src/types/ride.ts),
  [`types/fare.ts`](saferide-frontend/src/types/fare.ts)) mirror the
  backend DTOs field-for-field.
- **JWT handled once, centrally.** [`services/api.ts`](saferide-frontend/src/services/api.ts)
  attaches the access token to every request and, on a 401, transparently
  refreshes and retries the original call - concurrent 401s all await the
  same in-flight refresh rather than each firing their own. A refresh
  that itself fails clears the session and redirects to `/login` via a
  `window` event `AuthContext` listens for (keeps the interceptor, which
  has no router context, decoupled from navigation).
- **"Confirm & Book Ride" does exactly what the backend contract says.**
  The fare-breakdown page's confirm button calls `POST /api/rides` then
  immediately `POST /api/rides/{id}/fare/confirm` - create, then lock -
  matching [`RideService`](saferide-backend/src/main/java/com/saferide/ride/RideService.java)'s
  actual two-step design (see [Fare Lock](#fare-lock--how-the-guarantee-actually-holds)
  above) instead of a UI that implies the fare was "locked" before a ride
  even exists.
- **No fake geocoding.** `MapService` is backend-only and has no
  address-lookup capability yet, so pickup/destination selection is a
  curated list of known-coordinate Guwahati landmarks
  ([`lib/suggestedLocations.ts`](saferide-frontend/src/lib/suggestedLocations.ts))
  or a pin dropped directly on the map - never a text field that silently
  fails to resolve to real coordinates.
- **Real-time push, with polling only as a fallback.** Finding-driver and
  ride-tracking both subscribe to `/topic/rides/{id}/status` and react
  within a second or two of any change; `GET /api/rides/{id}` via TanStack
  Query's `refetchInterval` still runs underneath, just far less
  frequently, so a mid-reconnect socket never leaves the page stale for
  more than one fallback tick. See [Real-time (WebSocket)](#real-time-websocket).
- **The OTP is shown only where the backend allows it.** `GET /api/rides/{id}`
  only ever returns `otp` in the ride's own customer response (see Phase
  5) - the tracking page's OTP banner isn't hiding it from the driver by
  choice, it's rendering exactly what the API returned.
- **Deferred, not faked - until it wasn't.** Payment, rating, SOS and trip
  sharing had no backend through Phase 11, and rather than build screens
  that called nothing, they said so plainly and stayed explicitly "coming
  soon" when tapped. Phase 12 is what makes all four real - see
  [Payment, rating, complaints and safety](#payment-rating-complaints-and-safety).
  Call/Chat remain the one deliberately-deferred pair left (no voice/chat
  infrastructure exists anywhere in this stack), still a "coming soon"
  toast rather than a silent no-op.
- **The post-ride screen renders whatever status actually came back, not a
  fixed "you're done" state.** `RideTrackingPage`'s finished view branches
  on `ride.status` itself - a `Pay ₹X` button only while `PAYMENT_PENDING`,
  the rating form only once `PAYMENT_COMPLETED`, the submitted rating only
  once `RATED` - so refreshing mid-flow, or a WebSocket push landing while
  the rating form is open, can never show a stale action a request would
  now reject.
- **One `ReportIssueDialog`, two ride screens.** The customer's
  `RideTrackingPage` and the driver's `DriverRideDetailPage` both render
  the same [`components/ride/ReportIssueDialog`](saferide-frontend/src/components/ride/ReportIssueDialog.tsx)
  - "who this complaint is about" is inferred server-side from which side
  of the ride the caller is on (see `ComplaintService`), so the component
  itself needs no role prop or branching to work correctly from either side.
- **Every route is lazy-loaded.** [`routes/AppRoutes.tsx`](saferide-frontend/src/routes/AppRoutes.tsx)
  code-splits every page - the Leaflet map stack (the single heaviest
  dependency in the app) never loads for someone who's only looking at
  the login screen. Splitting brought the main bundle from ~700KB to
  ~395KB gzip-measured at build time.

## Driver frontend

The full driver journey against the real backend - going online, a
proactively-assigned request, pickup through drop-off, and the earnings
those completed rides produced:

**Login/Register (role picker) → Role Landing → Dashboard (online toggle,
incoming request) → Ride Detail (accept → arrive → OTP → start → complete)
→ Earnings / Profile (vehicles, documents).**

A few design decisions worth calling out:

- **Login and registration are shared with the customer flow, not
  duplicated.** [`RegisterPage`](saferide-frontend/src/pages/auth/RegisterPage.tsx)'s
  role picker (`CUSTOMER`/`DRIVER`) already collects the driving license
  number when `DRIVER` is selected, and `LoginPage` doesn't need to know a
  caller's role at all - the account itself carries it. A dedicated
  `/driver/login` would be the same form with different chrome.
- **"/" resolves role, it doesn't assume one.** [`RoleLandingPage`](saferide-frontend/src/pages/RoleLandingPage.tsx)
  reads `isCustomer`/`isDriver` off the authenticated account and redirects
  straight through for the common single-role case; an account holding
  *both* roles (the backend explicitly supports a customer later adding
  the DRIVER role - see `POST /api/drivers/register`) sees a two-card
  chooser instead of the SPA silently guessing which experience they meant
  to open. [`ProtectedRoute`](saferide-frontend/src/components/common/ProtectedRoute.tsx)
  takes a `requiredRole` prop for the same reason: it's the same gate
  customer and driver routes share, not two copies of similar logic.
- **There's no dedicated "current assignment" endpoint, so the frontend
  doesn't invent one.** [`useCurrentDriverRide`](saferide-frontend/src/hooks/useCurrentDriverRide.ts)
  derives "my active ride" by scanning the driver's own paginated ride
  history (`GET /api/drivers/rides`) for anything in a busy status
  (`ACTIVE_DRIVER_RIDE_STATUSES`, mirroring the backend's
  `ActiveRideStatuses.DRIVER`) - the dashboard, the requests page, and the
  ride-detail page all share this one hook so "what is my current ride"
  never has two different answers.
- **Going online reports a location immediately, not eventually.** Toggling
  online in [`DriverDashboardPage`](saferide-frontend/src/pages/driver/DriverDashboardPage.tsx)
  calls the browser's `navigator.geolocation.getCurrentPosition` and pushes
  it via `PUT /api/drivers/location` right away, matching
  [Matching](#matching-reliability-and-automatic-reassignment)'s
  requirement that a driver needs a reported location to be a candidate at
  all - a driver who just tapped "online" doesn't have to wait for a
  polling cycle to become matchable.
- **The proactive assignment and the fallback list are visually distinct,
  on purpose.** [`IncomingRequestCard`](saferide-frontend/src/components/driver/IncomingRequestCard.tsx)
  (a `DRIVER_ASSIGNED` ride the matching engine picked *for* this driver)
  only ever renders one at a time and requires an explicit Accept/Reject;
  [`DriverRidesPage`](saferide-frontend/src/pages/driver/DriverRidesPage.tsx)'s
  browse list (`GET /api/rides/open`) is the Phase 4 fallback and is only
  ever shown once there's no active assignment - a driver is never asked
  to choose between the two.
- **The OTP entry only appears where the state machine allows it.** The
  ride-detail page's Accept → Mark Arrived → OTP → Start → Complete flow
  ([`DriverRideDetailPage`](saferide-frontend/src/pages/driver/DriverRideDetailPage.tsx))
  renders one action per ride status, never a form for a transition the
  backend would reject - `RideStateMachine` stays the single source of
  truth for what's legal next, the UI just reflects it.
- **Earnings are read from the locked fare, never recomputed.** [`DriverEarningsPage`](saferide-frontend/src/pages/driver/DriverEarningsPage.tsx)
  aggregates `GET /api/rides/{id}/fare` across the driver's completed rides
  for the chart (Recharts) and trip list - the same [Fare Lock](#fare-lock--how-the-guarantee-actually-holds)
  amount the customer confirmed, not a client-side distance×rate guess
  that could silently drift from what was actually charged.
- **Documents are URL-based, honestly.** The backend has no file-upload
  endpoint yet (`documentUrl` is just a validated string column) -
  [`DriverProfilePage`](saferide-frontend/src/pages/driver/DriverProfilePage.tsx)'s
  document form says so directly ("paste a hosted URL... direct uploads
  arrive in a later phase") instead of faking a file picker that has
  nowhere real to send the bytes.
- **A driver can report a rider too (Phase 12).** `DriverRideDetailPage`'s
  finished view renders the same `ReportIssueDialog` the customer side
  does - filing works identically either way, the backend just doesn't
  update any driver stat when the *driver* is the one filing (there's no
  symmetric field on the customer side yet to update).

## Admin frontend

Phase 9 built the **driver verification console**
(`/api/admin/drivers/**`) end to end rather than a wider dashboard with
panels that called nothing - there was no `/api/admin/complaints` or
`/api/admin/safety/alerts` yet for a wider console to show. Phase 12 adds
both, following that same console's shape rather than inventing a new one;
there's still no `/api/admin/users`, `/api/admin/rides`, `/api/admin/payments`
or `/api/admin/reports` endpoint - those land with the phases that give
admins something real to review there.

**Login → Role Landing → Dashboard (counts + pending queue) → Drivers (filter
+ paginate) → Driver Detail (approve/block driver, approve/reject each
vehicle and document) → Complaints (filter + paginate → resolve/dismiss) →
Safety Alerts (active SOS presses → resolve).**

A few design decisions worth calling out:

- **The role model generalized, not duplicated.** `AuthContext` already had
  `isCustomer`/`isDriver` from Phase 8; Phase 9 adds `isAdmin` next to them
  and widens `ProtectedRoute`'s `requiredRole` prop from
  `"CUSTOMER" | "DRIVER"` to the full `RoleName` rather than writing a
  second, admin-specific guard component.
  [`RoleLandingPage`](saferide-frontend/src/pages/RoleLandingPage.tsx)'s
  chooser is now driven by an array of `{role, to, label, icon}` entries
  filtered against whichever roles the account actually has, so it already
  supports three roles - or, in principle, more - without another rewrite.
- **No summary endpoint, so the frontend doesn't invent one.** The
  dashboard's stat cards (total / pending / verified / rejected) are four
  parallel calls to the same `GET /api/admin/drivers?status=&size=1`
  the drivers table uses, reading only `page.totalElements` - real counts
  from the real paginated endpoint, not a client-side aggregate that could
  drift from what the table below it actually shows.
- **Block and reject ask *why*, approve and unblock don't.**
  [`ReasonConfirmDialog`](saferide-frontend/src/components/admin/ReasonConfirmDialog.tsx)
  is `ConfirmDialog` plus an optional reason field for the two actions the
  backend's `AdminActionRequest.reason` actually persists to the audit log
  (`PUT .../block`, `.../vehicles/{id}/reject`, `.../documents/{id}/reject`);
  approving or unblocking is a single confirmed click with no prompt,
  matching that only the negative actions carry a reason worth recording.
- **One `VerificationStatus` badge, one place.** The backend deliberately
  reuses a single `VerificationStatus` enum across `DRIVER`, `VEHICLE` and
  `DRIVER_DOCUMENT` (see [Database design](#database-design)); the frontend
  mirrors that with one
  [`VerificationBadge`](saferide-frontend/src/components/admin/StatusBadges.tsx)
  used on all three, rather than three near-identical badge components that
  could quietly drift apart.
- **Desktop-first, deliberately.** `AdminLayout` has no mobile bottom-tab
  nav, unlike `CustomerLayout`/`DriverLayout` - tables and multi-field
  review screens are what an admin console actually needs, not a phone-sized
  card feed, so it isn't built as one.
- **Complaints reuse the driver console's exact pattern (Phase 12).**
  `AdminComplaintsPage`/`AdminComplaintDetailPage` are the same status-tab
  list + paginate + `ReasonConfirmDialog` resolve/dismiss shape as
  `AdminDriversPage`/`AdminDriverDetailPage`, right down to reusing
  `AdminActionRequest` for the optional note - a fourth near-identical
  admin review flow would have been a good reason to extract a shared
  component, but three total data shapes (driver, complaint, safety alert)
  each with small enough differences kept that not yet worth doing.
- **Safety alerts poll while they matter, not always.** `AdminSafetyPage`
  only refetches every 15s while viewing the `ACTIVE` tab - an SOS press
  is the one thing in this console worth noticing without a manual
  refresh; `RESOLVED`/`ALL` don't poll at all.
- **The seeded admin account works immediately.** `admin@example.com` /
  `Password123!` (see [Seed data](#seed--demo-credentials)) logs straight
  into `/admin/dashboard` via `RoleLandingPage` and can review the seeded
  `driver@example.com` account (already `VERIFIED`, so try registering a
  second driver locally to see the PENDING queue populate).

## Real-time (WebSocket)

Every polling loop from Phases 7-9 (ride status, driver assignments) now has
a push counterpart, and driver location - which had no REST field to poll in
the first place - exists for the first time. Plain STOMP over a native
`WebSocket` at `/ws`, no SockJS: every browser this app targets supports
`WebSocket` directly, so a long-polling fallback would add nothing.

**Three destinations**, each solving a different constraint:

| Destination | Direction | Why it's shaped this way |
|---|---|---|
| `/topic/rides/{id}/status` | Broadcast to the ride's own customer + driver | Deliberately minimal (`rideId`, `status`, `updatedAt`) - **no `otp`, no per-caller fields**. `RideResponse` varies by who's asking (only the customer ever sees the OTP - see [Fare Lock](#fare-lock--how-the-guarantee-actually-holds)'s sibling guarantee), but this channel is shared by both participants, so it can only ever carry what's safe for either of them. It's a "something changed, refetch" nudge, not a data feed - the frontend calls `queryClient.invalidateQueries(["ride", rideId])` on receipt, and the existing secure `GET /api/rides/{id}` supplies the rest. |
| `/topic/rides/{id}/location` | Broadcast to the ride's own customer + driver | No customer/driver asymmetry to protect here, so this one *does* carry the real payload (`latitude`, `longitude`) directly - the frontend moves a map marker on every message with zero REST round trips. Fed by the driver's own device: `DriverRideDetailPage` reports a fresh position every 5s via the existing `PUT /api/drivers/location` for as long as the ride is `DRIVER_ACCEPTED`/`DRIVER_ARRIVED`/`RIDE_STARTED`. |
| `/user/queue/assignments` | Private, per-driver | A driver has no ride id to subscribe to *before* being assigned one, unlike a customer tracking their own already-created ride - Spring's user-destination feature (resolved via `StompPrincipal.getName()`, the user id) solves that without a guessable per-driver topic. |

**Authentication happens on the STOMP CONNECT frame, not the HTTP
handshake.** A browser's native `WebSocket` can't set an `Authorization`
header on the handshake request, so `JwtAuthenticationFilter` (HTTP-only)
has nothing to check for `/ws` - `SecurityConfig` deliberately leaves it
`permitAll()` at the HTTP layer. Instead, `StompAuthChannelInterceptor`
validates the same JWT as a native STOMP header on CONNECT (exactly what
`@stomp/stompjs`'s `beforeConnect` hook sets, re-read from storage on every
reconnect in case it was refreshed since) and attaches the resulting
`StompPrincipal` to the session for its lifetime.

**Subscribing to someone else's ride is rejected, not filtered.** Ride ids
are sequential and guessable, so `StompAuthChannelInterceptor` also checks
every `SUBSCRIBE` to `/topic/rides/{id}/**` against `RideRepository`
(session-free `EXISTS` queries - `Ride.customer`/`Ride.driver` are lazy
associations, and the interceptor runs on the message channel, not a
request thread, so hydrating either would throw
`LazyInitializationException`) before it's allowed to succeed - an
unauthorized subscription never receives anything to begin with, rather
than quietly filtering a broadcast after the fact.

**A broadcast only ever describes a committed state.** `RideEventPublisher`
- the one place every send in the app goes through - never calls
`SimpMessagingTemplate` directly; it registers a
`TransactionSynchronization.afterCommit` callback instead. `RideService`'s
mutating methods call it from inside their own `@Transactional` boundary,
before returning - broadcasting immediately would risk telling a client a
ride reached e.g. `DRIVER_ACCEPTED` moments before a concurrent `@Version`
conflict rolled that exact save back (see
[Concurrency](#concurrency--optimistic-locking)). A push that can describe
a state that never actually persisted would be worse than no push at all.

**The frontend's polling didn't disappear, it demoted to a fallback.**
Every page that used to poll every 3-5s now polls every 8-15s instead of
not at all - if the socket is mid-reconnect (a network blip, a laptop
waking from sleep), the page still catches up within one fallback tick
rather than going stale indefinitely.
`WebSocketProvider` ([`contexts/WebSocketContext.tsx`](saferide-frontend/src/contexts/WebSocketContext.tsx))
holds one STOMP connection per authenticated session and a registry of
desired subscriptions that's replayed in full on every `onConnect` -
including reconnects - so a component calling
[`useRideStatusChannel`/`useRideLocationChannel`/`useDriverAssignmentChannel`](saferide-frontend/src/hooks/useRideChannel.ts)
never has to know whether the underlying socket just dropped and came back.

See [`WebSocketConfig`](saferide-backend/src/main/java/com/saferide/websocket/WebSocketConfig.java),
[`StompAuthChannelInterceptor`](saferide-backend/src/main/java/com/saferide/websocket/StompAuthChannelInterceptor.java),
[`RideEventPublisher`](saferide-backend/src/main/java/com/saferide/ride/RideEventPublisher.java),
and [`RideWebSocketIntegrationTest`](saferide-backend/src/test/java/com/saferide/websocket/RideWebSocketIntegrationTest.java)
(a real WebSocket connection end to end - CONNECT rejected without a valid
token, a stranger's SUBSCRIBE to someone else's ride rejected, a customer
receiving a live status event, a driver receiving a private assignment
notification, and a location broadcast) for the full mechanism and its
test coverage.

## Redis (driver geo-matching, rate limiting)

Two independent uses, chosen per feature via a config property rather than
a global "is Redis on" switch - see each one's dedicated section below for
why that matters:

| Feature | Property | Default | Test profile |
|---|---|---|---|
| Matching candidate lookup | `app.matching.geo-index-source` | `redis` | `sql` |
| Login rate limiting | `app.security.login-rate-limit.source` | `redis` | `in-memory` |

### Driver geo-index

`DriverMatchingService`'s candidate lookup (see
[Matching](#matching-reliability-and-automatic-reassignment) above) used
to be a full scan of every online driver, filtered and Haversine-distance-
checked in Java - correct, but O(all online drivers) regardless of how far
away most of them are. `DriverGeoIndex` replaces just that one lookup with
a real geospatial query:

- **`RedisDriverGeoIndex`** (default) - one Redis key holding a
  `GEOADD`/`GEOSEARCH`/`ZREM` sorted set of every online driver's position.
  `findWithinRadius` is a single `GEOSEARCH ... BYRADIUS` call, O(nearby
  drivers) instead of O(all of them).
- **`SqlDriverGeoIndex`** - the original full-scan-then-filter logic,
  extracted rather than deleted. Active when
  `app.matching.geo-index-source=sql` - a legitimate small-scale,
  no-Redis deployment choice, and what `SqlDriverGeoIndexTest` exercises
  directly (no Spring context needed for that one) - not a test stub, a
  complete second implementation, exactly `MockMapService`'s relationship
  to a real map provider. The main integration suite runs against real
  Redis instead (Phase 14, via Testcontainers - see
  [Testing](#testing)).

Both sit behind the same interface, so `DriverMatchingService` (exclusions,
the busy check, vehicle/verification eligibility, scoring) never changes
regardless of which is active - see
[Matching](#matching-reliability-and-automatic-reassignment) for what
happens after candidates come back.

**MySQL stays the source of truth.** `Driver.currentLatitude`/
`currentLongitude` are still what `PUT /api/drivers/location` writes
first; `DriverService` only *mirrors* an ONLINE driver's position into
whichever `DriverGeoIndex` is active afterward, and removes them from it
the moment they go OFFLINE (an offline driver must never be a matching
candidate, in MySQL's business rules or Redis's index alike). If Redis is
flushed or restarted, the index is empty until drivers next report a
location or toggle online - not a data-loss risk, since nothing
authoritative ever lived there.

**Every Redis call fails open, never fails the request.** If Redis is
briefly unreachable, `RedisDriverGeoIndex` catches, logs a warning, and
degrades: a location/online-status write still succeeds against MySQL, and
`findWithinRadius` returns no candidates rather than throwing - which just
means a ride waits at `SEARCHING_DRIVER`, still reachable through the
`GET /api/rides/open` fallback (which never touches Redis at all). A cache
being down should never be able to take an unrelated MySQL write down with
it.

### Login rate limiting

`LoginRateLimiter` slows down repeated password guessing against one
account - deliberately keyed by login identifier (email/phone), not caller
IP. That's a specific, deliberate choice: the threat this defends against
is many guesses aimed at *one* known account, which an IP-based limiter
alone wouldn't catch from a distributed attacker, and an identifier-based
one does regardless of where the requests originate. (IP-based limiting,
for the different threat of one attacker spraying many accounts, is a
deliberate non-goal here, not an oversight.)

`AuthService.login` checks `loginRateLimiter.checkAllowed(identifier)`
before touching the database or computing a bcrypt hash at all; a blocked
account fails for its own, unrelated reason and isn't counted as a
rate-limited failure, so the limiter never double-punishes an account an
admin already blocked. `app.security.login-rate-limit.max-attempts` (10)
and `.window-minutes` (15) are shared config, regardless of which
implementation is enforcing them:

- **`RedisLoginRateLimiter`** (default) - one Redis string counter per
  identifier, `INCR`-ed on every failure with `EXPIRE` set only on the
  first increment of a fresh window (so the window is "N minutes since the
  first recent failure," not extended by every subsequent one). Shared
  across every backend instance.
- **`InMemoryLoginRateLimiter`** - the same enforcement logic against a
  per-instance `ConcurrentHashMap` instead. Active when
  `app.security.login-rate-limit.source=in-memory` (the test profile), and
  a real, complete implementation in its own right, not a mock.

**Also fails open.** A rate limiter that itself becomes a way to lock every
user out of login the moment its backing store hiccups would trade a minor
security control for a major availability one - `RedisLoginRateLimiter`
catches, logs, and lets the login attempt proceed unrated-limited rather
than blocking it.

See [`DriverGeoIndex`](saferide-backend/src/main/java/com/saferide/matching/DriverGeoIndex.java),
[`RedisDriverGeoIndex`](saferide-backend/src/main/java/com/saferide/matching/RedisDriverGeoIndex.java) /
[`SqlDriverGeoIndex`](saferide-backend/src/main/java/com/saferide/matching/SqlDriverGeoIndex.java),
[`LoginRateLimiter`](saferide-backend/src/main/java/com/saferide/auth/LoginRateLimiter.java),
and the `RedisDriverGeoIndexTest` / `SqlDriverGeoIndexTest` /
`RedisLoginRateLimiterTest` / `InMemoryLoginRateLimiterTest` /
`AuthIntegrationTest#repeatedFailedLoginsAreRateLimitedPerIdentifier` tests
for the full mechanism and its test coverage.

## Payment, rating, complaints and safety

`RideStateMachine` had `RIDE_COMPLETED → PAYMENT_PENDING → PAYMENT_COMPLETED
→ RATED` from Phase 4 on (its own class doc says as much - "for Phase 12's
rating flow"); this phase is what finally drives those edges, plus the
Safety Center screens that have shown "coming soon" toasts since Phase 7.

### Payment and rating finish the Fare Lock guarantee, they don't reopen it

`PaymentService.pay` never accepts an amount - it reads the ride's
currently-**locked** fare itself via `FareService.getRideFare` and charges
exactly that, through `PaymentGateway` (`MockPaymentGateway` today, a real
provider - Razorpay, Stripe - a second implementation later, precisely
`MapService`'s relationship to a real routing provider). The "driver
cannot demand an unauthorized extra fare" promise wouldn't mean much if
Phase 12 quietly let a different number get charged at the end - it can't:
there is no request field for one.

`RideService.payForRide`/`rateRide` follow the exact shape
`confirmFare`/`changeDestination` already established: validate the
transition first (`stateMachine.validateTransition`, so a ride in the
wrong state is rejected before the gateway is ever touched or a rating is
ever recorded), then delegate the domain-specific work to
`PaymentService`/`RatingService`, then perform the transition itself.
Rating a driver updates `Driver.rating` through the exact same
exponential-moving-average `DriverReliabilityService` already uses for
`acceptanceRate`/`completionRate`/`cancellationRate` (see
[Matching](#matching-reliability-and-automatic-reassignment)) - one bad
rating nudges it down proportionally to `app.reliability.ema-alpha`,
never overwrites it outright.

### Complaints are the dispute system the whole README has been building toward

`ComplaintService.file` needs no `RideStateMachine` involvement at all - a
complaint is a standalone record attachable to a ride at any point by
either its customer or its assigned driver, independent of what status the
ride is in. What *is* enforced: **every complaint filed by a customer -
regardless of `type`, not just `EXTRA_FARE_DEMANDED` - increments the
driver's `complaintCount` and recomputes `reliabilityScore`** through
`DriverReliabilityService.recordComplaint`, the same field
`ReliabilityCalculator`'s complaint penalty has read since Phase 6, seeded
at 0 and waiting exactly for this. A driver can file about a customer too
(symmetry, and drivers face bad-faith riders as often as the reverse), but
there's no `complaintCount`-equivalent on the customer side to update -
`User`/`Customer` has no such field, so filing there is honestly just a
record, not a stat that feeds anything yet.

Admin review (`AdminComplaintService`, `/admin/complaints`) mirrors driver
verification's shape exactly: list/filter by status, inspect one, then
`resolve`/`dismiss` with an optional note - the same `AdminActionRequest`
and `ReasonConfirmDialog` [Admin frontend](#admin-frontend) already uses
for block/reject, reused rather than rebuilt for a fourth time.

### Safety Center

Two independent features behind the buttons that have said "coming soon"
since Phase 7:

- **SOS** (`SafetyAlert`) - a plain record of who pressed it, from where
  (the device's live position at that moment, not the ride's pickup point
  - captured fresh, since an emergency mid-trip can be anywhere along the
  route), visible to admins in real time-ish (`AdminSafetyPage` polls
  every 15s while viewing active alerts) and resolvable once handled.
- **Trip sharing** (`TripShare`) - a long random token (never a sequential
  id), guarded by nothing else: `GET /api/public/trips/{token}` is the one
  deliberately unauthenticated endpoint in the entire API
  (`SecurityConfig` permits `/api/public/**` at the HTTP layer for exactly
  this). `PublicTripResponse` is a stripped-down view on purpose - no OTP,
  no phone numbers, nothing that would let a stranger holding the link
  contact either party or interfere with the ride, just enough for a
  friend or family member to watch it progress. The public tracking page
  (`/track/:token`) polls every 8s rather than subscribing over
  [WebSocket](#real-time-websocket) - the real-time channel authenticates
  on CONNECT with a JWT a visitor here never has, so this stays an honest
  plain poll instead of a live push it can't actually offer.

See [`PaymentService`](saferide-backend/src/main/java/com/saferide/payment/PaymentService.java),
[`RatingService`](saferide-backend/src/main/java/com/saferide/rating/RatingService.java),
[`ComplaintService`](saferide-backend/src/main/java/com/saferide/complaint/ComplaintService.java),
[`SafetyService`](saferide-backend/src/main/java/com/saferide/safety/SafetyService.java),
and [`PostRideIntegrationTest`](saferide-backend/src/test/java/com/saferide/payment/PostRideIntegrationTest.java)
(a real ride driven all the way through `PAYMENT_PENDING → PAYMENT_COMPLETED
→ RATED`, exact-locked-fare charging, a rating that measurably moves the
driver's score, a complaint that measurably moves `complaintCount`/
`reliabilityScore`, admin resolve/dismiss idempotency, SOS visibility +
resolution, and a public share link that works with no `Authorization`
header at all) for the full mechanism and its test coverage.

## Domain events, notifications, and analytics

Three things bundled into one phase because they share one root: a small
set of `DomainEvent` records (`RideRequestedEvent`, `DriverAssignedEvent`,
`RideCompletedEvent`, `PaymentCompletedEvent`, `ComplaintFiledEvent`,
`ComplaintResolvedEvent`) published from `RideService`/`PaymentService`/
`ComplaintService`/`AdminComplaintService` at the exact points those
actions already commit.

### Two independent listeners, one guarantee that doesn't depend on Kafka

Both listeners react to the same events via Spring's own
`ApplicationEventPublisher` + `@TransactionalEventListener(phase =
AFTER_COMMIT)` - never a raw `@EventListener`, since a notification (or a
Kafka message) describing something a rolled-back transaction undid would
be worse than not sending one at all. This is the same reasoning
`RideEventPublisher` used for WebSocket broadcasts back in Phase 10, just
via Spring's built-in mechanism instead of manual
`TransactionSynchronizationManager` bookkeeping, since these are plain
listener methods rather than something a REST controller calls directly.

- **`NotificationEventListener`** turns each event into a `Notification`
  row for whoever should see it (both sides get one for
  `DriverAssignedEvent`/`RideCompletedEvent`/`PaymentCompletedEvent`; only
  the filer for `ComplaintFiledEvent`/`ComplaintResolvedEvent`) - this is
  the **only** thing `GET /api/notifications` depends on, and it never
  touches Kafka. `NotificationService.create` runs with
  `Propagation.REQUIRES_NEW` rather than the default - Spring's own
  documented caveat for `AFTER_COMMIT` listeners is that the triggering
  transaction's resources are still bound to the thread at that point
  (final cleanup happens one step later), so a plain `REQUIRED` write here
  would silently "join" a transaction that has already committed and will
  never commit again, and the insert would never actually be flushed. A
  genuinely new transaction is what makes the write real - see
  [`KafkaEventForwarderTest`](saferide-backend/src/test/java/com/saferide/event/KafkaEventForwarderTest.java)'s
  class doc, which found this the hard way.
- **`KafkaEventForwarder`** mirrors the exact same event onto
  `saferide.domain-events`, keyed by event type - "optional/additive"
  literally: it's `@ConditionalOnProperty(app.events.kafka-forwarding-enabled)`
  (`true` by default, `false` under the test profile), and even when
  enabled it runs on a background thread (`@Async`, backed by
  `@EnableAsync` on `SafeRideApplication`) with a broad catch around the
  send - a slow or completely unreachable broker never adds latency to,
  or fails, the request that triggered it. Nothing in this app reads from
  this topic yet; it exists for a consumer outside this JVM that doesn't
  exist yet (a future analytics pipeline, another service).

### Notifications

`Notification` (`user`, `type`, `title`, `message`, `rideId`, `read`,
`readAt`) is a plain per-user inbox row - `rideId` is a bare `long`, not an
FK, same reasoning `AuditLog` already documents: a notification should
keep meaning what it said even if the ride it references later changes.
`NotificationController` (`/api/notifications`) is entirely
role-agnostic - list (optionally unread-only), unread count, mark one
read, mark all read - the same four endpoints serve the customer, driver,
and admin navbars identically, since ownership (not role) is what's being
checked (`markAsRead` throws `UnauthorizedException` → `403` if the
notification isn't the caller's own). The frontend's `NotificationBell`
(shared by all three navbars) polls the unread count every 20s rather
than subscribing over WebSocket - Phase 10's channels are ride-scoped
(`/topic/rides/{id}/...`), not a generic per-user feed, so extending that
would have meant a new STOMP destination this phase didn't need to justify
yet; polling is the same honest fallback the public tracking page already
uses for the same reason.

### Analytics

`AdminAnalyticsService.summary()` (`GET /api/admin/analytics/summary`,
`ADMIN` only) is fifteen numbers, every one a real `COUNT`/`SUM`/`AVG`
query - never an estimate, never rows pulled into Java and summed by
hand. `activeRides` is the one derived value (`totalRides - completedRides
- cancelledRides`, computed from two separate `countByStatusIn` queries
against `RideStateMachine`'s own status groupings), everything else maps
to exactly one repository call. The frontend's `AdminAnalyticsPage`
renders it as stat cards (reusing the `StatCard` component from Phase 8)
plus two small Recharts bar charts (rides by status, complaints by
status) - a snapshot, not a time series, matching what the endpoint
actually returns; it refetches every 30s.

See [`event/`](saferide-backend/src/main/java/com/saferide/event/),
[`NotificationEventListener`](saferide-backend/src/main/java/com/saferide/notification/NotificationEventListener.java),
[`KafkaEventForwarder`](saferide-backend/src/main/java/com/saferide/event/KafkaEventForwarder.java),
[`AdminAnalyticsService`](saferide-backend/src/main/java/com/saferide/admin/AdminAnalyticsService.java),
and [`NotificationIntegrationTest`](saferide-backend/src/test/java/com/saferide/notification/NotificationIntegrationTest.java)
(a full ride lifecycle driven through real HTTP requests, asserting the
right notification shows up for the right user at every step, plus
unread-count/mark-read/mark-all-read/ownership checks) for the full
mechanism and its test coverage.

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

Payment, rating and complaints ended up ride-scoped sub-resources
(`/api/rides/{id}/pay`, `/api/rides/{id}/rating`,
`/api/rides/{id}/complaints`) rather than the top-level
`/api/payments`/`/api/ratings` collections envisioned in Phase 1 - once
Phases 4-6 established that same convention for fare/accept/decline/...,
following it kept every ride-lifecycle action in one consistent place
instead of splitting an otherwise-identical pattern across two shapes.
`/api/complaints` (a caller's own history, not scoped to one ride),
`/api/admin/complaints`/`/api/admin/safety/alerts` (admin review), and
`/api/notifications`/`/api/admin/analytics/summary` (Phase 13, neither
ride- nor role-scoped in the same way) are the exceptions, for the same
reason `GET /api/rides` and `GET /api/admin/drivers` already aren't
ride-scoped either. Endpoint groups not yet built
(`/api/admin/{users,rides,reports}`, ...) are introduced with the phase
that implements them and documented live via Swagger UI once the app is
running:

- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs

### Live endpoints

| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/api/ping` | Public | Liveness check |
| POST | `/api/auth/register` | Public | `role: CUSTOMER \| DRIVER`; `licenseNumber` required when `role=DRIVER` |
| POST | `/api/auth/login` | Public | `identifier` accepts email or phone; rate-limited per identifier (Phase 11) → `429 TOO_MANY_ATTEMPTS` after repeated failures |
| POST | `/api/auth/refresh` | Public | Rotates the refresh token; the old one is revoked |
| POST | `/api/auth/logout` | Public | Revokes the given refresh token (idempotent) |
| GET / PUT | `/api/customers/profile` | `CUSTOMER` | Always operates on the caller's own account |
| POST | `/api/drivers/register` | Any authenticated user | Adds the DRIVER role + driver profile to the current account; re-login/refresh needed to pick up the new role |
| GET | `/api/drivers/profile` | `DRIVER` | Includes `onTimeRate`, `complaintCount`, `reliabilityScore`, current location |
| PUT | `/api/drivers/status` | `DRIVER` | `{onlineStatus: ONLINE\|OFFLINE}`; going `ONLINE` requires an admin-verified driver |
| PUT | `/api/drivers/location` | `DRIVER` | `{latitude, longitude}` - MySQL source-of-truth write; what makes a driver a matching candidate |
| POST / GET | `/api/drivers/documents` | `DRIVER` | Submit/list documents for review (`documentUrl` references external storage - no upload endpoint yet) |
| GET | `/api/drivers/rides` | `DRIVER` | Own ride history, paginated |
| POST / GET | `/api/vehicles` | `DRIVER` | Register a vehicle (auto-becomes the active one) / list own vehicles |
| PUT | `/api/vehicles/{id}/activate` | `DRIVER` | Switch which owned vehicle is active |
| GET | `/api/admin/drivers` | `ADMIN` | `?status=PENDING\|VERIFIED\|REJECTED&page=&size=`; includes `reliabilityScore` |
| GET | `/api/admin/drivers/{id}` | `ADMIN` | Driver + vehicles + documents in one call |
| PUT | `/api/admin/drivers/{id}/approve` \| `/block` \| `/unblock` | `ADMIN` | `/block` also forces the driver offline |
| PUT | `/api/admin/drivers/vehicles/{id}/approve` \| `/reject` | `ADMIN` | |
| PUT | `/api/admin/drivers/documents/{id}/approve` \| `/reject` | `ADMIN` | |
| POST | `/api/rides/estimate` | `CUSTOMER` | Distance/ETA preview via `MapService` - no ride created |
| POST | `/api/rides` | `CUSTOMER` | Creates a ride at `REQUESTED` (not yet driver-visible - see [Fare Lock](#fare-lock--how-the-guarantee-actually-holds)); rejects a second active ride per customer (409 `ACTIVE_RIDE_EXISTS`) |
| GET | `/api/rides` | `CUSTOMER` | Own ride history, paginated |
| GET | `/api/rides/open` | `DRIVER` | Fallback browse-and-claim for whenever proactive matching hasn't (yet) found anyone - excludes any ride this driver already declined/cancelled |
| GET | `/api/rides/{id}` | Customer, assigned driver, or `ADMIN` | `otp` is only included for the ride's own customer |
| GET | `/api/rides/{id}/fare` | Customer, assigned driver, or `ADMIN` | Current breakdown + lock status; `lockStatus` is absent until first confirmed |
| POST | `/api/rides/{id}/fare/confirm` | `CUSTOMER` | **Locks the fare**, advances `REQUESTED → SEARCHING_DRIVER`, and triggers the first proactive matching attempt; confirming an already-locked fare → `409 FARE_LOCKED` |
| PUT | `/api/rides/{id}/destination` | `CUSTOMER` | Recalculates distance/fare and **unlocks** the fare (never edits the locked amount in place); only before a driver is assigned; pulls a searching ride back to `REQUESTED` until re-confirmed |
| POST | `/api/rides/{id}/accept` | `DRIVER` | Confirms a proactive assignment, **or** self-claims an open ride as a fallback; requires `ONLINE` + verified driver + verified, type-matching active vehicle; `409 CONCURRENT_MODIFICATION` if another driver won the race |
| POST | `/api/rides/{id}/decline` | `DRIVER` (assigned) | Turns down a proactive assignment - permanently excludes this driver from the ride and immediately reassigns to the next-best candidate |
| POST | `/api/rides/{id}/arrive` | `DRIVER` (assigned) | Generates the 4-digit OTP |
| POST | `/api/rides/{id}/start` | `DRIVER` (assigned) | `{otp}`; wrong code → `400 INVALID_OTP` |
| POST | `/api/rides/{id}/complete` | `DRIVER` (assigned) | Auto-advances straight to `PAYMENT_PENDING` |
| POST | `/api/rides/{id}/cancel` | Customer or assigned driver | Customer → `CANCELLED` (terminal); driver → excluded + reliability updated + **automatically reassigned** (or back to `SEARCHING_DRIVER` if nobody's left); blocked once `RIDE_STARTED` |
| POST | `/api/fare/calculate` | `CUSTOMER` | Standalone preview (e.g. one call per vehicle card on the ride-options page) - computes but never persists anything |
| POST | `/api/rides/{id}/pay` | `CUSTOMER` | Charges the ride's **locked** fare (never a client-supplied amount), advances `PAYMENT_PENDING → PAYMENT_COMPLETED`; a repeat call → `409 INVALID_RIDE_STATE` |
| GET | `/api/rides/{id}/payment` | Customer, assigned driver, or `ADMIN` | `404` until `pay` has been called |
| POST | `/api/rides/{id}/rating` | `CUSTOMER` | `{score: 1-5, comment?}`; advances `PAYMENT_COMPLETED → RATED` (terminal - a second attempt → `409`); updates the driver's `rating`/`reliabilityScore` via the same EMA every other driver stat uses |
| GET | `/api/rides/{id}/rating` | Customer, assigned driver, or `ADMIN` | `404` until rated |
| POST | `/api/rides/{id}/complaints` | Customer or assigned driver | `{type, description}`; a customer-filed complaint also increments the driver's `complaintCount` and recomputes `reliabilityScore`, regardless of `type` |
| GET | `/api/complaints` | Any authenticated user | Every complaint the caller has ever filed, as either a customer or a driver, paginated |
| GET | `/api/admin/complaints` | `ADMIN` | `?status=OPEN\|UNDER_REVIEW\|RESOLVED\|DISMISSED&page=&size=` |
| GET | `/api/admin/complaints/{id}` | `ADMIN` | |
| PUT | `/api/admin/complaints/{id}/resolve` \| `/dismiss` | `ADMIN` | `{reason?}`; either on an already-closed complaint → `409 INVALID_COMPLAINT_STATE` |
| POST | `/api/rides/{id}/sos` | Customer or assigned driver | `{latitude, longitude}` - the caller's live position, not the ride's pickup point |
| GET | `/api/admin/safety/alerts` | `ADMIN` | `?status=ACTIVE\|RESOLVED&page=&size=` |
| PUT | `/api/admin/safety/alerts/{id}/resolve` | `ADMIN` | Resolving an already-resolved alert → `409 INVALID_SAFETY_ALERT_STATE` |
| POST | `/api/rides/{id}/share` | `CUSTOMER` | Creates (or returns the still-valid existing) share token, 24h TTL |
| GET | `/api/public/trips/{token}` | **Public - no auth** | Guarded only by possessing the token; deliberately stripped-down response (no OTP, no phone numbers) - see [Safety Center](#safety-center) |
| GET | `/api/notifications` | Any authenticated user | `?unreadOnly=&page=&size=`; always the caller's own, regardless of role |
| GET | `/api/notifications/unread-count` | Any authenticated user | Polled by `NotificationBell` every 20s |
| PUT | `/api/notifications/{id}/read` | Any authenticated user | `403` if the notification isn't the caller's own |
| PUT | `/api/notifications/read-all` | Any authenticated user | |
| GET | `/api/admin/analytics/summary` | `ADMIN` | Fifteen numbers, every one a real `COUNT`/`SUM`/`AVG` query - see [Domain events, notifications, and analytics](#domain-events-notifications-and-analytics) |

### Real-time channels (Phase 10)

Not REST - a single STOMP-over-WebSocket connection at `/ws`, authenticated
on the CONNECT frame (see [Real-time (WebSocket)](#real-time-websocket)
above for the full mechanism).

| Destination | Who receives it | Payload |
|---|---|---|
| `/topic/rides/{id}/status` | The ride's own customer + assigned driver (subscription rejected for anyone else) | `{rideId, status, updatedAt}` - a refetch signal, not a data feed |
| `/topic/rides/{id}/location` | Same as above | `{rideId, driverId, latitude, longitude, recordedAt}` - the real coordinates, fed by the driver's device every 5s while the ride is active |
| `/user/queue/assignments` | The driver it's addressed to, privately | `{rideId, status}` - sent the moment matching proactively assigns them a ride |

Access tokens are short-lived (15 min default, `JWT_EXPIRATION`) self-contained
JWTs carrying `sub` (user id), `email` and `roles` claims - no DB hit per
request. Refresh tokens (7 days default, `JWT_REFRESH_EXPIRATION`) are opaque
random values stored only as a SHA-256 hash, rotated on every use, and
revocable server-side (what makes logout actually work). See
[`AuthService`](saferide-backend/src/main/java/com/saferide/auth/AuthService.java)
for the full flow.

**Dev-only seed accounts** (see [Seed data](#seed--demo-credentials)
below) let you log in immediately without registering - the seeded driver
is pre-verified with a pre-verified active vehicle, so it's ready to go
online immediately.

## Local setup

Prerequisites: **Java 21+** (JDK 24 with `--release 21` also works), **Maven
3.9+**, **Node 20+**, **Docker Desktop** (for local MySQL/Redis, and for
`mvn test` - see [Testing](#testing), Phase 14 runs backend integration
tests against real Testcontainers-managed MySQL/Redis rather than an
in-memory stand-in).

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

Runs on `http://localhost:5173` and redirects to `/login`. Register a new
account (or use a [seed account](#seed--demo-credentials)) to book
a real ride end to end - the seeded `driver@example.com` is online,
verified, and positioned at Guwahati Railway Station, so a ride booked
from there gets proactively matched to it immediately.

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

The WebSocket endpoint needs no environment variable of its own on the
backend - `WebSocketConfig` reuses `app.cors.allowed-origins`, so a `/ws`
connection and a REST call are always allowed from the same origins by
construction. The frontend's `VITE_WS_BASE_URL` (see `.env.example`,
default `ws://localhost:8080/ws`) is the one new frontend-side variable
this phase adds.

## Docker

Two separate compose files for two separate jobs - each documents its own
reasoning in its own header comment, not repeated here:

- `docker-compose.dev.yml` — MySQL 8.4 + Redis 7.4 only, so the backend and
  frontend can run natively for fast iteration (`mvn spring-boot:run`,
  `npm run dev`). Still the right choice for day-to-day development.
- `docker-compose.yml` (Phase 15) — the full containerized stack, one
  command:

  ```bash
  docker compose up --build
  ```

  Brings up `mysql` → `redis` → `backend` (waits on both being
  *healthy*, not just started) → `frontend` (waits on the backend being
  healthy) in dependency order automatically, via each service's
  `healthcheck` + `depends_on: condition: service_healthy`. Once it's up:

  - Frontend: http://localhost:5173
  - Backend: http://localhost:8080 (Swagger at `/swagger-ui.html`)

  Both images are multi-stage builds
  ([`saferide-backend/Dockerfile`](saferide-backend/Dockerfile),
  [`saferide-frontend/Dockerfile`](saferide-frontend/Dockerfile)) - the
  final backend image has a JRE and the built jar, never Maven or the
  source tree; the final frontend image is nginx serving the static Vite
  build (with SPA fallback routing - see
  [`nginx.conf`](saferide-frontend/nginx.conf)), never Node. Both run as
  non-root. `VITE_API_BASE_URL`/`VITE_WS_BASE_URL` are baked into the
  frontend bundle at *build* time (Vite's own constraint - `import.meta.env`
  values don't exist at container runtime), so they default to the
  browser-reachable address the backend's port is published at
  (`localhost:8080`), not the internal `backend:8080` service name only
  other containers can resolve.

  **Kafka is genuinely optional here too** (same "optional/additive"
  design as everywhere else - see
  [Domain events, notifications, and analytics](#domain-events-notifications-and-analytics)):
  a separate opt-in Compose profile, a real single-node KRaft broker (no
  Zookeeper needed), left out of the default `docker compose up` so the
  common path stays fast and lean.

  ```bash
  docker compose --profile kafka up --build
  ```

  `KAFKA_FORWARDING_ENABLED=true` still needs setting explicitly (`.env`
  or the shell environment) even with the profile running - verified for
  real: with the profile up and forwarding flipped on, booking a ride
  through the containerized backend and reading
  `saferide.domain-events` back with the broker's own console consumer
  shows the exact `RideRequestedEvent` payload.

## Testing

```bash
cd saferide-backend
mvn test
```

**Docker must be running** - every `@SpringBootTest` class extends
[`AbstractIntegrationTest`](saferide-backend/src/test/java/com/saferide/testsupport/AbstractIntegrationTest.java)
(Phase 14), which boots a real MySQL 8.4 and a real Redis 7.4 via
Testcontainers rather than the in-memory H2/SQL-fallback stand-ins the
suite used through Phase 13. One MySQL container and one Redis container
for the *entire* test JVM (the "singleton containers" pattern - started
once in a static initializer, never explicitly stopped; Testcontainers'
own Ryuk reaper removes them when the JVM exits), not one pair per test
class, which would make a ~20-class suite unusably slow.
`app.matching.geo-index-source`/`app.security.login-rate-limit.source`
are switched to `redis` for these tests too - the whole suite now
exercises `RedisDriverGeoIndex`/`RedisLoginRateLimiter` themselves through
real HTTP requests, not their SQL/in-memory fallbacks (which keep their
own dedicated, fast, no-Spring-context unit tests - see
`SqlDriverGeoIndexTest`/`InMemoryLoginRateLimiterTest` below - that
responsibility didn't move). Pure unit tests (Mockito, no Spring context -
`FareCalculatorTest`, `RideStateMachineTest`, `DriverMatchScorerTest`,
`ReliabilityCalculatorTest`, `VehicleServiceTest`, `AuthServiceTest`,
`AdminAnalyticsServiceTest`, `JwtServiceTest`, and the Redis/SQL
command-shape tests below) need no container at all and run exactly as
fast as before.

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
- [`DriverMatchScorerTest`](saferide-backend/src/test/java/com/saferide/matching/DriverMatchScorerTest.java) — reproduces the spec's own example verbatim (a driver further away but better-rated with a lower cancellation rate outranks the nearest one), plus isolated checks that distance, rating, and cancellation rate each move the score in the right direction, and that out-of-radius distance never makes the score negative.
- [`ReliabilityCalculatorTest`](saferide-backend/src/test/java/com/saferide/driver/ReliabilityCalculatorTest.java) — a perfect driver scores exactly 100, the worst possible driver scores exactly 0 (never negative), cancellation rate reduces the score proportionally to its configured weight, and the complaint penalty is capped rather than unbounded.
- [`MatchingIntegrationTest`](saferide-backend/src/test/java/com/saferide/matching/MatchingIntegrationTest.java) — the full Phase 6 pipeline through real HTTP requests: confirming a fare proactively assigns the best of several otherwise-identical drivers; a driver not assigned can't accept; the assigned driver declines and is permanently excluded while matching immediately reassigns to the next-best driver; the decliner can never claw the ride back through *either* `/api/rides/{id}/accept` or the `/api/rides/open` fallback; declining/cancelling measurably move `acceptanceRate`/`cancellationRate`; a post-acceptance cancellation auto-reassigns to a third driver instead of dead-ending; and a ride with zero eligible drivers waits at `SEARCHING_DRIVER` until one comes online and self-claims it.
- [`RideWebSocketIntegrationTest`](saferide-backend/src/test/java/com/saferide/websocket/RideWebSocketIntegrationTest.java) — a real WebSocket connection end to end (`StandardWebSocketClient` + `WebSocketStompClient` against a random-port `@SpringBootTest`, not MockMvc, which can't exercise an upgraded connection): connecting without a valid token is rejected; a customer subscribed to their own ride's status topic receives a live event the instant a real REST call (the driver accepting) transitions it; a stranger's subscription to someone else's ride topic is rejected outright; a driver receives a private `/user/queue/assignments` notification the moment matching proactively assigns them a ride; and a `PUT /api/drivers/location` call broadcasts to the ride's own location topic with the exact coordinates sent.
- [`SqlDriverGeoIndexTest`](saferide-backend/src/test/java/com/saferide/matching/SqlDriverGeoIndexTest.java) — the SQL fallback returns only within-radius online drivers, nearest first, skips anyone with no reported location, and its `indexLocation`/`removeDriver` are confirmed no-ops (MySQL already is the index).
- [`RedisDriverGeoIndexTest`](saferide-backend/src/test/java/com/saferide/matching/RedisDriverGeoIndexTest.java) — verifies the actual Redis command shape against a mocked `GeoOperations` (`GEOADD` with the right `(longitude, latitude)` point, `GEOSEARCH` results mapped back to driver ids, `ZREM` on removal), plus that a simulated Redis failure on any of the three methods degrades (empty results / silent no-op) rather than throwing.
- [`InMemoryLoginRateLimiterTest`](saferide-backend/src/test/java/com/saferide/auth/InMemoryLoginRateLimiterTest.java) — a real behavioral test (not a mock) of the enforcement logic itself: allowed up to the limit, blocked at it, a success clears the counter entirely, and identifiers are tracked independently.
- [`RedisLoginRateLimiterTest`](saferide-backend/src/test/java/com/saferide/auth/RedisLoginRateLimiterTest.java) — verifies the Redis command shape (`INCR`, `EXPIRE` set only on the first increment of a window, `DELETE` on success) and that a simulated Redis failure fails open on every method.
- `AuthServiceTest`/`AuthIntegrationTest` gained rate-limiter coverage: a blocked account's failure isn't counted, the limiter is checked before the database is even touched, and (integration) ten real failed logins against one identifier are followed by an eleventh - correct password included - coming back `429 TOO_MANY_ATTEMPTS`, while a different identifier stays unaffected.
- [`PostRideIntegrationTest`](saferide-backend/src/test/java/com/saferide/payment/PostRideIntegrationTest.java) — everything Phase 12 adds, driven through a real ride booked all the way to `PAYMENT_PENDING`: paying charges exactly the locked fare and a repeat attempt is rejected; rating moves the driver's `rating` measurably (EMA, not overwrite) and `RATED` being terminal blocks a second attempt; a stranger can't file a complaint but the ride's customer can, which measurably increments the driver's `complaintCount` and lowers `reliabilityScore`, shows up in the customer's own complaint history, and an admin can review/resolve it (twice → `409 INVALID_COMPLAINT_STATE`); an SOS alert is visible to admins and resolvable once (twice → `409 INVALID_SAFETY_ALERT_STATE`); and a trip-share link is readable with *no* `Authorization` header at all, rejects a bogus token, and returns the same still-valid token on a repeat request rather than minting a new one.
- [`NotificationIntegrationTest`](saferide-backend/src/test/java/com/saferide/notification/NotificationIntegrationTest.java) — a full ride lifecycle (book → confirm fare, which proactively assigns a driver → accept → arrive → start → complete → pay → file a complaint → admin resolves it) driven through real HTTP requests, asserting the right `Notification` shows up for the right user (customer, driver, or both) after every event; plus unread-count, mark-one-read, mark-all-read, and that a stranger gets `403` trying to mark someone else's notification read. None of this needs Kafka - `NotificationEventListener` runs off Spring's own `ApplicationEventPublisher` regardless of whether forwarding is enabled.
- [`KafkaEventForwarderTest`](saferide-backend/src/test/java/com/saferide/event/KafkaEventForwarderTest.java) — the one test in the suite needing a real broker, via `@EmbeddedKafka` (a genuine in-JVM Kafka, no Docker required for the broker itself): a domain event published inside a real transaction that commits lands on `saferide.domain-events`, keyed by event type, with the exact payload; the same event published with no transaction at all is never forwarded, per `@TransactionalEventListener`'s own documented `AFTER_COMMIT` contract. Gets its own small, dedicated MySQL Testcontainer (`@Testcontainers`/`@Container`, per-class lifecycle) rather than the shared singleton every other integration test uses - this class already needs its own one-off Spring context (different Kafka properties, `@DirtiesContext`), and with `ddl-auto: create-drop`, `@DirtiesContext` closing a context sharing the common container would have dropped its schema out from under every other test class still relying on it.
- [`AdminAnalyticsServiceTest`](saferide-backend/src/test/java/com/saferide/admin/AdminAnalyticsServiceTest.java) — every field of `AnalyticsSummaryResponse` traces to exactly one (fully mocked) repository call, `activeRides` is confirmed to be the `total - completed - cancelled` remainder and never negative even when every ride is already terminal, and `openComplaints` is confirmed to be `OPEN + UNDER_REVIEW` summed rather than a single query.
- [`AdminAnalyticsIntegrationTest`](saferide-backend/src/test/java/com/saferide/admin/AdminAnalyticsIntegrationTest.java) — a real ride booked, completed, and paid through actual HTTP requests moves `totalRides`/`completedRides`/`totalUsers`/`totalDrivers`/`verifiedDrivers`/`onlineDriversNow`/`totalRevenue` by exactly what this test itself contributed (asserted as a before/after delta, since the shared MySQL Testcontainers instance also holds every other test class's data); a non-admin gets `403`.

134/134 backend tests passing (`mvn test`).

**Frontend** (Phase 14): component/unit tests via **Vitest + React Testing
Library** - `cd saferide-frontend && npm test`. Full browser E2E
(Playwright/Cypress, driven against a real running backend) is deliberately
**not** part of this - disproportionate scope for what this phase needed,
and better suited to sitting alongside [CI/CD](#cicd) later, where a real
backend+database is already being stood up for the deploy pipeline anyway.
What's here instead:

- [`utils.test.ts`](saferide-frontend/src/lib/utils.test.ts) — `cn` joins plain classes, drops falsy conditionals, and resolves conflicting Tailwind utilities to the last one rather than shipping both.
- [`Badge.test.tsx`](saferide-frontend/src/components/common/Badge.test.tsx) — renders its children, defaults to the neutral variant, applies the requested variant's classes, and merges a caller-supplied `className` rather than dropping it.
- [`RatingStars.test.tsx`](saferide-frontend/src/components/ride/RatingStars.test.tsx) — one labelled button per star; clicking a star calls `onChange` with exactly that value; stars fill up to and including the current value; hovering previews a value without committing it and reverts cleanly on mouse-leave.
- [`NotificationBell.test.tsx`](saferide-frontend/src/components/common/NotificationBell.test.tsx) — `notificationService` mocked, rendered inside a real `QueryClientProvider`: no badge when unread count is 0, the count shown when it isn't, capped at `9+`; the notification list isn't fetched until the bell is actually opened; fetched notifications render; clicking an unread one calls `markAsRead` and refreshes the count; "Mark all read" calls `markAllAsRead`; clicking outside the open dropdown closes it.

Every frontend phase is also verified by `cd saferide-frontend && npm run
build` (`tsc -b` strict type-checking, including
`noUnusedLocals`/`noUnusedParameters`, then the production Vite bundle) and
`npm run lint` (zero warnings tolerated) - both clean as of Phase 14 - plus
a manual pass of the real flow against the backend described above.

## CI/CD

Two workflows (Phase 16), split by how often each should run:

- [`.github/workflows/ci.yml`](.github/workflows/ci.yml) — every push/PR
  against `main`. `backend`: JDK 21 (Temurin, Maven-cached), `mvn test` -
  `ubuntu-latest` ships Docker pre-installed and running, which is all
  [`AbstractIntegrationTest`](saferide-backend/src/test/java/com/saferide/testsupport/AbstractIntegrationTest.java)
  (Phase 14) needs for its real MySQL/Redis Testcontainers; surefire
  reports upload as an artifact on failure. `frontend`: Node 20
  (npm-cached), `npm ci` → `npm run lint` → `npm test` → `npm run build`.
  Runs are cancelled if superseded by a newer push to the same
  branch/PR.
- [`.github/workflows/cd.yml`](.github/workflows/cd.yml) — pushes to
  `main` (plus manual `workflow_dispatch`). Repeats the same test/lint
  steps first (CD can't assume CI already ran for this exact commit - a
  `workflow_dispatch` run has no associated CI run at all), then builds
  and pushes both Docker images to **GHCR** (`ghcr.io/<owner>/saferide-backend`,
  `ghcr.io/<owner>/saferide-frontend`, tagged `latest` and the commit SHA)
  using the workflow's own automatic `GITHUB_TOKEN` - zero secrets to
  configure by hand for a fork to get this far. Stops there deliberately:
  actually deploying that image anywhere is Phase 17's job, not this
  one.

Both workflows target `main` as the default branch, which this
repository's own current branch (an app-modernization tooling artifact,
not something created for this project) isn't - rename to `main` (the
normal setup for a real fork) or adjust the `branches:` triggers to match
before pushing to GitHub.

**Verification note:** this session has no GitHub remote or `gh` CLI
available, so neither workflow has run for real on GitHub's own runners.
What *was* verified locally: both files pass
[`actionlint`](https://github.com/rhysd/actionlint) (syntax, expression,
and action-input validation, shellcheck-backed) with zero findings, and
every command each job invokes (`mvn -B test`, `npm ci`, `npm run lint`,
`npm test`, `npm run build`) was run directly and confirmed to exit `0`.
The one thing that couldn't be exercised outside GitHub's own runners is
the workflow orchestration itself (triggers, job dependency ordering, the
GHCR push).

## Deployment

Local Docker first, free-tier MVP hosting next, AWS later — never required
for local development. Target AWS shape (Phase 17+): Route 53 → CloudFront →
ALB → Spring Boot containers → RDS MySQL + ElastiCache Redis, S3 for static
assets, CloudWatch for logs/metrics, MSK (or a managed Kafka) as the
production home for the domain-events topic `KafkaEventForwarder` already
publishes to.

## Roadmap

| Phase | Scope | Status |
|---|---|---|
| 1 | Project setup — Spring Boot, React, MySQL, Git, basic structure | ✅ Done |
| 2 | User/Customer/Driver/Admin, JWT auth, Spring Security | ✅ Done |
| 3 | Vehicle, driver verification, driver status | ✅ Done |
| 4 | Ride booking, ride state machine, ride APIs | ✅ Done |
| 5 | Fare engine, fare breakdown, **Fare Lock**, destination changes | ✅ Done |
| 6 | Driver matching, reliability score, automatic reassignment | ✅ Done |
| 7 | Customer frontend (login → tracking) | ✅ Done |
| 8 | Driver frontend (dashboard → earnings) | ✅ Done |
| 9 | Admin frontend (driver verification console) | ✅ Done |
| 10 | WebSocket/STOMP — real-time status & location | ✅ Done |
| 11 | Redis — driver geo-matching, login rate limiting | ✅ Done |
| 12 | Payment, rating, complaints, safety | ✅ Done |
| 13 | Kafka events, notifications, analytics | ✅ Done |
| 14 | Testing — JUnit, Mockito, Testcontainers | ✅ Done |
| 15 | Docker / Docker Compose (full stack) | ✅ Done |
| 16 | GitHub Actions CI/CD | ✅ Done |
| 17 | Deployment (free-tier MVP → AWS) | ⏳ Next |
| 18 | Production optimization | Planned |

---

### Seed / demo credentials

Seeded automatically on startup under the `dev` profile only (never `test`
or `prod`, added incrementally from Phase 2 through Phase 6) by
[`DevDataSeeder`](saferide-backend/src/main/java/com/saferide/config/DevDataSeeder.java) —
log in with these immediately, no registration needed:

| Email | Password | Role | Notes |
|---|---|---|---|
| `customer@example.com` | `Password123!` | CUSTOMER | |
| `driver@example.com` | `Password123!` | DRIVER | Pre-verified, `ONLINE`, with a pre-verified active vehicle (2020 Maruti Suzuki Dzire) and a reported location (Guwahati Railway Station) - a real matching candidate immediately, no setup needed |
| `admin@example.com` | `Password123!` | ADMIN | Use this to review other drivers via `GET/PUT /api/admin/drivers/**` |

These are intentionally public, dev-only credentials — never reachable in
`prod` (`ProdSafetyChecks` also refuses to boot the `prod` profile if
`JWT_SECRET` was left at its dev default).
