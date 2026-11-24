package com.checkin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.checkin.entity.UserSubscription;
import com.checkin.mapper.UserSubscriptionMapper;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.*;

/**
 * 订阅消息授权接口（清单 N-03）。
 *   POST /api/notifications/subscribe   保存用户订阅消息授权
 *
 * 前端在 wx.requestSubscribeMessage 的回调里把每个模板的授权结果上报上来，
 * 后端记录状态，后续 N-01/N-02 发通知时按此过滤。
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final UserSubscriptionMapper subscriptionMapper;

    public NotificationController(UserSubscriptionMapper subscriptionMapper) {
        this.subscriptionMapper = subscriptionMapper;
    }

    @PostMapping("/subscribe")
    public Map<String, Object> subscribe(HttpServletRequest req, @RequestBody(required = false) Map<String, Object> body) {
        Long userId = (Long) req.getAttribute("userId");
        Map<String, String> results = new LinkedHashMap<>();

        Object raw = body == null ? null : body.get("templates");
        if (raw instanceof Map) {
            for (Map.Entry<?, ?> e : ((Map<?, ?>) raw).entrySet()) {
                results.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
            }
        } else if (body != null) {
            // 兼容单模板上报：{ "templateId": "xxx", "status": "accepted" }
            Object tid = body.get("templateId");
            if (tid != null) {
                results.put(String.valueOf(tid), String.valueOf(body.getOrDefault("status", "accepted")));
            }
        }

        int saved = 0;
        for (Map.Entry<String, String> e : results.entrySet()) {
            String templateId = e.getKey();
            String status = normalizeStatus(e.getValue());
            if (templateId.isEmpty()) { continue; }

            UserSubscription exists = subscriptionMapper.selectOne(new LambdaQueryWrapper<UserSubscription>()
                    .eq(UserSubscription::getUserId, userId)
                    .eq(UserSubscription::getTemplateId, templateId));
            if (exists == null) {
                UserSubscription s = new UserSubscription();
                s.setUserId(userId);
                s.setTemplateId(templateId);
                s.setStatus(status);
                subscriptionMapper.insert(s);
            } else {
                exists.setStatus(status);
                subscriptionMapper.updateById(exists);
            }
            saved++;
        }

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("code", 200);
        r.put("data", Collections.singletonMap("saved", saved));
        r.put("message", "订阅授权已保存");
        return r;
    }

    /** 查询当前用户的订阅授权情况（前端判断是否需要再次引导授权） */
    @GetMapping("/subscriptions")
    public Map<String, Object> list(HttpServletRequest req) {
        Long userId = (Long) req.getAttribute("userId");
        List<UserSubscription> list = subscriptionMapper.selectList(new LambdaQueryWrapper<UserSubscription>()
                .eq(UserSubscription::getUserId, userId));
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("code", 200);
        r.put("data", list);
        return r;
    }

    private String normalizeStatus(String raw) {
        if (raw == null) { return "accepted"; }
        String v = raw.trim().toLowerCase();
        if (v.contains("reject")) { return "rejected"; }
        if (v.contains("ban")) { return "ban"; }
        return "accepted";
    }
}
