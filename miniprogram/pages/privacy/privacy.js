Page({
  data: {
    agreed: false
  },

  onLoad() {
    this.setData({ agreed: !!wx.getStorageSync('privacyAgreed') });
  },

  goBack() {
    wx.navigateBack({
      fail: function () { wx.reLaunch({ url: '/pages/login/login' }); }
    });
  },

  agree() {
    try {
      wx.setStorageSync('privacyAgreed', true);
      wx.setStorageSync('privacyAgreedAt', new Date().toISOString());
    } catch (e) {}
    this.setData({ agreed: true });
    wx.showToast({ title: '已同意', icon: 'success' });
    setTimeout(function () { wx.navigateBack({ fail: function () {} }); }, 800);
  }
});
