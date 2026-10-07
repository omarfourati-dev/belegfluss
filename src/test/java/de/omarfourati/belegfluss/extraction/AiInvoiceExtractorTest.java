package de.omarfourati.belegfluss.extraction;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiInvoiceExtractorTest {

    @Test
    void mapsModelJsonToRecordAndSendsInvoiceText() {
        AtomicReference<Prompt> sent = new AtomicReference<>();
        ChatModel model = prompt -> {
            sent.set(prompt);
            return reply("""
                    {"supplierName":"Muster Buerobedarf GmbH","invoiceNumber":"RE-2026-0042",
                     "invoiceDate":"2026-10-01","dueDate":null,"netAmount":100.00,"vatAmount":19.00,
                     "grossAmount":119.00,"currency":"EUR","iban":"DE89370400440532013000"}
                    """);
        };

        ExtractedInvoice result = new AiInvoiceExtractor(ChatClient.builder(model)).extract("Rechnung RE-2026-0042");

        assertThat(result.supplierName()).isEqualTo("Muster Buerobedarf GmbH");
        assertThat(result.invoiceDate()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(result.grossAmount()).isEqualByComparingTo(new BigDecimal("119.00"));
        assertThat(result.dueDate()).isNull();
        assertThat(sent.get().getContents()).contains("Rechnung RE-2026-0042").contains("Never guess");
    }

    @Test
    void scannedPagesAreSentAsImages() {
        AtomicReference<Prompt> sent = new AtomicReference<>();
        ChatModel model = prompt -> {
            sent.set(prompt);
            return reply("{\"supplierName\":\"Scan GmbH\",\"invoiceNumber\":\"S-1\",\"grossAmount\":10.0}");
        };

        ExtractedInvoice result = new AiInvoiceExtractor(ChatClient.builder(model))
                .extractFromImages(List.of(new byte[]{1, 2, 3}, new byte[]{4, 5}));

        assertThat(result.supplierName()).isEqualTo("Scan GmbH");
        var userMessage = (org.springframework.ai.chat.messages.UserMessage) sent.get().getInstructions().stream()
                .filter(m -> m instanceof org.springframework.ai.chat.messages.UserMessage).findFirst().orElseThrow();
        assertThat(userMessage.getMedia()).hasSize(2)
                .allSatisfy(m -> assertThat(m.getMimeType().toString()).isEqualTo("image/png"));
    }

    @Test
    void providerErrorBodyIsNeverExposed() {
        ChatModel failing = prompt -> {
            throw new IllegalStateException(
                    "HTTP 401 - {\"error\":{\"message\":\"Incorrect API key provided: sk-abc***xyz\"}}");
        };

        assertThatThrownBy(() -> new AiInvoiceExtractor(ChatClient.builder(failing)).extract("text"))
                .isInstanceOf(ExtractionException.class)
                .hasMessage("AI provider rejected the API key (HTTP 401)")
                .message().doesNotContain("sk-");
    }

    @Test
    void mapsOtherProviderErrorsToGenericMessages() {
        assertThat(AiInvoiceExtractor.safeMessage(new IllegalStateException("HTTP 429 - slow down")))
                .isEqualTo("AI provider rate limit reached (HTTP 429)");
        assertThat(AiInvoiceExtractor.safeMessage(new IllegalStateException("HTTP 503 - body")))
                .isEqualTo("AI provider request failed (HTTP 503)");
        assertThat(AiInvoiceExtractor.safeMessage(new IllegalStateException("connection reset")))
                .isEqualTo("AI provider request failed");
    }

    private static ChatResponse reply(String json) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(json))));
    }
}
