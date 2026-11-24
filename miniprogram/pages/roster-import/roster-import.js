var request = require('../../utils/request');

Page({
  data: {
    activityId: '',
    rosterList: [],
    loading: false
  },

  onLoad(options) {
    this.setData({ activityId: options.activityId });
    this.loadRoster();
  },

  goBack() { wx.navigateBack(); },

  async loadRoster() {
    try {
      var res = await request.get('/api/activities/' + this.data.activityId + '/roster');
      if (res.code === 200) {
        this.setData({ rosterList: res.data || [] });
      }
    } catch (e) {
      console.error('加载花名册失败', e);
    }
  },

  chooseFile() {
    var that = this;
    wx.chooseMessageFile({
      count: 1,
      type: 'file',
      extension: ['xlsx', 'xls', 'csv'],
      success: function(res) {
        var filePath = res.tempFiles[0].path;
        that.uploadFile(filePath);
      }
    });
  },

  uploadFile(filePath) {
    var that = this;
    this.setData({ loading: true });
    wx.showLoading({ title: '导入中...' });

    var token = wx.getStorageSync('token') || '';
    wx.uploadFile({
      url: request.getBaseUrl() + '/api/activities/' + that.data.activityId + '/roster/import',
      filePath: filePath,
      name: 'file',
      header: {
        'Authorization': token ? 'Bearer ' + token : ''
      },
      success: function(res) {
        wx.hideLoading();
        that.setData({ loading: false });
        try {
          var data = JSON.parse(res.data);
          if (data.code === 200) {
            wx.showToast({ title: data.message || '导入成功', icon: 'success' });
            that.loadRoster();
          } else {
            wx.showToast({ title: data.message || '导入失败', icon: 'none' });
          }
        } catch (e) {
          wx.showToast({ title: '导入失败，请检查文件格式', icon: 'none' });
        }
      },
      fail: function() {
        wx.hideLoading();
        that.setData({ loading: false });
        wx.showToast({ title: '网络异常，导入失败', icon: 'none' });
      }
    });
  },

  getMatchStatus(item) {
    return item.matchedUserId ? '已匹配' : '未匹配';
  }
});
