package com.github.slamdev.currencylayer.convert;

import org.javamoney.moneta.spi.CompoundRateProvider;

import javax.money.MonetaryException;
import javax.money.convert.ConversionQuery;
import javax.money.convert.ExchangeRateProvider;
import javax.money.spi.MonetaryConversionsSingletonSpi;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Arrays.stream;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;
import static org.javamoney.moneta.spi.MonetaryConfig.getConfig;

public class CLMonetaryConversionsSingletonSpi implements MonetaryConversionsSingletonSpi {

    private static final Class<?> PROVIDER_TYPES[] = {CurrencylayerRateProvider.class};

    private Map<String, ExchangeRateProvider> conversionProviders = new ConcurrentHashMap<>();

    public CLMonetaryConversionsSingletonSpi() {
        reload();
    }

    @Override
    public List<String> getDefaultProviderChain() {
        return stream(getConfig().get("conversion.chain").split(",")).filter(conversionProviders::containsKey)
                .collect(toList());
    }

    @Override
    public ExchangeRateProvider getExchangeRateProvider(ConversionQuery conversionQuery) {
        Collection<String> providers = getProvidersToUse(conversionQuery);
        return getExchangeRateProvider(providers.toArray(new String[providers.size()]));
    }

    @Override
    public ExchangeRateProvider getExchangeRateProvider(String... providers) {
        List<ExchangeRateProvider> provInstances = stream(providers).map(this::acquireProvider).collect(toList());
        if (provInstances.size() == 1) {
            return provInstances.get(0);
        }
        return new CompoundRateProvider(provInstances);
    }

    @Override
    public Set<String> getProviderNames() {
        return conversionProviders.keySet();
    }

    @Override
    public boolean isConversionAvailable(ConversionQuery conversionQuery) {
        return isExchangeRateProviderAvailable(conversionQuery)
                && getExchangeRateProvider(conversionQuery).getCurrencyConversion(conversionQuery) != null;
    }

    @Override
    public boolean isExchangeRateProviderAvailable(ConversionQuery conversionQuery) {
        return !getProvidersToUse(conversionQuery).isEmpty();
    }

    public void reload() {
        Map<String, ExchangeRateProvider> newProviders = new ConcurrentHashMap<>();
        for (Class<?> type : PROVIDER_TYPES) {
            ExchangeRateProvider provider;
            try {
                provider = (ExchangeRateProvider) type.newInstance();
            } catch (ReflectiveOperationException e) {
                throw new IllegalArgumentException(e);
            }
            newProviders.put(provider.getContext().getProviderName(), provider);
        }
        conversionProviders = newProviders;
    }

    private ExchangeRateProvider acquireProvider(String name) {
        return ofNullable(conversionProviders.get(name))
                .orElseThrow(() -> new MonetaryException("Unsupported conversion/rate provider: " + name));
    }

    private Collection<String> getProvidersToUse(ConversionQuery query) {
        List<String> providers = query.getProviderNames();
        if (providers.isEmpty()) {
            providers = getDefaultProviderChain();
            if (providers.isEmpty()) {
                throw new IllegalStateException("No default provider chain available.");
            }
        }
        providers.forEach(this::acquireProvider);
        return new ArrayList<>(providers);
    }
}
