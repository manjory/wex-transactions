package com.wex.transactions.controller;

import com.wex.transactions.dto.ConvertedTransactionResponse;
import com.wex.transactions.dto.CreateTransactionRequest;
import com.wex.transactions.dto.TransactionResponse;
import com.wex.transactions.dto.TreasuryExchangeRateResponse;
import com.wex.transactions.service.PurchaseTransactionService;
import com.wex.transactions.service.TreasuryExchangeRateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PurchaseTransactionController {

    private final PurchaseTransactionService transactionService;
    private final TreasuryExchangeRateService treasuryService;

    /**
     * POST /api/v1/transactions
     * Stores a new purchase transaction.
     */
    @PostMapping("/transactions")
    public ResponseEntity<TransactionResponse> createTransaction(
            @Valid @RequestBody CreateTransactionRequest request) {
        TransactionResponse response = transactionService.createTransaction(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET /api/v1/transactions/{id}?currency=Canada-Dollar
     * Retrieves a stored transaction converted to the specified currency.
     * The currency param must match a Treasury API country_currency_desc (e.g. "Canada-Dollar").
     */
    @GetMapping("/transactions/{id}")
    public ResponseEntity<ConvertedTransactionResponse> getTransactionWithConversion(
            @PathVariable String id,
            @RequestParam("currency") String countryCurrencyDesc) {
        ConvertedTransactionResponse response =
                transactionService.getTransactionWithConversion(id, countryCurrencyDesc);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/currencies?date=2024-03-15
     * Helper endpoint to list available currencies for a given date.
     * Useful for discovering valid currency values to pass to the conversion endpoint.
     */
    @GetMapping("/currencies")
    public ResponseEntity<TreasuryExchangeRateResponse> listCurrencies(
            @RequestParam(value = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate queryDate = (date != null) ? date : LocalDate.now();
        return ResponseEntity.ok(treasuryService.listAvailableCurrencies(queryDate));
    }
}
