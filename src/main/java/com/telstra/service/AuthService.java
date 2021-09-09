package com.telstra.service;


import com.telstra.dto.*;
import com.telstra.model.User;
import com.telstra.repository.UserRepository;
import com.telstra.security.JwtSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.time.Instant;

@Service
public class AuthService {

    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    UserRepository userRepository;
    @Autowired
    AuthenticationManager authenticationManager;
    @Autowired
    JwtSource jwtSource;
    @Autowired
    RefreshTokenService refreshTokenService;

    @Transactional
    public String signUp(RegisterRequest registerRequest) {
        if (userRepository.existsByEmail(registerRequest.getEmail())) {
            return "Email already exists";
        }
        User user = new User();
        user.setEmail(registerRequest.getEmail());
        user.setCreated(Instant.now());
        user.setUsername(registerRequest.getUsername());
        user.setPassword(passwordEncoder.encode(registerRequest.getPassword()));
        user.setEnabled(true);
        userRepository.save(user);
        return "User Created Successfully";
    }

    @Transactional
    public SigninResponse signIn(SigninRequest signinRequest) {

        System.out.println("1");
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        signinRequest.getEmail(),
                        signinRequest.getPassword()
                )
        );

        System.out.println("2");
        SecurityContextHolder.getContext().setAuthentication(authentication);

        System.out.println("3");
        String token = jwtSource.generateToken(authentication);

        System.out.println("4");
        org.springframework.security.core.userdetails.User principal = (org.springframework.security.core.userdetails.User) authentication.getPrincipal();
        return new SigninResponse(principal.getUsername(), token,
                refreshTokenService.generateRefreshToken().getToken()
                , Instant.now().plusMillis(jwtSource.getJwtExpirationInMillis()).toString());

    }


    @Transactional
    public User getCurrentUser() {
        org.springframework.security.core.userdetails.User principal = (org.springframework.security.core.userdetails.User) SecurityContextHolder.
                getContext().getAuthentication().getPrincipal();
        return userRepository.findByEmail(principal.getUsername())
                .orElseThrow(() -> new RuntimeException("User name not found - " + principal.getUsername()));
    }

    public AuthenticationResponse refreshToken(RefreshTokenRequest refreshTokenRequest) {
        refreshTokenService.validateRefreshToken(refreshTokenRequest.getRefreshToken());
        String token = jwtSource.generateTokenWithUserName(refreshTokenRequest.getUsername());
        return AuthenticationResponse.builder()
                .authenticationToken(token)
                .refreshToken(refreshTokenRequest.getRefreshToken())
                .expiresAt(Instant.now().plusMillis(jwtSource.getJwtExpirationInMillis()))
                .username(refreshTokenRequest.getUsername())
                .build();
    }

}
