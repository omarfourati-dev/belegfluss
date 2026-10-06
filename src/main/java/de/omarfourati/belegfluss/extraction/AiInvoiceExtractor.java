package de.omarfourati.belegfluss.extraction;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

/**
 * Uses Spring AI structured output: the model answers in JSON that matches
 * {@link ExtractedInvoice}, and Spring AI maps it to the record.
 */
@Component
public class AiInvoiceExtractor implements InvoiceExtractor {

    private static final String SYSTEM_PROMPT = """
            You extract data from supplier invoices for an accounts payable system.
            Rules:
            - Only use information that is printed on the invoice. Never guess.
            - Use null for every field you cannot find.
            - Amounts are decimal numbers with a dot as separator, without currency symbols.
            - German invoices write 1.234,56 - convert this to 1234.56.
            """;

    private final ChatClient chatClient;

    public AiInvoiceExtractor(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.defaultSystem(SYSTEM_PROMPT).build();
    }

    @Override
    public ExtractedInvoice extract(String invoiceText) {
        try {
            ExtractedInvoice result = chatClient.prompt()
                    .user(u -> u.text("Extract the invoice fields from this text:\n\n{text}")
                            .param("text", invoiceText))
                    .call()
                    .entity(ExtractedInvoice.class);
            if (result == null) {
                throw new ExtractionException("Model returned no result");
            }
            return result;
        } catch (ExtractionException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ExtractionException("AI extraction failed: " + e.getMessage(), e);
        }
    }
}
