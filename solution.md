# Purchase Transaction Service - Solution

## What This Service Does

This is a Spring Boot REST API that solves two problems:

1. It gives callers a place to record a purchase transaction (description, date, amount in USD)
   and get back a stable UUID they can reference later.
2. Given that UUID and a target currency, it fetches the appropriate exchange rate from the
   U.S. Treasury API and returns the converted amount alongside the original transaction details.

---

## How Each Requirement Is Addressed

### Storing a transaction (Requirement 1)

`POST /api/v1/transactions` accepts a JSON body with description, transactionDate, and
purchaseAmount. Bean Validation enforces all field rules before the request reaches service
code: description length, date presence, and a positive amount. The amount is rounded to two
decimal places using HALF_UP before it is written to the database. The response is `201 Created`
with the stored record including the generated UUID.

### Retrieving with currency conversion (Requirement 2)

`GET /api/v1/transactions/{id}?currency=Canada-Dollar` loads the stored transaction, then calls
the Treasury Fiscal Data API to find the most recent exchange rate for the requested
`country_currency_desc` that falls on or before the transaction date and within the 6-month
lookback window. The query uses the API's `filter`, `sort=-effective_date`, and `page[size]=1`
parameters to retrieve exactly the one rate that satisfies the rule. If none exists, a
`422 Unprocessable Entity` is returned with a clear message. If one is found, the converted
amount is computed and rounded to two decimal places before being returned to the caller.

---

## Key Design Decisions

### Why H2 file-based database

H2 in file mode gives persistence across restarts without requiring Docker or a database server
to be running locally. Any developer can clone the repo and run the application immediately.
Switching to PostgreSQL or another database requires only changing four lines in
`application.properties`; no code changes are needed because Spring Data JPA abstracts the
differences.

### Why Spring Boot

Spring Boot provides the validation, JPA, web, and testing infrastructure that this kind of
service needs. The auto-configuration means the application is runnable from a single class
with no boilerplate setup code. The test slices (`@WebMvcTest`, `@SpringBootTest`) let unit
tests run quickly without a full server, while integration tests use a real embedded server
and WireMock to stand in for the Treasury API.

### Why WireMock for integration tests

The Treasury API is an external dependency. Tests that hit it directly would be flaky (network
issues, rate limits, data changing over time) and would require internet access in CI. WireMock
stubs the API at the HTTP level, so integration tests exercise the full request/response cycle
including HTTP client configuration, JSON deserialization, and error handling, without any
real network calls.

### Exchange rate lookup strategy

Rather than fetching a large date range and filtering in code, the service pushes the date
constraints into the Treasury API query itself. This keeps the service stateless and keeps
memory usage bounded regardless of how many rates exist for a given currency. The API response
is limited to one record using `page[size]=1` after sorting by `effective_date` descending, so
the service always gets the most recent qualifying rate with a single HTTP call.

### SSL configuration

The HTTP client is configured to use the JVM default trust store by default, which trusts
standard public CAs (including the one used by `api.fiscaldata.treasury.gov`). A
`http.client.ssl.trust-store` property allows plugging in a custom truststore file for
environments where the Treasury API is accessed through a corporate proxy with a private CA
certificate, or where a self-signed cert is used in an internal test environment. Hostname
verification is always on.

---

## What Was Improved During Development

The original implementation had several issues that were corrected before submission:

- **Trust-all SSL removed.** `loadTrustMaterial((chain, authType) -> true)` and
  `NoopHostnameVerifier` were disabled, bypassing all certificate and hostname checks. This was
  replaced with a pluggable truststore pattern that defaults to the JVM trust store.
- **H2 console scoped to dev.** `spring.h2.console.enabled=true` was in the main
  `application.properties`, which would expose a database browser on any environment. Moved to
  `application-dev.properties`.
- **`.gitignore` added.** The H2 database file (`data/transactions.mv.db`) and IDE files
  (`.idea/`) were not excluded from version control.
- **Jacoco added.** The README referenced `./mvnw test jacoco:report` but the Jacoco plugin was
  not in `pom.xml`. The plugin is now configured with `prepare-agent` and `report` goals.
- **Decorative comment characters removed.** Box-drawing characters used as section dividers in
  test files were replaced with plain ASCII comment lines.
- **HTTP client properties externalized.** Connect and read timeouts were hardcoded. They are
  now configurable via `application.properties`.

---

## Known Limitations

- **In-memory database in tests.** The integration tests use H2 in-memory mode with
  `ddl-auto=create-drop`, which means each test run starts with a clean schema. This is correct
  for test isolation but means tests do not exercise the exact same schema as production (file
  mode with `ddl-auto=update`).
- **No pagination on the transaction list.** There is no `GET /api/v1/transactions` endpoint to
  list stored transactions. This was not in scope for the requirements but would be needed in a
  real product.
- **H2 in production.** The default configuration uses H2 which is suitable for a demo but not
  for a production deployment with concurrent writers or data durability requirements. See the
  deployment section in `explain.md` for how to switch to PostgreSQL.
