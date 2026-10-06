package de.omarfourati.belegfluss.invoice;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Plausibility checks that run after the AI extraction. They catch typical
 * accounts-payable risks: double payments, calculation errors and changed bank details.
 */
@Component
public class InvoiceChecks {

    private static final BigDecimal TOLERANCE = new BigDecimal("0.01");
    private static final BigDecimal RATE_TOLERANCE = new BigDecimal("0.3");
    private static final Set<BigDecimal> GERMAN_VAT_RATES = Set.of(
            BigDecimal.ZERO, new BigDecimal("7"), new BigDecimal("19"));

    private final InvoiceRepository repository;

    public InvoiceChecks(InvoiceRepository repository) {
        this.repository = repository;
    }

    public List<InvoiceWarning> check(Invoice invoice) {
        List<InvoiceWarning> warnings = new ArrayList<>();
        if (invoice.getSupplierName() == null || invoice.getInvoiceNumber() == null || invoice.getGrossAmount() == null) {
            warnings.add(InvoiceWarning.MISSING_FIELDS);
        }
        warnings.addAll(checkAmounts(invoice.getNetAmount(), invoice.getVatAmount(), invoice.getGrossAmount()));
        if (invoice.getIban() != null && !isValidIban(invoice.getIban())) {
            warnings.add(InvoiceWarning.INVALID_IBAN);
        }
        if (invoice.getSupplierName() != null) {
            if (invoice.getIban() != null && repository.findOtherIbansOfSupplier(invoice.getSupplierName(), invoice.getId())
                    .stream().anyMatch(iban -> !normalizeIban(iban).equals(normalizeIban(invoice.getIban())))) {
                warnings.add(InvoiceWarning.IBAN_CHANGED);
            }
            if (invoice.getInvoiceNumber() != null && repository.existsDuplicate(
                    invoice.getSupplierName(), invoice.getInvoiceNumber(), invoice.getId())) {
                warnings.add(InvoiceWarning.DUPLICATE);
            }
        }
        return warnings;
    }

    static List<InvoiceWarning> checkAmounts(BigDecimal net, BigDecimal vat, BigDecimal gross) {
        List<InvoiceWarning> warnings = new ArrayList<>();
        if (net == null || vat == null) {
            return warnings;
        }
        if (gross != null && net.add(vat).subtract(gross).abs().compareTo(TOLERANCE) > 0) {
            warnings.add(InvoiceWarning.VAT_MISMATCH);
        }
        if (net.signum() > 0) {
            BigDecimal rate = vat.multiply(BigDecimal.valueOf(100)).divide(net, 2, RoundingMode.HALF_UP);
            boolean known = GERMAN_VAT_RATES.stream()
                    .anyMatch(r -> rate.subtract(r).abs().compareTo(RATE_TOLERANCE) <= 0);
            if (!known) {
                warnings.add(InvoiceWarning.UNUSUAL_VAT_RATE);
            }
        }
        return warnings;
    }

    /** ISO 13616 check: move the first four characters to the end, map letters to numbers, mod 97 must be 1. */
    static boolean isValidIban(String raw) {
        String iban = normalizeIban(raw);
        if (!iban.matches("[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}")) {
            return false;
        }
        String rearranged = iban.substring(4) + iban.substring(0, 4);
        StringBuilder digits = new StringBuilder();
        for (char c : rearranged.toCharArray()) {
            digits.append(Character.isLetter(c) ? String.valueOf(c - 'A' + 10) : String.valueOf(c));
        }
        return new BigInteger(digits.toString()).mod(BigInteger.valueOf(97)).intValue() == 1;
    }

    static String normalizeIban(String iban) {
        return iban.replaceAll("\\s", "").toUpperCase(Locale.ROOT);
    }
}
