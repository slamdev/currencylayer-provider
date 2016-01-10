package com.github.slamdev.currencylayer.convert;

import javax.money.convert.ExchangeRateProviderSupplier;

public enum ExchangeRateType implements ExchangeRateProviderSupplier {
    /**
     * Exchange rate to the Currencylayer. Uses the {@link CurrencylayerRateProvider} implementation.
     */
    CL("CL", "Exchange rate to the Currencylayer.");

    private final String description;

    private final String type;

    ExchangeRateType(String type, String description) {
        this.type = type;
        this.description = description;
    }

    @Override
    public String get() {
        return type;
    }

    public String getDescription() {
        return description;
    }
}
