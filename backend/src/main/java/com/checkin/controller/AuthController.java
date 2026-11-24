package com.checkin.controller;

import com.checkin.dto.request.LoginRequest;
import com.checkin.dto.response.LoginResponse;
import com.checkin.service.UserService;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserService userService;
    public AuthController(UserService userService) { this.userService = userService; }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginRequest req) {
        LoginResponse resp = userService.login(req);
        Map<String, Object> r = new LinkedHashMap<>(); r.put("code", 200); r.put("data", resp); return r;
    }
}
