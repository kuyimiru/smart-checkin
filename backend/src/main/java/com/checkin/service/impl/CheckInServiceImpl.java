package com.checkin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.checkin.common.exception.BusinessException;
import com.checkin.common.utils.DistanceUtil;
import com.checkin.dto.request.CheckInRequest;
import com.checkin.entity.Activity;
import com.checkin.entity.CheckIn;
import com.checkin.entity.Leave;
import com.checkin.mapper.ActivityMapper;
import com.checkin.mapper.CheckInMapper;
import com.checkin.mapper.LeaveMapper;
import com.checkin.service.CheckInService;
import com.checkin.service.FaceVerifyService;
import com.checkin.service.PermissionService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 打卡服务。
 *
 * 相对 v1 的修复点：
 *   - 状态判定规则重写：清单 C-04/C-06 要求「结束时间内=正常 / 结束后1小时内=迟到(可补签) / 超过1小时=异常」，
 *     v1 是「早于开始=异常、晚于结束=迟到」，规则完全不符，且永远判不出"超时异常"。
 *   - 补齐四道前置校验：活动状态、活动起止日期、打卡频率日、参与者身份（清单 A-05/C-01 相关用例）
 *   - 请假互斥：当日请假已审批通过则不可打卡（清单 L-04）
 *   - 人脸核验改为调用 FaceVerifyService（清单 C-02），而不是只判 URL 非空
 *   - 重复打卡用「先查后写 + 唯一键兜底」保证并发下只留一条记录（清单 C-05）
 */
@Service
public class CheckInServiceImpl implements CheckInService {

    private static final Pattern NUMBER = Pattern.compile("\\d+");
    /** 结束时间后允许补签的时长（小时），清单 C-06 */
    private static final int MAKEUP_WINDOW_HOURS = 1;

    private final CheckInMapper checkInMapper;
    private final ActivityMapper activityMapper;
    private final LeaveMapper leaveMapper;
    private final PermissionService permissionService;
    private final FaceVerifyService faceVerifyService;

    public CheckInServiceImpl(CheckInMapper checkInMapper,
                              ActivityMapper activityMapper,
                              LeaveMapper leaveMapper,
                              PermissionService permissionService,
                              FaceVerifyService faceVerifyService) {
        this.checkInMapper = checkInMapper;
        this.activityMapper = activityMapper;
        this.leaveMapper = leaveMapper;
        this.permissionService = permissionService;
        this.faceVerifyService = faceVerifyService;
    }

    @Override
    public CheckIn doCheckIn(Long userId, CheckInRequest req) {
        Activity activity = activityMapper.selectById(req.getActivityId());
        if (activity == null) {
            throw BusinessException.notFound("活动不存在");
        }

        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();

        // ── 前置校验 1：活动必须处于可用状态（区分"已关闭"和"已结束"，提示才准确）
        if (!"active".equals(activity.getStatus())) {
            String msg = "archived".equals(activity.getStatus())
                    ? "活动已于 " + activity.getEndDate() + " 结束，无法打卡"
                    : "活动已关闭，无法打卡";
            throw BusinessException.validationFailed(msg);
        }

        // ── 前置校验 2：必须在活动起止日期内（给出明确原因，而不是笼统的"不在周期内"）
        if (activity.isNotStarted()) {
            throw BusinessException.validationFailed("活动将于 " + activity.getStartDate() + " 开始，暂时无法打卡");
        }
        if (activity.isExpired()) {
            throw BusinessException.validationFailed("活动已于 " + activity.getEndDate() + " 结束，无法打卡");
        }

        // ── 前置校验 3：必须落在打卡频率日（清单：自定义频率当日不可打卡返回 422）
        if (!isCheckInDay(activity, today)) {
            throw BusinessException.validationFailed("今天不是该活动的打卡日");
        }

        // ── 前置校验 4：必须是活动的有效参与者（清单：非成员打卡返回 403）
        permissionService.requireActiveMember(req.getActivityId(), userId);

        // ── 前置校验 5：当日已批准请假则不可打卡（清单 L-04 请假与打卡互斥）
        if (hasApprovedLeave(userId, req.getActivityId(), today)) {
            throw BusinessException.validationFailed("今日已请假，无需打卡");
        }

        String type = activity.getCheckInType();

        // 服务端距离校验（location / combo 模式）——防伪造坐标
        if ("location".equals(type) || "combo".equals(type)) {
            validateLocation(activity, req);
        }

        // 人脸核验（face / combo 模式）——清单 C-02 要求服务端比对身份
        if ("face".equals(type) || "combo".equals(type)) {
            faceVerifyService.verify(userId, req.getFaceImageUrl());
        }

        String status = calcStatus(activity, now);

        // 今日已打卡 → 覆盖（清单 C-05：前端已提示用户是否覆盖）
        CheckIn existing = findToday(userId, req.getActivityId(), today);
        if (existing != null) {
            return overwrite(existing, req, type, status, now);
        }

        CheckIn ci = new CheckIn();
        ci.setActivityId(req.getActivityId());
        ci.setUserId(userId);
        ci.setCheckInDate(today);
        ci.setCheckInTime(now);
        ci.setStatus(status);
        ci.setType(type);
        ci.setLocationLat(req.getLocationLat());
        ci.setLocationLng(req.getLocationLng());
        ci.setFaceImageUrl(req.getFaceImageUrl());
        try {
            checkInMapper.insert(ci);
            return ci;
        } catch (DuplicateKeyException e) {
            // 并发下的兜底：唯一键 uk_activity_user_date 命中，转为覆盖写，保证最终只有一条记录
            CheckIn concurrent = findToday(userId, req.getActivityId(), today);
            if (concurrent == null) { throw e; }
            return overwrite(concurrent, req, type, status, now);
        }
    }

    /**
     * 打卡状态判定（清单 C-04 / C-06）。
     *
     *   打卡开始时间 ~ 结束时间      → normal   （正常）
     *   结束时间 ~ 结束时间 + 1 小时 → late     （迟到，可补签）
     *   结束时间 + 1 小时之后        → abnormal （异常，不可补签）
     *
     * 注：早于开始时间一律不发"异常"，前端会拦（按钮处于"等待打卡"状态）。
     */
    private String calcStatus(Activity activity, LocalTime now) {
        LocalTime start = activity.getCheckInStartTime();
        LocalTime end = activity.getCheckInEndTime();

        if (end == null) { return "normal"; }
        if (!now.isAfter(end)) { return "normal"; }                       // 结束时间内（含边界）
        if (now.isAfter(end.plusHours(MAKEUP_WINDOW_HOURS))) { return "abnormal"; } // 超过1小时，不可补签
        return "late";                                                     // 1小时以内，迟到补签
    }

    /** 判断某天是否属于打卡日（frequency_type=custom 时看 frequency_days） */
    private boolean isCheckInDay(Activity activity, LocalDate date) {
        if (!"custom".equals(activity.getFrequencyType())) { return true; }
        String json = activity.getFrequencyDays();
        if (json == null || json.trim().isEmpty()) { return true; }
        int isoDayOfWeek = date.getDayOfWeek().getValue(); // 周一=1 ... 周日=7
        Matcher m = NUMBER.matcher(json);
        while (m.find()) {
            if (Integer.parseInt(m.group()) == isoDayOfWeek) { return true; }
        }
        return false;
    }

    /** 当日是否存在已审批通过的请假 */
    private boolean hasApprovedLeave(Long userId, Long activityId, LocalDate today) {
        Long c = leaveMapper.selectCount(new LambdaQueryWrapper<Leave>()
                .eq(Leave::getUserId, userId)
                .eq(Leave::getActivityId, activityId)
                .eq(Leave::getStatus, "approved")
                .le(Leave::getStartDate, today)
                .ge(Leave::getEndDate, today));
        return c != null && c > 0;
    }

    private CheckIn findToday(Long userId, Long activityId, LocalDate today) {
        return checkInMapper.selectOne(new LambdaQueryWrapper<CheckIn>()
                .eq(CheckIn::getActivityId, activityId)
                .eq(CheckIn::getUserId, userId)
                .eq(CheckIn::getCheckInDate, today));
    }

    private CheckIn overwrite(CheckIn target, CheckInRequest req, String type, String status, LocalTime now) {
        target.setCheckInTime(now);
        target.setStatus(status);
        target.setType(type);
        target.setLocationLat(req.getLocationLat());
        target.setLocationLng(req.getLocationLng());
        target.setFaceImageUrl(req.getFaceImageUrl());
        checkInMapper.updateById(target);
        return target;
    }

    private void validateLocation(Activity activity, CheckInRequest req) {
        if (req.getLocationLat() == null || req.getLocationLng() == null) {
            throw BusinessException.validationFailed("缺少定位信息，请先获取位置");
        }
        if (activity.getLocationLat() == null || activity.getLocationLng() == null) {
            throw BusinessException.validationFailed("该活动未配置打卡点坐标");
        }
        int radius = activity.getLocationRadius() == null ? 500 : activity.getLocationRadius();
        double distance = DistanceUtil.calculateDistance(
                req.getLocationLat().doubleValue(), req.getLocationLng().doubleValue(),
                activity.getLocationLat().doubleValue(), activity.getLocationLng().doubleValue());

        if (distance > radius) {
            throw new BusinessException(422,
                    "您不在打卡范围内，当前距离" + Math.round(distance) + "米",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    @Override
    public List<CheckIn> getRecords(Long activityId, Long userId, LocalDate start, LocalDate end) {
        LambdaQueryWrapper<CheckIn> w = new LambdaQueryWrapper<CheckIn>()
                .eq(CheckIn::getActivityId, activityId)
                .eq(CheckIn::getUserId, userId)
                .orderByDesc(CheckIn::getCheckInDate)
                .orderByDesc(CheckIn::getCheckInTime);
        if (start != null) { w.ge(CheckIn::getCheckInDate, start); }
        if (end != null) { w.le(CheckIn::getCheckInDate, end); }
        return checkInMapper.selectList(w);
    }

    @Override
    public CheckIn getByDate(Long activityId, Long userId, LocalDate date) {
        return findToday(userId, activityId, date);
    }
}
