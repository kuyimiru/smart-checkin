package com.checkin.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.checkin.entity.*;
import com.checkin.mapper.*;
import com.checkin.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 定时任务（清单 N-01 未打卡自动提醒 / SYS-02 定时任务调度 / A-08 到期自动归档）。
 *
 * v1 在启动类上开了 @EnableScheduling，但全项目没有任何 @Scheduled 方法，定时能力形同虚设。
 */
@Component
public class ScheduledTasks {

    private static final Logger log = LoggerFactory.getLogger(ScheduledTasks.class);
    private static final Pattern NUMBER = Pattern.compile("\\d+");

    private final ActivityMapper activityMapper;
    private final ActivityMemberMapper memberMapper;
    private final ActivityManagerMapper managerMapper;
    private final CheckInMapper checkInMapper;
    private final LeaveMapper leaveMapper;
    private final NotificationService notificationService;

    public ScheduledTasks(ActivityMapper activityMapper,
                          ActivityMemberMapper memberMapper,
                          ActivityManagerMapper managerMapper,
                          CheckInMapper checkInMapper,
                          LeaveMapper leaveMapper,
                          NotificationService notificationService) {
        this.activityMapper = activityMapper;
        this.memberMapper = memberMapper;
        this.managerMapper = managerMapper;
        this.checkInMapper = checkInMapper;
        this.leaveMapper = leaveMapper;
        this.notificationService = notificationService;
    }

    /**
     * N-01：每 10 分钟检查一次，凡是"今天该打卡 + 打卡时间已结束 + 尚未打卡 + 未请假"的人，发未打卡提醒。
     * 为避免重复轰炸，只在结束时间后的 2 小时窗口内提醒。
     */
    @Scheduled(cron = "0 */10 * * * ?")
    public void remindUnchecked() {
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();

        List<Activity> activities = activityMapper.selectList(new LambdaQueryWrapper<Activity>()
                .eq(Activity::getStatus, "active")
                .le(Activity::getStartDate, today)
                .ge(Activity::getEndDate, today));

        for (Activity a : activities) {
            if (a.getCheckInEndTime() == null) { continue; }
            if (now.isBefore(a.getCheckInEndTime())) { continue; }                        // 还没到结束时间
            if (now.isAfter(a.getCheckInEndTime().plusHours(2))) { continue; }            // 超过提醒窗口
            if (!isCheckInDay(a, today)) { continue; }

            Set<Long> checked = new HashSet<>();
            for (CheckIn c : checkInMapper.selectList(new LambdaQueryWrapper<CheckIn>()
                    .eq(CheckIn::getActivityId, a.getId())
                    .eq(CheckIn::getCheckInDate, today))) {
                checked.add(c.getUserId());
            }

            Set<Long> excluded = excludedUserIds(a);
            for (ActivityMember m : memberMapper.selectList(new LambdaQueryWrapper<ActivityMember>()
                    .eq(ActivityMember::getActivityId, a.getId())
                    .eq(ActivityMember::getStatus, "active"))) {
                Long uid = m.getUserId();
                if (uid == null || excluded.contains(uid) || checked.contains(uid)) { continue; }
                if (hasApprovedLeave(uid, a.getId(), today)) { continue; }
                try {
                    notificationService.notifyUnchecked(uid, a.getId(), a.getName());
                } catch (Exception e) {
                    log.warn("未打卡提醒发送失败 userId={} activityId={}: {}", uid, a.getId(), e.getMessage());
                }
            }
        }
    }

    /**
     * 启动时先补跑一次归档。
     *
     * 这条是为了解决"服务器不常开"的现实情况：原来只在凌晨 1 点归档，
     * 如果服务白天才启动，过期活动会一直停留在 active 状态。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void archiveOnStartup() {
        try {
            archiveExpiredActivities();
        } catch (Exception e) {
            log.warn("启动时归档过期活动失败: {}", e.getMessage());
        }
    }

    /** A-08：每小时检查一次，把已过结束日期的进行中活动自动归档（原来只在凌晨 1 点跑一次） */
    @Scheduled(cron = "0 5 * * * ?")
    public void archiveExpiredActivities() {
        LocalDate today = LocalDate.now();
        List<Activity> expired = activityMapper.selectList(new LambdaQueryWrapper<Activity>()
                .eq(Activity::getStatus, "active")
                .lt(Activity::getEndDate, today));
        for (Activity a : expired) {
            a.setStatus("archived");
            activityMapper.updateById(a);
            log.info("活动已到期自动归档: id={} name={}", a.getId(), a.getName());
        }
        if (!expired.isEmpty()) {
            log.info("本次共归档 {} 个到期活动", expired.size());
        }
    }

    // ─────────────────────── 内部工具 ───────────────────────

    /** 发起人与负责人不计入被提醒人群 */
    private Set<Long> excludedUserIds(Activity a) {
        Set<Long> excluded = new HashSet<>();
        if (a.getCreatorId() != null) { excluded.add(a.getCreatorId()); }
        for (ActivityManager m : managerMapper.selectList(new LambdaQueryWrapper<ActivityManager>()
                .eq(ActivityManager::getActivityId, a.getId()))) {
            if (m.getUserId() != null) { excluded.add(m.getUserId()); }
        }
        return excluded;
    }

    private boolean isCheckInDay(Activity a, LocalDate date) {
        if (!"custom".equals(a.getFrequencyType())) { return true; }
        String json = a.getFrequencyDays();
        if (json == null || json.trim().isEmpty()) { return true; }
        int iso = date.getDayOfWeek().getValue();
        Matcher m = NUMBER.matcher(json);
        while (m.find()) {
            if (Integer.parseInt(m.group()) == iso) { return true; }
        }
        return false;
    }

    private boolean hasApprovedLeave(Long userId, Long activityId, LocalDate date) {
        Long c = leaveMapper.selectCount(new LambdaQueryWrapper<Leave>()
                .eq(Leave::getUserId, userId)
                .eq(Leave::getActivityId, activityId)
                .eq(Leave::getStatus, "approved")
                .le(Leave::getStartDate, date)
                .ge(Leave::getEndDate, date));
        return c != null && c > 0;
    }
}
