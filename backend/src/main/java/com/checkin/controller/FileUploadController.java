package com.checkin.controller;

import com.checkin.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.*;

@RestController
@RequestMapping("/api/upload")
public class FileUploadController {

    @Value("${file.upload-path:./uploads}")
    private String uploadPath;

    /** 上传打卡照片（带水印的现场照） */
    @PostMapping("/checkin")
    public Map<String, Object> uploadCheckin(@RequestParam("file") MultipartFile file) {
        return save(file, "checkin");
    }

    /**
     * 上传人脸底图（接口清单中的 /api/upload/face）。
     * 人脸比对需要服务端能读到这张图，所以必须落到服务器上——
     * 早期版本直接把微信临时路径存进了 users.face_image_url，服务端根本读不到。
     */
    @PostMapping("/face")
    public Map<String, Object> uploadFace(@RequestParam("file") MultipartFile file) {
        return save(file, "face");
    }

    /** 统一的落盘逻辑，返回相对地址 /uploads/xxx，前端拼 BASE_URL 使用 */
    private Map<String, Object> save(MultipartFile file, String prefix) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("文件为空");
        }
        try {
            // 必须用绝对路径：Spring 的 MultipartFile.transferTo(File) 对相对路径
            // 会按 Servlet 规范解析到容器的临时目录（tomcat.xxx/work/...），从而报
            // FileNotFoundException —— 这是"上传人脸底图失败"的根因。
            File dir = new File(uploadPath).getAbsoluteFile();
            if (!dir.exists()) { dir.mkdirs(); }

            String original = file.getOriginalFilename();
            String ext = "";
            if (original != null && original.contains(".")) {
                ext = original.substring(original.lastIndexOf("."));
            }
            String filename = prefix + "_" + System.currentTimeMillis() + "_"
                    + UUID.randomUUID().toString().substring(0, 8) + ext;
            file.transferTo(new File(dir, filename).getAbsoluteFile());

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("url", "/uploads/" + filename);
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("code", 200);
            r.put("data", data);
            r.put("message", "上传成功");
            return r;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw BusinessException.badRequest("上传失败: " + e.getMessage());
        }
    }
}
