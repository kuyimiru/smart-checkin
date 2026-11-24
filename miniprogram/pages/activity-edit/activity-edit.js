const { get, put } = require('../../utils/request');

Page({
  data: {
    id: '',
    name: '',
    startDate: '',
    endDate: '',
    checkInStartTime: '08:00',
    checkInEndTime: '09:00',
    frequencyType: 'daily',
    frequencyDays: [1, 2, 3, 4, 5],
    checkInType: 'location',
    locationLat: '',
    locationLng: '',
    locationRadius: 500,
    weekOptions: [
      { label: '周一', value: 1 }, { label: '周二', value: 2 },
      { label: '周三', value: 3 }, { label: '周四', value: 4 },
      { label: '周五', value: 5 }, { label: '周六', value: 6 },
      { label: '周日', value: 7 }
    ]
  },

  onLoad(options) {
    this.setData({ id: options.id });
    this.loadActivity();
  },

  goBack() { wx.navigateBack(); },

  onFreqTap(e) {
    this.setData({ frequencyType: e.currentTarget.dataset.val });
  },

  async loadActivity() {
    try {
      const res = await get('/api/activities/' + this.data.id);
      if (res.code === 200) {
        const a = res.data;
        this.setData({
          name: a.name || '',
          startDate: a.startDate || '',
          endDate: a.endDate || '',
          checkInStartTime: a.checkInStartTime || '08:00',
          checkInEndTime: a.checkInEndTime || '09:00',
          frequencyType: a.frequencyType || 'daily',
          frequencyDays: a.frequencyDays ? JSON.parse(a.frequencyDays) : [1, 2, 3, 4, 5],
          checkInType: a.checkInType || 'location',
          locationLat: a.locationLat ? String(a.locationLat) : '',
          locationLng: a.locationLng ? String(a.locationLng) : '',
          locationRadius: a.locationRadius || 500
        });
      }
    } catch (e) {
      wx.showToast({ title: '加载活动信息失败', icon: 'none' });
    }
  },

  onNameInput(e) { this.setData({ name: e.detail.value }); },
  onStartDateChange(e) { this.setData({ startDate: e.detail.value }); },
  onEndDateChange(e) { this.setData({ endDate: e.detail.value }); },
  onStartTimeChange(e) { this.setData({ checkInStartTime: e.detail.value }); },
  onEndTimeChange(e) { this.setData({ checkInEndTime: e.detail.value }); },

  onFrequencyChange(e) { this.setData({ frequencyType: e.detail.value }); },

  onWeekToggle(e) {
    const val = e.currentTarget.dataset.val;
    let days = this.data.frequencyDays;
    const idx = days.indexOf(val);
    if (idx > -1) {
      days = days.filter(function(d) { return d !== val; });
    } else {
      days = days.concat([val]).sort(function(a, b) { return a - b; });
    }
    this.setData({ frequencyDays: days });
  },

  onCheckInTypeChange(e) { this.setData({ checkInType: e.detail.value }); },

  chooseLocation() {
    var that = this;
    wx.chooseLocation({
      success: function(res) {
        that.setData({ locationLat: res.latitude, locationLng: res.longitude });
      },
      fail: function(err) {
        if (err.errMsg.indexOf('auth deny') > -1) {
          wx.showModal({
            title: '需要位置权限',
            content: '请在设置中开启位置权限',
            success: function(m) { if (m.confirm) wx.openSetting(); }
          });
        } else {
          wx.showToast({ title: '选择位置失败，请重试', icon: 'none' });
        }
      }
    });
  },

  onRadiusChange(e) { this.setData({ locationRadius: parseInt(e.detail.value) || 500 }); },

  async submit() {
    if (!this.data.name) {
      wx.showToast({ title: '请输入活动名称', icon: 'none' });
      return;
    }
    if (!this.data.startDate || !this.data.endDate) {
      wx.showToast({ title: '请选择活动日期', icon: 'none' });
      return;
    }

    const data = {
      name: this.data.name,
      startDate: this.data.startDate,
      endDate: this.data.endDate,
      checkInStartTime: this.data.checkInStartTime,
      checkInEndTime: this.data.checkInEndTime,
      frequencyType: this.data.frequencyType,
      frequencyDays: this.data.frequencyType === 'custom' ? this.data.frequencyDays : null,
      checkInType: this.data.checkInType,
      locationLat: parseFloat(this.data.locationLat) || null,
      locationLng: parseFloat(this.data.locationLng) || null,
      locationRadius: this.data.locationRadius
    };

    try {
      const res = await put('/api/activities/' + this.data.id, data);
      if (res.code === 200) {
        wx.showToast({ title: '活动更新成功', icon: 'success' });
        setTimeout(function() { wx.navigateBack(); }, 1500);
      }
    } catch (e) {
      wx.showToast({ title: e.message || '更新失败', icon: 'none' });
    }
  }
});
