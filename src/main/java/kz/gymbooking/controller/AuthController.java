package kz.gymbooking.controller;

import jakarta.validation.Valid;
import kz.gymbooking.dto.LoginRequest;
import kz.gymbooking.dto.RefreshRequest;
import kz.gymbooking.dto.RegisterRequest;
import kz.gymbooking.error.ApiException;
import kz.gymbooking.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/auth", produces = "text/plain;charset=UTF-8")
public class AuthController {

    private static final String TOKENS = """
            accessToken: stub-access-token
            refreshToken: stub-refresh-token
            expiresIn: 900
            """;

    private final UserRepository users;

    public AuthController(UserRepository users) {
        this.users = users;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public String register(@Valid @RequestBody RegisterRequest req) {
        if (users.findByEmail(req.email()).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_TAKEN", "Email is already registered");
        }
        UserRepository.UserRecord user = users.create(req.fullName(), req.email(), req.phone(),
                req.password(), "CLIENT");
        return TOKENS + "user: #" + user.id() + " " + user.fullName() + ", " + user.email() + ", CLIENT\n";
    }

    @PostMapping("/login")
    public String login(@Valid @RequestBody LoginRequest req) {
        var user = users.findByEmail(req.email())
                .filter(record -> record.passwordHash() != null && record.passwordHash().equals(req.password()))
                .orElse(null);
        if (user == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Wrong email or password");
        }
        return TOKENS + "user: #" + user.id() + " " + user.fullName() + ", " + user.email() + ", " + user.role() + "\n";
    }

    @PostMapping("/refresh")
    public String refresh(@Valid @RequestBody RefreshRequest req) {
        return TOKENS;
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout() {
    }
}
