package com.checkin.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.time.*;


@TableName("leaves")
public class Leave {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long activityId;
    private Long userId;
    private LocalDate startDate;
    private LocalDate endDate;
    private String destination;
    private String reason;
    private String emergencyContact;
    private Long approverId;
    private String status;
    private String rejectReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    public Long getId() { return id; }
    public void setId(Long v) { id = v; }
    public Long getActivityId() { return activityId; }
    public void setActivityId(Long v) { activityId = v; }
    public Long getUserId() { return userId; }
    public void setUserId(Long v) { userId = v; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate v) { startDate = v; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate v) { endDate = v; }
    public String getDestination() { return destination; }
    public void setDestination(String v) { destination = v; }
    public String getReason() { return reason; }
    public void setReason(String v) { reason = v; }
    public String getEmergencyContact() { return emergencyContact; }
    public void setEmergencyContact(String v) { emergencyContact = v; }
    public Long getApproverId() { return approverId; }
    public void setApproverId(Long v) { approverId = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { status = v; }
    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String v) { rejectReason = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { createdAt = v; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime v) { updatedAt = v; }
}
