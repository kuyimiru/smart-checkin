package com.checkin.common.exception;


import org.springframework.http.HttpStatus;


public class BusinessException extends RuntimeException {
    private final int code;
    private final HttpStatus httpStatus;

    public BusinessException(int code, String message) { super(message); this.code = code; this.httpStatus = HttpStatus.OK; }
    public BusinessException(int code, String message, HttpStatus httpStatus) { super(message); this.code = code; this.httpStatus = httpStatus; }

    public static BusinessException badRequest(String m) { return new BusinessException(400, m); }
    public static BusinessException unauthorized(String m) { return new BusinessException(401, m, HttpStatus.UNAUTHORIZED); }
    public static BusinessException forbidden(String m) { return new BusinessException(403, m, HttpStatus.FORBIDDEN); }
    public static BusinessException notFound(String m) { return new BusinessException(404, m, HttpStatus.NOT_FOUND); }
    public static BusinessException conflict(String m) { return new BusinessException(409, m, HttpStatus.CONFLICT); }
    /**
     * 业务校验失败 → HTTP 422。
     *
     * v1 这里错误地使用了 HttpStatus.OK，导致服务端虽然判定了"超出打卡范围""邀请码已失效"
     * 等错误，HTTP 状态码却是 200，与测试用例要求（返回 422）不符，前端也必须解析 body 才能
     * 知道失败。现修正为真正的 422。
     */
    public static BusinessException validationFailed(String m) { return new BusinessException(422, m, HttpStatus.UNPROCESSABLE_ENTITY); }

    public int getCode() { return code; }
    public HttpStatus getHttpStatus() { return httpStatus; }
}
