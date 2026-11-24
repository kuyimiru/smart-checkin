package com.checkin.service;

import cn.smartjavaai.common.config.Config;
import cn.smartjavaai.common.entity.R;
import cn.smartjavaai.face.config.FaceDetConfig;
import cn.smartjavaai.face.config.FaceRecConfig;
import cn.smartjavaai.face.enums.FaceDetModelEnum;
import cn.smartjavaai.face.enums.FaceRecModelEnum;
import cn.smartjavaai.face.factory.FaceDetModelFactory;
import cn.smartjavaai.face.factory.FaceRecModelFactory;
import cn.smartjavaai.face.model.facedect.FaceDetModel;
import cn.smartjavaai.face.model.facerec.FaceRecModel;
import com.checkin.common.exception.BusinessException;
import com.checkin.entity.User;
import com.checkin.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 人脸核验服务（清单 C-02：拍照 + 服务端人脸比对核验身份）。
 *
 * 实现方式：SmartJavaAI（MIT License）的 SeetaFace6 引擎，本地离线推理，不依赖任何云服务。
 *   - 人脸检测 + 人脸对齐 + 512 维特征提取 + 1:1 相似度比对
 *   - 模型文件由 face.model-path 指定（SeetaFace6 的 .csta 文件所在目录）
 *
 * 三种运行状态：
 *   1. mock-enabled=true          → 模拟模式：只做照片存在性校验，日志标注 [人脸核验-模拟]
 *   2. mock-enabled=false + 配了模型且加载成功 → 真实比对，低于阈值返回 422
 *   3. mock-enabled=false + 模型加载失败        → 抛出 422 并说明原因（不会静默放行）
 *
 * 模型是重量级资源（约 100MB），因此用懒加载 + 双检锁保证**整个进程只加载一次**。
 */
@Service
public class FaceVerifyService {

    private static final Logger log = LoggerFactory.getLogger(FaceVerifyService.class);

    private final UserMapper userMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${file.upload-path:./uploads}")
    private String uploadPath;

    @Value("${face.model-path:}")
    private String modelPath;

    @Value("${face.mock-enabled:true}")
    private boolean mockEnabled;

    /**
     * 原生库与模型缓存目录（绝对路径，不能含中文和空格）。
     * SmartJavaAI 默认写到 C:/Users/{user}/smartjavaai_cache；
     * 这里留空则沿用默认值，配置了就改到指定盘（C 盘紧张时很有用）。
     */
    @Value("${face.cache-path:}")
    private String cachePath;

    @Value("${face.threshold:0}")
    private double configuredThreshold;

    /** 单例模型（懒加载） */
    private volatile FaceRecModel faceRecModel;
    /** 加载失败后不再反复重试，避免每次打卡都卡几百毫秒 */
    private volatile boolean loadFailed = false;

    public FaceVerifyService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    /**
     * 核验打卡照片与用户底图是否同一人。
     * @throws BusinessException 校验失败时抛出 422
     */
    public void verify(Long userId, String checkInImageUrl) {
        if (checkInImageUrl == null || checkInImageUrl.isEmpty()) {
            throw BusinessException.validationFailed("请先拍摄人脸照片");
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw BusinessException.notFound("用户不存在");
        }
        String baseImage = user.getFaceImageUrl();
        if (baseImage == null || baseImage.isEmpty()) {
            throw BusinessException.validationFailed(
                    "尚未录入人脸底图，请先到「个人信息」中上传人脸照片");
        }

        // ── 模拟模式 ──
        if (mockEnabled || modelPath == null || modelPath.trim().isEmpty()) {
            log.info("[人脸核验-模拟] 未启用真实比对，仅做照片存在性校验。userId={}", userId);
            return;
        }

        // ── 真实比对 ──
        byte[] baseBytes = resolveImageBytes(baseImage, "底图");
        byte[] checkInBytes = resolveImageBytes(checkInImageUrl, "打卡照片");

        FaceRecModel model = getModel();
        R<Float> result;
        try {
            result = model.featureComparison(baseBytes, checkInBytes);
        } catch (Exception e) {
            log.error("人脸比对执行异常 userId={}: {}", userId, e.getMessage());
            throw BusinessException.validationFailed("人脸比对失败，请重新拍摄");
        }

        if (result == null || !result.isSuccess() || result.getData() == null) {
            String msg = result == null ? "未知错误" : result.getMessage();
            log.warn("人脸比对未得到有效结果 userId={} msg={}", userId, msg);
            throw BusinessException.validationFailed(
                    "未能识别到清晰人脸，请正对镜头、保证光线充足后重试");
        }

        float similarity = result.getData();
        double threshold = configuredThreshold > 0
                ? configuredThreshold
                : FaceRecModelEnum.SEETA_FACE6_MODEL.getThreshold();

        log.info("人脸比对结果 userId={} similarity={} threshold={}", userId, similarity, threshold);

        if (similarity < threshold) {
            throw BusinessException.validationFailed(
                    "人脸核验未通过，请本人正对镜头重新拍摄（相似度 " + Math.round(similarity * 100) + "%）");
        }
    }

    // ─────────────────────────── 模型加载 ───────────────────────────

    /** 主动预热模型（供 /api/diagnostic/face/load 调用） */
    public void warmUp() {
        getModel();
    }

    /**
     * 直接比对两张图片（供 /api/diagnostic/face/compare 使用）。
     * 用于在不走打卡流程的情况下验证模型是否工作、以及本项目的阈值是否合适。
     */
    public Map<String, Object> compare(byte[] imageA, byte[] imageB) {
        FaceRecModel model = getModel();
        R<Float> result = model.featureComparison(imageA, imageB);
        Map<String, Object> m = new LinkedHashMap<>();
        double threshold = configuredThreshold > 0
                ? configuredThreshold : FaceRecModelEnum.SEETA_FACE6_MODEL.getThreshold();
        m.put("threshold", threshold);
        if (result == null || !result.isSuccess() || result.getData() == null) {
            m.put("success", false);
            m.put("message", result == null ? "未知错误" : result.getMessage());
            return m;
        }
        float similarity = result.getData();
        m.put("success", true);
        m.put("similarity", similarity);
        m.put("similarityPercent", Math.round(similarity * 100));
        m.put("samePerson", similarity >= threshold);
        return m;
    }

    private FaceRecModel getModel() {
        FaceRecModel local = faceRecModel;
        if (local != null) { return local; }
        if (loadFailed) {
            throw BusinessException.validationFailed("人脸模型不可用，请联系管理员检查 face.model-path 配置");
        }
        synchronized (this) {
            if (faceRecModel != null) { return faceRecModel; }
            if (loadFailed) {
                throw BusinessException.validationFailed("人脸模型不可用，请联系管理员检查 face.model-path 配置");
            }
            try {
                File dir = new File(modelPath);
                if (!dir.exists() || !dir.isDirectory()) {
                    throw new IllegalStateException("模型目录不存在: " + modelPath);
                }
                log.info("正在加载 SeetaFace6 人脸模型，目录: {}", modelPath);
                long t0 = System.currentTimeMillis();

                // 必须在获取模型之前设置缓存路径，否则会用默认的 C:/Users/{user}/smartjavaai_cache
                if (cachePath != null && !cachePath.trim().isEmpty()) {
                    Config.setCachePath(cachePath.trim());
                    log.info("已设置 SmartJavaAI 缓存路径: {}", cachePath.trim());
                } else {
                    log.info("未配置 face.cache-path，使用默认缓存路径: {}", Config.getCachePath());
                }

                FaceDetConfig detConfig = new FaceDetConfig();
                detConfig.setModelEnum(FaceDetModelEnum.SEETA_FACE6_MODEL);
                detConfig.setModelPath(modelPath);
                detConfig.setConfidenceThreshold(0.5d);
                FaceDetModel detModel = FaceDetModelFactory.getInstance().getModel(detConfig);

                FaceRecConfig recConfig = new FaceRecConfig();
                recConfig.setModelEnum(FaceRecModelEnum.SEETA_FACE6_MODEL);
                recConfig.setModelPath(modelPath);
                recConfig.setCropFace(true);   // 先裁出人脸区域
                recConfig.setAlign(true);      // 再做对齐，显著提升比对稳定性
                recConfig.setAutoLoadFace(false); // 不使用人脸库，避免初始化 SQLite
                recConfig.setDetectModel(detModel);

                faceRecModel = FaceRecModelFactory.getInstance().getModel(recConfig);
                log.info("SeetaFace6 人脸模型加载完成，耗时 {} ms", System.currentTimeMillis() - t0);
                return faceRecModel;
            } catch (Throwable t) {
                loadFailed = true;
                log.error("人脸模型加载失败，将拒绝人脸打卡: {}", t.getMessage(), t);
                throw BusinessException.validationFailed("人脸模型加载失败：" + t.getMessage());
            }
        }
    }

    // ─────────────────────────── 图片读取 ───────────────────────────

    /**
     * 把数据库里存的图片地址解析成字节。
     * 支持三种形式：/uploads/xxx.jpg（本地上传目录）、本地绝对路径、http(s) 远程地址。
     */
    private byte[] resolveImageBytes(String url, String label) {
        if (url == null || url.isEmpty()) {
            throw BusinessException.validationFailed(label + "为空");
        }
        try {
            // 1) 上传接口返回的相对地址
            if (url.startsWith("/uploads/")) {
                String name = url.substring("/uploads/".length());
                File f = new File(new File(uploadPath).getAbsoluteFile(), name);
                if (f.exists()) { return Files.readAllBytes(f.toPath()); }
                throw new IllegalStateException(label + "文件不存在: " + f.getAbsolutePath());
            }
            // 2) http(s) 远程地址
            if (url.startsWith("http://") || url.startsWith("https://")) {
                HttpHeaders headers = new HttpHeaders();
                headers.setAccept(java.util.Collections.singletonList(MediaType.ALL));
                ResponseEntity<byte[]> resp = restTemplate.exchange(
                        url, HttpMethod.GET, new HttpEntity<>(headers), byte[].class);
                byte[] body = resp.getBody();
                if (body == null || body.length == 0) {
                    throw new IllegalStateException(label + "下载为空");
                }
                return body;
            }
            // 3) 本地绝对路径
            File f = new File(url);
            if (f.exists()) { return Files.readAllBytes(f.toPath()); }

            throw new IllegalStateException(label + "无法定位: " + url);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("读取{}失败 {}: {}", label, url, e.getMessage());
            throw BusinessException.validationFailed("读取" + label + "失败：" + e.getMessage());
        }
    }

    /** 供健康检查/诊断用：返回当前人脸核验的状态 */
    public Map<String, Object> status() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("mockEnabled", mockEnabled);
        m.put("modelPath", modelPath);
        m.put("modelLoaded", faceRecModel != null);
        m.put("loadFailed", loadFailed);
        m.put("threshold", configuredThreshold > 0
                ? configuredThreshold : FaceRecModelEnum.SEETA_FACE6_MODEL.getThreshold());
        return m;
    }
}
