package com.checkin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.checkin.common.exception.BusinessException;
import com.checkin.common.utils.ExcelUtil;
import com.checkin.entity.Activity;
import com.checkin.entity.Roster;
import com.checkin.entity.User;
import com.checkin.mapper.ActivityMapper;
import com.checkin.mapper.RosterMapper;
import com.checkin.mapper.UserMapper;
import com.checkin.service.ActivityService;
import com.checkin.service.PermissionService;
import com.checkin.service.RosterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

/**
 * 花名册服务（清单 R-01 ~ R-04）。
 *
 * v1 只有接口声明、没有实现类，ExcelUtil 也没有调用方，前端花名册页必然 404。此处补齐。
 */
@Service
public class RosterServiceImpl implements RosterService {

    private static final Logger log = LoggerFactory.getLogger(RosterServiceImpl.class);

    private final RosterMapper rosterMapper;
    private final ActivityMapper activityMapper;
    private final UserMapper userMapper;
    private final PermissionService permissionService;
    private final ActivityService activityService;

    public RosterServiceImpl(RosterMapper rosterMapper,
                             ActivityMapper activityMapper,
                             UserMapper userMapper,
                             PermissionService permissionService,
                             ActivityService activityService) {
        this.rosterMapper = rosterMapper;
        this.activityMapper = activityMapper;
        this.userMapper = userMapper;
        this.permissionService = permissionService;
        this.activityService = activityService;
    }

    @Override
    @Transactional
    public List<Roster> importRoster(Long activityId, Long userId, MultipartFile file) {
        Activity activity = requireActivity(activityId);
        permissionService.requireAdmin(activity, userId);

        List<Roster> parsed;
        try {
            parsed = ExcelUtil.importRoster(file, activityId);
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            throw BusinessException.badRequest("文件解析失败：" + e.getMessage());
        }

        // 按「姓名+性别+电话」去重，避免同一份文件重复导入产生脏数据
        Set<String> exists = new HashSet<>();
        for (Roster r : rosterMapper.selectList(new LambdaQueryWrapper<Roster>()
                .eq(Roster::getActivityId, activityId))) {
            exists.add(dedupeKey(r.getName(), r.getGender(), r.getPhone()));
        }

        int inserted = 0;
        for (Roster r : parsed) {
            if (!exists.add(dedupeKey(r.getName(), r.getGender(), r.getPhone()))) { continue; }
            rosterMapper.insert(r);
            inserted++;
        }
        log.info("花名册导入 activityId={} 解析={} 新增={}", activityId, parsed.size(), inserted);

        // 导入后立即做一次匹配（清单 R-02）
        matchRoster(activityId);

        return rosterMapper.selectList(new LambdaQueryWrapper<Roster>()
                .eq(Roster::getActivityId, activityId).orderByAsc(Roster::getId));
    }

    @Override
    public List<Roster> list(Long activityId) {
        return rosterMapper.selectList(new LambdaQueryWrapper<Roster>()
                .eq(Roster::getActivityId, activityId).orderByAsc(Roster::getId));
    }

    @Override
    public void removeMember(Long activityId, Long targetUserId, Long operatorId) {
        activityService.removeMember(activityId, targetUserId, operatorId);
    }

    /**
     * 花名册与注册用户的匹配（清单 R-02：按「姓名+性别」精确匹配）。
     * 若用户的 gender 尚未填写，则退化为仅按姓名匹配，保证不会漏匹配。
     */
    @Override
    @Transactional
    public void matchRoster(Long activityId) {
        List<Roster> rosterList = list(activityId);
        if (rosterList.isEmpty()) { return; }

        for (Roster r : rosterList) {
            if (r.getName() == null || r.getName().trim().isEmpty()) { continue; }
            User matched = null;
            if (r.getGender() != null && !r.getGender().isEmpty()) {
                // 姓名 + 性别精确匹配优先
                matched = userMapper.selectOne(new LambdaQueryWrapper<User>()
                        .eq(User::getRealName, r.getName().trim())
                        .eq(User::getGender, r.getGender())
                        .last("limit 1"));
            }
            if (matched == null) {
                // 用户未填写性别时退化为仅按姓名匹配，避免漏匹配
                matched = userMapper.selectOne(new LambdaQueryWrapper<User>()
                        .eq(User::getRealName, r.getName().trim())
                        .last("limit 1"));
            }
            r.setMatchedUserId(matched == null ? null : matched.getId());
            rosterMapper.updateById(r);
        }
    }

    private Activity requireActivity(Long activityId) {
        Activity a = activityMapper.selectById(activityId);
        if (a == null) { throw BusinessException.notFound("活动不存在"); }
        return a;
    }

    private String dedupeKey(String name, String gender, String phone) {
        return (name == null ? "" : name) + "|" + (gender == null ? "" : gender) + "|" + (phone == null ? "" : phone);
    }
}
