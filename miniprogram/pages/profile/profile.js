const { get, resolveUrl } = require('../../utils/request');
const imageUtil = require('../../utils/image');

Page({
  data: {
    userInfo: {},
    faceImagePreview: '',
    realName: '',
    stats: {
      totalCheckIns: 0,
      streak: 0,
      activityCount: 0
    }
  },

  onShow() {
    this.setData({
      realName: wx.getStorageSync('realName') || '未设置'
    });
    this.loadProfile();
    this.loadStats();
  },

  async loadProfile() {
    if (getApp().isOffline()) return;
    try {
      const res = await get('/api/users/profile');
      if (res.code === 200) {
        const u = res.data || {};
        this.setData({
          userInfo: u,
          // 后端存的是 /uploads/xxx.jpg 相对地址，<image> 需要完整地址
          faceImagePreview: resolveUrl(u.faceImageUrl)
        });
        // 真机上 http 图片可能被拦 → 先下载到本地再显示（失败自动退回原地址）
        if (u.faceImageUrl) {
          imageUtil.loadRemote(u.faceImageUrl).then((local) => {
            this.setData({ faceImagePreview: local });
          });
        }
      }
    } catch (e) {
      // silent fail
    }
  },

  async loadStats() {
    if (getApp().isOffline()) return;
    try {
      const res = await get('/api/users/stats');
      if (res.code === 200) {
        this.setData({ stats: res.data || this.data.stats });
      }
    } catch (e) {
      // use defaults
    }
  },

  goProfileEdit() {
    wx.navigateTo({ url: '/pages/profile-edit/profile-edit' });
  },

  /** U-04：从"我的"跳转到首页的对应分类（我参与的 / 我发起的 / 我负责的） */
  goMyActivities(e) {
    var tab = e.currentTarget.dataset.tab;
    try { wx.setStorageSync('indexTab', tab); } catch (err) {}
    wx.switchTab({ url: '/pages/index/index' });
  },

  goPrivacy() {
    wx.navigateTo({ url: '/pages/privacy/privacy' });
  },

  goCalendar() {
    wx.navigateTo({ url: '/pages/calendar/calendar' });
  },

  goAbout() {
    wx.showModal({
      title: '关于智能打卡',
      content: '版本 1.0.0\n基于微信小程序原生开发\n\n为实习、宿舍、研学、团建等场景\n提供智能化的考勤打卡体验',
      showCancel: false,
      confirmText: '知道了'
    });
  },

  logout() {
    wx.showModal({
      title: '退出登录',
      content: '确定要退出登录吗？',
      confirmColor: '#FF3B30',
      success: (res) => {
        if (res.confirm) {
          const app = getApp();
          app.logout();
        }
      }
    });
  }
});
