package com.wex.transactions.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.wex.transactions.dto.CreateTransactionRequest;
import com.wex.transactions.dto.TransactionResponse;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PurchaseTransactionIntegrationTest {

    static WireMockServer wireMockServer;

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    ObjectMapper objectMapper;

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockServer.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMockServer.stop();
    }

    @AfterEach
    void resetWireMock() {
        wireMockServer.resetAll();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("treasury.api.base-url", () -> "http://localhost:" + wireMockServer.port());
        registry.add("wiremock.server.port", wireMockServer::port);
    }

    private String baseUrl() {
        return "http://localhost:" + port + "/api/v1";
    }

    // -- Helpers --

    private TransactionResponse createTransaction(String description, LocalDate date, BigDecimal amount) {
        CreateTransactionRequest request = CreateTransactionRequest.builder()
                .description(description)
                .transactionDate(date)
                .purchaseAmount(amount)
                .build();

        ResponseEntity<TransactionResponse> response = restTemplate.postForEntity(
                baseUrl() + "/transactions", request, TransactionResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private void stubTreasurySuccess(String countryCurrencyDesc, String exchangeRate, String effectiveDate) {
        String body = """
                {
                  "data": [{
                    "country": "%s",
                    "currency": "%s",
                    "exchange_rate": "%s",
                    "effective_date": "%s",
                    "country_currency_desc": "%s"
                  }],
                  "meta": { "count": 1 }
                }
                """.formatted(
                countryCurrencyDesc.split("-")[0],
                countryCurrencyDesc.split("-")[1],
                exchangeRate,
                effectiveDate,
                countryCurrencyDesc
        );

        wireMockServer.stubFor(get(urlPathMatching("/services/api/v1/accounting/od/rates_of_exchange"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }

    private void stubTreasuryEmpty() {
        wireMockServer.stubFor(get(urlPathMatching("/services/api/v1/accounting/od/rates_of_exchange"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"data\": [], \"meta\": {\"count\": 0}}")));
    }

    // -- Tests --

    @Test
    @DisplayName("Full flow: store transaction then retrieve with currency conversion")
    void storeAndRetrieve_withConversion_success() {
        TransactionResponse stored = createTransaction(
                "Office Supplies", LocalDate.of(2024, 3, 15), new BigDecimal("100.00"));

        assertThat(stored.getId()).isNotNull();

        stubTreasurySuccess("Canada-Dollar", "1.3500", "2024-03-31");

        ResponseEntity<String> converted = restTemplate.getForEntity(
                baseUrl() + "/transactions/" + stored.getId() + "?currency=Canada-Dollar",
                String.class);

        assertThat(converted.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(converted.getBody()).contains("135.00");
        assertThat(converted.getBody()).contains("1.3500");
        assertThat(converted.getBody()).contains("Office Supplies");
    }

    @Test
    @DisplayName("Retrieve returns 404 for unknown transaction ID")
    void getTransaction_unknownId_returns404() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl() + "/transactions/00000000-0000-0000-0000-000000000000?currency=Canada-Dollar",
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Retrieve returns 422 when no exchange rate available within 6 months")
    void getTransaction_noExchangeRate_returns422() {
        TransactionResponse stored = createTransaction(
                "Test Purchase", LocalDate.of(2024, 3, 15), new BigDecimal("50.00"));

        stubTreasuryEmpty();

        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl() + "/transactions/" + stored.getId() + "?currency=Fake-Currency",
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).contains("cannot be converted");
    }

    @Test
    @DisplayName("Create transaction returns 400 when description exceeds 50 characters")
    void createTransaction_longDescription_returns400() {
        CreateTransactionRequest request = CreateTransactionRequest.builder()
                .description("A".repeat(51))
                .transactionDate(LocalDate.now())
                .purchaseAmount(new BigDecimal("10.00"))
                .build();

        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl() + "/transactions", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Create transaction returns 400 for negative amount")
    void createTransaction_negativeAmount_returns400() {
        CreateTransactionRequest request = CreateTransactionRequest.builder()
                .description("Test")
                .transactionDate(LocalDate.now())
                .purchaseAmount(new BigDecimal("-5.00"))
                .build();

        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl() + "/transactions", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Purchase amount is rounded to nearest cent before storage")
    void createTransaction_amountRounded() {
        TransactionResponse stored = createTransaction(
                "Rounding Test", LocalDate.now(), new BigDecimal("10.555"));

        assertThat(stored.getPurchaseAmount()).isEqualByComparingTo("10.56");
    }

    @Test
    @DisplayName("Converted amount is rounded to two decimal places")
    void getTransaction_convertedAmountRounded() {
        TransactionResponse stored = createTransaction(
                "Currency Test", LocalDate.of(2024, 3, 15), new BigDecimal("10.00"));

        // exchange rate that produces fractional cents: 10.00 * 149.123 = 1491.23
        stubTreasurySuccess("Japan-Yen", "149.123", "2024-03-31");

        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl() + "/transactions/" + stored.getId() + "?currency=Japan-Yen",
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("1491.23");
    }
}
