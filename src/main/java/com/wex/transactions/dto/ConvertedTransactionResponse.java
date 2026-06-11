package com.wex.transactions.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConvertedTransactionResponse {
    private UUID id;
    private String description;
    private LocalDate transactionDate;
    private BigDecimal purchaseAmount;
    private BigDecimal exchangeRate;
    private BigDecimal convertedAmount;
    private String currency;
    private String country;
}
