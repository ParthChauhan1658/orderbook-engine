package com.example.orderbook.api;

import com.example.orderbook.persistence.UserEntity;
import com.example.orderbook.persistence.UserRepository;
import com.example.orderbook.security.JwtService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/auth")
public class AuthController {

    public record AuthRequest(@NotBlank String username, @NotBlank @Size(min = 6) String password) {}
    public record TokenResponse(String token) {}

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public AuthController(UserRepository users, PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public TokenResponse register(@Valid @RequestBody AuthRequest req) {
        if (users.findByUsername(req.username()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "username taken");
        }
        users.save(new UserEntity(req.username(), encoder.encode(req.password())));
        return new TokenResponse(jwt.create(req.username()));
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody AuthRequest req) {
        UserEntity u = users.findByUsername(req.username())
                .filter(x -> encoder.matches(req.password(), x.getPasswordHash()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "bad credentials"));
        return new TokenResponse(jwt.create(u.getUsername()));
    }
}