package com.checkin.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.time.LocalDateTime;


@TableName("activity_members")
public class ActivityMember {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long activityId;
    private Long userId;
    private LocalDateTime joinedAt;
    private String status;
    public Long getId() { return id; }
    public void setId(Long v) { id = v; }
    public Long getActivityId() { return activityId; }
    public void setActivityId(Long v) { activityId = v; }
    public Long getUserId() { return userId; }
    public void setUserId(Long v) { userId = v; }
    public LocalDateTime getJoinedAt() { return joinedAt; }
    public void setJoinedAt(LocalDateTime v) { joinedAt = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { status = v; }
}
