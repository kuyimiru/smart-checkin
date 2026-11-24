package com.checkin.service;

import com.checkin.entity.User;
import com.checkin.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 个人统计（"我的"页面用）。
 * v1 前端调用了 /api/users/stats 但后端没有这个接口，页面数字永远是 0。
 */
@Service
public class UserStatsService {

    private static final Pattern NUMBER = Pattern.compile("\\d+");
    private static final Logger log = LoggerFactory.getLogger(UserStatsService.class);

    private final UserMapper userMapper;
    private final com.checkin.mapper.CheckInMapper checkInMapper;
    private final com.checkin.mapper.ActivityMemberMapper memberMapper;

    public UserStatsService(UserMapper userMapper,
                            com.checkin.mapper.CheckInMapper checkInMapper,
                            com.checkin.mapper.ActivityMemberMapper memberMapper) {
        this.userMapper = userMapper;
        this.checkInMapper = checkInMapper;
        this.memberMapper = memberMapper;
    }

    public Map<String, Object> stats(Long userId) {
        Map<String, Object> out = new LinkedHashMap<>();

        Long total = checkInMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.checkin.entity.CheckIn>()
                        .eq(com.checkin.entity.CheckIn::getUserId, userId));
        out.put("totalCheckIns", total == null ? 0 : total.intValue());

        Long activities = memberMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.checkin.entity.ActivityMember>()
                        .eq(com.checkin.entity.ActivityMember::getUserId, userId)
                        .eq(com.checkin.entity.ActivityMember::getStatus, "active"));
        out.put("activityCount", activities == null ? 0 : activities.intValue());

        out.put("streak", calcStreak(userId));
        User u = userMapper.selectById(userId);
        out.put("realName", u == null ? null : u.getRealName());
        return out;
    }

    /** 连续打卡天数：从今天（或昨天）往前推，遇到没有打卡记录的日期即停止 */
    private int calcStreak(Long userId) {
        List<com.checkin.entity.CheckIn> list = checkInMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.checkin.entity.CheckIn>()
                        .eq(com.checkin.entity.CheckIn::getUserId, userId)
                        .orderByDesc(com.checkin.entity.CheckIn::getCheckInDate)
                        .last("limit 400"));
        if (list == null || list.isEmpty()) { return 0; }

        Set<java.time.LocalDate> days = new HashSet<>();
        for (com.checkin.entity.CheckIn c : list) { days.add(c.getCheckInDate()); }

        java.time.LocalDate cursor = java.time.LocalDate.now();
        if (!days.contains(cursor)) { cursor = cursor.minusDays(1); }  // 今天还没打卡不算断
        int streak = 0;
        while (days.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    /** 占位：解析模板 ID 之类的字符串里的数字（保留给后续扩展） */
    static int firstNumber(String s) {
        if (s == null) { return -1; }
        Matcher m = NUMBER.matcher(s);
        return m.find() ? Integer.parseInt(m.group()) : -1;
    }
}
