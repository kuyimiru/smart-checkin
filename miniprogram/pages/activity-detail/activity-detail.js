const { get, post } = require('../../utils/request');
const { getCheckInButtonState, formatDate } = require('../../utils/util');

Page({
  data: {
    id: '',
    activity: {},
    todayRecord: null,
    leaveInfo: null,
    btnState: {},
    stats: null
  },

  onLoad(options) {
    this.setData({ id: options.id });
    this.loadData();
  },

  onShow() {
    if (this.data.id) this.loadData();
  },

  goBack() {
    wx.navigateBack();
  },

  async loadData() {
    if (getApp().isOffline()) return;
    try {
      const [activityRes, todayRes, statsRes, leaveRes] = await Promise.all([
        get('/api/activities/' + this.data.id),
        get('/api/check-ins/' + formatDate(new Date()) + '?activityId=' + this.data.id).catch(() => ({ data: null })),
        get('/api/activities/' + this.data.id + '/statistics').catch(() => ({ data: null })),
        get('/api/leaves').catch(() => ({ data: [] }))
      ]);

      if (activityRes.code === 200) {
        const activity = activityRes.data;
        const todayRecord = todayRes.data || null;
        // 今日已批准的请假 → 打卡按钮显示"今日已请假"（清单 L-04 互斥 + A-04 按钮状态）
        const leaveInfo = this.findTodayLeave(leaveRes.data);
        const btnState = getCheckInButtonState(activity, todayRecord, leaveInfo);
        const stats = statsRes ? (statsRes.data || null) : null;

        this.setData({ activity, todayRecord, leaveInfo, btnState, stats });
      }
    } catch (e) {
      console.error('加载活动详情失败', e);
    }
  },

  /** 在"我的请假"里找出覆盖今天、且针对本活动的已批准记录 */
  findTodayLeave(leaves) {
    if (!leaves || !leaves.length) return null;
    const today = formatDate(new Date());
    for (let i = 0; i < leaves.length; i++) {
      const l = leaves[i];
      if (String(l.activityId) !== String(this.data.id)) continue;
      if (l.status !== 'approved') continue;
      if (l.startDate <= today && today <= l.endDate) return l;
    }
    return null;
  },

  goCheckIn() {
    if (this.data.btnState.disabled) return;
    const type = this.data.activity.checkInType;
    wx.navigateTo({
      url: '/pages/check-in/check-in?activityId=' + this.data.id + '&type=' + type
    });
  },

  goCheckInHistory() {
    wx.navigateTo({
      url: '/pages/check-in-history/check-in-history?activityId=' + this.data.id
    });
  },

  goLeaveApply() {
    wx.navigateTo({
      url: '/pages/leave-apply/leave-apply?activityId=' + this.data.id
    });
  },

  goCalendar() {
    wx.navigateTo({
      url: '/pages/calendar/calendar?activityId=' + this.data.id
    });
  },

  async leaveActivity() {
    wx.showModal({
      title: '确认退出',
      content: '退出后将无法打卡，确定要退出该活动吗？',
      success: async (res) => {
        if (res.confirm) {
          try {
            await post('/api/activities/' + this.data.id + '/leave');
            wx.showToast({ title: '已退出活动', icon: 'success' });
            setTimeout(() => wx.navigateBack(), 1500);
          } catch (e) {
            wx.showToast({ title: e.message || '操作失败', icon: 'none' });
          }
        }
      }
    });
  }
});
