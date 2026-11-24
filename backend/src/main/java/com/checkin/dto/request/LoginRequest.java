package com.checkin.dto.request;
public class LoginRequest {
    private String code; private String deviceId; private String nickName; private String avatarUrl;
    public String getCode() { return code; } public void setCode(String v) { code = v; }
    public String getDeviceId() { return deviceId; } public void setDeviceId(String v) { deviceId = v; }
    public String getNickName() { return nickName; } public void setNickName(String v) { nickName = v; }
    public String getAvatarUrl() { return avatarUrl; } public void setAvatarUrl(String v) { avatarUrl = v; }
}
