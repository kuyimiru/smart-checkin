package com.checkin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.checkin.common.exception.BusinessException;
import com.checkin.common.utils.AesUtil;
import com.checkin.common.utils.JwtUtil;
import com.checkin.dto.request.LoginRequest;
import com.checkin.dto.response.LoginResponse;
import com.checkin.entity.ActivityManager;
import com.checkin.entity.User;
import com.checkin.mapper.ActivityManagerMapper;
import com.checkin.mapper.UserMapper;
import com.checkin.service.UserService;
import com.checkin.service.WechatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

@Service
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);
    private static final Pattern ID_CARD_PATTERN =
            Pattern.compile("^[1-9]\\d{5}(19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[\\dXx]$");

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final AesUtil aesUtil;
    private final WechatService wechatService;
    private final ActivityManagerMapper managerMapper;

    public UserServiceImpl(UserMapper userMapper, JwtUtil jwtUtil, AesUtil aesUtil,
                           WechatService wechatService, ActivityManagerMapper managerMapper) {
        this.userMapper = userMapper; this.jwtUtil = jwtUtil; this.aesUtil = aesUtil;
        this.wechatService = wechatService;
        this.managerMapper = managerMapper;
    }

    /**
     * 把"负责人"记录绑定到当前用户。
     *
     * 场景：发起人创建活动时按手机号录入了负责人，但对方当时还没注册小程序，
     * 因此 activity_managers.user_id 只能为空 → 对方登录后"我负责的"列表会是空的。
     * 这里在登录 / 完善资料拿到手机号时做一次回填，保证权限与列表都能生效。
     */
    private void bindManagerRecords(User user) {
        if (user == null || user.getId() == null) { return; }
        String phone = user.getPhone();
        if (phone == null || phone.trim().isEmpty()) { return; }
        try {
            managerMapper.update(null, new LambdaUpdateWrapper<ActivityManager>()
                    .eq(ActivityManager::getPhone, phone.trim())
                    .isNull(ActivityManager::getUserId)
                    .set(ActivityManager::getUserId, user.getId()));
        } catch (Exception e) {
            log.warn("回填活动负责人绑定失败 userId={}: {}", user.getId(), e.getMessage());
        }
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        // 1. 用微信 code 换真实 openid
        String openid = wechatService.code2Session(request.getCode());
        if (openid == null) {
            // 2. 微信接口不可用 → deviceId 兜底（开发调试）
            openid = request.getDeviceId();
            if (openid == null || openid.isEmpty()) {
                throw BusinessException.badRequest("登录失败，请重试");
            }
            log.info("微信API不可用，使用设备ID登录: {}", openid);
        }

        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getOpenid, openid));
        if (user == null) {
            user = new User(); user.setOpenid(openid); user.setRealName(""); user.setIdCard("");
            user.setNickName(request.getNickName());
            user.setAvatarUrl(request.getAvatarUrl());
            userMapper.insert(user);
            return new LoginResponse(jwtUtil.generateToken(user.getId()), user.getId(),
                    request.getNickName(), request.getAvatarUrl(), "", false);
        }

        boolean needUpdate = false;
        if (request.getNickName() != null && !request.getNickName().isEmpty()) {
            user.setNickName(request.getNickName()); needUpdate = true;
        }
        if (request.getAvatarUrl() != null && !request.getAvatarUrl().isEmpty()) {
            user.setAvatarUrl(request.getAvatarUrl()); needUpdate = true;
        }
        if (needUpdate) { userMapper.updateById(user); }

        bindManagerRecords(user);

        String token = jwtUtil.generateToken(user.getId());
        boolean hasProfile = user.getRealName() != null && !user.getRealName().isEmpty();
        return new LoginResponse(token, user.getId(), user.getNickName(), user.getAvatarUrl(),
                user.getRealName(), hasProfile);
    }

    @Override
    public void updateProfile(Long userId, User profile) {
        User user = userMapper.selectById(userId);
        if (user == null) throw BusinessException.notFound("用户不存在");

        if (profile.getRealName() != null) user.setRealName(profile.getRealName());
        if (profile.getFaceImageUrl() != null) user.setFaceImageUrl(profile.getFaceImageUrl());
        if (profile.getPhone() != null) user.setPhone(profile.getPhone());

        if (profile.getIdCard() != null) {
            String rawIdCard = profile.getIdCard().trim();
            // 清单 U-03：身份证号不可修改（首次录入后即锁定）
            if (user.getIdCard() != null && !user.getIdCard().isEmpty()) {
                throw BusinessException.validationFailed("身份证号已实名认证，不可修改");
            }
            if (!ID_CARD_PATTERN.matcher(rawIdCard).matches()) {
                throw BusinessException.validationFailed("身份证号格式不正确");
            }
            String encryptedIdCard = aesUtil.encrypt(rawIdCard);
            User existing = userMapper.selectOne(new LambdaQueryWrapper<User>()
                    .eq(User::getIdCard, encryptedIdCard).ne(User::getId, userId));
            if (existing != null) throw BusinessException.conflict("该身份证号已被其他用户绑定");
            user.setIdCard(encryptedIdCard);
        }

        if (profile.getGender() != null && !profile.getGender().trim().isEmpty()) {
            user.setGender(profile.getGender().trim());
        }

        userMapper.updateById(user);
        bindManagerRecords(user);
    }

    @Override
    public User getProfile(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) throw BusinessException.notFound("用户不存在");
        if (user.getIdCard() != null && !user.getIdCard().isEmpty()) {
            try {
                user.setIdCard(AesUtil.maskIdCard(aesUtil.decrypt(user.getIdCard())));
            } catch (Exception e) { user.setIdCard("****"); }
        }
        return user;
    }
}
