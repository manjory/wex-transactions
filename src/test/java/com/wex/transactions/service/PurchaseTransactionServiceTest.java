package com.wex.transactions.service;

import com.wex.transactions.dto.ConvertedTransactionResponse;
import com.wex.transactions.dto.CreateTransactionRequest;
import com.wex.transactions.dto.TransactionResponse;
import com.wex.transactions.dto.TreasuryExchangeRateResponse.ExchangeRateData;
import com.wex.transactions.exception.CurrencyConversionException;
import com.wex.transactions.exception.TransactionNotFoundException;
import com.wex.transactions.model.PurchaseTransaction;
import com.wex.transactions.repository.PurchaseTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PurchaseTransactionServiceTest {

    @Mock
    private PurchaseTransactionRepository repository;

    @Mock
    private TreasuryExchangeRateService treasuryService;

    @InjectMocks
    private PurchaseTransactionService service;

    private UUID transactionId;
    private PurchaseTransaction savedTransaction;

    @BeforeEach
    void setUp() {
        transactionId = UUID.randomUUID();
        savedTransaction = PurchaseTransaction.builder()
                .id(transactionId)
                .description("Office Supplies")
                .transactionDate(LocalDate.of(2024, 3, 15))
                .purchaseAmount(new BigDecimal("100.00"))
                .build();
    }

    // -- createTransaction --

    @Test
    @DisplayName("createTransaction: stores transaction and returns response with ID")
    void createTransaction_success() {
        CreateTransactionRequest request = CreateTransactionRequest.builder()
                .description("Office Supplies")
                .transactionDate(LocalDate.of(2024, 3, 15))
                .purchaseAmount(new BigDecimal("100.00"))
                .build();

        when(repository.save(any(PurchaseTransaction.class))).thenReturn(savedTransaction);

        TransactionResponse response = service.createTransaction(request);

        assertThat(response.getId()).isEqualTo(transactionId);
        assertThat(response.getDescription()).isEqualTo("Office Supplies");
        assertThat(response.getPurchaseAmount()).isEqualByComparingTo("100.00");
        verify(repository).save(any(PurchaseTransaction.class));
    }

    @Test
    @DisplayName("createTransaction: rounds amount to nearest cent (half-up)")
    void createTransaction_roundsAmountToNearestCent() {
        CreateTransactionRequest request = CreateTransactionRequest.builder()
                .description("Test")
                .transactionDate(LocalDate.now())
                .purchaseAmount(new BigDecimal("10.555"))
                .build();

        PurchaseTransaction roundedTransaction = PurchaseTransaction.builder()
                .id(UUID.randomUUID())
                .description("Test")
                .transactionDate(LocalDate.now())
                .purchaseAmount(new BigDecimal("10.56"))
                .build();

        when(repository.save(argThat(t -> t.getPurchaseAmount().compareTo(new BigDecimal("10.56")) == 0)))
                .thenReturn(roundedTransaction);

        TransactionResponse response = service.createTransaction(request);

        assertThat(response.getPurchaseAmount()).isEqualByComparingTo("10.56");
    }

    // -- getTransactionWithConversion --

    @Test
    @DisplayName("getTransactionWithConversion: returns converted amount correctly")
    void getTransactionWithConversion_success() {
        ExchangeRateData rate = new ExchangeRateData();
        rate.setCountry("Canada");
        rate.setCurrency("Dollar");
        rate.setExchangeRate(new BigDecimal("1.3500"));
        rate.setEffectiveDate("2024-03-31");

        when(repository.findById(transactionId)).thenReturn(Optional.of(savedTransaction));
        when(treasuryService.getExchangeRate(eq("Canada-Dollar"), eq(LocalDate.of(2024, 3, 15))))
                .thenReturn(rate);

        ConvertedTransactionResponse response =
                service.getTransactionWithConversion(transactionId.toString(), "Canada-Dollar");

        assertThat(response.getId()).isEqualTo(transactionId);
        assertThat(response.getPurchaseAmount()).isEqualByComparingTo("100.00");
        assertThat(response.getExchangeRate()).isEqualByComparingTo("1.3500");
        assertThat(response.getConvertedAmount()).isEqualByComparingTo("135.00");
        assertThat(response.getCurrency()).isEqualTo("Dollar");
        assertThat(response.getCountry()).isEqualTo("Canada");
    }

    @Test
    @DisplayName("getTransactionWithConversion: rounds converted amount to two decimal places")
    void getTransactionWithConversion_roundsConvertedAmount() {
        PurchaseTransaction t = PurchaseTransaction.builder()
                .id(transactionId)
                .description("Test")
                .transactionDate(LocalDate.of(2024, 3, 15))
                .purchaseAmount(new BigDecimal("10.00"))
                .build();

        ExchangeRateData rate = new ExchangeRateData();
        rate.setCountry("Japan");
        rate.setCurrency("Yen");
        rate.setExchangeRate(new BigDecimal("149.123"));
        rate.setEffectiveDate("2024-03-31");

        when(repository.findById(transactionId)).thenReturn(Optional.of(t));
        when(treasuryService.getExchangeRate(any(), any())).thenReturn(rate);

        ConvertedTransactionResponse response =
                service.getTransactionWithConversion(transactionId.toString(), "Japan-Yen");

        // 10.00 * 149.123 = 1491.23
        assertThat(response.getConvertedAmount()).isEqualByComparingTo("1491.23");
    }

    @Test
    @DisplayName("getTransactionWithConversion: throws TransactionNotFoundException for unknown ID")
    void getTransactionWithConversion_transactionNotFound() {
        when(repository.findById(transactionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.getTransactionWithConversion(transactionId.toString(), "Canada-Dollar"))
                .isInstanceOf(TransactionNotFoundException.class)
                .hasMessageContaining(transactionId.toString());
    }

    @Test
    @DisplayName("getTransactionWithConversion: throws TransactionNotFoundException for invalid UUID format")
    void getTransactionWithConversion_invalidUuidFormat() {
        assertThatThrownBy(() ->
                service.getTransactionWithConversion("not-a-uuid", "Canada-Dollar"))
                .isInstanceOf(TransactionNotFoundException.class);
    }

    @Test
    @DisplayName("getTransactionWithConversion: propagates CurrencyConversionException from treasury service")
    void getTransactionWithConversion_noExchangeRate() {
        when(repository.findById(transactionId)).thenReturn(Optional.of(savedTransaction));
        when(treasuryService.getExchangeRate(any(), any()))
                .thenThrow(new CurrencyConversionException("No exchange rate available"));

        assertThatThrownBy(() ->
                service.getTransactionWithConversion(transactionId.toString(), "Fake-Currency"))
                .isInstanceOf(CurrencyConversionException.class)
                .hasMessageContaining("No exchange rate available");
    }
}
