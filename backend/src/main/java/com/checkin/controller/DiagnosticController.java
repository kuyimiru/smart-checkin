package com.checkin.controller;

import com.checkin.service.FaceVerifyService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 诊断接口：用于在不发起打卡的情况下检查人脸模型是否能正常加载。
 *
 *   GET /api/diagnostic/face       查看当前人脸核验配置与模型加载状态
 *   GET /api/diagnostic/face/load  主动触发一次模型加载（首次加载约需数秒）
 */
@RestController
@RequestMapping("/api/diagnostic")
public class DiagnosticController {

    private final FaceVerifyService faceVerifyService;

    public DiagnosticController(FaceVerifyService faceVerifyService) {
        this.faceVerifyService = faceVerifyService;
    }

    @GetMapping("/face")
    public Map<String, Object> face() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("code", 200);
        r.put("data", faceVerifyService.status());
        return r;
    }

    @GetMapping("/face/load")
    public Map<String, Object> loadFace() {
        Map<String, Object> r = new LinkedHashMap<>();
        long t0 = System.currentTimeMillis();
        try {
            faceVerifyService.warmUp();
            r.put("code", 200);
            r.put("message", "人脸模型加载成功");
            r.put("data", faceVerifyService.status());
        } catch (Exception e) {
            r.put("code", 500);
            r.put("message", "人脸模型加载失败：" + e.getMessage());
        }
        r.put("costMs", System.currentTimeMillis() - t0);
        return r;
    }

    /**
     * 上传两张图片做 1:1 人脸比对，直接返回相似度。
     * 用法：表单字段 fileA / fileB，各传一张人脸照片。
     * 这是验证"识别到底准不准"最直接的方式，不必走完整打卡流程。
     */
    @PostMapping("/face/compare")
    public Map<String, Object> compare(@RequestParam("fileA") MultipartFile fileA,
                                       @RequestParam("fileB") MultipartFile fileB) {
        Map<String, Object> r = new LinkedHashMap<>();
        if (fileA == null || fileA.isEmpty() || fileB == null || fileB.isEmpty()) {
            r.put("code", 400);
            r.put("message", "请分别上传两张人脸照片（字段名 fileA / fileB）");
            return r;
        }
        long t0 = System.currentTimeMillis();
        try {
            Map<String, Object> data = faceVerifyService.compare(fileA.getBytes(), fileB.getBytes());
            r.put("code", Boolean.TRUE.equals(data.get("success")) ? 200 : 422);
            r.put("data", data);
            r.put("message", Boolean.TRUE.equals(data.get("success")) ? "比对完成" : data.get("message"));
        } catch (Exception e) {
            r.put("code", 500);
            r.put("message", "比对失败：" + e.getMessage());
        }
        r.put("costMs", System.currentTimeMillis() - t0);
        return r;
    }
}
