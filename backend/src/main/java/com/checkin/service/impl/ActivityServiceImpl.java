package com.checkin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.checkin.common.exception.BusinessException;
import com.checkin.dto.request.ActivityCreateRequest;
import com.checkin.entity.Activity;
import com.checkin.entity.ActivityManager;
import com.checkin.entity.ActivityMember;
import com.checkin.entity.User;
import com.checkin.mapper.ActivityManagerMapper;
import com.checkin.mapper.ActivityMapper;
import com.checkin.mapper.ActivityMemberMapper;
import com.checkin.mapper.UserMapper;
import com.checkin.service.ActivityService;
import com.checkin.service.PermissionService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

/**
 * 活动服务。
 *
 * 相对 v1 的修复点：
 *   - 权限统一走 PermissionService（负责人不再是"无权限的人"）
 *   - 创建活动时把负责人绑定到 users（原来只写 name/phone，导致"我负责的"列表永远为空）
 *   - 创建时生成 invite_link（清单 A-01 备注「自动生成邀请码和链接」）
 *   - 邀请码失效/激活改为真实的 invite_enabled 字段（原来改 invite_link 是无效操作）
 *   - 退出/被移除后可以重新加入（原来一律 409）
 *   - 移除成员改为软删除（原来物理删除，与"被移除者可重新加入"冲突）
 *   - 编辑活动不再允许修改打卡方式（清单 A-02 明确「打卡方式不可修改」）
 *   - 补齐 frequencyType / frequencyDays 的编辑能力
 */
@Service
public class ActivityServiceImpl implements ActivityService {

    private final ActivityMapper activityMapper;
    private final ActivityMemberMapper memberMapper;
    private final ActivityManagerMapper managerMapper;
    private final UserMapper userMapper;
    private final PermissionService permissionService;

    public ActivityServiceImpl(ActivityMapper activityMapper,
                               ActivityMemberMapper memberMapper,
                               ActivityManagerMapper managerMapper,
                               UserMapper userMapper,
                               PermissionService permissionService) {
        this.activityMapper = activityMapper;
        this.memberMapper = memberMapper;
        this.managerMapper = managerMapper;
        this.userMapper = userMapper;
        this.permissionService = permissionService;
    }

    @Override
    public Activity create(Long creatorId, ActivityCreateRequest req) {
        validateSchedule(req.getStartDate(), req.getEndDate(),
                req.getCheckInStartTime(), req.getCheckInEndTime());
        Activity a = new Activity();
        a.setName(req.getName());
        a.setStartDate(req.getStartDate());
        a.setEndDate(req.getEndDate());
        a.setCheckInStartTime(req.getCheckInStartTime());
        a.setCheckInEndTime(req.getCheckInEndTime());
        a.setFrequencyType(req.getFrequencyType() == null ? "daily" : req.getFrequencyType());
        a.setFrequencyDays(toFrequencyDaysJson(req.getFrequencyDays()));
        a.setCheckInType(req.getCheckInType());
        a.setLocationLat(req.getLocationLat());
        a.setLocationLng(req.getLocationLng());
        a.setLocationRadius(req.getLocationRadius() == null ? 500 : req.getLocationRadius());
        a.setInviteCode(generateUniqueInviteCode());
        a.setInviteLink(buildInviteLink(a.getInviteCode()));
        a.setInviteEnabled(true);
        a.setCreatorId(creatorId);
        a.setStatus("active");
        activityMapper.insert(a);

        if (req.getManagers() != null) {
            for (ActivityCreateRequest.ManagerDTO m : req.getManagers()) {
                if (m.getName() == null || m.getName().trim().isEmpty()) { continue; }
                ActivityManager am = new ActivityManager();
                am.setActivityId(a.getId());
                am.setName(m.getName().trim());
                am.setPhone(m.getPhone());
                am.setRole(m.getRole() == null ? "manager" : m.getRole());
                // 关键修复：把负责人绑定到系统用户，否则"我负责的"列表永远查不到
                am.setUserId(resolveUserIdByPhone(m.getPhone()));
                managerMapper.insert(am);
            }
        }

        // 创建者自动加入为成员
        ActivityMember member = new ActivityMember();
        member.setActivityId(a.getId());
        member.setUserId(creatorId);
        member.setStatus("active");
        memberMapper.insert(member);
        a.refreshDisplayStatus();
        return a;
    }

    @Override
    public Map<String, List<Activity>> list(Long userId) {
        Map<String, List<Activity>> map = new LinkedHashMap<>();
        map.put("created", activityMapper.selectList(new LambdaQueryWrapper<Activity>()
                .eq(Activity::getCreatorId, userId).orderByDesc(Activity::getId)));

        List<Activity> managing = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        List<ActivityManager> mgrs = managerMapper.selectList(new LambdaQueryWrapper<ActivityManager>()
                .eq(ActivityManager::getUserId, userId));
        for (ActivityManager m : mgrs) {
            if (m.getActivityId() == null || seen.contains(m.getActivityId())) { continue; }
            Activity a = activityMapper.selectById(m.getActivityId());
            if (a != null && (a.getCreatorId() == null || !a.getCreatorId().equals(userId))) {
                managing.add(a);
                seen.add(m.getActivityId());
            }
        }
        map.put("managing", managing);

        List<Activity> joined = new ArrayList<>();
        List<ActivityMember> members = memberMapper.selectList(new LambdaQueryWrapper<ActivityMember>()
                .eq(ActivityMember::getUserId, userId).eq(ActivityMember::getStatus, "active"));
        for (ActivityMember m : members) {
            Activity a = activityMapper.selectById(m.getActivityId());
            if (a != null) { joined.add(a); }
        }
        map.put("joined", joined);

        // 实时推导展示状态，避免"活动早就结束了却还显示进行中"
        for (List<Activity> group : map.values()) {
            for (Activity a : group) { a.refreshDisplayStatus(); }
        }
        return map;
    }

    @Override
    public Activity getById(Long id) {
        Activity a = activityMapper.selectById(id);
        if (a == null) { throw BusinessException.notFound("活动不存在"); }
        a.refreshDisplayStatus();
        return a;
    }

    @Override
    public Activity update(Long id, Long userId, ActivityCreateRequest req) {
        Activity a = getById(id);
        permissionService.requireAdmin(a, userId);
        // 校验变更后的时间是否自洽（未传的字段沿用原值）
        validateSchedule(
                req.getStartDate() == null ? a.getStartDate() : req.getStartDate(),
                req.getEndDate() == null ? a.getEndDate() : req.getEndDate(),
                req.getCheckInStartTime() == null ? a.getCheckInStartTime() : req.getCheckInStartTime(),
                req.getCheckInEndTime() == null ? a.getCheckInEndTime() : req.getCheckInEndTime());
        if (req.getName() != null) { a.setName(req.getName()); }
        if (req.getStartDate() != null) { a.setStartDate(req.getStartDate()); }
        if (req.getEndDate() != null) { a.setEndDate(req.getEndDate()); }
        if (req.getCheckInStartTime() != null) { a.setCheckInStartTime(req.getCheckInStartTime()); }
        if (req.getCheckInEndTime() != null) { a.setCheckInEndTime(req.getCheckInEndTime()); }
        if (req.getFrequencyType() != null) { a.setFrequencyType(req.getFrequencyType()); }
        if (req.getFrequencyDays() != null) { a.setFrequencyDays(toFrequencyDaysJson(req.getFrequencyDays())); }
        if (req.getLocationLat() != null) { a.setLocationLat(req.getLocationLat()); }
        if (req.getLocationLng() != null) { a.setLocationLng(req.getLocationLng()); }
        if (req.getLocationRadius() != null) { a.setLocationRadius(req.getLocationRadius()); }
        // 注意：checkInType 不可修改（清单 A-02），此处刻意忽略 req.getCheckInType()
        activityMapper.updateById(a);
        return a;
    }

    @Override
    public void join(Long userId, String inviteCode) {
        if (inviteCode == null || inviteCode.trim().isEmpty()) {
            throw BusinessException.badRequest("请输入邀请码");
        }
        Activity a = activityMapper.selectOne(new LambdaQueryWrapper<Activity>()
                .eq(Activity::getInviteCode, inviteCode.trim().toUpperCase()));
        if (a == null) { throw BusinessException.notFound("邀请码无效"); }
        // 真实生效的邀请码开关
        if (a.getInviteEnabled() != null && !a.getInviteEnabled()) {
            throw BusinessException.validationFailed("邀请码已失效，请联系活动发起人");
        }
        if (!"active".equals(a.getStatus())) {
            String msg = "archived".equals(a.getStatus())
                    ? "活动已于 " + a.getEndDate() + " 结束，无法加入"
                    : "活动已关闭，无法加入";
            throw BusinessException.validationFailed(msg);
        }
        // 已过结束日期 / 还没开始的活动不允许再加入
        if (a.isExpired()) {
            throw BusinessException.validationFailed("活动已于 " + a.getEndDate() + " 结束，无法加入");
        }
        if (a.isNotStarted()) {
            throw BusinessException.validationFailed("活动将于 " + a.getStartDate() + " 开始，暂不能加入");
        }

        ActivityMember exists = memberMapper.selectOne(new LambdaQueryWrapper<ActivityMember>()
                .eq(ActivityMember::getActivityId, a.getId())
                .eq(ActivityMember::getUserId, userId));
        if (exists != null) {
            if ("active".equals(exists.getStatus())) {
                throw BusinessException.conflict("您已加入该活动");
            }
            // 退出过 / 被移除过 → 恢复为 active（清单 R-04 备注：被移除者可重新加入）
            exists.setStatus("active");
            memberMapper.updateById(exists);
            return;
        }

        ActivityMember m = new ActivityMember();
        m.setActivityId(a.getId());
        m.setUserId(userId);
        m.setStatus("active");
        memberMapper.insert(m);
    }

    @Override
    public void leave(Long activityId, Long userId) {
        ActivityMember m = memberMapper.selectOne(new LambdaQueryWrapper<ActivityMember>()
                .eq(ActivityMember::getActivityId, activityId).eq(ActivityMember::getUserId, userId));
        if (m == null) { throw BusinessException.notFound("您不在该活动中"); }
        if (a_isCreator(activityId, userId)) {
            throw BusinessException.validationFailed("发起人不能退出自己创建的活动，请改为关闭活动");
        }
        m.setStatus("left");
        memberMapper.updateById(m);
    }

    @Override
    public void close(Long activityId, Long userId) {
        Activity a = getById(activityId);
        permissionService.requireAdmin(a, userId);
        a.setStatus("closed");
        activityMapper.updateById(a);
    }

    @Override
    public void disableInviteCode(Long activityId, Long userId) {
        Activity a = getById(activityId);
        permissionService.requireAdmin(a, userId);
        a.setInviteEnabled(false);
        activityMapper.updateById(a);
    }

    @Override
    public void enableInviteCode(Long activityId, Long userId) {
        Activity a = getById(activityId);
        permissionService.requireAdmin(a, userId);
        a.setInviteEnabled(true);
        if (a.getInviteLink() == null || a.getInviteLink().isEmpty()) {
            a.setInviteLink(buildInviteLink(a.getInviteCode()));
        }
        activityMapper.updateById(a);
    }

    @Override
    public void removeMember(Long activityId, Long targetUserId, Long operatorId) {
        Activity a = getById(activityId);
        permissionService.requireAdmin(a, operatorId);
        if (a.getCreatorId() != null && a.getCreatorId().equals(targetUserId)) {
            throw BusinessException.validationFailed("不能移除活动发起人");
        }
        ActivityMember m = memberMapper.selectOne(new LambdaQueryWrapper<ActivityMember>()
                .eq(ActivityMember::getActivityId, activityId)
                .eq(ActivityMember::getUserId, targetUserId));
        if (m == null) { throw BusinessException.notFound("该用户不在活动中"); }
        // 软删除：保留记录，便于被移除者重新加入
        m.setStatus("left");
        memberMapper.updateById(m);
    }

    @Override
    public List<ActivityManager> listManagers(Long activityId) {
        return managerMapper.selectList(new LambdaQueryWrapper<ActivityManager>()
                .eq(ActivityManager::getActivityId, activityId)
                .orderByAsc(ActivityManager::getId));
    }

    // ─────────────────────────── 内部工具 ───────────────────────────

    private boolean a_isCreator(Long activityId, Long userId) {
        Activity a = activityMapper.selectById(activityId);
        return a != null && a.getCreatorId() != null && a.getCreatorId().equals(userId);
    }

    /** 把 List<Integer> 序列化成 JSON 数组字符串，例如 [1,3,5] */
    private String toFrequencyDaysJson(List<Integer> days) {
        if (days == null || days.isEmpty()) { return null; }
        List<Integer> sorted = new ArrayList<>(new TreeSet<>(days));
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < sorted.size(); i++) {
            if (i > 0) { sb.append(","); }
            sb.append(sorted.get(i));
        }
        return sb.append("]").toString();
    }

    /** 时间自洽性校验：结束日期不能早于开始日期，打卡结束时间必须晚于开始时间 */
    private void validateSchedule(LocalDate startDate, LocalDate endDate,
                                  LocalTime startTime, LocalTime endTime) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw BusinessException.validationFailed("活动结束日期不能早于开始日期");
        }
        if (startTime != null && endTime != null && !endTime.isAfter(startTime)) {
            throw BusinessException.validationFailed("打卡结束时间必须晚于开始时间");
        }
    }

    private String buildInviteLink(String inviteCode) {
        return "https://checkin.example.com/join/" + inviteCode;
    }

    /** 按手机号反查系统用户，查不到返回 null（负责人可能还没注册） */
    private Long resolveUserIdByPhone(String phone) {
        if (phone == null || phone.trim().isEmpty()) { return null; }
        User u = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getPhone, phone.trim()).last("limit 1"));
        return u == null ? null : u.getId();
    }

    /** 生成 6 位邀请码，撞库则重试（invite_code 上有唯一索引） */
    private String generateUniqueInviteCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        Random rnd = new Random();
        for (int attempt = 0; attempt < 20; attempt++) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 6; i++) { sb.append(chars.charAt(rnd.nextInt(chars.length()))); }
            String code = sb.toString();
            Long c = activityMapper.selectCount(new LambdaQueryWrapper<Activity>()
                    .eq(Activity::getInviteCode, code));
            if (c == null || c == 0) { return code; }
        }
        throw new BusinessException(500, "邀请码生成失败，请重试");
    }
}
