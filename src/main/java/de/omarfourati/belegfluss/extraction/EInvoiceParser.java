package de.omarfourati.belegfluss.extraction;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary;
import org.apache.pdfbox.pdmodel.PDEmbeddedFilesNameTreeNode;
import org.apache.pdfbox.pdmodel.common.PDNameTreeNode;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.apache.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Reads German/EU e-invoices without any AI: XRechnung and other EN 16931 invoices in
 * UBL 2.1 or UN/CEFACT CII syntax, and ZUGFeRD / Factur-X PDFs with an embedded CII file.
 * Paths use local-name() so namespace prefixes in the files do not matter.
 */
@Component
public class EInvoiceParser {

    private static final DateTimeFormatter CII_DATE = DateTimeFormatter.BASIC_ISO_DATE; // format code 102: yyyyMMdd

    /** Parses an XML upload. Empty if the XML is well-formed but not a supported invoice. */
    public Optional<EInvoice> parseXml(byte[] xml) {
        Document doc = parseSecurely(xml);
        String root = doc.getDocumentElement().getLocalName();
        if ("Invoice".equals(root)) {
            return Optional.of(readUbl(doc));
        }
        if ("CrossIndustryInvoice".equals(root)) {
            return Optional.of(readCii(doc, false));
        }
        return Optional.empty();
    }

    /** Looks for an embedded e-invoice XML in a PDF (ZUGFeRD / Factur-X / XRechnung as attachment). */
    public Optional<EInvoice> parseEmbedded(byte[] pdf) {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            PDDocumentNameDictionary names = document.getDocumentCatalog().getNames();
            if (names == null || names.getEmbeddedFiles() == null) {
                return Optional.empty();
            }
            for (byte[] xml : embeddedXmlFiles(names.getEmbeddedFiles())) {
                Optional<EInvoice> invoice = parseXml(xml).map(e ->
                        e.format().startsWith("XRechnung") ? e : new EInvoice(e.data(), "ZUGFeRD / Factur-X"));
                if (invoice.isPresent()) {
                    return invoice;
                }
            }
            return Optional.empty();
        } catch (IOException e) {
            return Optional.empty();
        } catch (ExtractionException e) {
            // an attached XML that is not a valid invoice: fall back to reading the PDF itself
            return Optional.empty();
        }
    }

    private static List<byte[]> embeddedXmlFiles(PDEmbeddedFilesNameTreeNode tree) throws IOException {
        List<byte[]> files = new ArrayList<>();
        collect(tree, files);
        return files;
    }

    private static void collect(PDNameTreeNode<PDComplexFileSpecification> node, List<byte[]> files) throws IOException {
        Map<String, PDComplexFileSpecification> entries = node.getNames();
        if (entries != null) {
            for (Map.Entry<String, PDComplexFileSpecification> entry : entries.entrySet()) {
                PDEmbeddedFile file = entry.getValue().getEmbeddedFile();
                if (file != null && entry.getKey().toLowerCase(Locale.ROOT).endsWith(".xml")) {
                    files.add(file.toByteArray());
                }
            }
        }
        if (node.getKids() != null) {
            for (PDNameTreeNode<PDComplexFileSpecification> kid : node.getKids()) {
                collect(kid, files);
            }
        }
    }

    private EInvoice readUbl(Document doc) {
        XPath x = XPathFactory.newInstance().newXPath();
        String customization = text(x, doc, "/*/*[local-name()='CustomizationID']");
        String format = customization != null && customization.toLowerCase(Locale.ROOT).contains("xrechnung")
                ? "XRechnung (UBL)" : "EN 16931 (UBL)";
        String supplier = first(
                text(x, doc, "//*[local-name()='AccountingSupplierParty']//*[local-name()='PartyLegalEntity']/*[local-name()='RegistrationName']"),
                text(x, doc, "//*[local-name()='AccountingSupplierParty']//*[local-name()='PartyName']/*[local-name()='Name']"));
        String due = first(
                text(x, doc, "/*/*[local-name()='DueDate']"),
                text(x, doc, "//*[local-name()='PaymentMeans']/*[local-name()='PaymentDueDate']"));
        ExtractedInvoice data = new ExtractedInvoice(
                supplier,
                text(x, doc, "/*/*[local-name()='ID']"),
                isoDate(text(x, doc, "/*/*[local-name()='IssueDate']")),
                isoDate(due),
                amount(text(x, doc, "//*[local-name()='LegalMonetaryTotal']/*[local-name()='TaxExclusiveAmount']")),
                amount(text(x, doc, "/*/*[local-name()='TaxTotal']/*[local-name()='TaxAmount']")),
                amount(text(x, doc, "//*[local-name()='LegalMonetaryTotal']/*[local-name()='TaxInclusiveAmount']")),
                text(x, doc, "/*/*[local-name()='DocumentCurrencyCode']"),
                iban(text(x, doc, "//*[local-name()='PayeeFinancialAccount']/*[local-name()='ID']")));
        return new EInvoice(data, format);
    }

    private EInvoice readCii(Document doc, boolean embedded) {
        XPath x = XPathFactory.newInstance().newXPath();
        String guideline = text(x, doc, "//*[local-name()='GuidelineSpecifiedDocumentContextParameter']/*[local-name()='ID']");
        String g = guideline == null ? "" : guideline.toLowerCase(Locale.ROOT);
        String format = g.contains("xrechnung") ? "XRechnung (CII)"
                : (g.contains("factur-x") || g.contains("zugferd") || embedded) ? "ZUGFeRD / Factur-X" : "EN 16931 (CII)";
        String summation = "//*[local-name()='SpecifiedTradeSettlementHeaderMonetarySummation']";
        ExtractedInvoice data = new ExtractedInvoice(
                text(x, doc, "//*[local-name()='SellerTradeParty']/*[local-name()='Name']"),
                text(x, doc, "//*[local-name()='ExchangedDocument']/*[local-name()='ID']"),
                ciiDate(text(x, doc, "//*[local-name()='ExchangedDocument']/*[local-name()='IssueDateTime']/*[local-name()='DateTimeString']")),
                ciiDate(text(x, doc, "//*[local-name()='SpecifiedTradePaymentTerms']/*[local-name()='DueDateDateTime']/*[local-name()='DateTimeString']")),
                amount(text(x, doc, summation + "/*[local-name()='TaxBasisTotalAmount']")),
                amount(text(x, doc, summation + "/*[local-name()='TaxTotalAmount']")),
                amount(text(x, doc, summation + "/*[local-name()='GrandTotalAmount']")),
                text(x, doc, "//*[local-name()='InvoiceCurrencyCode']"),
                iban(text(x, doc, "//*[local-name()='PayeePartyCreditorFinancialAccount']/*[local-name()='IBANID']")));
        return new EInvoice(data, format);
    }

    /** XXE-safe parsing: no DTDs, no external entities, no XInclude. */
    static Document parseSecurely(byte[] xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setErrorHandler(null);
            return builder.parse(new ByteArrayInputStream(xml));
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new ExtractionException("XML file could not be read (invalid or unsafe XML)");
        }
    }

    private static String text(XPath x, Document doc, String path) {
        try {
            String value = x.evaluate("normalize-space((" + path + ")[1])", doc);
            return value == null || value.isBlank() ? null : value;
        } catch (XPathExpressionException e) {
            throw new IllegalStateException("Invalid XPath: " + path, e);
        }
    }

    private static String first(String a, String b) {
        return a != null ? a : b;
    }

    private static BigDecimal amount(String value) {
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalDate isoDate(String value) {
        try {
            return value == null ? null : LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static LocalDate ciiDate(String value) {
        try {
            return value == null ? null : LocalDate.parse(value, CII_DATE);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String iban(String value) {
        return value == null ? null : value.replaceAll("\\s", "").toUpperCase(Locale.ROOT);
    }

    /** True if the bytes look like XML (optionally after a UTF-8 BOM and whitespace). */
    public static boolean looksLikeXml(byte[] content) {
        String head = new String(content, 0, Math.min(content.length, 64), StandardCharsets.UTF_8)
                .replace("﻿", "").stripLeading();
        return head.startsWith("<?xml") || head.startsWith("<");
    }
}
