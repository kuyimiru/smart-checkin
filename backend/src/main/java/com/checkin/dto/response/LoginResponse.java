package com.checkin.dto.response;





public class LoginResponse {
    private String token; private Long userId; private String nickName;
    private String avatarUrl; private String realName; private boolean hasProfile;

    public LoginResponse() {}
    public LoginResponse(String token, Long userId, String nickName, String avatarUrl, String realName, boolean hasProfile) {
        this.token = token; this.userId = userId; this.nickName = nickName;
        this.avatarUrl = avatarUrl; this.realName = realName; this.hasProfile = hasProfile;
    }
    public String getToken() { return token; }
    public void setToken(String v) { token = v; }
    public Long getUserId() { return userId; }
    public void setUserId(Long v) { userId = v; }
    public String getNickName() { return nickName; }
    public void setNickName(String v) { nickName = v; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String v) { avatarUrl = v; }
    public String getRealName() { return realName; }
    public void setRealName(String v) { realName = v; }
    public boolean isHasProfile() { return hasProfile; }
    public void setHasProfile(boolean v) { hasProfile = v; }
}
