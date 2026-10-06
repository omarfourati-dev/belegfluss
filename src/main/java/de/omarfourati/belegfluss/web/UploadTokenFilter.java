package de.omarfourati.belegfluss.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Interim protection until user login (Spring Security) is in place: when
 * {@code belegfluss.upload-token} is set, uploads need a matching
 * {@code X-Upload-Token} header. Read endpoints stay public for the demo.
 * Without a configured token (local development) uploads are open.
 */
@Component
public class UploadTokenFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Upload-Token";

    private final byte[] expectedToken;

    public UploadTokenFilter(@Value("${belegfluss.upload-token:}") String uploadToken) {
        this.expectedToken = uploadToken.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return expectedToken.length == 0
                || !"POST".equals(request.getMethod())
                || !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String provided = request.getHeader(HEADER);
        if (provided != null
                && MessageDigest.isEqual(expectedToken, provided.getBytes(StandardCharsets.UTF_8))) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("""
                {"type":"about:blank","title":"Unauthorized","status":401,\
                "detail":"Uploads are disabled in the public demo"}""");
    }
}
