package de.omarfourati.belegfluss.invoice;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    List<Invoice> findAllByOrderByCreatedAtDesc();

    List<Invoice> findByStatusInOrderByInvoiceDateAscCreatedAtAsc(Collection<InvoiceStatus> statuses);

    @Query("""
            select count(i) > 0 from Invoice i
            where lower(i.supplierName) = lower(:supplier) and i.invoiceNumber = :number and i.id <> :id
              and i.status not in (de.omarfourati.belegfluss.invoice.InvoiceStatus.REJECTED,
                                   de.omarfourati.belegfluss.invoice.InvoiceStatus.FAILED)
            """)
    boolean existsDuplicate(@Param("supplier") String supplier, @Param("number") String number, @Param("id") UUID id);

    @Query("""
            select distinct i.iban from Invoice i
            where lower(i.supplierName) = lower(:supplier) and i.iban is not null and i.id <> :id
              and i.status not in (de.omarfourati.belegfluss.invoice.InvoiceStatus.REJECTED,
                                   de.omarfourati.belegfluss.invoice.InvoiceStatus.FAILED)
            """)
    List<String> findOtherIbansOfSupplier(@Param("supplier") String supplier, @Param("id") UUID id);
}
