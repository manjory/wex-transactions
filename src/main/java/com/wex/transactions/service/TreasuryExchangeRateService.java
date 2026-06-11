package com.wex.transactions.service;

import com.wex.transactions.dto.TreasuryExchangeRateResponse;
import com.wex.transactions.dto.TreasuryExchangeRateResponse.ExchangeRateData;
import com.wex.transactions.exception.CurrencyConversionException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
public class TreasuryExchangeRateService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String exchangeRatesPath;

    public TreasuryExchangeRateService(
            RestTemplate restTemplate,
            @Value("${treasury.api.base-url}") String baseUrl,
            @Value("${treasury.api.exchange-rates-path}") String exchangeRatesPath) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
        this.exchangeRatesPath = exchangeRatesPath;
    }

    /**
     * Fetches the most recent exchange rate for the given country/currency on or before
     * the purchase date, within the last 6 months.
     *
     * @param countryCurrencyDesc the country-currency description (e.g. "Canada-Dollar")
     * @param purchaseDate        the date of the purchase transaction
     * @return the best matching ExchangeRateData
     * @throws CurrencyConversionException if no valid rate is found within 6 months
     */
    @Cacheable(value = "exchangeRates", key = "#countryCurrencyDesc + '_' + #purchaseDate")
    public ExchangeRateData getExchangeRate(String countryCurrencyDesc, LocalDate purchaseDate) {
        LocalDate sixMonthsAgo = purchaseDate.minusMonths(6);

        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + exchangeRatesPath)
                .queryParam("fields", "country,currency,exchange_rate,effective_date,country_currency_desc")
                .queryParam("filter",
                        "country_currency_desc:eq:" + countryCurrencyDesc
                        + ",effective_date:lte:" + purchaseDate.format(DATE_FORMATTER)
                        + ",effective_date:gte:" + sixMonthsAgo.format(DATE_FORMATTER))
                .queryParam("sort", "-effective_date")
                .queryParam("page[size]", "1")
                .build(false)
                .toUriString();

        TreasuryExchangeRateResponse response;
        try {
            response = restTemplate.getForObject(url, TreasuryExchangeRateResponse.class);
        } catch (Exception e) {
            throw new CurrencyConversionException(
                    "Failed to retrieve exchange rate from Treasury API: " + e.getMessage());
        }

        if (response == null || response.getData() == null || response.getData().isEmpty()) {
            throw new CurrencyConversionException(
                    "The purchase cannot be converted to the target currency. " +
                    "No exchange rate available for '" + countryCurrencyDesc +
                    "' within 6 months of the purchase date (" + purchaseDate + ").");
        }

        return response.getData().get(0);
    }

    /**
     * Lists all available country-currency descriptions for a given purchase date window.
     * Useful for clients to discover valid currency options.
     */
    public TreasuryExchangeRateResponse listAvailableCurrencies(LocalDate purchaseDate) {
        LocalDate sixMonthsAgo = purchaseDate.minusMonths(6);

        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + exchangeRatesPath)
                .queryParam("fields", "country,currency,country_currency_desc,effective_date")
                .queryParam("filter",
                        "effective_date:lte:" + purchaseDate.format(DATE_FORMATTER)
                        + ",effective_date:gte:" + sixMonthsAgo.format(DATE_FORMATTER))
                .queryParam("sort", "country_currency_desc")
                .queryParam("page[size]", "200")
                .build(false)
                .toUriString();

        return restTemplate.getForObject(url, TreasuryExchangeRateResponse.class);
    }
}
