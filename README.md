# WEX Purchase Transaction Service

A Spring Boot REST API for storing purchase transactions and retrieving them with real-time currency conversion using the [U.S. Treasury Reporting Rates of Exchange API](https://fiscaldata.treasury.gov/datasets/treasury-reporting-rates-exchange/treasury-reporting-rates-of-exchange).

## Prerequisites

- Java 21+
- Maven 3.8+ (or use the included Maven Wrapper: `./mvnw`)

## Quick Start

```bash
# Clone / unzip the project, then:
cd wex-transactions

# Run tests
./mvnw test

# Start the application
./mvnw spring-boot:run
```

The API will be available at `http://localhost:8080`.

> **H2 Console** (dev convenience): `http://localhost:8080/h2-console`  
> JDBC URL: `jdbc:h2:file:./data/transactions`, Username: `sa`, Password: *(empty)*

---

## API Reference

### 1. Store a Purchase Transaction

**`POST /api/v1/transactions`**

Stores a new purchase transaction. The amount is rounded to the nearest cent.

**Request Body:**
```json
{
  "description": "Office Supplies",
  "transactionDate": "2024-03-15",
  "purchaseAmount": 99.99
}
```

| Field | Type | Rules |
|---|---|---|
| `description` | String | Required, max 50 characters |
| `transactionDate` | ISO date (`YYYY-MM-DD`) | Required, not in future |
| `purchaseAmount` | Decimal | Required, positive value |

**Response `201 Created`:**
```json
{
  "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "description": "Office Supplies",
  "transactionDate": "2024-03-15",
  "purchaseAmount": 99.99
}
```

---

### 2. Retrieve a Transaction with Currency Conversion

**`GET /api/v1/transactions/{id}?currency={country_currency_desc}`**

Retrieves a stored transaction and converts the purchase amount to the specified currency using the Treasury exchange rate active on or before the transaction date (within the last 6 months).

The `currency` parameter must match a `country_currency_desc` value from the Treasury API (e.g. `Canada-Dollar`, `Euro Zone-Euro`, `Japan-Yen`).

**Example:**
```
GET /api/v1/transactions/a1b2c3d4-e5f6-7890-abcd-ef1234567890?currency=Canada-Dollar
```

**Response `200 OK`:**
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

**Error responses:**
- `404 Not Found` — transaction ID does not exist
- `422 Unprocessable Entity` — no exchange rate available within 6 months of the purchase date

---

### 3. List Available Currencies (Helper)

**`GET /api/v1/currencies?date=2024-03-15`**

Returns all available `country_currency_desc` values from the Treasury API for a given date. Useful for discovering valid values to pass to the conversion endpoint. Defaults to today if `date` is omitted.

---

## Design Decisions

### Currency Parameter
The Treasury API uses `country_currency_desc` as a compound field (e.g. `Canada-Dollar`, `Euro Zone-Euro`). This is passed directly as the `currency` query param. The helper `/api/v1/currencies` endpoint lets callers discover valid values.

### Exchange Rate Selection
Per the spec, the API uses the most recent exchange rate **on or before** the purchase date, within the **last 6 months**. If no rate is found in that window, a `422` error is returned with a clear message.

### Rounding
- Purchase amounts are stored rounded to the nearest cent (HALF_UP).
- Converted amounts are also rounded to two decimal places (HALF_UP).

### Persistence
Uses H2 file-based database by default (data persists across restarts in `./data/`). Swap to PostgreSQL or any other JPA-compatible database by updating `application.properties`.

---

## Running Tests

```bash
# All tests (unit + integration)
./mvnw test

# With coverage report
./mvnw test jacoco:report
# Report at: target/site/jacoco/index.html
```

The integration tests use **WireMock** to stub the Treasury API, so no network access is required to run tests.

### Test Coverage

| Layer | Test Type |
|---|---|
| `PurchaseTransactionService` | Unit — Mockito |
| `TreasuryExchangeRateService` | Unit — Mockito |
| `PurchaseTransactionController` | Slice — `@WebMvcTest` + MockMvc |
| End-to-end API flow | Integration — `@SpringBootTest` + WireMock |

---

## Project Structure

```
src/
├── main/java/com/wex/transactions/
│   ├── TransactionApplication.java
│   ├── config/           AppConfig.java (RestTemplate bean)
│   ├── controller/       PurchaseTransactionController.java
│   ├── dto/              Request/Response DTOs, Treasury API DTOs
│   ├── exception/        Custom exceptions, GlobalExceptionHandler
│   ├── model/            PurchaseTransaction (JPA entity)
│   ├── repository/       PurchaseTransactionRepository
│   └── service/          PurchaseTransactionService, TreasuryExchangeRateService
└── test/java/com/wex/transactions/
    ├── controller/       PurchaseTransactionControllerTest
    ├── integration/      PurchaseTransactionIntegrationTest
    └── service/          PurchaseTransactionServiceTest, TreasuryExchangeRateServiceTest
```
