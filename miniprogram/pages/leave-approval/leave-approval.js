const { get, put } = require('../../utils/request');

Page({
  data: {
    list: []
  },

  onShow() {
    this.loadList();
  },

  goBack() { wx.navigateBack(); },

  async loadList() {
    try {
      const res = await get('/api/leaves/pending');
      if (res.code === 200) {
        this.setData({ list: res.data || [] });
      }
    } catch (e) {
      console.error('加载待审批列表失败', e);
    }
  },

  async approve(e) {
    const id = e.currentTarget.dataset.id;
    wx.showModal({
      title: '确认审批',
      content: '确定通过该请假申请？',
      success: async (res) => {
        if (res.confirm) {
          try {
            await put('/api/leaves/' + id + '/approve');
            wx.showToast({ title: '审批通过', icon: 'success' });
            this.loadList();
          } catch (e) {
            wx.showToast({ title: e.message || '操作失败', icon: 'none' });
          }
        }
      }
    });
  },

  async reject(e) {
    const id = e.currentTarget.dataset.id;
    wx.showModal({
      title: '拒绝申请',
      content: '确定拒绝该请假申请？',
      success: async (res) => {
        if (res.confirm) {
          try {
            await put('/api/leaves/' + id + '/reject', { reason: '审批人拒绝' });
            wx.showToast({ title: '已拒绝', icon: 'none' });
            this.loadList();
          } catch (e) {
            wx.showToast({ title: e.message || '操作失败', icon: 'none' });
          }
        }
      }
    });
  },

  getStatusText(status) {
    return status === 'pending' ? '待审批' : status === 'approved' ? '已通过' : '已拒绝';
  }
});
