package com.checkin.dto.request;

import javax.validation.constraints.NotNull;
import java.math.BigDecimal;


public class CheckInRequest {
    @NotNull(message = "活动ID不能为空")
    private Long activityId;
    private BigDecimal locationLat;
    private BigDecimal locationLng;
    private String faceImageUrl;
    public Long getActivityId() { return activityId; }
    public void setActivityId(Long v) { activityId = v; }
    public BigDecimal getLocationLat() { return locationLat; }
    public void setLocationLat(BigDecimal v) { locationLat = v; }
    public BigDecimal getLocationLng() { return locationLng; }
    public void setLocationLng(BigDecimal v) { locationLng = v; }
    public String getFaceImageUrl() { return faceImageUrl; }
    public void setFaceImageUrl(String v) { faceImageUrl = v; }
}
