# WEX Purchase Transaction Service

A Spring Boot REST API that stores purchase transactions in US dollars and retrieves them
converted to any currency supported by the U.S. Treasury Reporting Rates of Exchange API.

---

## The Problem

Two things need to work:

1. Accept a purchase transaction (description, date, amount) and store it with a unique ID.
2. Given that ID and a target currency, return the transaction with the amount converted
   using the Treasury exchange rate that was active on or before the purchase date.

The Treasury API publishes exchange rates quarterly. The conversion must use the most recent
rate that falls within 6 months before the purchase date. If no rate exists in that window,
the API returns a clear error.

---

## Design Choices

**Spring Boot + Java 21** - straightforward choice for a production REST service. Validation,
JPA, and the test framework are all built in.

**H2 file-based database** - runs with zero setup. Data persists across restarts in `./data/`.
Switching to PostgreSQL requires changing four lines in `application.properties` and adding
the driver dependency. No code changes needed.

**UUID for transaction IDs** - avoids sequential IDs that leak record counts and are easy to
enumerate. The database generates the UUID on insert.

**Single Treasury API call per lookup** - the query pushes the date range filter, descending
sort, and a page size of 1 directly to the Treasury API. One HTTP call returns exactly the
right rate with no in-memory filtering.

**Caffeine in-process cache** - since Treasury rates only change quarterly, the same
currency + date pair will always return the same result. Responses are cached for 6 hours,
which means a freshly published quarterly rate shows up within half a business day.

**Apache HttpClient5** - gives configurable connect and read timeouts. The SSL truststore
is pluggable via config so teams behind a corporate proxy can drop in their CA certificate
without touching code.

**WireMock in integration tests** - stubs the Treasury API at the HTTP level. Tests run
fully offline with no flakiness from network or rate limits.

---

## Requirements

- Java 21+
- Maven 3.8+ (or use `./mvnw` which delegates to your local Maven install)

---

## Running Tests

```bash
./mvnw clean test
```

All 28 tests should pass. The integration tests use WireMock so no internet connection
is needed.

To generate a coverage report:

```bash
./mvnw verify
open target/site/jacoco/index.html
```

---

## Starting the Application

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

The API starts at `http://localhost:8080`.

The `dev` profile enables the H2 console at `http://localhost:8080/h2-console`
(JDBC URL: `jdbc:h2:file:./data/transactions`, username: `sa`, password: empty).

---

## API

### Store a transaction

```
POST /api/v1/transactions
```

```json
{
  "description": "Office Supplies",
  "transactionDate": "2024-03-15",
  "purchaseAmount": 99.99
}
```

Returns `201 Created` with the stored transaction including its generated UUID.

### Retrieve with currency conversion

```
GET /api/v1/transactions/{id}?currency=Canada-Dollar
```

The `currency` parameter must match a `country_currency_desc` value from the Treasury API
(for example `Canada-Dollar`, `Euro Zone-Euro`, `Japan-Yen`).

Returns the transaction with `exchangeRate` and `convertedAmount` added.

Returns `404` if the transaction does not exist, `422` if no exchange rate is available
within 6 months of the purchase date.

### Discover available currencies

```
GET /api/v1/currencies?date=2024-03-15
```

Returns all available `country_currency_desc` values for the given date. Useful for finding
valid values to pass to the conversion endpoint.

---

## Future Work

- **Schema migrations** - replace `ddl-auto=update` with Flyway or Liquibase for versioned,
  auditable schema changes in production.
- **Transaction list endpoint** - a paginated `GET /api/v1/transactions` was not in scope
  but would be the obvious next addition.
- **Distributed cache** - Caffeine is in-process. A multi-instance deployment would benefit
  from Redis so all nodes share the same cached rates.
- **Docker image** - a `Dockerfile` and `docker-compose.yml` with PostgreSQL would make
  the production-like setup one command.
