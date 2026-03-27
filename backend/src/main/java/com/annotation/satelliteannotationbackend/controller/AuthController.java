package com.annotation.satelliteannotationbackend.controller;

import com.annotation.satelliteannotationbackend.dto.LoginRequest;
import com.annotation.satelliteannotationbackend.dto.LoginResponse;
import com.annotation.satelliteannotationbackend.dto.RegisterRequest;
import com.annotation.satelliteannotationbackend.entity.User;
import com.annotation.satelliteannotationbackend.repository.UserRepository;
import com.annotation.satelliteannotationbackend.security.JwtUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 认证控制器
 */
@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthController(AuthenticationManager authenticationManager,
                          UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          JwtUtil jwtUtil) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );

            User user = userRepository.findByUsername(request.getUsername()).orElseThrow();
            String token = jwtUtil.generateToken(user);
            return ResponseEntity.ok(new LoginResponse(token, user.getUsername(), user.getRole()));
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "用户名或密码错误");
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "用户名已存在");
            return ResponseEntity.badRequest().body(error);
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setEmail(request.getEmail());
        user.setRole("ROLE_USER");

        userRepository.save(user);

        Map<String, String> response = new HashMap<>();
        response.put("message", "注册成功");
        return ResponseEntity.ok(response);
    }
}
