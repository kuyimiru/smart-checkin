package com.checkin.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.time.LocalDateTime;


@TableName("roster")
public class Roster {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long activityId;
    private String name;
    private String gender;
    private String phone;
    private Long matchedUserId;
    private LocalDateTime createdAt;
    public Long getId() { return id; }
    public void setId(Long v) { id = v; }
    public Long getActivityId() { return activityId; }
    public void setActivityId(Long v) { activityId = v; }
    public String getName() { return name; }
    public void setName(String v) { name = v; }
    public String getGender() { return gender; }
    public void setGender(String v) { gender = v; }
    public String getPhone() { return phone; }
    public void setPhone(String v) { phone = v; }
    public Long getMatchedUserId() { return matchedUserId; }
    public void setMatchedUserId(Long v) { matchedUserId = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { createdAt = v; }
}
