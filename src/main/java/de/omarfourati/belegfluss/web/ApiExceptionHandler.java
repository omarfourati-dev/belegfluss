package de.omarfourati.belegfluss.web;

import de.omarfourati.belegfluss.auth.InvalidCredentialsException;
import de.omarfourati.belegfluss.invoice.FourEyesViolationException;
import de.omarfourati.belegfluss.invoice.InvalidInvoiceStateException;
import de.omarfourati.belegfluss.invoice.InvalidUploadException;
import de.omarfourati.belegfluss.invoice.InvoiceNotFoundException;
import de.omarfourati.belegfluss.invoice.WarningsNotAcknowledgedException;
import de.omarfourati.belegfluss.user.EmailAlreadyUsedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/** Maps domain errors to RFC 9457 problem details. */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(InvoiceNotFoundException.class)
    ProblemDetail notFound(InvoiceNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Invoice not found", e.getMessage());
    }

    @ExceptionHandler(InvalidUploadException.class)
    ProblemDetail invalidUpload(InvalidUploadException e) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid upload", e.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail tooLarge(MaxUploadSizeExceededException e) {
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, "File too large", "The maximum file size is 10 MB");
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ProblemDetail invalidCredentials(InvalidCredentialsException e) {
        return problem(HttpStatus.UNAUTHORIZED, "Login failed", e.getMessage());
    }

    @ExceptionHandler(InvalidInvoiceStateException.class)
    ProblemDetail invalidState(InvalidInvoiceStateException e) {
        return problem(HttpStatus.CONFLICT, "Invalid status", e.getMessage());
    }

    @ExceptionHandler(FourEyesViolationException.class)
    ProblemDetail fourEyes(FourEyesViolationException e) {
        return problem(HttpStatus.FORBIDDEN, "Four-eyes principle", e.getMessage());
    }

    @ExceptionHandler(WarningsNotAcknowledgedException.class)
    ProblemDetail warningsNotAcknowledged(WarningsNotAcknowledgedException e) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Comment required", e.getMessage());
    }

    @ExceptionHandler(EmailAlreadyUsedException.class)
    ProblemDetail emailUsed(EmailAlreadyUsedException e) {
        return problem(HttpStatus.CONFLICT, "E-mail already used", e.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
