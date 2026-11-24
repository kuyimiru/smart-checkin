package com.checkin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.checkin.common.exception.BusinessException;
import com.checkin.entity.Activity;
import com.checkin.entity.ActivityManager;
import com.checkin.entity.ActivityMember;
import com.checkin.mapper.ActivityManagerMapper;
import com.checkin.mapper.ActivityMemberMapper;
import org.springframework.stereotype.Service;

/**
 * 统一权限校验。
 *
 * 修复了 v1 的两处致命缺陷：
 *   1) v1 的 ActivityServiceImpl.checkAdmin() 只认 creator_id，导致"活动负责人(manager)"
 *      在清单 A-05/关闭/编辑/邀请码/花名册/导出 等场景全部 403；
 *   2) v1 各处的权限逻辑不一致（AdminController 认 manager，ActivityServiceImpl 不认）。
 *
 * 现在的统一规则：**发起人(creator) 或 被指派的负责人(activity_managers.user_id)** 即视为管理员。
 */
@Service
public class PermissionService {

    private final ActivityManagerMapper managerMapper;
    private final ActivityMemberMapper memberMapper;

    public PermissionService(ActivityManagerMapper managerMapper, ActivityMemberMapper memberMapper) {
        this.managerMapper = managerMapper;
        this.memberMapper = memberMapper;
    }

    /** 是否为该活动的管理员（发起人 / 负责人） */
    public boolean isAdmin(Activity activity, Long userId) {
        if (activity == null || userId == null) return false;
        if (activity.getCreatorId() != null && activity.getCreatorId().equals(userId)) return true;
        Long c = managerMapper.selectCount(new LambdaQueryWrapper<ActivityManager>()
                .eq(ActivityManager::getActivityId, activity.getId())
                .eq(ActivityManager::getUserId, userId));
        return c != null && c > 0;
    }

    /** 断言是管理员，否则 403 */
    public void requireAdmin(Activity activity, Long userId) {
        if (!isAdmin(activity, userId)) {
            throw BusinessException.forbidden("无权限操作该活动");
        }
    }

    /** 是否为该活动的有效成员（用于打卡等参与者行为） */
    public boolean isActiveMember(Long activityId, Long userId) {
        if (activityId == null || userId == null) return false;
        Long c = memberMapper.selectCount(new LambdaQueryWrapper<ActivityMember>()
                .eq(ActivityMember::getActivityId, activityId)
                .eq(ActivityMember::getUserId, userId)
                .eq(ActivityMember::getStatus, "active"));
        return c != null && c > 0;
    }

    /** 断言是活动管理员（发起人/负责人之一） */
    public void requireActiveMember(Long activityId, Long userId) {
        if (!isActiveMember(activityId, userId)) {
            throw BusinessException.forbidden("您不是该活动的参与者");
        }
    }
}
