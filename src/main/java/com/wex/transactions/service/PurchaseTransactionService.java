package com.wex.transactions.service;

import com.wex.transactions.dto.*;
import com.wex.transactions.dto.TreasuryExchangeRateResponse.ExchangeRateData;
import com.wex.transactions.exception.TransactionNotFoundException;
import com.wex.transactions.model.PurchaseTransaction;
import com.wex.transactions.repository.PurchaseTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PurchaseTransactionService {

    private final PurchaseTransactionRepository repository;
    private final TreasuryExchangeRateService treasuryService;

    /**
     * Stores a new purchase transaction, rounding the amount to the nearest cent.
     */
    @Transactional
    public TransactionResponse createTransaction(CreateTransactionRequest request) {
        BigDecimal roundedAmount = request.getPurchaseAmount()
                .setScale(2, RoundingMode.HALF_UP);

        PurchaseTransaction transaction = PurchaseTransaction.builder()
                .description(request.getDescription())
                .transactionDate(request.getTransactionDate())
                .purchaseAmount(roundedAmount)
                .build();

        PurchaseTransaction saved = repository.save(transaction);
        return toResponse(saved);
    }

    /**
     * Retrieves a stored transaction and converts its amount to the specified currency.
     *
     * @param id                  the transaction UUID
     * @param countryCurrencyDesc the Treasury API country-currency description (e.g. "Canada-Dollar")
     */
    @Transactional(readOnly = true)
    public ConvertedTransactionResponse getTransactionWithConversion(String id, String countryCurrencyDesc) {
        UUID uuid = parseUUID(id);
        PurchaseTransaction transaction = repository.findById(uuid)
                .orElseThrow(() -> new TransactionNotFoundException(id));

        ExchangeRateData rateData = treasuryService.getExchangeRate(
                countryCurrencyDesc, transaction.getTransactionDate());

        BigDecimal exchangeRate = rateData.getExchangeRate();
        BigDecimal convertedAmount = transaction.getPurchaseAmount()
                .multiply(exchangeRate)
                .setScale(2, RoundingMode.HALF_UP);

        return ConvertedTransactionResponse.builder()
                .id(transaction.getId())
                .description(transaction.getDescription())
                .transactionDate(transaction.getTransactionDate())
                .purchaseAmount(transaction.getPurchaseAmount())
                .exchangeRate(exchangeRate)
                .convertedAmount(convertedAmount)
                .currency(rateData.getCurrency())
                .country(rateData.getCountry())
                .build();
    }

    private TransactionResponse toResponse(PurchaseTransaction t) {
        return TransactionResponse.builder()
                .id(t.getId())
                .description(t.getDescription())
                .transactionDate(t.getTransactionDate())
                .purchaseAmount(t.getPurchaseAmount())
                .build();
    }

    private UUID parseUUID(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new TransactionNotFoundException(id);
        }
    }
}
