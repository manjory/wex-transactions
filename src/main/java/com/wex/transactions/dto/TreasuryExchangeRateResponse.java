package com.wex.transactions.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class TreasuryExchangeRateResponse {

    private List<ExchangeRateData> data;
    private Meta meta;

    @Data
    public static class ExchangeRateData {
        @JsonProperty("country")
        private String country;

        @JsonProperty("currency")
        private String currency;

        @JsonProperty("exchange_rate")
        private BigDecimal exchangeRate;

        @JsonProperty("effective_date")
        private String effectiveDate;

        @JsonProperty("country_currency_desc")
        private String countryCurrencyDesc;
    }

    @Data
    public static class Meta {
        @JsonProperty("count")
        private int count;

        @JsonProperty("labels")
        private Object labels;
    }
}
