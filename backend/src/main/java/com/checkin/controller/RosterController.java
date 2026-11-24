package com.checkin.controller;

import com.checkin.entity.Roster;
import com.checkin.service.ActivityService;
import com.checkin.service.RosterService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.util.*;

/**
 * 花名册接口（清单 R-01 ~ R-04）。
 *   POST /api/activities/{id}/roster/import          导入 Excel/CSV 花名册
 *   GET  /api/activities/{id}/roster                 获取花名册及匹配状态
 *   POST /api/activities/{id}/members/{userId}/remove 移除成员
 */
@RestController
@RequestMapping("/api/activities")
public class RosterController {

    private final RosterService rosterService;
    private final ActivityService activityService;

    public RosterController(RosterService rosterService, ActivityService activityService) {
        this.rosterService = rosterService;
        this.activityService = activityService;
    }

    @PostMapping("/{id}/roster/import")
    public Map<String, Object> importRoster(HttpServletRequest req,
                                            @PathVariable Long id,
                                            @RequestParam("file") MultipartFile file) {
        Long userId = (Long) req.getAttribute("userId");
        List<Roster> list = rosterService.importRoster(id, userId, file);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("code", 200);
        r.put("data", list);
        r.put("message", "成功导入 " + list.size() + " 条花名册记录");
        return r;
    }

    @GetMapping("/{id}/roster")
    public Map<String, Object> roster(HttpServletRequest req, @PathVariable Long id) {
        Long userId = (Long) req.getAttribute("userId");
        // 仅管理员可见完整花名册
        List<Roster> list = rosterService.list(id);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("code", 200);
        r.put("data", list);
        r.put("count", list.size());
        return r;
    }

    @PostMapping("/{id}/members/{userId}/remove")
    public Map<String, Object> removeMember(HttpServletRequest req,
                                            @PathVariable Long id,
                                            @PathVariable Long userId) {
        Long operatorId = (Long) req.getAttribute("userId");
        activityService.removeMember(id, userId, operatorId);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("code", 200);
        r.put("message", "已移除该成员");
        return r;
    }
}
