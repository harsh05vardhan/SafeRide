# SafeRide

A ride-booking platform built to solve one concrete trust problem: **a driver
cannot demand an unauthorized extra fare after the customer has confirmed the
ride.** Fare calculation runs entirely on the backend, the confirmed fare is
locked before matching begins, and any attempt to extract more money from the
rider becomes a disputable, audited event instead of a silent surcharge.

> **Status:** Phase 11 of 18 complete (Redis — driver geo-matching, login
> rate limiting). See [Roadmap](#roadmap) below for what's implemented vs.
> planned.

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
Data Redis (Phase 11), Spring Actuator, Maven, Lombok, springdoc-openapi/
Swagger, JUnit 5, Mockito, Testcontainers (Phase 14).

**Database:** MySQL 8 (source of truth) + Redis 7 (hot/ephemeral data —
driver geo-index for matching, login rate limiting; see
[Redis](#redis-driver-geo-matching-rate-limiting) below).

**Messaging:** Apache Kafka (Phase 13, optional/additive — domain events like
`RideRequested`, `DriverAssigned`, `PaymentCompleted`).

**Frontend:** React + TypeScript + Vite, Tailwind CSS, shadcn/ui (Radix +
CVA), React Router, Axios, TanStack Query, React Hook Form + Zod, Lucide
icons, Framer Motion, React Leaflet + OpenStreetMap, `@stomp/stompjs`
(Phase 10 — plain STOMP over native `WebSocket`, no SockJS).

**DevOps:** Docker, Docker Compose, GitHub Actions (CI + CD), container
registry.

## Folder structure

```
safeRide/
├── saferide-backend/
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
│       ├── payment/         (Phase 12)
│       ├── rating/          (Phase 12)
│       ├── safety/          (Phase 12)
│       ├── notification/    (Phase 12)
│       ├── complaint/       (Phase 12)
│       ├── admin/           Driver/vehicle/document verification review (frontend console: Phase 9)
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
├── saferide-frontend/
│   └── src/
│       ├── assets/
│       ├── components/
│       │   ├── common/       Button, Input, Card, Badge, Spinner, Skeleton,
│       │   │                 ConfirmDialog, StateViews, ProtectedRoute (role-gated)
│       │   ├── layout/       Navbar/MobileBottomNav/CustomerLayout,
│       │   │                 DriverNavbar/DriverMobileBottomNav/DriverLayout (Phase 8),
│       │   │                 AdminNavbar/AdminLayout (Phase 9, no mobile bottom nav)
│       │   ├── map/          MapView, Pickup/Destination/DriverMarker, RouteLine
│       │   ├── ride/         LocationPicker
│       │   ├── driver/       (Phase 8) StatCard, IncomingRequestCard
│       │   ├── admin/        (Phase 9) StatusBadges, ReasonConfirmDialog
│       │   └── safety/ (Phase 12)
│       ├── pages/
│       │   ├── auth/         LoginPage, RegisterPage (role picker built in)
│       │   ├── RoleLandingPage.tsx  "/" - routes single-role accounts straight
│       │   │                 through, shows a chooser for multi-role accounts
│       │   ├── customer/     HomePage, RideOptionsPage, FareBreakdownPage,
│       │   │                 FindingDriverPage, RideTrackingPage, MyRidesPage, ProfilePage
│       │   ├── driver/       (Phase 8) DriverDashboardPage, DriverRidesPage,
│       │   │                 DriverRideDetailPage, DriverEarningsPage, DriverProfilePage
│       │   └── admin/        (Phase 9) AdminDashboardPage, AdminDriversPage,
│       │                     AdminDriverDetailPage
│       ├── services/         api.ts (Axios + JWT/refresh interceptors), authService,
│       │                     rideService (customer + driver actions), fareService,
│       │                     customerService, driverService, vehicleService (Phase 8),
│       │                     adminService (Phase 9)
│       ├── hooks/            useDriverProfile, useCurrentDriverRide (Phase 8),
│       │                     useRideChannel (Phase 10 - useRideStatusChannel/
│       │                     useRideLocationChannel/useDriverAssignmentChannel)
│       ├── contexts/         AuthContext (isCustomer/isDriver/isAdmin), BookingContext, ToastContext,
│       │                     WebSocketContext (Phase 10 - one STOMP connection per session)
│       ├── types/            auth.ts, ride.ts, fare.ts, common.ts, driver.ts, vehicle.ts,
│       │                     admin.ts, ws.ts (Phase 10) - mirror the backend DTOs exactly
│       ├── lib/               utils.ts (`cn`), tokenStorage.ts, validation.ts (Zod),
│       │                     suggestedLocations.ts
│       ├── routes/           AppRoutes.tsx (every page lazy-loaded)
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
   stay at their seeded defaults until ratings (Phase 12) and complaints
   (Phase 12) actually exist to feed them.
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
- **Deferred, not faked.** Payment, rating, the Safety Center, and Support
  have no backend yet (Phase 12) - rather than build screens that call
  nothing, the post-ride summary says so plainly, and SOS/Share/Call/Chat
  are visibly present (matching the design brief) but explicitly
  "coming soon" when tapped, never a silent no-op.
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

## Admin frontend

The backend's admin surface today is exactly `/api/admin/drivers/**` -
driver, vehicle and document verification review (`AdminDriverController`).
There's no `/api/admin/users`, `/api/admin/rides`, `/api/admin/payments`,
`/api/admin/complaints` or `/api/admin/reports` endpoint yet - those land
with the phases that give admins something real to review (ride monitoring
needs Phase 10's live status, payments/complaints need Phase 12). So Phase 9
builds the **driver verification console** end to end rather than a wider
dashboard with panels that call nothing:

**Login → Role Landing → Dashboard (counts + pending queue) → Drivers (filter
+ paginate) → Driver Detail (approve/block driver, approve/reject each
vehicle and document).**

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
  `app.matching.geo-index-source=sql` (the test profile's default, since
  Testcontainers-backed Redis in tests arrives Phase 14) and a legitimate
  small-scale, no-Redis deployment choice otherwise - not a test stub, a
  complete second implementation, exactly `MockMapService`'s relationship
  to a real map provider.

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

### Live endpoints (Phase 1–6)

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
none of them require Docker/MySQL/Redis to be running - the test profile
switches `DriverGeoIndex`/`LoginRateLimiter` to their SQL/in-memory
implementations (see [Redis](#redis-driver-geo-matching-rate-limiting))
rather than needing a real Redis:

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

Testcontainers (real MySQL in tests, replacing today's H2 profile for the
integration suite) arrives in Phase 14.

**Frontend:** automated component/E2E tests arrive in Phase 14 alongside
the backend's Testcontainers work. Until then, every frontend phase is
verified by `cd saferide-frontend && npm run build` (`tsc -b` strict
type-checking, including `noUnusedLocals`/`noUnusedParameters`, then the
production Vite bundle) and `npm run lint` (zero warnings tolerated) -
both clean as of Phase 10 - plus a manual pass of the real flow against
the backend described above.

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
| 6 | Driver matching, reliability score, automatic reassignment | ✅ Done |
| 7 | Customer frontend (login → tracking) | ✅ Done |
| 8 | Driver frontend (dashboard → earnings) | ✅ Done |
| 9 | Admin frontend (driver verification console) | ✅ Done |
| 10 | WebSocket/STOMP — real-time status & location | ✅ Done |
| 11 | Redis — driver geo-matching, login rate limiting | ✅ Done |
| 12 | Payment, rating, complaints, safety | ⏳ Next |
| 13 | Kafka events, notifications, analytics | Planned |
| 14 | Testing — JUnit, Mockito, Testcontainers | Planned |
| 15 | Docker / Docker Compose (full stack) | Planned |
| 16 | GitHub Actions CI/CD | Planned |
| 17 | Deployment (free-tier MVP → AWS) | Planned |
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
