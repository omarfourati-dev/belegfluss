package de.omarfourati.belegfluss.user;

/**
 * Roles are hierarchical (see {@code SecurityConfig#roleHierarchy}):
 * ADMIN > ACCOUNTANT > APPROVER > EMPLOYEE > VIEWER.
 */
public enum Role {
    /** Read-only access, used for the public demo account. */
    VIEWER,
    /** Uploads invoices. */
    EMPLOYEE,
    /** Approves or rejects extracted invoices (four-eyes principle). */
    APPROVER,
    /** Books approved invoices. */
    ACCOUNTANT,
    /** Manages users. */
    ADMIN
}
