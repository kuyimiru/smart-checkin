package com.checkin.controller;

import com.checkin.dto.request.LeaveRequest;
import com.checkin.entity.Leave;
import com.checkin.service.LeaveService;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.*;

/**
 * 请假接口（清单 L-01 / L-02）。
 * 对应接口清单：
 *   POST /api/leaves                  提交请假申请
 *   GET  /api/leaves                  获取我的请假记录
 *   GET  /api/leaves/pending          负责人获取待审批列表
 *   PUT  /api/leaves/{id}/approve     审批通过
 *   PUT  /api/leaves/{id}/reject      审批拒绝
 */
@RestController
@RequestMapping("/api/leaves")
public class LeaveController {

    private final LeaveService leaveService;

    public LeaveController(LeaveService leaveService) {
        this.leaveService = leaveService;
    }

    @PostMapping
    public Map<String, Object> submit(HttpServletRequest req, @Valid @RequestBody LeaveRequest body) {
        Long userId = (Long) req.getAttribute("userId");
        Leave leave = leaveService.submit(userId, body);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("code", 200);
        r.put("data", leave);
        r.put("message", "请假申请已提交");
        return r;
    }

    @GetMapping
    public Map<String, Object> myLeaves(HttpServletRequest req) {
        Long userId = (Long) req.getAttribute("userId");
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("code", 200);
        r.put("data", leaveService.getMyLeaves(userId));
        return r;
    }

    @GetMapping("/pending")
    public Map<String, Object> pending(HttpServletRequest req) {
        Long userId = (Long) req.getAttribute("userId");
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("code", 200);
        r.put("data", leaveService.getPendingList(userId));
        return r;
    }

    @PutMapping("/{id}/approve")
    public Map<String, Object> approve(HttpServletRequest req, @PathVariable Long id) {
        Long userId = (Long) req.getAttribute("userId");
        leaveService.approve(id, userId);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("code", 200);
        r.put("message", "审批通过");
        return r;
    }

    @PutMapping("/{id}/reject")
    public Map<String, Object> reject(HttpServletRequest req, @PathVariable Long id,
                                      @RequestBody(required = false) Map<String, Object> body) {
        Long userId = (Long) req.getAttribute("userId");
        String reason = body == null || body.get("reason") == null ? null : body.get("reason").toString();
        leaveService.reject(id, userId, reason);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("code", 200);
        r.put("message", "已拒绝");
        return r;
    }
}
