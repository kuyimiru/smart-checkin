var request = require('../../utils/request');
var util = require('../../utils/util');

Page({
  data: {
    activityId: '',
    year: 2026,
    month: 8,
    days: [],
    records: [],
    weekDays: ['日', '一', '二', '三', '四', '五', '六'],
    selectedDate: '',
    selectedRecord: null
  },

  onLoad(options) {
    var now = new Date();
    this.setData({
      activityId: options.activityId || '',
      year: now.getFullYear(),
      month: now.getMonth() + 1
    });
    this.loadRecords();
  },

  goBack() { wx.navigateBack(); },

  async loadRecords() {
    try {
      var qs = '?year=' + this.data.year + '&month=' + this.data.month;
      if (this.data.activityId) { qs += '&activityId=' + this.data.activityId; }
      var res = await request.get('/api/calendar' + qs);
      if (res.code === 200) {
        this.setData({ records: res.data || [] });
      }
    } catch (e) {
      console.warn('加载打卡日历失败', e);
    }
    this.renderCalendar();
  },

  renderCalendar() {
    var year = this.data.year;
    var month = this.data.month;
    var records = this.data.records;
    var daysInMonth = util.getDaysInMonth(year, month);
    var firstDay = util.getFirstDayOfMonth(year, month);

    var days = [];
    for (var i = 0; i < firstDay; i++) {
      days.push({ day: '', empty: true });
    }
    for (var d = 1; d <= daysInMonth; d++) {
      var dateStr = year + '-' + String(month).padStart(2, '0') + '-' + String(d).padStart(2, '0');
      var record = null;
      for (var r = 0; r < records.length; r++) {
        if (records[r].date === dateStr) { record = records[r]; break; }
      }
      days.push({
        day: d,
        date: dateStr,
        status: record ? record.status : null,
        hasRecord: !!record
      });
    }
    this.setData({ days: days });
  },

  prevMonth() {
    var year = this.data.year;
    var month = this.data.month;
    if (month === 1) {
      this.setData({ year: year - 1, month: 12 });
    } else {
      this.setData({ month: month - 1 });
    }
    this.setData({ selectedDate: '', selectedRecord: null });
    this.loadRecords();
  },

  nextMonth() {
    var year = this.data.year;
    var month = this.data.month;
    if (month === 12) {
      this.setData({ year: year + 1, month: 1 });
    } else {
      this.setData({ month: month + 1 });
    }
    this.setData({ selectedDate: '', selectedRecord: null });
    this.loadRecords();
  },

  async selectDay(e) {
    var date = e.currentTarget.dataset.date;
    var status = e.currentTarget.dataset.status;
    if (!date) return;

    var that = this;
    // 尝试获取详情
    try {
      var actId = that.data.activityId;
      if (actId) {
        var res = await request.get('/api/check-ins/' + date + '?activityId=' + actId);
        if (res.code === 200 && res.data) {
          that.setData({
            selectedDate: date,
            selectedRecord: res.data
          });
          return;
        }
      }
    } catch (err) {
      // fallback to basic info
    }

    that.setData({
      selectedDate: date,
      selectedRecord: status ? { date: date, status: status } : null
    });
  },

  getDayClass(status) {
    if (!status) return '';
    return 'day-' + status;
  }
});
