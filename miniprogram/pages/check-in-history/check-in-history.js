const { get } = require('../../utils/request');
const { formatDate } = require('../../utils/util');

Page({
  data: {
    activityId: '',
    activityName: '',
    records: [],
    expandedId: -1,
    loading: true,
    loadingMore: false,
    hasMore: true,
    page: 1,
    pageSize: 20,
    totalCount: 0,
    normalCount: 0,
    lateCount: 0,
    abnormalCount: 0
  },

  onLoad(options) {
    var activityId = options.activityId || '';
    this.setData({ activityId: activityId });
    if (activityId) {
      this.loadActivityInfo();
      this.loadRecords();
    }
  },

  loadActivityInfo() {
    var that = this;
    get('/api/activities/' + this.data.activityId).then(function (res) {
      if (res.code === 200 && res.data) {
        that.setData({ activityName: res.data.name });
      }
    }).catch(function () {});
  },

  loadRecords() {
    var that = this;
    this.setData({ loading: true });

    var endDate = formatDate(new Date());
    var start = new Date();
    start.setMonth(start.getMonth() - 3);
    var startDate = formatDate(start);

    get('/api/check-ins', {
      activityId: parseInt(this.data.activityId),
      startDate: startDate,
      endDate: endDate
    }).then(function (res) {
      if (res.code === 200 && res.data) {
        var records = res.data || [];
        records.sort(function (a, b) {
          return new Date(b.checkInDate + 'T' + (b.checkInTime || '00:00')) -
                 new Date(a.checkInDate + 'T' + (a.checkInTime || '00:00'));
        });

        var stats = { normalCount: 0, lateCount: 0, abnormalCount: 0, totalCount: records.length };
        records.forEach(function (r) {
          if (r.status === 'normal') stats.normalCount++;
          else if (r.status === 'late') stats.lateCount++;
          else if (r.status === 'abnormal') stats.abnormalCount++;
        });

        that.setData({
          records: records,
          totalCount: stats.totalCount,
          normalCount: stats.normalCount,
          lateCount: stats.lateCount,
          abnormalCount: stats.abnormalCount,
          loading: false
        });
      } else {
        that.setData({ loading: false });
      }
    }).catch(function () {
      that.setData({ loading: false });
      wx.showToast({ title: '加载失败', icon: 'none' });
    });
  },

  toggleExpand(e) {
    var id = e.currentTarget.dataset.id;
    this.setData({
      expandedId: this.data.expandedId === id ? -1 : id
    });
  },

  getStatusText(status) {
    var map = { normal: '正常', late: '迟到', abnormal: '异常' };
    return map[status] || status;
  },

  getStatusClass(status) {
    var map = { normal: 'status-normal', late: 'status-late', abnormal: 'status-abnormal' };
    return map[status] || '';
  },

  goBack() {
    wx.navigateBack();
  },

  onPullDownRefresh() {
    var that = this;
    this.loadRecords();
    setTimeout(function () { wx.stopPullDownRefresh(); }, 1000);
  }
});
