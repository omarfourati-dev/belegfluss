package de.omarfourati.belegfluss.invoice;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceChecksTest {

    @Test
    void correctGermanInvoiceHasNoAmountWarnings() {
        assertThat(InvoiceChecks.checkAmounts(bd("100.00"), bd("19.00"), bd("119.00"))).isEmpty();
        assertThat(InvoiceChecks.checkAmounts(bd("100.00"), bd("7.00"), bd("107.00"))).isEmpty();
        assertThat(InvoiceChecks.checkAmounts(bd("100.00"), bd("0.00"), bd("100.00"))).isEmpty();
    }

    @Test
    void roundingDifferencesOfOneCentAreAccepted() {
        assertThat(InvoiceChecks.checkAmounts(bd("33.33"), bd("6.33"), bd("39.67"))).isEmpty();
    }

    @Test
    void detectsWrongSum() {
        assertThat(InvoiceChecks.checkAmounts(bd("100.00"), bd("19.00"), bd("129.00")))
                .containsExactly(InvoiceWarning.VAT_MISMATCH);
    }

    @Test
    void detectsUnusualVatRate() {
        assertThat(InvoiceChecks.checkAmounts(bd("100.00"), bd("16.00"), bd("116.00")))
                .containsExactly(InvoiceWarning.UNUSUAL_VAT_RATE);
    }

    @Test
    void missingAmountsAreNotCheckedHere() {
        assertThat(InvoiceChecks.checkAmounts(null, bd("19.00"), bd("119.00"))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"DE89370400440532013000", "DE89 3704 0044 0532 0130 00", "de89370400440532013000",
            "GB82WEST12345698765432", "AT611904300234573201"})
    void acceptsValidIbans(String iban) {
        assertThat(InvoiceChecks.isValidIban(iban)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"DE89370400440532013001", "DE00370400440532013000", "1234", "DE89-3704-0044"})
    void rejectsInvalidIbans(String iban) {
        assertThat(InvoiceChecks.isValidIban(iban)).isFalse();
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
