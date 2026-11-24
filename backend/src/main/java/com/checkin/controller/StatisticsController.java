package com.checkin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.checkin.common.exception.BusinessException;
import com.checkin.common.utils.ExcelUtil;
import com.checkin.entity.*;
import com.checkin.mapper.*;
import com.checkin.service.ActivityService;
import com.checkin.service.PermissionService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.temporal.ChronoUnit;
import java.time.LocalDate;
import java.util.*;

/**
 * 统计 / 名单 / 日历 / 导出接口（清单 S-01 ~ S-04、E-01）。
 *
 * 对应接口清单：
 *   GET  /api/activities/{id}/statistics   活动统计面板
 *   GET  /api/activities/{id}/members      参与者名单（含异常用户标记）
 *   GET  /api/activities/{id}/managers     活动负责人列表（请假选审批人用）
 *   GET  /api/calendar                     个人打卡日历
 *   POST /api/activities/{id}/export       导出打卡数据 Excel
 */
@RestController
@RequestMapping("/api")
public class StatisticsController {

    private final ActivityMapper activityMapper;
    private final ActivityMemberMapper memberMapper;
    private final ActivityManagerMapper managerMapper;
    private final CheckInMapper checkInMapper;
    private final LeaveMapper leaveMapper;
    private final UserMapper userMapper;
    private final RosterMapper rosterMapper;
    private final PermissionService permissionService;
    private final ActivityService activityService;

    public StatisticsController(ActivityMapper activityMapper,
                                ActivityMemberMapper memberMapper,
                                ActivityManagerMapper managerMapper,
                                CheckInMapper checkInMapper,
                                LeaveMapper leaveMapper,
                                UserMapper userMapper,
                                RosterMapper rosterMapper,
                                PermissionService permissionService,
                                ActivityService activityService) {
        this.activityMapper = activityMapper;
        this.memberMapper = memberMapper;
        this.managerMapper = managerMapper;
        this.checkInMapper = checkInMapper;
        this.leaveMapper = leaveMapper;
        this.userMapper = userMapper;
        this.rosterMapper = rosterMapper;
        this.permissionService = permissionService;
        this.activityService = activityService;
    }

    // ═══════════════════════ S-01 活动统计面板 ═══════════════════════

    @GetMapping("/activities/{id}/statistics")
    public Map<String, Object> statistics(HttpServletRequest req, @PathVariable Long id) {
        Long userId = (Long) req.getAttribute("userId");
        Activity activity = requireActivity(id);
        // 参与者访问统计返回 403
        permissionService.requireAdmin(activity, userId);

        List<ActivityMember> participants = participantsOf(activity);
        LocalDate today = LocalDate.now();
        Set<Long> checkedUserIds = new HashSet<>();
        int normal = 0, late = 0, abnormal = 0;
        for (CheckIn c : checkInsOn(id, today)) {
            checkedUserIds.add(c.getUserId());
            switch (c.getStatus() == null ? "" : c.getStatus()) {
                case "normal": normal++; break;
                case "late": late++; break;
                case "abnormal": abnormal++; break;
                default: break;
            }
        }
        int leaveCount = 0;
        for (ActivityMember m : participants) {
            if (checkedUserIds.contains(m.getUserId())) { continue; }
            if (hasApprovedLeave(m.getUserId(), id, today)) { leaveCount++; }
        }
        int total = participants.size();
        int unchecked = total - normal - late - abnormal - leaveCount;
        if (unchecked < 0) { unchecked = 0; }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalMembers", total);
        data.put("checkedInToday", normal + late + abnormal);
        data.put("normalCount", normal);
        data.put("lateCount", late);
        data.put("abnormalCount", abnormal);
        data.put("leaveCount", leaveCount);
        data.put("uncheckedCount", unchecked);
        data.put("date", today.toString());

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("code", 200);
        r.put("data", data);
        return r;
    }

    // ═══════════════════════ S-02 参与者名单 ═══════════════════════

    @GetMapping("/activities/{id}/members")
    public Map<String, Object> members(HttpServletRequest req, @PathVariable Long id) {
        Long userId = (Long) req.getAttribute("userId");
        Activity activity = requireActivity(id);
        permissionService.requireAdmin(activity, userId);

        Map<Long, CheckIn> todayMap = new HashMap<>();
        LocalDate today = LocalDate.now();
        for (CheckIn c : checkInsOn(id, today)) { todayMap.put(c.getUserId(), c); }

        // 花名册匹配到的用户集合；花名册为空时不做异常标记
        Set<Long> matchedUserIds = new HashSet<>();
        List<Roster> roster = rosterMapper.selectList(new LambdaQueryWrapper<Roster>()
                .eq(Roster::getActivityId, id));
        boolean hasRoster = !roster.isEmpty();
        for (Roster ro : roster) {
            if (ro.getMatchedUserId() != null) { matchedUserIds.add(ro.getMatchedUserId()); }
        }

        List<Map<String, Object>> list = new ArrayList<>();
        for (ActivityMember m : participantsOf(activity)) {
            User u = userMapper.selectById(m.getUserId());
            String realName = u == null ? null
                    : (u.getRealName() != null && !u.getRealName().isEmpty() ? u.getRealName() : u.getNickName());

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("userId", m.getUserId());
            item.put("userName", realName == null ? ("用户" + m.getUserId()) : realName);
            item.put("phone", u == null ? null : u.getPhone());
            item.put("avatarUrl", u == null ? null : u.getAvatarUrl());
            item.put("inRoster", !hasRoster || matchedUserIds.contains(m.getUserId()));

            CheckIn c = todayMap.get(m.getUserId());
            String todayStatus;
            if (c != null) {
                todayStatus = c.getStatus();
            } else if (hasApprovedLeave(m.getUserId(), id, today)) {
                todayStatus = "leave";
            } else {
                todayStatus = "unchecked";
            }
            item.put("todayStatus", todayStatus);
            item.put("checkInTime", c == null || c.getCheckInTime() == null ? null : c.getCheckInTime().toString());
            list.add(item);
        }
        // 异常用户（不在花名册中的加入人员）排前面，便于管理员处理
        list.sort((a, b) -> Boolean.compare(Boolean.TRUE.equals(a.get("inRoster")),
                                            Boolean.TRUE.equals(b.get("inRoster"))));

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("code", 200);
        r.put("data", list);
        r.put("count", list.size());
        return r;
    }

    // ═══════════════════════ 活动负责人列表 ═══════════════════════

    @GetMapping("/activities/{id}/managers")
    public Map<String, Object> managers(@PathVariable Long id) {
        Activity activity = requireActivity(id);
        List<Map<String, Object>> list = new ArrayList<>();

        // 发起人
        User creator = userMapper.selectById(activity.getCreatorId());
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("userId", activity.getCreatorId());
        c.put("name", displayName(creator, "发起人"));
        c.put("role", "creator");
        list.add(c);

        for (ActivityManager m : activityService.listManagers(id)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("userId", m.getUserId());
            item.put("name", m.getName());
            item.put("phone", m.getPhone());
            item.put("role", m.getRole());
            item.put("bound", m.getUserId() != null);
            list.add(item);
        }

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("code", 200);
        r.put("data", list);
        return r;
    }

    // ═══════════════════════ S-03 个人打卡日历 ═══════════════════════

    @GetMapping("/calendar")
    public Map<String, Object> calendar(HttpServletRequest req,
            @RequestParam(required = false) Long activityId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        Long userId = (Long) req.getAttribute("userId");
        LocalDate now = LocalDate.now();
        int y = year == null ? now.getYear() : year;
        int m = month == null ? now.getMonthValue() : month;
        LocalDate from = LocalDate.of(y, m, 1);
        LocalDate to = from.withDayOfMonth(from.lengthOfMonth());

        // 只查自己的记录（权限隔离）
        LambdaQueryWrapper<CheckIn> w = new LambdaQueryWrapper<CheckIn>()
                .eq(CheckIn::getUserId, userId)
                .ge(CheckIn::getCheckInDate, from)
                .le(CheckIn::getCheckInDate, to)
                .orderByAsc(CheckIn::getCheckInDate);
        if (activityId != null) { w.eq(CheckIn::getActivityId, activityId); }

        Map<String, String> byDate = new LinkedHashMap<>();
        for (CheckIn c : checkInMapper.selectList(w)) {
            byDate.put(c.getCheckInDate().toString(), c.getStatus());
        }

        // 请假状态补齐（清单 S-03：日历要能显示灰色"请假"）
        LambdaQueryWrapper<Leave> lw = new LambdaQueryWrapper<Leave>()
                .eq(Leave::getUserId, userId)
                .eq(Leave::getStatus, "approved")
                .le(Leave::getStartDate, to)
                .ge(Leave::getEndDate, from);
        if (activityId != null) { lw.eq(Leave::getActivityId, activityId); }
        for (Leave l : leaveMapper.selectList(lw)) {
            LocalDate d = l.getStartDate().isBefore(from) ? from : l.getStartDate();
            LocalDate end = l.getEndDate().isAfter(to) ? to : l.getEndDate();
            while (!d.isAfter(end)) {
                byDate.putIfAbsent(d.toString(), "leave");
                d = d.plusDays(1);
            }
        }

        List<Map<String, Object>> list = new ArrayList<>();
        for (Map.Entry<String, String> e : byDate.entrySet()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", e.getKey());
            item.put("status", e.getValue());
            list.add(item);
        }
        list.sort(Comparator.comparing(a -> String.valueOf(a.get("date"))));

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("code", 200);
        r.put("data", list);
        r.put("year", y);
        r.put("month", m);
        return r;
    }

    // ═══════════════════════ E-01 导出打卡数据 ═══════════════════════

    @RequestMapping(value = "/activities/{id}/export", method = {RequestMethod.POST, RequestMethod.GET})
    public ResponseEntity<byte[]> export(HttpServletRequest req, @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) throws IOException {
        Long userId = (Long) req.getAttribute("userId");
        Activity activity = requireActivity(id);
        permissionService.requireAdmin(activity, userId);

        LocalDate from = startDate == null ? activity.getStartDate() : startDate;
        LocalDate to = endDate == null ? activity.getEndDate() : endDate;
        if (from == null) { from = LocalDate.now(); }
        if (to == null) { to = from; }
        if (to.isBefore(from)) { throw BusinessException.badRequest("结束日期不能早于开始日期"); }

        List<ActivityMember> participants = participantsOf(activity);

        // 日期列：区间超过 92 天时保留**最近**的 92 天。
        // 早期实现是从起始日往后截断，导致跨年长活动导出的报表里看不到最新打卡记录。
        final int MAX_DATE_COLUMNS = 92;
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_DATE_COLUMNS) {
            from = to.minusDays(MAX_DATE_COLUMNS - 1L);
        }
        List<LocalDate> dates = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) { dates.add(d); }

        // 打卡 & 请假索引
        Map<String, CheckIn> checkMap = new HashMap<>();
        LambdaQueryWrapper<CheckIn> cw = new LambdaQueryWrapper<CheckIn>()
                .eq(CheckIn::getActivityId, id)
                .ge(CheckIn::getCheckInDate, from)
                .le(CheckIn::getCheckInDate, to);
        for (CheckIn c : checkInMapper.selectList(cw)) {
            checkMap.put(c.getUserId() + "_" + c.getCheckInDate(), c);
        }
        Map<String, Boolean> leaveMap = new HashMap<>();
        LambdaQueryWrapper<Leave> lw = new LambdaQueryWrapper<Leave>()
                .eq(Leave::getActivityId, id)
                .eq(Leave::getStatus, "approved")
                .le(Leave::getStartDate, to)
                .ge(Leave::getEndDate, from);
        for (Leave l : leaveMapper.selectList(lw)) {
            LocalDate d = l.getStartDate().isBefore(from) ? from : l.getStartDate();
            LocalDate end = l.getEndDate().isAfter(to) ? to : l.getEndDate();
            while (!d.isAfter(end)) {
                leaveMap.put(l.getUserId() + "_" + d, Boolean.TRUE);
                d = d.plusDays(1);
            }
        }

        // 表头
        List<String> headerList = new ArrayList<>();
        headerList.add("姓名");
        headerList.add("手机号");
        for (LocalDate d : dates) { headerList.add(d.getMonthValue() + "/" + d.getDayOfMonth()); }
        headerList.add("正常");
        headerList.add("迟到");
        headerList.add("异常");
        headerList.add("请假");
        headerList.add("未打卡");
        String[] headers = headerList.toArray(new String[0]);

        // 数据行 + 汇总
        List<String[]> rows = new ArrayList<>();
        int sumNormal = 0, sumLate = 0, sumAbnormal = 0, sumLeave = 0, sumUnchecked = 0;
        for (ActivityMember m : participants) {
            User u = userMapper.selectById(m.getUserId());
            String name = displayName(u, "用户" + m.getUserId());
            int n = 0, lt = 0, ab = 0, lv = 0, un = 0;

            List<String> row = new ArrayList<>();
            row.add(name);
            row.add(u == null || u.getPhone() == null ? "" : u.getPhone());
            for (LocalDate d : dates) {
                CheckIn c = checkMap.get(m.getUserId() + "_" + d);
                if (c != null) {
                    switch (c.getStatus() == null ? "" : c.getStatus()) {
                        case "normal": row.add("正常"); n++; break;
                        case "late": row.add("迟到"); lt++; break;
                        case "abnormal": row.add("异常"); ab++; break;
                        default: row.add("已打卡"); break;
                    }
                } else if (leaveMap.containsKey(m.getUserId() + "_" + d)) {
                    row.add("请假"); lv++;
                } else {
                    row.add("—"); un++;
                }
            }
            row.add(String.valueOf(n));
            row.add(String.valueOf(lt));
            row.add(String.valueOf(ab));
            row.add(String.valueOf(lv));
            row.add(String.valueOf(un));
            rows.add(row.toArray(new String[0]));

            sumNormal += n; sumLate += lt; sumAbnormal += ab; sumLeave += lv; sumUnchecked += un;
        }

        // 汇总统计行
        List<String> sumRow = new ArrayList<>();
        sumRow.add("汇总");
        sumRow.add("");
        for (int i = 0; i < dates.size(); i++) { sumRow.add(""); }
        sumRow.add(String.valueOf(sumNormal));
        sumRow.add(String.valueOf(sumLate));
        sumRow.add(String.valueOf(sumAbnormal));
        sumRow.add(String.valueOf(sumLeave));
        sumRow.add(String.valueOf(sumUnchecked));
        rows.add(sumRow.toArray(new String[0]));

        byte[] bytes = ExcelUtil.exportCheckInData(rows, headers);
        // 用 RFC 5987 编码文件名，中文活动名不会乱码
        String filename = activity.getName() + "_打卡数据.xlsx";

        HttpHeaders respHeaders = new HttpHeaders();
        respHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        respHeaders.setContentDisposition(
                ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build());
        respHeaders.set("Access-Control-Expose-Headers", "Content-Disposition");
        return new ResponseEntity<>(bytes, respHeaders, HttpStatus.OK);
    }

    // ═══════════════════════ 内部工具 ═══════════════════════

    /** 参与者 = activity_members 中 active 的成员，但排除发起人与负责人（清单 S-01 备注） */
    private List<ActivityMember> participantsOf(Activity activity) {
        Set<Long> excluded = new HashSet<>();
        if (activity.getCreatorId() != null) { excluded.add(activity.getCreatorId()); }
        for (ActivityManager m : activityService.listManagers(activity.getId())) {
            if (m.getUserId() != null) { excluded.add(m.getUserId()); }
        }
        List<ActivityMember> out = new ArrayList<>();
        for (ActivityMember m : memberMapper.selectList(new LambdaQueryWrapper<ActivityMember>()
                .eq(ActivityMember::getActivityId, activity.getId())
                .eq(ActivityMember::getStatus, "active"))) {
            if (!excluded.contains(m.getUserId())) { out.add(m); }
        }
        return out;
    }

    private List<CheckIn> checkInsOn(Long activityId, LocalDate date) {
        return checkInMapper.selectList(new LambdaQueryWrapper<CheckIn>()
                .eq(CheckIn::getActivityId, activityId)
                .eq(CheckIn::getCheckInDate, date));
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

    private Activity requireActivity(Long id) {
        Activity a = activityMapper.selectById(id);
        if (a == null) { throw BusinessException.notFound("活动不存在"); }
        return a;
    }

    private String displayName(User u, String fallback) {
        if (u == null) { return fallback; }
        if (u.getRealName() != null && !u.getRealName().isEmpty()) { return u.getRealName(); }
        if (u.getNickName() != null && !u.getNickName().isEmpty()) { return u.getNickName(); }
        return fallback;
    }
}
