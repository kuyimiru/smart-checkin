const { get, post } = require('../../utils/request');

Page({
  data: {
    created: [], joined: [], managing: [],
    activeTab: 'joined',
    tabs: [
      { key: 'joined', label: '我参与的', count: 0 },
      { key: 'created', label: '我创建的', count: 0 },
      { key: 'managing', label: '我负责的', count: 0 }
    ],
    segOffset: 0,
    showJoinDialog: false,
    inviteCode: '',
    refreshing: false
  },

  onLoad(options) {
    // A-06：支持通过链接/二维码进入并自动带出邀请码，例如 pages/index/index?inviteCode=ABC123
    const code = options && (options.inviteCode || options.code);
    if (code) {
      this.setData({ showJoinDialog: true, inviteCode: String(code).toUpperCase() });
      this._autoJoin = true;
    }
  },

  onShow() {
    if (!getApp().checkLogin()) return;
    // U-04：响应"我的"页面发起的分类跳转
    try {
      const tab = wx.getStorageSync('indexTab');
      if (tab) {
        wx.removeStorageSync('indexTab');
        const idx = this.data.tabs.findIndex(t => t.key === tab);
        if (idx >= 0) this.setData({ activeTab: tab, segOffset: idx * 100 });
      }
    } catch (e) {}

    this.loadActivities();
    // 清单 N-03：引导订阅消息授权（未配置模板ID时自动跳过）
    var app = getApp();
    if (app && typeof app.requestSubscribeMessages === 'function') {
      app.requestSubscribeMessages();
    }
  },

  async loadActivities() {
    try {
      const res = await get('/api/activities');
      if (res.code === 200) {
        const joined = res.data.joined || [];
        const created = res.data.created || [];
        const managing = res.data.managing || [];
        const tabs = [
          { key: 'joined', label: '我参与的', count: joined.length },
          { key: 'created', label: '我创建的', count: created.length },
          { key: 'managing', label: '我负责的', count: managing.length }
        ];
        this.setData({ created, joined, managing, tabs });
      }
    } catch (e) {
      console.error('加载活动失败', e);
    }
  },

  switchTab(e) {
    var tab = e.currentTarget.dataset.tab;
    var idx = e.currentTarget.dataset.index;
    // 3段等宽，每段33.33%，offset = idx * 100%
    this.setData({ activeTab: tab, segOffset: idx * 100 });
  },

  goCreate() { wx.navigateTo({ url: '/pages/activity-create/activity-create' }); },
  openJoinDialog() { this.setData({ showJoinDialog: true, inviteCode: '' }); },
  closeJoinDialog() { this.setData({ showJoinDialog: false }); },

  onInviteCodeInput(e) { this.setData({ inviteCode: e.detail.value.toUpperCase() }); },

  async doJoin() {
    if (!this.data.inviteCode) { wx.showToast({ title: '请输入邀请码', icon: 'none' }); return; }
    try {
      await post('/api/activities/join', { inviteCode: this.data.inviteCode });
      wx.showToast({ title: '加入成功', icon: 'success' });
      this.closeJoinDialog();
      this.loadActivities();
    } catch (e) { wx.showToast({ title: e.message || '加入失败', icon: 'none' }); }
  },

  goDetail(e) {
    var id = e.currentTarget.dataset.id;
    var role = e.currentTarget.dataset.role;
    var page = role === 'managing' ? '/pages/activity-admin/activity-admin' : '/pages/activity-detail/activity-detail';
    wx.navigateTo({ url: page + '?id=' + id });
  },

  noop() {}
});
