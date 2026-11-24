package com.checkin.service;
import com.checkin.entity.Roster;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

public interface RosterService {
    List<Roster> importRoster(Long activityId, Long userId, MultipartFile file);
    List<Roster> list(Long activityId);
    void removeMember(Long activityId, Long targetUserId, Long operatorId);
    void matchRoster(Long activityId);
}
