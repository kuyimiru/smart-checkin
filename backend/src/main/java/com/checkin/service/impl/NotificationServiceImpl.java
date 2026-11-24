package com.checkin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.checkin.common.config.WechatConfig;
import com.checkin.entity.User;
import com.checkin.entity.UserSubscription;
import com.checkin.mapper.UserMapper;
import com.checkin.mapper.UserSubscriptionMapper;
import com.checkin.service.NotificationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * 通知服务（清单 L-03 / N-01 / N-02）。
 *
 * v1 的实现只是三行 log.info，等于"没有通知"。这里升级为真正调用微信订阅消息：
 *   - 配置了 wechat.template.* 且用户已授权 → 调用 subscribeMessage/send 下发；
 *   - 未配置模板 ID / 用户未授权 / 微信接口失败 → 退化为日志，**不影响主业务流程**。
 */
@Service
public class NotificationServiceImpl implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceImpl.class);
    private static final String TOKEN_URL = "https://api.weixin.qq.com/cgi-bin/token";
    private static final String SEND_URL = "https://api.weixin.qq.com/cgi-bin/message/subscribe/send";

    private final UserMapper userMapper;
    private final UserSubscriptionMapper subscriptionMapper;
    private final WechatConfig wechatConfig;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${wechat.template.leave-request:}")
    private String tplLeaveRequest;
    @Value("${wechat.template.leave-result:}")
    private String tplLeaveResult;
    @Value("${wechat.template.unchecked:}")
    private String tplUnchecked;

    /** access_token 缓存 */
    private volatile String accessToken;
    private volatile long accessTokenExpireAt = 0L;

    public NotificationServiceImpl(UserMapper userMapper,
                                   UserSubscriptionMapper subscriptionMapper,
                                   WechatConfig wechatConfig) {
        this.userMapper = userMapper;
        this.subscriptionMapper = subscriptionMapper;
        this.wechatConfig = wechatConfig;
    }

    @Override
    public void notifyNewLeaveRequest(Long approverId, String applicantName, String activityName) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("thing1", truncate(applicantName, 20));      // 申请人
        data.put("thing2", truncate(activityName, 20));       // 活动名称
        data.put("phrase3", "待审批");                          // 审批状态
        data.put("time4", nowText());
        send(approverId, tplLeaveRequest, data, "请假审批提醒");
    }

    @Override
    public void notifyLeaveResult(Long userId, String activityName, String result) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("thing1", truncate(activityName, 20));
        data.put("phrase2", truncate(result, 5));
        data.put("time3", nowText());
        send(userId, tplLeaveResult, data, "请假结果通知");
    }

    @Override
    public void notifyUnchecked(Long userId, Long activityId, String activityName) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("thing1", truncate(activityName, 20));
        data.put("thing2", "您今日尚未打卡，请尽快完成");
        data.put("time3", nowText());
        send(userId, tplUnchecked, data, "未打卡提醒");
    }

    // ─────────────────────────── 内部实现 ───────────────────────────

    private void send(Long userId, String templateId, Map<String, Object> data, String scene) {
        if (userId == null) { return; }
        if (templateId == null || templateId.trim().isEmpty()) {
            log.info("[通知-未配置模板] {} → userId={}", scene, userId);
            return;
        }
        User user = userMapper.selectById(userId);
        if (user == null || user.getOpenid() == null || user.getOpenid().isEmpty()) {
            log.warn("[通知] 用户不存在或缺少 openid，userId={}", userId);
            return;
        }
        // 用户未授权则不下发（清单 N-03 的授权过滤）
        Long accepted = subscriptionMapper.selectCount(new LambdaQueryWrapper<UserSubscription>()
                .eq(UserSubscription::getUserId, userId)
                .eq(UserSubscription::getTemplateId, templateId)
                .eq(UserSubscription::getStatus, "accepted"));
        if (accepted == null || accepted == 0) {
            log.info("[通知] 用户未授权模板 {}，跳过。userId={}", templateId, userId);
            return;
        }

        try {
            String token = getAccessToken();
            if (token == null) {
                log.warn("[通知] 无法获取 access_token，放弃下发。scene={}", scene);
                return;
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("touser", user.getOpenid());
            body.put("template_id", templateId);
            body.put("page", "pages/index/index");
            body.put("data", wrapData(data));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String resp = restTemplate.postForObject(SEND_URL + "?access_token=" + token,
                    new HttpEntity<>(objectMapper.writeValueAsString(body), headers), String.class);
            JsonNode node = objectMapper.readTree(resp == null ? "{}" : resp);
            int errcode = node.has("errcode") ? node.get("errcode").asInt() : -1;
            if (errcode == 0) {
                log.info("[通知-成功] {} → userId={}", scene, userId);
            } else {
                log.warn("[通知-失败] {} errcode={} errmsg={}", scene, errcode,
                        node.has("errmsg") ? node.get("errmsg").asText() : "");
            }
        } catch (Exception e) {
            // 通知失败绝不能影响主业务
            log.error("[通知-异常] {} : {}", scene, e.getMessage());
        }
    }

    /** 微信订阅消息要求每个字段是 { "value": "..." } 结构 */
    private Map<String, Object> wrapData(Map<String, Object> data) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : data.entrySet()) {
            out.put(e.getKey(), Collections.singletonMap("value", e.getValue()));
        }
        return out;
    }

    private synchronized String getAccessToken() {
        long now = System.currentTimeMillis();
        if (accessToken != null && now < accessTokenExpireAt) { return accessToken; }
        String appId = wechatConfig.getAppId();
        String secret = wechatConfig.getAppSecret();
        if (appId == null || appId.isEmpty() || secret == null || secret.isEmpty()) {
            log.warn("[通知] 微信 appId/appSecret 未配置");
            return null;
        }
        try {
            String url = TOKEN_URL + "?grant_type=client_credential&appid=" + appId + "&secret=" + secret;
            String resp = restTemplate.getForObject(url, String.class);
            JsonNode node = objectMapper.readTree(resp == null ? "{}" : resp);
            if (node.has("access_token")) {
                accessToken = node.get("access_token").asText();
                int expiresIn = node.has("expires_in") ? node.get("expires_in").asInt() : 7200;
                accessTokenExpireAt = now + (expiresIn - 300) * 1000L;
                return accessToken;
            }
            log.warn("[通知] 获取 access_token 失败: {}", resp);
        } catch (Exception e) {
            log.error("[通知] 获取 access_token 异常: {}", e.getMessage());
        }
        return null;
    }

    private String truncate(String s, int max) {
        if (s == null) { return ""; }
        return s.length() <= max ? s : s.substring(0, max);
    }

    private String nowText() {
        return java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }
}
