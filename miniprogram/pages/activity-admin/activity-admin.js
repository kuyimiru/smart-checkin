const { get, post, put, getBaseUrl } = require('../../utils/request');

Page({
  data: {
    id: '',
    activity: {},
    statistics: {},
    members: []
  },

  onLoad(options) {
    this.setData({ id: options.id });
  },

  goBack() {
    wx.navigateBack();
  },

  onShow() {
    this.loadData();
  },

  async loadData() {
    try {
      const [actRes, statsRes, membersRes] = await Promise.all([
        get('/api/activities/' + this.data.id),
        get('/api/activities/' + this.data.id + '/statistics').catch(() => ({ data: null })),
        get('/api/activities/' + this.data.id + '/members').catch(() => ({ data: null }))
      ]);

      if (actRes.code === 200) this.setData({ activity: actRes.data });
      if (statsRes && statsRes.code === 200 && statsRes.data) this.setData({ statistics: statsRes.data });
      if (membersRes && membersRes.code === 200) this.setData({ members: membersRes.data || [] });
    } catch (e) {
      console.error('加载管理数据失败', e);
      wx.showToast({ title: '加载管理数据失败', icon: 'none' });
    }
  },

  async closeActivity() {
    wx.showModal({
      title: '关闭活动',
      content: '关闭后参与者将无法打卡，确定关闭？',
      success: async (res) => {
        if (res.confirm) {
          await put('/api/activities/' + this.data.id + '/close');
          wx.showToast({ title: '活动已关闭', icon: 'success' });
          this.loadData();
        }
      }
    });
  },

  async disableInviteCode() {
    try {
      await put('/api/activities/' + this.data.id + '/invite-code/disable');
      wx.showToast({ title: '邀请码已失效', icon: 'success' });
      this.loadData();
    } catch (e) {
      wx.showToast({ title: (e && e.message) || '操作失败', icon: 'none' });
    }
  },

  async enableInviteCode() {
    try {
      await put('/api/activities/' + this.data.id + '/invite-code/enable');
      wx.showToast({ title: '邀请码已激活', icon: 'success' });
      this.loadData();
    } catch (e) {
      wx.showToast({ title: (e && e.message) || '操作失败', icon: 'none' });
    }
  },

  goLeaveApproval() {
    wx.navigateTo({ url: '/pages/leave-approval/leave-approval' });
  },

  goEdit() {
    wx.navigateTo({ url: '/pages/activity-edit/activity-edit?id=' + this.data.id });
  },

  goRosterImport() {
    wx.navigateTo({ url: '/pages/roster-import/roster-import?activityId=' + this.data.id });
  },

  /** E-01 导出打卡数据（wx.downloadFile 只支持 GET，后端同时提供 GET 版本） */
  exportExcel() {
    var that = this;
    wx.showLoading({ title: '导出中...' });
    wx.downloadFile({
      url: getBaseUrl() + '/api/activities/' + this.data.id + '/export',
      header: { 'Authorization': 'Bearer ' + (wx.getStorageSync('token') || '') },
      success: function (res) {
        wx.hideLoading();
        if (res.statusCode === 200) {
          wx.openDocument({
            filePath: res.tempFilePath,
            fileType: 'xlsx',
            showMenu: true,
            success: function () { wx.showToast({ title: '导出成功', icon: 'success' }); },
            fail: function () { wx.showToast({ title: '打开文件失败', icon: 'none' }); }
          });
        } else {
          wx.showToast({ title: '导出失败(' + res.statusCode + ')', icon: 'none' });
        }
      },
      fail: function () {
        wx.hideLoading();
        wx.showToast({ title: '导出失败，请检查网络', icon: 'none' });
      }
    });
  },

  /** R-04 移除成员 */
  removeMember(e) {
    var userId = e.currentTarget.dataset.id;
    var name = e.currentTarget.dataset.name || '该成员';
    var that = this;
    wx.showModal({
      title: '移除成员',
      content: '确定将「' + name + '」移出活动？被移除者之后可用邀请码重新加入。',
      confirmColor: '#FF3B30',
      success: function (res) {
        if (!res.confirm) return;
        post('/api/activities/' + that.data.id + '/members/' + userId + '/remove')
          .then(function () {
            wx.showToast({ title: '已移除', icon: 'success' });
            that.loadData();
          })
          .catch(function (err) {
            wx.showToast({ title: (err && err.message) || '移除失败', icon: 'none' });
          });
      }
    });
  },

  getStatusClass(status) {
    return 'tag tag-' + status;
  },

  getStatusText(status) {
    const map = {
      'normal': '正常', 'late': '迟到', 'abnormal': '异常',
      'leave': '请假', 'unchecked': '未打卡'
    };
    return map[status] || status;
  }
});
