require('./utils/request');
require('./utils/util');
require('./utils/location');

App({
  onLaunch: function () {
    var token = wx.getStorageSync('token');
    if (token) {
      this.globalData.token = token;
      this.globalData.userId = wx.getStorageSync('userId') || 0;
      this.globalData.hasProfile = !!(wx.getStorageSync('realName') || wx.getStorageSync('nickName'));
      this.globalData.nickName = wx.getStorageSync('nickName') || '';
      this.globalData.avatarUrl = wx.getStorageSync('avatarUrl') || '';
      // 检测是否为离线模式
      this.globalData._offline = token.indexOf('offline-token') === 0;
      if (this.globalData._offline) {
        console.log('[App] 当前为离线体验模式');
      }
    }
  },

  checkLogin: function () {
    var token = wx.getStorageSync('token');
    if (!token) {
      wx.reLaunch({ url: '/pages/login/login' });
      return false;
    }
    return true;
  },

  // 检测是否为离线模式
  isOffline: function () {
    var token = wx.getStorageSync('token') || '';
    return token.indexOf('offline-token') === 0;
  },

  /**
   * 清单 N-03：引导用户授权订阅消息。
   * 把在微信公众平台申请到的模板ID填进 globalData.templateIds 即可生效；
   * 留空则直接跳过（本地开发无需配置）。
   */
  requestSubscribeMessages: function () {
    var ids = (this.globalData.templateIds || []).filter(function (id) { return !!id; });
    if (!ids.length) return;
    if (typeof wx.requestSubscribeMessage !== 'function') return;

    // 每天最多引导一次，避免骚扰
    var today = new Date().toISOString().slice(0, 10);
    if (wx.getStorageSync('subscribePromptDate') === today) return;

    wx.requestSubscribeMessage({
      tmplIds: ids,
      success: function (res) {
        wx.setStorageSync('subscribePromptDate', today);
        var templates = {};
        ids.forEach(function (id) {
          if (res[id]) { templates[id] = res[id]; }
        });
        if (Object.keys(templates).length === 0) return;
        require('./utils/request').post('/api/notifications/subscribe', { templates: templates })
          .catch(function () { /* 授权上报失败不影响使用 */ });
      },
      fail: function () { /* 用户环境不支持则静默跳过 */ }
    });
  },

  logout: function () {
    wx.removeStorageSync('token');
    wx.removeStorageSync('userId');
    wx.removeStorageSync('nickName');
    wx.removeStorageSync('avatarUrl');
    wx.removeStorageSync('realName');
    this.globalData.token = '';
    this.globalData.userId = 0;
    this.globalData.hasProfile = false;
    this.globalData.nickName = '';
    this.globalData.avatarUrl = '';
    this.globalData._offline = false;
    wx.reLaunch({ url: '/pages/login/login' });
  },

  globalData: {
    hasProfile: false,
    token: '',
    userId: 0,
    nickName: '',
    avatarUrl: '',
    _offline: false,
    // 订阅消息模板ID（在微信公众平台申请后填入，清单 N-01/N-02/N-03）
    templateIds: [
      // '请假审批提醒模板ID',
      // '请假结果通知模板ID',
      // '未打卡提醒模板ID'
    ]
  }
});
