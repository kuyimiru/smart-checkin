package com.checkin.service;
import com.checkin.dto.request.ActivityCreateRequest;
import com.checkin.entity.Activity;
import com.checkin.entity.ActivityManager;
import java.util.*;

public interface ActivityService {
    Activity create(Long creatorId, ActivityCreateRequest request);
    Map<String, List<Activity>> list(Long userId);
    Activity getById(Long id);
    Activity update(Long id, Long userId, ActivityCreateRequest request);
    void join(Long userId, String inviteCode);
    void leave(Long activityId, Long userId);
    void close(Long activityId, Long userId);
    void disableInviteCode(Long activityId, Long userId);
    void enableInviteCode(Long activityId, Long userId);
    void removeMember(Long activityId, Long targetUserId, Long operatorId);
    /** 活动负责人列表（前端请假时选择审批人用） */
    List<ActivityManager> listManagers(Long activityId);
}
