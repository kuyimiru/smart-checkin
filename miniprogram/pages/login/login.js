var request = require('../../utils/request');

Page({
  data: { loading: false, showPrivacy: false, agreed: false },

  onLoad: function () {
    var app = getApp();
    // 已有有效 token → 直接跳转
    if (app.globalData.token && app.globalData.token.indexOf('offline') === -1) {
      this.navigateToHome(app);
      return;
    }
    if (wx.getStorageSync('privacyAgreed')) {
      this.setData({ agreed: true });
    } else {
      this.setData({ showPrivacy: true });
    }
  },

  // ── 隐私 ──
  agreePrivacy: function () {
    wx.setStorageSync('privacyAgreed', true);
    this.setData({ showPrivacy: false, agreed: true });
  },
  disagreePrivacy: function () {
    wx.showModal({ title: '提示', content: '需要同意隐私政策才能使用。', showCancel: false, confirmText: '我知道了' });
  },
  // 勾选框 / 协议链接 → 弹出同意弹窗（保持原有交互，不要改）
  showPrivacyPolicy: function () {
    this.setData({ showPrivacy: true });
  },

  // 单独的入口：查看完整的隐私政策页面
  openPrivacyDoc: function () {
    wx.navigateTo({ url: '/pages/privacy/privacy' });
  },

  // 从隐私政策页返回时同步同意状态。
  // 否则用户在新页面点了"我已阅读并同意"，回到登录页 agreed 仍是 false，会被一直拦住。
  onShow: function () {
    if (wx.getStorageSync('privacyAgreed')) {
      this.setData({ agreed: true, showPrivacy: false });
    }
  },
  noop: function () {},

  // ── 微信一键登录 ──
  handleLogin: function () {
    if (!this.data.agreed) { this.setData({ showPrivacy: true }); return; }
    if (this.data.loading) return;

    var that = this;
    this.setData({ loading: true });

    // 第1步：获取微信登录 code
    wx.login({
      success: function (res) {
        if (res.code) {
          that.doBackendLogin(res.code);
        } else {
          that.setData({ loading: false });
          wx.showToast({ title: '微信登录失败，请重试', icon: 'none' });
        }
      },
      fail: function () {
        that.setData({ loading: false });
        wx.showToast({ title: '微信登录失败，请重试', icon: 'none' });
      }
    });
  },

  // ── 后端登录 ──
  doBackendLogin: function (code) {
    var that = this;
    var deviceId = 'wechat_mp';
    try { var s = wx.getSystemInfoSync(); deviceId = s.brand + '_' + s.model; } catch (e) {}

    request.post('/api/auth/login', { code: code, deviceId: deviceId })
      .then(function (res) {
        that.setData({ loading: false });
        if (res.code === 200 && res.data) {
          var d = res.data;
          wx.setStorageSync('token', d.token);
          wx.setStorageSync('userId', d.userId);
          if (d.nickName) wx.setStorageSync('nickName', d.nickName);
          if (d.avatarUrl) wx.setStorageSync('avatarUrl', d.avatarUrl);
          if (d.realName) wx.setStorageSync('realName', d.realName);

          var app = getApp();
          app.globalData.token = d.token;
          app.globalData.userId = d.userId;
          app.globalData.hasProfile = d.hasProfile;
          app.globalData.nickName = d.nickName || '';
          app.globalData._offline = false;

          wx.reLaunch({ url: d.hasProfile ? '/pages/index/index' : '/pages/profile-edit/profile-edit' });
        } else {
          wx.showToast({ title: res.message || '登录失败', icon: 'none' });
        }
      })
      .catch(function (err) {
        that.setData({ loading: false });
        // 后端不可达 → 提示（不提供离线模式）
        wx.showModal({
          title: '连接后端失败',
          content: '无法连接服务器 ' + request.getBaseUrl() + '\n\n'
            + '【模拟器】勾选「不校验合法域名」即可\n\n'
            + '【真机】三步：\n'
            + '① 手机能访问电脑（同一网络，或用手机热点给电脑）\n'
            + '② 用「真机调试」而非「预览」\n'
            + '③ 改 utils/request.js 顶部的 DEFAULT_BASE_URL\n'
            + '   为 http://电脑局域网IP:8080（ipconfig 查，别用 192.168.56.1）',
          showCancel: false,
          confirmText: '我知道了'
        });
      });
  },

  navigateToHome: function (app) {
    wx.reLaunch({ url: app.globalData.hasProfile ? '/pages/index/index' : '/pages/profile-edit/profile-edit' });
  }
});
