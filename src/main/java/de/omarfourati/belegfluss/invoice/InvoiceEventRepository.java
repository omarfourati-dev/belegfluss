package de.omarfourati.belegfluss.invoice;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InvoiceEventRepository extends JpaRepository<InvoiceEvent, UUID> {

    List<InvoiceEvent> findByInvoiceIdOrderByCreatedAtAsc(UUID invoiceId);
}
