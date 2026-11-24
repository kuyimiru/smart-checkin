const { post, get } = require('../../utils/request');
const { formatDate } = require('../../utils/util');

Page({
  data: {
    activityId: '',
    startDate: '',
    endDate: '',
    destination: '',
    reason: '',
    emergencyContact: '',
    approverId: '',
    approverName: '',
    managers: []
  },

  onLoad(options) {
    this.setData({ activityId: options.activityId });
    this.loadManagers();
  },

  goBack() { wx.navigateBack(); },

  async loadManagers() {
    try {
      const res = await get('/api/activities/' + this.data.activityId + '/managers');
      if (res.code === 200) {
        // 只保留已绑定系统用户的审批人——未注册的负责人收不到审批通知
        const list = (res.data || [])
          .filter(m => m.userId)
          .map(m => ({
            id: m.userId,
            name: m.name + (m.role === 'creator' ? '（发起人）' : '（负责人）')
          }));
        this.setData({ managers: list });
        if (list.length > 0) {
          this.setData({ approverId: list[0].id, approverName: list[0].name });
        }
      }
    } catch (e) {
      console.error('加载负责人失败', e);
    }
  },

  onStartDateChange(e) { this.setData({ startDate: e.detail.value }); },
  onEndDateChange(e) { this.setData({ endDate: e.detail.value }); },
  onDestinationInput(e) { this.setData({ destination: e.detail.value }); },
  onReasonInput(e) { this.setData({ reason: e.detail.value }); },
  onContactInput(e) { this.setData({ emergencyContact: e.detail.value }); },

  onApproverChange(e) {
    const picked = this.data.managers[e.detail.value];
    if (picked) {
      this.setData({ approverId: picked.id, approverName: picked.name });
    }
  },

  async submit() {
    if (!this.data.startDate || !this.data.endDate) {
      wx.showToast({ title: '请选择请假日期', icon: 'none' });
      return;
    }
    if (this.data.endDate < this.data.startDate) {
      wx.showToast({ title: '结束日期不能早于开始日期', icon: 'none' });
      return;
    }
    if (!this.data.emergencyContact) {
      wx.showToast({ title: '请填写紧急联系人', icon: 'none' });
      return;
    }
    if (!this.data.approverId) {
      wx.showToast({ title: '请选择审批人', icon: 'none' });
      return;
    }

    try {
      const res = await post('/api/leaves', {
        activityId: parseInt(this.data.activityId),
        startDate: this.data.startDate,
        endDate: this.data.endDate,
        destination: this.data.destination,
        reason: this.data.reason,
        emergencyContact: this.data.emergencyContact,
        approverId: this.data.approverId
      });
      if (res.code === 200) {
        wx.showToast({ title: '请假申请已提交', icon: 'success' });
        setTimeout(() => wx.navigateBack(), 1500);
      }
    } catch (e) {
      wx.showToast({ title: e.message || '提交失败', icon: 'none' });
    }
  }
});
