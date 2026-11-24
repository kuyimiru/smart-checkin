package com.checkin.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.math.BigDecimal;
import java.time.*;


@TableName("activities")
public class Activity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalTime checkInStartTime;
    private LocalTime checkInEndTime;
    private String frequencyType;
    private String frequencyDays;
    private String checkInType;
    private BigDecimal locationLat;
    private BigDecimal locationLng;
    private Integer locationRadius;
    private String inviteCode;
    private String inviteLink;
    private Boolean inviteEnabled;
    private Long creatorId;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 非数据库字段：**按日期实时推导**的展示状态。
     *
     * 为什么需要它：数据库里的 status 只在"关闭活动"或"定时归档"时才会变，
     * 而定时任务要求服务器一直开着。结果就是——活动早就过了 end_date，
     * 页面上仍然显示"进行中"。改成读取时实时推导，就与服务器有没有开无关了。
     *
     * 取值：pending(未开始) / active(进行中) / expired(已结束) / closed(已关闭) / archived(已归档)
     */
    @TableField(exist = false)
    private String displayStatus;

    /** 根据 status + start_date + end_date 重新计算展示状态 */
    public void refreshDisplayStatus() {
        if ("closed".equals(status)) { displayStatus = "closed"; return; }
        if ("archived".equals(status)) { displayStatus = "archived"; return; }
        LocalDate today = LocalDate.now();
        if (startDate != null && today.isBefore(startDate)) { displayStatus = "pending"; return; }
        if (endDate != null && today.isAfter(endDate)) { displayStatus = "expired"; return; }
        displayStatus = "active";
    }

    /** 是否已经过了活动结束日期 */
    public boolean isExpired() {
        return endDate != null && LocalDate.now().isAfter(endDate);
    }

    /** 是否还没到活动开始日期 */
    public boolean isNotStarted() {
        return startDate != null && LocalDate.now().isBefore(startDate);
    }

    public String getDisplayStatus() { return displayStatus; }
    public void setDisplayStatus(String v) { displayStatus = v; }
    public Long getId() { return id; }
    public void setId(Long v) { id = v; }
    public String getName() { return name; }
    public void setName(String v) { name = v; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate v) { startDate = v; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate v) { endDate = v; }
    public LocalTime getCheckInStartTime() { return checkInStartTime; }
    public void setCheckInStartTime(LocalTime v) { checkInStartTime = v; }
    public LocalTime getCheckInEndTime() { return checkInEndTime; }
    public void setCheckInEndTime(LocalTime v) { checkInEndTime = v; }
    public String getFrequencyType() { return frequencyType; }
    public void setFrequencyType(String v) { frequencyType = v; }
    public String getFrequencyDays() { return frequencyDays; }
    public void setFrequencyDays(String v) { frequencyDays = v; }
    public String getCheckInType() { return checkInType; }
    public void setCheckInType(String v) { checkInType = v; }
    public BigDecimal getLocationLat() { return locationLat; }
    public void setLocationLat(BigDecimal v) { locationLat = v; }
    public BigDecimal getLocationLng() { return locationLng; }
    public void setLocationLng(BigDecimal v) { locationLng = v; }
    public Integer getLocationRadius() { return locationRadius; }
    public void setLocationRadius(Integer v) { locationRadius = v; }
    public String getInviteCode() { return inviteCode; }
    public void setInviteCode(String v) { inviteCode = v; }
    public String getInviteLink() { return inviteLink; }
    public void setInviteLink(String v) { inviteLink = v; }
    public Boolean getInviteEnabled() { return inviteEnabled; }
    public void setInviteEnabled(Boolean v) { inviteEnabled = v; }
    public Long getCreatorId() { return creatorId; }
    public void setCreatorId(Long v) { creatorId = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { status = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { createdAt = v; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime v) { updatedAt = v; }
}
