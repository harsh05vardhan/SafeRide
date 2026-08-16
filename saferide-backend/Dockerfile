# Multi-stage build (Phase 15) - the final image never contains Maven, the JDK's
# javac, or the source tree, only a JRE and the built jar.

# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Dependencies resolved and cached in their own layer before any source is copied in,
# so an ordinary code change doesn't force re-downloading the entire dependency tree.
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
# Tests already run in CI (mvn test, against real Testcontainers-managed MySQL/Redis - see
# the README's Testing section) - running them again here would need Docker-in-Docker just to
# build an image, which is disproportionate for what this stage is for.
RUN mvn -B package -DskipTests

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Never run the app as root inside the container.
RUN addgroup -S saferide && adduser -S saferide -G saferide
COPY --from=build /build/target/saferide-backend.jar app.jar
RUN chown saferide:saferide app.jar
USER saferide

EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=5s --start-period=45s --retries=10 \
    CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
