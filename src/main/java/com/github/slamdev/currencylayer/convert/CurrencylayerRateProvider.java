package com.github.slamdev.currencylayer.convert;

import org.javamoney.moneta.ExchangeRateBuilder;
import org.javamoney.moneta.spi.AbstractRateProvider;
import org.javamoney.moneta.spi.LoaderService;
import org.javamoney.moneta.spi.LoaderService.LoaderListener;

import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonReaderFactory;
import javax.money.CurrencyUnit;
import javax.money.NumberValue;
import javax.money.convert.*;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.String.format;
import static java.math.MathContext.DECIMAL64;
import static java.time.Instant.ofEpochSecond;
import static java.time.LocalDate.from;
import static java.time.LocalDate.now;
import static java.time.Period.ofDays;
import static java.time.ZoneId.systemDefault;
import static java.util.Objects.*;
import static java.util.Optional.ofNullable;
import static javax.json.Json.createReaderFactory;
import static javax.money.Monetary.getCurrency;
import static javax.money.Monetary.isCurrencyAvailable;
import static javax.money.convert.ConversionContextBuilder.create;
import static javax.money.convert.RateType.DEFERRED;
import static javax.money.convert.RateType.HISTORIC;
import static javax.money.spi.Bootstrap.getService;
import static org.javamoney.moneta.spi.DefaultNumberValue.ONE;
import static org.javamoney.moneta.spi.DefaultNumberValue.of;

public class CurrencylayerRateProvider extends AbstractRateProvider implements LoaderListener {

    private static final ProviderContext CONTEXT = ProviderContextBuilder.of("CL", DEFERRED)
            .set("providerDescription", "Currencylayer").set("days", 1).build();
    private static final String DATA_ID = CurrencylayerRateProvider.class.getSimpleName();
    private static final String ERROR_FORMAT = "Error [%d]: %s";
    private static final JsonReaderFactory JSON_FACTORY = createReaderFactory(null);
    private final Map<LocalDate, Map<CurrencyUnit, ExchangeRate>> rates = new ConcurrentHashMap<>();
    private CurrencyUnit baseCurrency;

    public CurrencylayerRateProvider() {
        super(CONTEXT);
        LoaderService loader = getService(LoaderService.class);
        loader.addLoaderListener(this, DATA_ID);
        loader.loadDataAsync(DATA_ID);
    }

    private static String parseError(JsonObject json) {
        JsonObject error = json.getJsonObject("error");
        return format(ERROR_FORMAT, error.getInt("code"), error.getString("info"));
    }

    private static ExchangeRate reverse(ExchangeRate rate) {
        if (isNull(rate)) {
            throw new IllegalArgumentException("Rate null is not reversible.");
        }
        return new ExchangeRateBuilder(rate).setRate(rate).setBase(rate.getCurrency()).setTerm(rate.getBaseCurrency())
                .setFactor(divide(ONE, rate.getFactor(), DECIMAL64)).build();
    }

    private static CurrencyUnit safeGetCurrency(String code) {
        return isCurrencyAvailable(code) ? getCurrency(code) : null;
    }

    @Override
    public ExchangeRate getExchangeRate(ConversionQuery conversionQuery) {
        requireNonNull(conversionQuery);
        if (rates.isEmpty()) {
            return null;
        }
        LocalDate[] dates = getQueryDates(conversionQuery);
        LocalDate selectedDate = null;
        Map<CurrencyUnit, ExchangeRate> targets = null;
        for (LocalDate date : dates) {
            targets = rates.get(date);
            if (targets != null) {
                selectedDate = date;
                break;
            }
        }
        if (targets == null) {
            return null;
        }
        ExchangeRateBuilder builder = getBuilder(conversionQuery, selectedDate);
        ExchangeRate sourceRate = targets.get(conversionQuery.getBaseCurrency());
        ExchangeRate target = targets.get(conversionQuery.getCurrency());
        return createExchangeRate(conversionQuery, builder, sourceRate, target);
    }

    @Override
    public void newDataLoaded(String resourceId, InputStream is) {
        log.fine("New data loaded for " + resourceId);
        CurrencylayerResponse response = convertData(is);
        log.fine("Number of found conversion rates for " + resourceId + " is " + response.quotes.size());
        baseCurrency = response.source;
        LocalDate date = from(response.timestamp.atZone(systemDefault()));
        response.quotes.entrySet().forEach(quote -> {
            RateType rateType = date.equals(now()) ? DEFERRED : HISTORIC;
            ExchangeRateBuilder builder = new ExchangeRateBuilder(create(CONTEXT, rateType).set(date).build());
            builder.setBase(response.source);
            builder.setTerm(quote.getKey());
            builder.setFactor(quote.getValue());
            ExchangeRate exchangeRate = builder.build();
            Map<CurrencyUnit, ExchangeRate> rateMap = ofNullable(rates.get(date)).orElse(new ConcurrentHashMap<>());
            rateMap.put(quote.getKey(), exchangeRate);
            rates.putIfAbsent(date, rateMap);
        });
    }

    private LocalDate[] getQueryDates(ConversionQuery query) {
        LocalDate date = query.get(LocalDate.class);
        if (date == null) {
            LocalDateTime dateTime = query.get(LocalDateTime.class);
            if (dateTime != null) {
                date = dateTime.toLocalDate();
            } else {
                date = now();
            }
        }
        return new LocalDate[]{date, date.minus(ofDays(1)), date.minus(ofDays(2)), date.minus(ofDays(3))};
    }

    private boolean areBothBaseCurrencies(ConversionQuery query) {
        return baseCurrency.equals(query.getBaseCurrency()) && baseCurrency.equals(query.getCurrency());
    }

    private CurrencylayerResponse convertData(InputStream is) {
        try (JsonReader reader = JSON_FACTORY.createReader(is)) {
            JsonObject json = reader.readObject();
            return convertToResponse(json);
        }
    }

    private CurrencylayerResponse convertToResponse(JsonObject json) {
        if (!json.getBoolean("success")) {
            throw new IllegalStateException(parseError(json));
        }
        Map<CurrencyUnit, NumberValue> quotes = new HashMap<>();
        CurrencyUnit source = getCurrency(json.getString("source"));
        json.getJsonObject("quotes").forEach((k, v) -> {
            CurrencyUnit term = safeGetCurrency(k.replaceFirst(source.getCurrencyCode(), ""));
            if (term == null) {
                log.finest("Unable to find corresponding CurrencyUnit for " + k + ". Currency will be skipped");
                return;
            }
            NumberValue factor = of(((JsonNumber) v).bigDecimalValue());
            quotes.put(term, factor);
        });
        Instant timestamp = ofEpochSecond(json.getJsonNumber("timestamp").longValue());
        return new CurrencylayerResponse(timestamp, source, quotes);
    }

    private ExchangeRate createExchangeRate(ConversionQuery query, ExchangeRateBuilder builder, ExchangeRate sourceRate,
                                            ExchangeRate target) {
        if (areBothBaseCurrencies(query)) {
            builder.setFactor(ONE);
            return builder.build();
        } else if (baseCurrency.equals(query.getCurrency())) {
            return isNull(sourceRate) ? null : reverse(sourceRate);
        } else if (baseCurrency.equals(query.getBaseCurrency())) {
            return target;
        } else {
            // Get Conversion base as derived rate: base -> USD -> term
            ExchangeRate rate1 = getExchangeRate(query.toBuilder().setTermCurrency(baseCurrency).build());
            ExchangeRate rate2 = getExchangeRate(
                    query.toBuilder().setBaseCurrency(baseCurrency).setTermCurrency(query.getCurrency()).build());
            if (nonNull(rate1) && nonNull(rate2)) {
                builder.setFactor(multiply(rate1.getFactor(), rate2.getFactor()));
                builder.setRateChain(rate1, rate2);
                return builder.build();
            }
            throw new CurrencyConversionException(query.getBaseCurrency(), query.getCurrency(),
                    sourceRate.getContext());
        }
    }

    private ExchangeRateBuilder getBuilder(ConversionQuery query, LocalDate localDate) {
        ExchangeRateBuilder builder = new ExchangeRateBuilder(create(getContext(), HISTORIC).set(localDate).build());
        builder.setBase(query.getBaseCurrency());
        builder.setTerm(query.getCurrency());
        return builder;
    }

    private static final class CurrencylayerResponse {

        public final Map<CurrencyUnit, NumberValue> quotes;

        public final CurrencyUnit source;

        public final Instant timestamp;

        public CurrencylayerResponse(Instant timestamp, CurrencyUnit source, Map<CurrencyUnit, NumberValue> quotes) {
            this.timestamp = timestamp;
            this.source = source;
            this.quotes = quotes;
        }
    }
}
