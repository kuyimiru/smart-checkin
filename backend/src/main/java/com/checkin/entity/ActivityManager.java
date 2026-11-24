package com.checkin.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.time.LocalDateTime;


@TableName("activity_managers")
public class ActivityManager {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long activityId;
    private Long userId;
    private String name;
    private String phone;
    private String role;
    private LocalDateTime createdAt;
    public Long getId() { return id; }
    public void setId(Long v) { id = v; }
    public Long getActivityId() { return activityId; }
    public void setActivityId(Long v) { activityId = v; }
    public Long getUserId() { return userId; }
    public void setUserId(Long v) { userId = v; }
    public String getName() { return name; }
    public void setName(String v) { name = v; }
    public String getPhone() { return phone; }
    public void setPhone(String v) { phone = v; }
    public String getRole() { return role; }
    public void setRole(String v) { role = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { createdAt = v; }
}
