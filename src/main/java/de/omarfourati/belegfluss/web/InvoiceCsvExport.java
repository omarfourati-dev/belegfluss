package de.omarfourati.belegfluss.web;

import de.omarfourati.belegfluss.invoice.Invoice;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Accounting export in the style German bookkeeping tools (DATEV, Lexware) import:
 * semicolon separated, decimal comma, dd.MM.yyyy dates, UTF-8 with BOM for Excel.
 */
final class InvoiceCsvExport {

    static final String BOM = "﻿";
    private static final String HEADER =
            "Belegdatum;Fälligkeit;Lieferant;Rechnungsnummer;Netto;USt;Brutto;Währung;IBAN;Status;Beleg-ID";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private InvoiceCsvExport() {
    }

    static String toCsv(List<Invoice> invoices) {
        StringBuilder csv = new StringBuilder(BOM).append(HEADER).append("\r\n");
        for (Invoice i : invoices) {
            csv.append(String.join(";",
                    date(i.getInvoiceDate()),
                    date(i.getDueDate()),
                    text(i.getSupplierName()),
                    text(i.getInvoiceNumber()),
                    amount(i.getNetAmount()),
                    amount(i.getVatAmount()),
                    amount(i.getGrossAmount()),
                    text(i.getCurrency()),
                    text(i.getIban()),
                    i.getStatus().name(),
                    i.getId().toString())).append("\r\n");
        }
        return csv.toString();
    }

    private static String date(LocalDate date) {
        return date == null ? "" : DATE.format(date);
    }

    private static String amount(BigDecimal value) {
        if (value == null) {
            return "";
        }
        DecimalFormat format = new DecimalFormat("0.00", DecimalFormatSymbols.getInstance(Locale.GERMANY));
        return format.format(value);
    }

    /** Quotes every text field; also neutralises formula injection (=, +, -, @) for spreadsheet users. */
    static String text(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String safe = value.replace("\"", "\"\"").replace("\r", " ").replace("\n", " ");
        if ("=+-@".indexOf(safe.charAt(0)) >= 0) {
            safe = "'" + safe;
        }
        return "\"" + safe + "\"";
    }
}
