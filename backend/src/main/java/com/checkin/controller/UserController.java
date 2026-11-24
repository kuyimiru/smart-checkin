package com.checkin.controller;

import com.checkin.entity.User;
import com.checkin.service.UserService;
import com.checkin.service.UserStatsService;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletRequest;
import java.util.*;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService userService;
    private final UserStatsService userStatsService;
    public UserController(UserService userService, UserStatsService userStatsService) {
        this.userService = userService;
        this.userStatsService = userStatsService;
    }

    @PostMapping("/profile")
    public Map<String, Object> updateProfile(HttpServletRequest req, @RequestBody User user) {
        Long userId = (Long) req.getAttribute("userId");
        userService.updateProfile(userId, user);
        Map<String, Object> r = new LinkedHashMap<>(); r.put("code", 200); r.put("message", "个人信息更新成功"); return r;
    }

    @GetMapping("/profile")
    public Map<String, Object> getProfile(HttpServletRequest req) {
        Long userId = (Long) req.getAttribute("userId");
        User user = userService.getProfile(userId);
        Map<String, Object> r = new LinkedHashMap<>(); r.put("code", 200); r.put("data", user); return r;
    }

    @PutMapping("/profile")
    public Map<String, Object> updateProfileV2(HttpServletRequest req, @RequestBody User user) {
        Long userId = (Long) req.getAttribute("userId");
        userService.updateProfile(userId, user);
        Map<String, Object> r = new LinkedHashMap<>(); r.put("code", 200); r.put("message", "个人信息更新成功"); return r;
    }

    /** 个人统计（"我的"页面）——v1 前端调用此接口但后端缺失，导致统计永远为 0 */
    @GetMapping("/stats")
    public Map<String, Object> stats(HttpServletRequest req) {
        Long userId = (Long) req.getAttribute("userId");
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("code", 200);
        r.put("data", userStatsService.stats(userId));
        return r;
    }
}
