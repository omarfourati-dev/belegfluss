package de.omarfourati.belegfluss.auth;

import de.omarfourati.belegfluss.user.AppUser;
import de.omarfourati.belegfluss.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication")
public class AuthController {

    private final UserService userService;
    private final TokenService tokenService;

    public AuthController(UserService userService, TokenService tokenService) {
        this.userService = userService;
        this.tokenService = tokenService;
    }

    @PostMapping("/login")
    @Operation(summary = "Log in and receive a JWT",
            description = "Demo (read-only): demo@belegfluss.app / demo-belegfluss. Paste the token via \"Authorize\".")
    @SecurityRequirement(name = "")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        AppUser user = userService.authenticate(request.email(), request.password())
                .orElseThrow(InvalidCredentialsException::new);
        TokenService.IssuedToken token = tokenService.issue(user);
        return new LoginResponse(token.value(), "Bearer", token.expiresAt(),
                new Me(user.getEmail(), user.getDisplayName(), List.of(user.getRole().name())));
    }

    @GetMapping("/me")
    @Operation(summary = "The logged-in user")
    public Me me(@AuthenticationPrincipal Jwt jwt) {
        return new Me(jwt.getClaimAsString("email"), jwt.getClaimAsString("name"), jwt.getClaimAsStringList("roles"));
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank @Size(max = 200) String password) {
    }

    public record LoginResponse(String accessToken, String tokenType, Instant expiresAt, Me user) {
    }

    public record Me(String email, String displayName, List<String> roles) {
    }
}
