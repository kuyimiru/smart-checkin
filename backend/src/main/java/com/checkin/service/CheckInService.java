package com.checkin.service;
import com.checkin.dto.request.CheckInRequest;
import com.checkin.entity.CheckIn;
import java.time.LocalDate;
import java.util.List;

public interface CheckInService {
    CheckIn doCheckIn(Long userId, CheckInRequest req);
    List<CheckIn> getRecords(Long activityId, Long userId, LocalDate start, LocalDate end);
    CheckIn getByDate(Long activityId, Long userId, LocalDate date);
}
