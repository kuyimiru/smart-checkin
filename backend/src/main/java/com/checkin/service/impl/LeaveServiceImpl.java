package com.checkin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.checkin.common.exception.BusinessException;
import com.checkin.dto.request.LeaveRequest;
import com.checkin.entity.Activity;
import com.checkin.entity.Leave;
import com.checkin.entity.User;
import com.checkin.mapper.ActivityMapper;
import com.checkin.mapper.LeaveMapper;
import com.checkin.mapper.UserMapper;
import com.checkin.service.LeaveService;
import com.checkin.service.NotificationService;
import com.checkin.service.PermissionService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

/**
 * 请假服务（清单 L-01 / L-02 / L-03）。
 *
 * v1 只有接口声明、没有实现类，导致前端的请假申请与审批页面全部 404。此处补齐。
 */
@Service
public class LeaveServiceImpl implements LeaveService {

    private final LeaveMapper leaveMapper;
    private final ActivityMapper activityMapper;
    private final UserMapper userMapper;
    private final PermissionService permissionService;
    private final NotificationService notificationService;

    public LeaveServiceImpl(LeaveMapper leaveMapper,
                            ActivityMapper activityMapper,
                            UserMapper userMapper,
                            PermissionService permissionService,
                            NotificationService notificationService) {
        this.leaveMapper = leaveMapper;
        this.activityMapper = activityMapper;
        this.userMapper = userMapper;
        this.permissionService = permissionService;
        this.notificationService = notificationService;
    }

    @Override
    public Leave submit(Long userId, LeaveRequest req) {
        Activity activity = activityMapper.selectById(req.getActivityId());
        if (activity == null) { throw BusinessException.notFound("活动不存在"); }
        if (!"active".equals(activity.getStatus())) {
            throw BusinessException.validationFailed("活动已关闭，无法提交请假");
        }
        // 必须是活动参与者
        permissionService.requireActiveMember(req.getActivityId(), userId);

        if (req.getEndDate().isBefore(req.getStartDate())) {
            throw BusinessException.validationFailed("请假结束日期不能早于开始日期");
        }

        // 审批人必须是该活动的发起人或负责人（清单：选非法审批人返回 400）
        if (!permissionService.isAdmin(activity, req.getApproverId())) {
            throw BusinessException.badRequest("审批人必须是该活动的发起人或负责人");
        }
        if (req.getApproverId().equals(userId)) {
            throw BusinessException.badRequest("不能选择自己作为审批人");
        }

        // 同一时间段避免重复提交
        Long dup = leaveMapper.selectCount(new LambdaQueryWrapper<Leave>()
                .eq(Leave::getActivityId, req.getActivityId())
                .eq(Leave::getUserId, userId)
                .eq(Leave::getStatus, "pending")
                .le(Leave::getStartDate, req.getEndDate())
                .ge(Leave::getEndDate, req.getStartDate()));
        if (dup != null && dup > 0) {
            throw BusinessException.conflict("该时间段已有待审批的请假申请");
        }

        Leave leave = new Leave();
        leave.setActivityId(req.getActivityId());
        leave.setUserId(userId);
        leave.setStartDate(req.getStartDate());
        leave.setEndDate(req.getEndDate());
        leave.setDestination(req.getDestination());
        leave.setReason(req.getReason());
        leave.setEmergencyContact(req.getEmergencyContact());
        leave.setApproverId(req.getApproverId());
        leave.setStatus("pending");
        leaveMapper.insert(leave);

        // 清单 N-02：有新请假申请时通知审批人
        User applicant = userMapper.selectById(userId);
        String applicantName = applicant == null ? ("用户" + userId)
                : (applicant.getRealName() == null || applicant.getRealName().isEmpty()
                    ? "用户" + userId : applicant.getRealName());
        try {
            notificationService.notifyNewLeaveRequest(req.getApproverId(), applicantName, activity.getName());
        } catch (Exception ignored) {
            // 通知失败不影响请假单创建
        }
        return leave;
    }

    @Override
    public List<Map<String, Object>> getMyLeaves(Long userId) {
        List<Leave> list = leaveMapper.selectList(new LambdaQueryWrapper<Leave>()
                .eq(Leave::getUserId, userId)
                .orderByDesc(Leave::getId));
        List<Map<String, Object>> out = new ArrayList<>();
        for (Leave l : list) {
            out.add(toView(l, null));
        }
        return out;
    }

    @Override
    public List<Map<String, Object>> getPendingList(Long approverId) {
        // 只返回指派给当前审批人的单子（清单：只处理指派给自己的 / 权限隔离）
        List<Leave> list = leaveMapper.selectList(new LambdaQueryWrapper<Leave>()
                .eq(Leave::getApproverId, approverId)
                .eq(Leave::getStatus, "pending")
                .orderByDesc(Leave::getId));
        List<Map<String, Object>> out = new ArrayList<>();
        for (Leave l : list) {
            out.add(toView(l, resolveApplicantName(l.getUserId())));
        }
        return out;
    }

    @Override
    public void approve(Long leaveId, Long approverId) {
        Leave leave = requirePendingOwnedBy(leaveId, approverId);
        leave.setStatus("approved");
        leave.setRejectReason(null);
        leaveMapper.updateById(leave);
        notifyResult(leave, "已通过");
    }

    @Override
    public void reject(Long leaveId, Long approverId, String reason) {
        Leave leave = requirePendingOwnedBy(leaveId, approverId);
        leave.setStatus("rejected");
        leave.setRejectReason(reason == null || reason.trim().isEmpty() ? "审批人拒绝" : reason);
        leaveMapper.updateById(leave);
        notifyResult(leave, "已拒绝");
    }

    // ─────────────────────────── 内部工具 ───────────────────────────

    private Leave requirePendingOwnedBy(Long leaveId, Long approverId) {
        Leave leave = leaveMapper.selectById(leaveId);
        if (leave == null) { throw BusinessException.notFound("请假申请不存在"); }
        if (leave.getApproverId() == null || !leave.getApproverId().equals(approverId)) {
            throw BusinessException.forbidden("无权审批该申请");
        }
        if (!"pending".equals(leave.getStatus())) {
            throw BusinessException.validationFailed("该申请已被处理");
        }
        return leave;
    }

    /** 申请人显示名：真实姓名 → 微信昵称 → "用户{id}"，避免审批列表出现空白姓名 */
    private String resolveApplicantName(Long userId) {
        if (userId == null) { return "未知用户"; }
        User u = userMapper.selectById(userId);
        if (u != null) {
            if (u.getRealName() != null && !u.getRealName().isEmpty()) { return u.getRealName(); }
            if (u.getNickName() != null && !u.getNickName().isEmpty()) { return u.getNickName(); }
        }
        return "用户" + userId;
    }

    private void notifyResult(Leave leave, String result) {
        Activity activity = activityMapper.selectById(leave.getActivityId());
        String activityName = activity == null ? "活动" : activity.getName();
        try {
            notificationService.notifyLeaveResult(leave.getUserId(), activityName, result);
        } catch (Exception ignored) { }
    }

    private Map<String, Object> toView(Leave l, String userName) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", l.getId());
        m.put("activityId", l.getActivityId());
        m.put("userId", l.getUserId());
        m.put("userName", userName);
        m.put("startDate", l.getStartDate() == null ? null : l.getStartDate().toString());
        m.put("endDate", l.getEndDate() == null ? null : l.getEndDate().toString());
        m.put("destination", l.getDestination());
        m.put("reason", l.getReason());
        m.put("emergencyContact", l.getEmergencyContact());
        m.put("approverId", l.getApproverId());
        m.put("status", l.getStatus());
        m.put("rejectReason", l.getRejectReason());
        Activity a = activityMapper.selectById(l.getActivityId());
        m.put("activityName", a == null ? null : a.getName());
        m.put("today", LocalDate.now().toString());
        return m;
    }
}
