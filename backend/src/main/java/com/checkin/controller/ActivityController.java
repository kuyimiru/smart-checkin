package com.checkin.controller;

import com.checkin.dto.request.ActivityCreateRequest;
import com.checkin.entity.Activity;
import com.checkin.service.ActivityService;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.*;

@RestController
@RequestMapping("/api/activities")
public class ActivityController {
    private final ActivityService activityService;
    public ActivityController(ActivityService activityService) { this.activityService = activityService; }

    @GetMapping
    public Map<String, Object> list(HttpServletRequest req) {
        Long userId = (Long) req.getAttribute("userId");
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("code", 200);
        r.put("data", activityService.list(userId));
        return r;
    }

    @PostMapping
    public Map<String, Object> create(HttpServletRequest req, @Valid @RequestBody ActivityCreateRequest body) {
        Long userId = (Long) req.getAttribute("userId");
        Activity a = activityService.create(userId, body);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("code", 200);
        r.put("data", a);
        r.put("message", "创建成功");
        return r;
    }

    @GetMapping("/{id}")
    public Map<String, Object> getById(@PathVariable Long id) {
        Activity a = activityService.getById(id);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("code", 200);
        r.put("data", a);
        return r;
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(HttpServletRequest req, @PathVariable Long id,
                                      @RequestBody ActivityCreateRequest body) {
        Long userId = (Long) req.getAttribute("userId");
        Activity a = activityService.update(id, userId, body);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("code", 200);
        r.put("data", a);
        r.put("message", "更新成功");
        return r;
    }

    @PostMapping("/join")
    public Map<String, Object> join(HttpServletRequest req, @RequestBody Map<String, Object> body) {
        Long userId = (Long) req.getAttribute("userId");
        String inviteCode = body.get("inviteCode") == null ? null : body.get("inviteCode").toString();
        activityService.join(userId, inviteCode);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("code", 200);
        r.put("message", "加入成功");
        return r;
    }

    @PostMapping("/{id}/leave")
    public Map<String, Object> leave(HttpServletRequest req, @PathVariable Long id) {
        Long userId = (Long) req.getAttribute("userId");
        activityService.leave(id, userId);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("code", 200);
        r.put("message", "已退出活动");
        return r;
    }

    @PutMapping("/{id}/close")
    public Map<String, Object> close(HttpServletRequest req, @PathVariable Long id) {
        Long userId = (Long) req.getAttribute("userId");
        activityService.close(id, userId);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("code", 200);
        r.put("message", "活动已关闭");
        return r;
    }

    @PutMapping("/{id}/invite-code/disable")
    public Map<String, Object> disableInvite(HttpServletRequest req, @PathVariable Long id) {
        Long userId = (Long) req.getAttribute("userId");
        activityService.disableInviteCode(id, userId);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("code", 200);
        r.put("message", "邀请已关闭");
        return r;
    }

    @PutMapping("/{id}/invite-code/enable")
    public Map<String, Object> enableInvite(HttpServletRequest req, @PathVariable Long id) {
        Long userId = (Long) req.getAttribute("userId");
        activityService.enableInviteCode(id, userId);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("code", 200);
        r.put("message", "邀请已开启");
        return r;
    }
}
