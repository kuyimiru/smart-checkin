package com.checkin.service;

public interface NotificationService {
    void notifyNewLeaveRequest(Long approverId, String applicantName, String activityName);
    void notifyLeaveResult(Long userId, String activityName, String result);
    void notifyUnchecked(Long userId, Long activityId, String activityName);
}
