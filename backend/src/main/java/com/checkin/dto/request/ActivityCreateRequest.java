package com.checkin.dto.request;

import javax.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;

public class ActivityCreateRequest {
    @NotBlank(message = "活动名称不能为空")
    private String name;
    @NotNull private LocalDate startDate;
    @NotNull private LocalDate endDate;
    @NotNull private LocalTime checkInStartTime;
    @NotNull private LocalTime checkInEndTime;
    private String frequencyType = "daily";
    private List<Integer> frequencyDays;
    @NotBlank(message = "打卡方式不能为空")
    private String checkInType;
    private BigDecimal locationLat;
    private BigDecimal locationLng;
    private Integer locationRadius = 500;
    private List<ManagerDTO> managers;

    public static class ManagerDTO {
        private String name;
        private String phone;
        private String role;
        public String getName() { return name; } public void setName(String v) { name = v; }
        public String getPhone() { return phone; } public void setPhone(String v) { phone = v; }
        public String getRole() { return role; } public void setRole(String v) { role = v; }
    }
    public String getName() { return name; } public void setName(String v) { name = v; }
    public LocalDate getStartDate() { return startDate; } public void setStartDate(LocalDate v) { startDate = v; }
    public LocalDate getEndDate() { return endDate; } public void setEndDate(LocalDate v) { endDate = v; }
    public LocalTime getCheckInStartTime() { return checkInStartTime; } public void setCheckInStartTime(LocalTime v) { checkInStartTime = v; }
    public LocalTime getCheckInEndTime() { return checkInEndTime; } public void setCheckInEndTime(LocalTime v) { checkInEndTime = v; }
    public String getFrequencyType() { return frequencyType; } public void setFrequencyType(String v) { frequencyType = v; }
    public List<Integer> getFrequencyDays() { return frequencyDays; } public void setFrequencyDays(List<Integer> v) { frequencyDays = v; }
    public String getCheckInType() { return checkInType; } public void setCheckInType(String v) { checkInType = v; }
    public BigDecimal getLocationLat() { return locationLat; } public void setLocationLat(BigDecimal v) { locationLat = v; }
    public BigDecimal getLocationLng() { return locationLng; } public void setLocationLng(BigDecimal v) { locationLng = v; }
    public Integer getLocationRadius() { return locationRadius; } public void setLocationRadius(Integer v) { locationRadius = v; }
    public List<ManagerDTO> getManagers() { return managers; } public void setManagers(List<ManagerDTO> v) { managers = v; }
}
