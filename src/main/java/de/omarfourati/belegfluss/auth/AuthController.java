package de.omarfourati.belegfluss.auth;

import de.omarfourati.belegfluss.user.AppUser;
import de.omarfourati.belegfluss.user.UserService;
import io.micrometer.core.instrument.MeterRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication")
public class AuthController {

    private final UserService userService;
    private final TokenService tokenService;
    private final LoginRateLimiter rateLimiter;
    private final MeterRegistry meters;

    public AuthController(UserService userService, TokenService tokenService, LoginRateLimiter rateLimiter,
                          MeterRegistry meters) {
        this.meters = meters;
        this.userService = userService;
        this.tokenService = tokenService;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/login")
    @Operation(summary = "Log in and receive a JWT",
            description = "Demo (read-only): demo@belegfluss.app / demo-belegfluss. Paste the token via \"Authorize\". "
                    + "After 5 failed attempts per account (20 per IP) logins pause for 15 minutes.")
    @SecurityRequirement(name = "")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        // behind Caddy, forward-headers-strategy turns X-Forwarded-For into the remote address
        String ip = http.getRemoteAddr();
        try {
            rateLimiter.checkAllowed(ip, request.email());
        } catch (TooManyLoginAttemptsException e) {
            countLogin("throttled");
            throw e;
        }
        Optional<AppUser> user = userService.authenticate(request.email(), request.password());
        if (user.isEmpty()) {
            rateLimiter.recordFailure(ip, request.email());
            countLogin("failed");
            throw new InvalidCredentialsException();
        }
        rateLimiter.recordSuccess(ip, request.email());
        countLogin("success");
        TokenService.IssuedToken token = tokenService.issue(user.get());
        return new LoginResponse(token.value(), "Bearer", token.expiresAt(),
                new Me(user.get().getEmail(), user.get().getDisplayName(), List.of(user.get().getRole().name())));
    }

    /** belegfluss_logins_total{outcome}: a jump in "failed" or "throttled" means someone is guessing passwords. */
    private void countLogin(String outcome) {
        meters.counter("belegfluss.logins", "outcome", outcome).increment();
    }

    @GetMapping("/me")
    @Operation(summary = "The logged-in user")
    public Me me(@AuthenticationPrincipal Jwt jwt) {
        return new Me(jwt.getClaimAsString("email"), jwt.getClaimAsString("name"), jwt.getClaimAsStringList("roles"));
    }

    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Change your own password", description = "Not possible for the public demo account.")
    public void changePassword(@Valid @RequestBody ChangePasswordRequest request, @AuthenticationPrincipal Jwt jwt) {
        userService.changePassword(CurrentUser.from(jwt).id(), request.currentPassword(), request.newPassword());
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank @Size(max = 200) String password) {
    }

    public record LoginResponse(String accessToken, String tokenType, Instant expiresAt, Me user) {
    }

    public record Me(String email, String displayName, List<String> roles) {
    }

    public record ChangePasswordRequest(
            @NotBlank @Size(max = 200) String currentPassword,
            @NotBlank @Size(min = 12, max = 200) String newPassword) {
    }
}
