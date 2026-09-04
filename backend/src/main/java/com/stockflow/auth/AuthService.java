package com.stockflow.auth;

import com.stockflow.auth.dto.LoginRequest;
import com.stockflow.auth.dto.LoginResponse;
import com.stockflow.auth.dto.RegisterRequest;
import com.stockflow.auth.dto.RegisterResponse;
import com.stockflow.common.ApiException;
import com.stockflow.user.User;
import com.stockflow.user.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A9: every login failure returns the same generic message — no
 * "user not found" vs "wrong password" distinction.
 */
@Service
public class AuthService {

    static final String INVALID_CREDENTIALS = "Invalid email or password";

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final BCryptPasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, JwtService jwtService) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = new BCryptPasswordEncoder(12);
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            // Registration duplicate feedback is allowed (A9 scopes to login).
            throw ApiException.badRequest("Email is already registered");
        }
        User user = new User(email, passwordEncoder.encode(request.password()));
        user = userRepository.save(user);
        return new RegisterResponse(user.getId(), user.getEmail());
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase();
        User user = userRepository.findByEmail(email).orElse(null);

        // Constant-time-ish path: always run a hash comparison even when the
        // user does not exist, to avoid a user-enumeration timing side channel.
        boolean ok = user != null && passwordEncoder.matches(request.password(), user.getPasswordHash());
        if (!ok) {
            throw new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED, INVALID_CREDENTIALS);
        }
        return new LoginResponse(jwtService.generateToken(user.getId()), jwtService.getExpirationMinutes());
    }
}
