package de.omarfourati.belegfluss.extraction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.content.Media;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;

import java.util.List;
import java.util.function.Supplier;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Uses Spring AI structured output: the model answers in JSON that matches
 * {@link ExtractedInvoice}, and Spring AI maps it to the record.
 */
@Component
public class AiInvoiceExtractor implements InvoiceExtractor {

    private static final Logger log = LoggerFactory.getLogger(AiInvoiceExtractor.class);

    private static final String SYSTEM_PROMPT = """
            You extract data from supplier invoices for an accounts payable system.
            Rules:
            - Only use information that is printed on the invoice. Never guess.
            - Use null for every field you cannot find.
            - Amounts are decimal numbers with a dot as separator, without currency symbols.
            - German invoices write 1.234,56 - convert this to 1234.56.
            """;

    private static final Pattern HTTP_STATUS = Pattern.compile("HTTP (\\d{3})");

    private final ChatClient chatClient;

    public AiInvoiceExtractor(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.defaultSystem(SYSTEM_PROMPT).build();
    }

    @Override
    public ExtractedInvoice extract(String invoiceText) {
        return call(() -> chatClient.prompt()
                .user(u -> u.text("Extract the invoice fields from this text:\n\n{text}")
                        .param("text", invoiceText))
                .call()
                .entity(ExtractedInvoice.class));
    }

    @Override
    public ExtractedInvoice extractFromImages(List<byte[]> pngPages) {
        Media[] pages = pngPages.stream()
                .map(png -> new Media(MimeTypeUtils.IMAGE_PNG, new ByteArrayResource(png)))
                .toArray(Media[]::new);
        return call(() -> chatClient.prompt()
                .user(u -> u.text("This invoice is a scan. Extract the invoice fields from these page images.")
                        .media(pages))
                .call()
                .entity(ExtractedInvoice.class));
    }

    private ExtractedInvoice call(Supplier<ExtractedInvoice> request) {
        try {
            ExtractedInvoice result = request.get();
            if (result == null) {
                throw new ExtractionException("Model returned no result");
            }
            return result;
        } catch (ExtractionException e) {
            throw e;
        } catch (RuntimeException e) {
            String safeMessage = safeMessage(e);
            // Provider error bodies can echo parts of the API key: never store or log them verbatim.
            log.warn("AI extraction failed: {} ({})", safeMessage, e.getClass().getSimpleName());
            throw new ExtractionException(safeMessage, e);
        }
    }

    /** Maps provider errors to a fixed, public-safe message without the provider's response body. */
    static String safeMessage(RuntimeException e) {
        Matcher m = HTTP_STATUS.matcher(String.valueOf(e.getMessage()));
        if (!m.find()) {
            return "AI provider request failed";
        }
        return switch (m.group(1)) {
            case "401", "403" -> "AI provider rejected the API key (HTTP " + m.group(1) + ")";
            case "429" -> "AI provider rate limit reached (HTTP 429)";
            default -> "AI provider request failed (HTTP " + m.group(1) + ")";
        };
    }
}
