package com.github.slamdev.currencylayer.convert;

import org.junit.Before;
import org.junit.Test;

import javax.money.CurrencyUnit;
import javax.money.MonetaryAmount;
import javax.money.MonetaryAmountFactory;
import javax.money.convert.CurrencyConversion;
import javax.money.convert.ExchangeRateProvider;
import java.math.BigDecimal;

import static com.github.slamdev.currencylayer.convert.ExchangeRateType.CL;
import static java.math.BigDecimal.TEN;
import static javax.money.Monetary.getCurrency;
import static javax.money.Monetary.getDefaultAmountFactory;
import static javax.money.convert.MonetaryConversions.getExchangeRateProvider;
import static org.junit.Assert.*;

@SuppressWarnings("Duplicates")
public class CurrencylayerRateProviderTest {

    private static final CurrencyUnit BRAZILIAN_REAL = getCurrency("BRL");

    private static final CurrencyUnit DOLLAR = getCurrency("USD");

    private static final CurrencyUnit EURO = getCurrency("EUR");

    private MonetaryAmountFactory<?> monetaryFactory;

    private ExchangeRateProvider provider;

    @Before
    public void setup() {
        provider = getExchangeRateProvider(CL);
        monetaryFactory = getDefaultAmountFactory();
    }

    @Test
    public void shouldConvertsBrazilianToDollar() {
        CurrencyConversion currencyConversion = provider.getCurrencyConversion(DOLLAR);
        assertNotNull(currencyConversion);
        MonetaryAmount money = of(TEN, BRAZILIAN_REAL);
        MonetaryAmount result = currencyConversion.apply(money);
        assertEquals(result.getCurrency(), DOLLAR);
        assertTrue(result.getNumber().doubleValue() > 0);
    }

    @Test
    public void shouldConvertsDollarToBrazilian() {
        CurrencyConversion currencyConversion = provider.getCurrencyConversion(BRAZILIAN_REAL);
        assertNotNull(currencyConversion);
        MonetaryAmount money = of(TEN, DOLLAR);
        MonetaryAmount result = currencyConversion.apply(money);
        assertEquals(result.getCurrency(), BRAZILIAN_REAL);
        assertTrue(result.getNumber().doubleValue() > 0);
    }

    @Test
    public void shouldConvertsDollarToEuro() {
        CurrencyConversion currencyConversion = provider.getCurrencyConversion(EURO);
        assertNotNull(currencyConversion);
        MonetaryAmount money = of(TEN, DOLLAR);
        MonetaryAmount result = currencyConversion.apply(money);
        assertEquals(result.getCurrency(), EURO);
        assertTrue(result.getNumber().doubleValue() > 0);
    }

    @Test
    public void shouldConvertsEuroToDollar() {
        CurrencyConversion currencyConversion = provider.getCurrencyConversion(DOLLAR);
        assertNotNull(currencyConversion);
        MonetaryAmount money = of(TEN, EURO);
        MonetaryAmount result = currencyConversion.apply(money);
        assertEquals(result.getCurrency(), DOLLAR);
        assertTrue(result.getNumber().doubleValue() > 0);
    }

    @Test
    public void shouldReturnsECBCurrentRateProvider() {
        assertNotNull(provider);
        assertEquals(provider.getClass(), CurrencylayerRateProvider.class);
    }

    @Test
    public void shouldReturnsSameBrazilianValue() {
        CurrencyConversion currencyConversion = provider.getCurrencyConversion(BRAZILIAN_REAL);
        assertNotNull(currencyConversion);
        MonetaryAmount money = of(TEN, BRAZILIAN_REAL);
        MonetaryAmount result = currencyConversion.apply(money);
        assertEquals(result.getCurrency(), BRAZILIAN_REAL);
        assertEquals(result.getNumber().numberValue(BigDecimal.class), TEN);
    }

    @Test
    public void shouldReturnsSameDollarValue() {
        CurrencyConversion currencyConversion = provider.getCurrencyConversion(DOLLAR);
        assertNotNull(currencyConversion);
        MonetaryAmount money = of(TEN, DOLLAR);
        MonetaryAmount result = currencyConversion.apply(money);
        assertEquals(result.getCurrency(), DOLLAR);
        assertEquals(result.getNumber().numberValue(BigDecimal.class), TEN);
    }

    @Test
    public void shouldReturnsSameEuroValue() {
        CurrencyConversion currencyConversion = provider.getCurrencyConversion(EURO);
        assertNotNull(currencyConversion);
        MonetaryAmount money = of(TEN, EURO);
        MonetaryAmount result = currencyConversion.apply(money);
        assertEquals(result.getCurrency(), EURO);
        assertEquals(result.getNumber().numberValue(BigDecimal.class), TEN);
    }

    private MonetaryAmount of(BigDecimal value, CurrencyUnit unit) {
        return monetaryFactory.setNumber(value).setCurrency(unit).create();
    }
}
