package com.checkin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.checkin.common.exception.BusinessException;
import com.checkin.entity.Activity;
import com.checkin.entity.ActivityManager;
import com.checkin.entity.CheckIn;
import com.checkin.entity.User;
import com.checkin.mapper.ActivityManagerMapper;
import com.checkin.mapper.ActivityMapper;
import com.checkin.mapper.CheckInMapper;
import com.checkin.mapper.UserMapper;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final CheckInMapper checkInMapper;
    private final ActivityMapper activityMapper;
    private final ActivityManagerMapper managerMapper;
    private final UserMapper userMapper;

    public AdminController(CheckInMapper checkInMapper, ActivityMapper activityMapper,
                           ActivityManagerMapper managerMapper, UserMapper userMapper) {
        this.checkInMapper = checkInMapper;
        this.activityMapper = activityMapper;
        this.managerMapper = managerMapper;
        this.userMapper = userMapper;
    }

    /**
     * 管理员查询某活动的全部打卡记录（含照片URL），支持按用户/日期/状态筛选
     * GET /api/admin/check-ins?activityId=1&userId=2&startDate=2026-01-01&endDate=2026-01-31&status=late
     */
    @GetMapping("/check-ins")
    public Map<String, Object> listCheckIns(HttpServletRequest req,
            @RequestParam Long activityId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String status) {

        Long operatorId = (Long) req.getAttribute("userId");

        Activity activity = activityMapper.selectById(activityId);
        if (activity == null) { throw BusinessException.notFound("活动不存在"); }
        requireAdmin(activity, operatorId);

        LambdaQueryWrapper<CheckIn> w = new LambdaQueryWrapper<CheckIn>()
                .eq(CheckIn::getActivityId, activityId)
                .orderByDesc(CheckIn::getCheckInDate)
                .orderByDesc(CheckIn::getCheckInTime);
        if (userId != null) { w.eq(CheckIn::getUserId, userId); }
        if (status != null && !status.isEmpty()) { w.eq(CheckIn::getStatus, status); }
        if (startDate != null) { w.ge(CheckIn::getCheckInDate, startDate); }
        if (endDate != null) { w.le(CheckIn::getCheckInDate, endDate); }

        List<CheckIn> records = checkInMapper.selectList(w);
        List<Map<String, Object>> list = new ArrayList<>();
        for (CheckIn c : records) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("userId", c.getUserId());
            m.put("realName", resolveRealName(c.getUserId()));
            m.put("checkInDate", c.getCheckInDate());
            m.put("checkInTime", c.getCheckInTime());
            m.put("status", c.getStatus());
            m.put("type", c.getType());
            m.put("locationLat", c.getLocationLat());
            m.put("locationLng", c.getLocationLng());
            m.put("faceImageUrl", c.getFaceImageUrl());
            list.add(m);
        }

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("code", 200);
        r.put("data", list);
        r.put("count", list.size());
        return r;
    }

    private void requireAdmin(Activity activity, Long userId) {
        if (activity.getCreatorId() != null && activity.getCreatorId().equals(userId)) { return; }
        Long c = managerMapper.selectCount(new LambdaQueryWrapper<ActivityManager>()
                .eq(ActivityManager::getActivityId, activity.getId())
                .eq(ActivityManager::getUserId, userId));
        if (c == null || c == 0) { throw BusinessException.forbidden("无权限查看该活动记录"); }
    }

    private String resolveRealName(Long userId) {
        if (userId == null) { return null; }
        User u = userMapper.selectById(userId);
        return u == null ? null : u.getRealName();
    }
}
