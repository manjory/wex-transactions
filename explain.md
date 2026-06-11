# WEX Purchase Transaction Service - Technical Design

## Overview

This service is a Spring Boot REST API with two endpoints: one to store a purchase transaction
and one to retrieve it with the purchase amount converted to a target currency. Currency
conversion uses real-time exchange rates from the U.S. Treasury Reporting Rates of Exchange API.

---

## Architecture

```
Client
  |
  v
PurchaseTransactionController   (REST layer - validation, routing)
  |
  v
PurchaseTransactionService      (business logic - rounding, conversion math)
  |              |
  v              v
Repository    TreasuryExchangeRateService
(H2/JPA)      (HTTP client to Treasury API)
```

The layers are intentionally thin. The controller only handles HTTP concerns. The service owns
the business rules (rounding, the 6-month lookup window, conversion math). The repository is a
standard Spring Data JPA interface. The Treasury service is isolated so it can be mocked in unit
tests and replaced with a different rate source if needed.

---

## API Design

### POST /api/v1/transactions

Stores a new purchase transaction.

Request body:
```json
{
  "description": "Office Supplies",
  "transactionDate": "2024-03-15",
  "purchaseAmount": 99.99
}
```

Response `201 Created`:
```json
{
  "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "description": "Office Supplies",
  "transactionDate": "2024-03-15",
  "purchaseAmount": 99.99
}
```

Validation errors return `400 Bad Request` with a field-level message map.

### GET /api/v1/transactions/{id}?currency={country_currency_desc}

Retrieves a stored transaction converted to the target currency.

The `currency` parameter must be a `country_currency_desc` value from the Treasury API, for
example `Canada-Dollar`, `Euro Zone-Euro`, or `Japan-Yen`.

Response `200 OK`:
```json
{
  "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "description": "Office Supplies",
  "transactionDate": "2024-03-15",
  "purchaseAmount": 99.99,
  "exchangeRate": 1.3500,
  "convertedAmount": 134.99,
  "currency": "Dollar",
  "country": "Canada"
}
```

Error responses:
- `404 Not Found` - transaction ID does not exist
- `422 Unprocessable Entity` - no exchange rate available within 6 months of the purchase date

### GET /api/v1/currencies?date=2024-03-15

Helper endpoint. Returns available `country_currency_desc` values from the Treasury API for the
given date (defaults to today). Use this to discover valid values for the `currency` parameter.

---

## Data Model

```
purchase_transactions
  id               UUID        primary key, generated
  description      VARCHAR(50) not null
  transaction_date DATE        not null
  purchase_amount  DECIMAL(19,2) not null
```

The schema is managed by `spring.jpa.hibernate.ddl-auto=update`. In production, replace this
with a migration tool such as Flyway or Liquibase to have versioned, auditable schema changes.

---

## Exchange Rate Lookup

The service queries the Treasury API with three constraints on each request:

```
country_currency_desc = <requested currency>
effective_date <= <transaction date>
effective_date >= <transaction date minus 6 months>
```

Results are sorted by `effective_date` descending and capped at one record. This means a
single HTTP call retrieves the most recent qualifying rate. No local caching is used; each
retrieval hits the API. Adding a short-lived cache (for example, by transaction date and
currency) would reduce latency and API usage if throughput becomes a concern.

---

## SSL and HTTP Client Configuration

The HTTP client uses the JVM default trust store when no custom truststore is configured.
This is sufficient for the Treasury API, which has a certificate from a standard public CA.

To use a self-signed certificate or a private CA (for example, when the API is accessed
through a corporate proxy):

1. Export the certificate to a JKS or PKCS12 file.
2. Set the following properties:

```properties
http.client.ssl.trust-store=ssl/my-ca.jks
http.client.ssl.trust-store-password=changeit
http.client.ssl.trust-store-type=JKS
```

The path is resolved from the classpath first, then from the filesystem. Hostname verification
is always on. The connect and read timeouts are also configurable:

```properties
http.client.connect-timeout-ms=3000
http.client.read-timeout-ms=5000
```

---

## Technology Stack

| Concern | Choice | Why |
|---|---|---|
| Framework | Spring Boot 3.2 | Auto-configuration, JPA, validation, test slices |
| Language | Java 21 | Required by spec; virtual threads available if needed |
| Database | H2 (file mode) | Zero-setup for local dev; swap to Postgres via config only |
| HTTP client | Apache HttpClient 5 | Configurable timeouts, pluggable SSL, Spring integration |
| Build | Maven + wrapper | Reproducible builds; standard in Java ecosystem |
| Test mocking | WireMock | Stubs Treasury API at HTTP level without network calls |
| Unit testing | JUnit 5 + Mockito | Standard; integrates with Spring test slices |
| Coverage | Jacoco | Reports generated on `./mvnw verify` |

---

## How to Run

### Option 1: Maven (local development)

Requires Java 21+ on PATH. If Maven is installed:

```bash
# Run all tests
./mvnw test

# Start the application (dev profile enables H2 console)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

If Maven is not installed, generate a real wrapper first:

```bash
mvn -N wrapper:wrapper
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

The API will be available at `http://localhost:8080`.
H2 console (dev profile only): `http://localhost:8080/h2-console`

### Option 2: Packaged JAR

```bash
./mvnw package -DskipTests
java -jar target/transactions-1.0.0.jar
```

To activate the dev profile with the JAR:

```bash
java -jar target/transactions-1.0.0.jar --spring.profiles.active=dev
```

### Option 3: Docker (single container)

Build and run using Docker. Create a `Dockerfile` in the project root:

```dockerfile
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/transactions-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Then build the JAR and run the image:

```bash
./mvnw package -DskipTests
docker build -t wex-transactions:latest .
docker run -p 8080:8080 wex-transactions:latest
```

To persist the H2 database across container restarts, mount a volume:

```bash
docker run -p 8080:8080 -v $(pwd)/data:/app/data wex-transactions:latest
```

### Option 4: Docker Compose (with PostgreSQL for production-like setup)

Create a `docker-compose.yml`:

```yaml
services:
  app:
    build: .
    ports:
      - "8080:8080"
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/wex_transactions
      SPRING_DATASOURCE_USERNAME: wex
      SPRING_DATASOURCE_PASSWORD: wex
      SPRING_DATASOURCE_DRIVER_CLASS_NAME: org.postgresql.Driver
      SPRING_JPA_DATABASE_PLATFORM: org.hibernate.dialect.PostgreSQLDialect
      SPRING_JPA_HIBERNATE_DDL_AUTO: update
    depends_on:
      db:
        condition: service_healthy

  db:
    image: postgres:16
    environment:
      POSTGRES_DB: wex_transactions
      POSTGRES_USER: wex
      POSTGRES_PASSWORD: wex
    ports:
      - "5432:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U wex"]
      interval: 5s
      timeout: 5s
      retries: 5
```

Add the PostgreSQL driver to `pom.xml`, then:

```bash
./mvnw package -DskipTests
docker compose up --build
```

To switch to PostgreSQL locally without Docker, update `application.properties` with the four
datasource properties above and add the `postgresql` driver dependency.

---

## Running Tests

```bash
# Unit + integration tests
./mvnw test

# Tests with coverage report
./mvnw verify
# Report: target/site/jacoco/index.html
```

Integration tests use WireMock to stub the Treasury API. No network access is required.

### Test Coverage by Layer

| Layer | Test class | Approach |
|---|---|---|
| Service logic | `PurchaseTransactionServiceTest` | Unit, Mockito |
| Treasury client | `TreasuryExchangeRateServiceTest` | Unit, Mockito |
| Controller + validation | `PurchaseTransactionControllerTest` | Slice, `@WebMvcTest` + MockMvc |
| Full API flow | `PurchaseTransactionIntegrationTest` | `@SpringBootTest` + WireMock |

---

## Configuration Reference

| Property | Default | Description |
|---|---|---|
| `treasury.api.base-url` | `https://api.fiscaldata.treasury.gov` | Treasury API base URL |
| `treasury.api.exchange-rates-path` | `/services/api/fiscal_service/v1/...` | Exchange rates endpoint path |
| `http.client.connect-timeout-ms` | `3000` | HTTP connect timeout in milliseconds |
| `http.client.read-timeout-ms` | `5000` | HTTP read timeout in milliseconds |
| `http.client.ssl.trust-store` | *(blank)* | Path to custom truststore; blank uses JVM default |
| `http.client.ssl.trust-store-password` | *(blank)* | Password for the truststore file |
| `http.client.ssl.trust-store-type` | `JKS` | Truststore format: `JKS` or `PKCS12` |
| `server.port` | `8080` | HTTP port the service listens on |

H2 console is only available when the `dev` profile is active
(`--spring.profiles.active=dev`).

---

## Project Structure

```
src/
  main/java/com/wex/transactions/
    TransactionApplication.java
    config/       AppConfig.java           (RestTemplate bean with pluggable SSL)
    controller/   PurchaseTransactionController.java
    dto/          Request/response DTOs and Treasury API response model
    exception/    Custom exceptions and GlobalExceptionHandler
    model/        PurchaseTransaction JPA entity
    repository/   PurchaseTransactionRepository (Spring Data JPA)
    service/      PurchaseTransactionService, TreasuryExchangeRateService
  main/resources/
    application.properties
    application-dev.properties             (H2 console, dev-only settings)
  test/java/com/wex/transactions/
    controller/   PurchaseTransactionControllerTest
    integration/  PurchaseTransactionIntegrationTest
    service/      PurchaseTransactionServiceTest, TreasuryExchangeRateServiceTest
  test/resources/
    application-test.properties
```
