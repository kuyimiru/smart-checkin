package com.checkin.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.math.BigDecimal;
import java.time.*;


@TableName("check_ins")
public class CheckIn {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long activityId;
    private Long userId;
    private LocalDate checkInDate;
    private LocalTime checkInTime;
    private String status;
    private String type;
    private BigDecimal locationLat;
    private BigDecimal locationLng;
    private String faceImageUrl;
    private LocalDateTime createdAt;
    public Long getId() { return id; }
    public void setId(Long v) { id = v; }
    public Long getActivityId() { return activityId; }
    public void setActivityId(Long v) { activityId = v; }
    public Long getUserId() { return userId; }
    public void setUserId(Long v) { userId = v; }
    public LocalDate getCheckInDate() { return checkInDate; }
    public void setCheckInDate(LocalDate v) { checkInDate = v; }
    public LocalTime getCheckInTime() { return checkInTime; }
    public void setCheckInTime(LocalTime v) { checkInTime = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { status = v; }
    public String getType() { return type; }
    public void setType(String v) { type = v; }
    public BigDecimal getLocationLat() { return locationLat; }
    public void setLocationLat(BigDecimal v) { locationLat = v; }
    public BigDecimal getLocationLng() { return locationLng; }
    public void setLocationLng(BigDecimal v) { locationLng = v; }
    public String getFaceImageUrl() { return faceImageUrl; }
    public void setFaceImageUrl(String v) { faceImageUrl = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { createdAt = v; }
}
