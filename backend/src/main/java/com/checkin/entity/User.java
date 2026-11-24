package com.checkin.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("users")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String openid;
    private String nickName;
    private String avatarUrl;
    private String realName;
    private String gender;
    private String idCard;
    private String faceImageUrl;
    private String phone;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public String getOpenid() { return openid; } public void setOpenid(String openid) { this.openid = openid; }
    public String getNickName() { return nickName; } public void setNickName(String nickName) { this.nickName = nickName; }
    public String getAvatarUrl() { return avatarUrl; } public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public String getRealName() { return realName; } public void setRealName(String realName) { this.realName = realName; }
    public String getGender() { return gender; } public void setGender(String gender) { this.gender = gender; }
    public String getIdCard() { return idCard; } public void setIdCard(String idCard) { this.idCard = idCard; }
    public String getFaceImageUrl() { return faceImageUrl; } public void setFaceImageUrl(String faceImageUrl) { this.faceImageUrl = faceImageUrl; }
    public String getPhone() { return phone; } public void setPhone(String phone) { this.phone = phone; }
    public LocalDateTime getCreatedAt() { return createdAt; } public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; } public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
