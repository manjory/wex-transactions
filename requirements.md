# Purchase Transaction Service - Requirements

## Background

Build a production-ready REST API that stores purchase transactions in US dollars and retrieves
them with real-time currency conversion powered by the U.S. Treasury Reporting Rates of Exchange API.

The application must be runnable locally by any developer with minimal setup, while also being
structured well enough to deploy to a production environment without modification.

---

## Requirement 1: Store a Purchase Transaction

The API must accept a purchase transaction and persist it. On successful storage, the system
returns the transaction along with a system-assigned unique identifier.

### Field Rules

| Field | Type | Rule |
|---|---|---|
| `description` | String | Required. Must not exceed 50 characters. |
| `transactionDate` | ISO date `YYYY-MM-DD` | Required. Must be a valid calendar date. |
| `purchaseAmount` | Decimal | Required. Must be a positive value. Stored rounded to the nearest cent (HALF_UP). |
| `id` | UUID | Assigned by the system on creation. Uniquely identifies each transaction. |

---

## Requirement 2: Retrieve a Purchase Transaction in a Target Currency

The API must retrieve a previously stored transaction and return the purchase amount converted
to a currency of the caller's choosing, using exchange rates from the Treasury API.

### Response Fields

The response must include all of the following:

- Transaction identifier
- Description
- Transaction date
- Original purchase amount in US dollars
- Exchange rate used for the conversion
- Converted amount in the target currency

### Conversion Rules

1. Use the most recent exchange rate that is on or before the transaction date and within the
   last 6 months of that date. An exact date match is not required.
2. If no exchange rate exists within that 6-month window, return an error indicating the
   purchase cannot be converted to the target currency.
3. Round the converted amount to two decimal places.

### Treasury API Reference

Exchange rates are fetched from:
```
https://api.fiscaldata.treasury.gov/services/api/fiscal_service/v1/accounting/od/rates_of_exchange
```

Dataset documentation:
https://fiscaldata.treasury.gov/datasets/treasury-reporting-rates-exchange/treasury-reporting-rates-of-exchange

---

## Non-Functional Requirements

- **Language:** Java (or C# with prior written approval).
- **Testing:** All functional automated tests that would be included for a production application
  are expected. Performance testing is out of scope.
- **Developer experience:** Local setup must be fast and require minimal prerequisites. Running
  the tests and starting the application must each be a single command.
- **Production readiness:** Configuration, error handling, and data persistence must be suitable
  for a production deployment with only environment-specific config changes.
