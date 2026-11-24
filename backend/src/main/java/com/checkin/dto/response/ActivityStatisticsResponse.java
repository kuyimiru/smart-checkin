package com.checkin.dto.response;



public class ActivityStatisticsResponse {
    private int totalMembers;
    private int checkedInToday;
    private int normalCount;
    private int lateCount;
    private int abnormalCount;
    private int leaveCount;
    private int uncheckedCount;
    public int getTotalMembers() { return totalMembers; }
    public void setTotalMembers(int v) { totalMembers = v; }
    public int getCheckedInToday() { return checkedInToday; }
    public void setCheckedInToday(int v) { checkedInToday = v; }
    public int getNormalCount() { return normalCount; }
    public void setNormalCount(int v) { normalCount = v; }
    public int getLateCount() { return lateCount; }
    public void setLateCount(int v) { lateCount = v; }
    public int getAbnormalCount() { return abnormalCount; }
    public void setAbnormalCount(int v) { abnormalCount = v; }
    public int getLeaveCount() { return leaveCount; }
    public void setLeaveCount(int v) { leaveCount = v; }
    public int getUncheckedCount() { return uncheckedCount; }
    public void setUncheckedCount(int v) { uncheckedCount = v; }
}
