package com.wex.transactions.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wex.transactions.dto.ConvertedTransactionResponse;
import com.wex.transactions.dto.CreateTransactionRequest;
import com.wex.transactions.dto.TransactionResponse;
import com.wex.transactions.exception.CurrencyConversionException;
import com.wex.transactions.exception.TransactionNotFoundException;
import com.wex.transactions.service.PurchaseTransactionService;
import com.wex.transactions.service.TreasuryExchangeRateService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PurchaseTransactionController.class)
class PurchaseTransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PurchaseTransactionService transactionService;

    @MockBean
    private TreasuryExchangeRateService treasuryService;

    // -- POST /transactions --

    @Test
    @DisplayName("POST /transactions: returns 201 with created transaction")
    void createTransaction_success() throws Exception {
        UUID id = UUID.randomUUID();
        CreateTransactionRequest request = CreateTransactionRequest.builder()
                .description("Office Supplies")
                .transactionDate(LocalDate.of(2024, 3, 15))
                .purchaseAmount(new BigDecimal("99.99"))
                .build();

        TransactionResponse response = TransactionResponse.builder()
                .id(id)
                .description("Office Supplies")
                .transactionDate(LocalDate.of(2024, 3, 15))
                .purchaseAmount(new BigDecimal("99.99"))
                .build();

        when(transactionService.createTransaction(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.description").value("Office Supplies"))
                .andExpect(jsonPath("$.purchaseAmount").value(99.99));
    }

    @Test
    @DisplayName("POST /transactions: returns 400 when description is blank")
    void createTransaction_blankDescription_returns400() throws Exception {
        CreateTransactionRequest request = CreateTransactionRequest.builder()
                .description("")
                .transactionDate(LocalDate.of(2024, 3, 15))
                .purchaseAmount(new BigDecimal("99.99"))
                .build();

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /transactions: returns 400 when description exceeds 50 characters")
    void createTransaction_descriptionTooLong_returns400() throws Exception {
        CreateTransactionRequest request = CreateTransactionRequest.builder()
                .description("A".repeat(51))
                .transactionDate(LocalDate.of(2024, 3, 15))
                .purchaseAmount(new BigDecimal("99.99"))
                .build();

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /transactions: returns 400 when purchase amount is zero")
    void createTransaction_zeroAmount_returns400() throws Exception {
        CreateTransactionRequest request = CreateTransactionRequest.builder()
                .description("Test")
                .transactionDate(LocalDate.of(2024, 3, 15))
                .purchaseAmount(BigDecimal.ZERO)
                .build();

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /transactions: returns 400 when purchase amount is negative")
    void createTransaction_negativeAmount_returns400() throws Exception {
        CreateTransactionRequest request = CreateTransactionRequest.builder()
                .description("Test")
                .transactionDate(LocalDate.of(2024, 3, 15))
                .purchaseAmount(new BigDecimal("-10.00"))
                .build();

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /transactions: returns 400 when transaction date is missing")
    void createTransaction_missingDate_returns400() throws Exception {
        String json = """
                {"description": "Test", "purchaseAmount": 10.00}
                """;

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    // -- GET /transactions/{id} --

    @Test
    @DisplayName("GET /transactions/{id}: returns 200 with converted transaction")
    void getTransaction_success() throws Exception {
        UUID id = UUID.randomUUID();
        ConvertedTransactionResponse response = ConvertedTransactionResponse.builder()
                .id(id)
                .description("Office Supplies")
                .transactionDate(LocalDate.of(2024, 3, 15))
                .purchaseAmount(new BigDecimal("100.00"))
                .exchangeRate(new BigDecimal("1.35"))
                .convertedAmount(new BigDecimal("135.00"))
                .currency("Dollar")
                .country("Canada")
                .build();

        when(transactionService.getTransactionWithConversion(eq(id.toString()), eq("Canada-Dollar")))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/transactions/{id}", id)
                        .param("currency", "Canada-Dollar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.convertedAmount").value(135.00))
                .andExpect(jsonPath("$.exchangeRate").value(1.35))
                .andExpect(jsonPath("$.country").value("Canada"));
    }

    @Test
    @DisplayName("GET /transactions/{id}: returns 404 when transaction not found")
    void getTransaction_notFound_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(transactionService.getTransactionWithConversion(any(), any()))
                .thenThrow(new TransactionNotFoundException(id.toString()));

        mockMvc.perform(get("/api/v1/transactions/{id}", id)
                        .param("currency", "Canada-Dollar"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("GET /transactions/{id}: returns 422 when currency conversion unavailable")
    void getTransaction_noCurrencyRate_returns422() throws Exception {
        UUID id = UUID.randomUUID();
        when(transactionService.getTransactionWithConversion(any(), any()))
                .thenThrow(new CurrencyConversionException("No exchange rate available"));

        mockMvc.perform(get("/api/v1/transactions/{id}", id)
                        .param("currency", "Fake-Currency"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").exists());
    }
}
