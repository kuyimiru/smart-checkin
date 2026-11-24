package com.checkin.common.config;


import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;


@Configuration
@ConfigurationProperties(prefix = "wechat")
public class WechatConfig {
    private String appId;
    private String appSecret;
    public String getAppId() { return appId; }
    public void setAppId(String v) { appId = v; }
    public String getAppSecret() { return appSecret; }
    public void setAppSecret(String v) { appSecret = v; }
}
