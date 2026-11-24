package com.checkin.controller;

import com.checkin.dto.request.CheckInRequest;
import com.checkin.entity.CheckIn;
import com.checkin.service.CheckInService;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/check-ins")
public class CheckInController {
    private final CheckInService checkInService;
    public CheckInController(CheckInService cis) { this.checkInService = cis; }

    @PostMapping
    public Map<String, Object> doCheckIn(HttpServletRequest req, @Valid @RequestBody CheckInRequest cr) {
        Long userId = (Long) req.getAttribute("userId");
        CheckIn ci = checkInService.doCheckIn(userId, cr);
        Map<String, Object> r = new LinkedHashMap<>(); r.put("code", 200); r.put("data", ci); r.put("message", "打卡成功"); return r;
    }

    @GetMapping
    public Map<String, Object> getRecords(@RequestParam Long activityId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate, HttpServletRequest req) {
        Long userId = (Long) req.getAttribute("userId");
        LocalDate s = startDate != null ? LocalDate.parse(startDate) : null;
        LocalDate e = endDate != null ? LocalDate.parse(endDate) : null;
        Map<String, Object> r = new LinkedHashMap<>(); r.put("code", 200); r.put("data", checkInService.getRecords(activityId, userId, s, e)); return r;
    }

    @GetMapping("/{date}")
    public Map<String, Object> getByDate(@RequestParam Long activityId, @PathVariable String date, HttpServletRequest req) {
        Long userId = (Long) req.getAttribute("userId");
        Map<String, Object> r = new LinkedHashMap<>(); r.put("code", 200); r.put("data", checkInService.getByDate(activityId, userId, LocalDate.parse(date))); return r;
    }
}
