package com.checkin.service;
import com.checkin.dto.request.LeaveRequest;
import com.checkin.entity.Leave;
import java.util.List;
import java.util.Map;

public interface LeaveService {
    Leave submit(Long userId, LeaveRequest req);
    /** 我的请假记录（附带活动名称） */
    List<Map<String, Object>> getMyLeaves(Long userId);
    /** 待我审批的请假（附带申请人姓名） */
    List<Map<String, Object>> getPendingList(Long approverId);
    void approve(Long leaveId, Long approverId);
    void reject(Long leaveId, Long approverId, String reason);
}
