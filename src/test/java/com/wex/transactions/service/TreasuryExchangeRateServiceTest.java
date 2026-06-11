package com.wex.transactions.service;

import com.wex.transactions.dto.TreasuryExchangeRateResponse;
import com.wex.transactions.dto.TreasuryExchangeRateResponse.ExchangeRateData;
import com.wex.transactions.exception.CurrencyConversionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TreasuryExchangeRateServiceTest {

    @Mock
    private RestTemplate restTemplate;

    private TreasuryExchangeRateService service;

    @BeforeEach
    void setUp() {
        service = new TreasuryExchangeRateService(
                restTemplate,
                "https://api.fiscaldata.treasury.gov",
                "/services/api/v1/accounting/od/rates_of_exchange"
        );
    }

    @Test
    @DisplayName("getExchangeRate: returns first result when data is available")
    void getExchangeRate_returnsData() {
        ExchangeRateData rateData = new ExchangeRateData();
        rateData.setCountry("Canada");
        rateData.setCurrency("Dollar");
        rateData.setExchangeRate(new BigDecimal("1.35"));
        rateData.setEffectiveDate("2024-03-31");

        TreasuryExchangeRateResponse mockResponse = new TreasuryExchangeRateResponse();
        mockResponse.setData(List.of(rateData));

        when(restTemplate.getForObject(anyString(), eq(TreasuryExchangeRateResponse.class)))
                .thenReturn(mockResponse);

        ExchangeRateData result = service.getExchangeRate("Canada-Dollar", LocalDate.of(2024, 3, 15));

        assertThat(result).isNotNull();
        assertThat(result.getExchangeRate()).isEqualByComparingTo("1.35");
        assertThat(result.getCountry()).isEqualTo("Canada");
    }

    @Test
    @DisplayName("getExchangeRate: throws CurrencyConversionException when response is null")
    void getExchangeRate_nullResponse_throwsException() {
        when(restTemplate.getForObject(anyString(), eq(TreasuryExchangeRateResponse.class)))
                .thenReturn(null);

        assertThatThrownBy(() -> service.getExchangeRate("Canada-Dollar", LocalDate.of(2024, 3, 15)))
                .isInstanceOf(CurrencyConversionException.class)
                .hasMessageContaining("cannot be converted");
    }

    @Test
    @DisplayName("getExchangeRate: throws CurrencyConversionException when data list is empty")
    void getExchangeRate_emptyData_throwsException() {
        TreasuryExchangeRateResponse mockResponse = new TreasuryExchangeRateResponse();
        mockResponse.setData(Collections.emptyList());

        when(restTemplate.getForObject(anyString(), eq(TreasuryExchangeRateResponse.class)))
                .thenReturn(mockResponse);

        assertThatThrownBy(() -> service.getExchangeRate("Fake-Currency", LocalDate.of(2024, 3, 15)))
                .isInstanceOf(CurrencyConversionException.class)
                .hasMessageContaining("No exchange rate available");
    }

    @Test
    @DisplayName("getExchangeRate: throws CurrencyConversionException when RestTemplate throws")
    void getExchangeRate_restTemplateThrows_throwsException() {
        when(restTemplate.getForObject(anyString(), eq(TreasuryExchangeRateResponse.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        assertThatThrownBy(() -> service.getExchangeRate("Canada-Dollar", LocalDate.of(2024, 3, 15)))
                .isInstanceOf(CurrencyConversionException.class)
                .hasMessageContaining("Failed to retrieve exchange rate");
    }

    @Test
    @DisplayName("getExchangeRate: URL includes correct date range (6 months prior to purchase date)")
    void getExchangeRate_urlContainsCorrectDateRange() {
        ExchangeRateData rateData = new ExchangeRateData();
        rateData.setExchangeRate(BigDecimal.ONE);

        TreasuryExchangeRateResponse mockResponse = new TreasuryExchangeRateResponse();
        mockResponse.setData(List.of(rateData));

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        when(restTemplate.getForObject(urlCaptor.capture(), eq(TreasuryExchangeRateResponse.class)))
                .thenReturn(mockResponse);

        service.getExchangeRate("Canada-Dollar", LocalDate.of(2024, 6, 15));

        String capturedUrl = urlCaptor.getValue();
        assertThat(capturedUrl).contains("effective_date:lte:2024-06-15");
        assertThat(capturedUrl).contains("effective_date:gte:2023-12-15");
        assertThat(capturedUrl).contains("sort=-effective_date");
    }
}
