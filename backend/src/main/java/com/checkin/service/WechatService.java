package com.checkin.service;

import com.checkin.common.config.WechatConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class WechatService {

    private static final Logger log = LoggerFactory.getLogger(WechatService.class);
    private static final String WECHAT_API = "https://api.weixin.qq.com/sns/jscode2session";

    private final WechatConfig wechatConfig;
    private final RestTemplate restTemplate;

    public WechatService(WechatConfig wechatConfig) {
        this.wechatConfig = wechatConfig;
        this.restTemplate = new RestTemplate();
    }

    public String code2Session(String code) {
        if (code == null || code.isEmpty()) return null;

        String appId = wechatConfig.getAppId();
        String appSecret = wechatConfig.getAppSecret();
        if (appId == null || appId.isEmpty() || appSecret == null || appSecret.isEmpty()) {
            log.warn("微信配置缺失，请在 application.yml 配置 wechat.app-id / wechat.app-secret");
            return null;
        }

        try {
            String url = WECHAT_API + "?appid=" + appId
                    + "&secret=" + appSecret
                    + "&js_code=" + code
                    + "&grant_type=authorization_code";
            String resp = restTemplate.getForObject(url, String.class);
            ObjectMapper om = new ObjectMapper();
            JsonNode node = om.readTree(resp);
            if (node.has("openid")) {
                String openid = node.get("openid").asText();
                log.info("微信登录成功, openid={}", openid);
                return openid;
            }
            int errcode = node.has("errcode") ? node.get("errcode").asInt() : -1;
            String errmsg = node.has("errmsg") ? node.get("errmsg").asText() : "unknown";
            log.warn("微信登录失败, errcode={}, errmsg={}", errcode, errmsg);
            return null;
        } catch (Exception e) {
            log.warn("调用微信API失败: {}", e.getMessage());
            return null;
        }
    }
}
