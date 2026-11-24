package com.checkin.dto.request;

import javax.validation.constraints.*;
import java.time.LocalDate;


public class LeaveRequest {
    @NotNull private Long activityId;
    @NotNull private LocalDate startDate;
    @NotNull private LocalDate endDate;
    private String destination;
    private String reason;
    @NotBlank(message = "紧急联系人不能为空")
    private String emergencyContact;
    @NotNull private Long approverId;
    public Long getActivityId() { return activityId; }
    public void setActivityId(Long v) { activityId = v; }
    public java.time.LocalDate getStartDate() { return startDate; }
    public void setStartDate(java.time.LocalDate v) { startDate = v; }
    public java.time.LocalDate getEndDate() { return endDate; }
    public void setEndDate(java.time.LocalDate v) { endDate = v; }
    public Long getApproverId() { return approverId; }
    public void setApproverId(Long v) { approverId = v; }
    public String getDestination() { return destination; }
    public void setDestination(String v) { destination = v; }
    public String getReason() { return reason; }
    public void setReason(String v) { reason = v; }
    public String getEmergencyContact() { return emergencyContact; }
    public void setEmergencyContact(String v) { emergencyContact = v; }
}
