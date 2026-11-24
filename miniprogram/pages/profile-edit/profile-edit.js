const { get, post, put, resolveUrl } = require('../../utils/request');
const imageUtil = require('../../utils/image');

Page({
  data: {
    realName: '',
    idCard: '',
    phone: '',
    faceImageUrl: '',
    faceUploaded: false,
    gender: '',
    genderOptions: ['男', '女'],
    genderIndex: -1,
    // 已实名后身份证号不可修改（清单 U-03）
    idCardLocked: false,
    maskedIdCard: ''
  },

  onLoad() {
    this.loadProfile();
  },

  goBack() { wx.navigateBack(); },

  async loadProfile() {
    try {
      const res = await get('/api/users/profile');
      if (res.code === 200 && res.data) {
        const user = res.data;
        // 后端返回的是脱敏身份证号（110101********1234），不能被当作用户输入回传
        const locked = !!user.idCard && user.idCard.indexOf('*') > -1;
        this.setData({
          realName: user.realName || '',
          idCard: '',
          maskedIdCard: locked ? user.idCard : '',
          idCardLocked: locked,
          phone: user.phone || '',
          faceImageUrl: user.faceImageUrl || '',
          // 服务端存的是 /uploads/xxx.jpg，预览必须拼域名才能显示
          faceImagePreview: resolveUrl(user.faceImageUrl),
          gender: user.gender || '',
          genderIndex: user.gender === '男' ? 0 : (user.gender === '女' ? 1 : -1)
        });
        // 真机上 http 图片可能被拦 → 先下载到本地再显示（失败自动退回原地址）
        if (user.faceImageUrl) {
          imageUtil.loadRemote(user.faceImageUrl).then((local) => {
            this.setData({ faceImagePreview: local });
          });
        }
      }
    } catch (e) {
      console.error('加载个人信息失败', e);
    }
  },

  onNameInput(e) { this.setData({ realName: e.detail.value }); },
  onIdCardInput(e) { this.setData({ idCard: e.detail.value }); },
  onPhoneInput(e) { this.setData({ phone: e.detail.value }); },

  onGenderChange(e) {
    const idx = parseInt(e.detail.value);
    this.setData({ genderIndex: idx, gender: this.data.genderOptions[idx] });
  },

  takePhoto() {
    wx.chooseMedia({
      count: 1,
      mediaType: ['image'],
      sourceType: ['camera'],
      camera: 'front',
      success: (res) => {
        const tempFilePath = res.tempFiles[0].tempFilePath;
        this.setData({ faceImageUrl: tempFilePath, faceImagePreview: tempFilePath });
        // 先压缩再上传：底图只需保留识别所需的细节，原图动辄 2~8MB，
        // 手机网络下又慢又容易被服务端大小限制卡住（质量 80 兼顾识别率与体积）
        imageUtil.compress(tempFilePath, 80).then((path) => {
          // 必须上传到服务器：人脸底图要在服务端做比对，临时路径后端读不到
          this.uploadFace(path);
        });
      }
    });
  },

  /** 上传人脸底图，成功后将 faceImageUrl 替换为服务端地址 */
  uploadFace(filePath) {
    const that = this;
    wx.showLoading({ title: '上传中...' });
    wx.uploadFile({
      url: require('../../utils/request').getBaseUrl() + '/api/upload/face',
      filePath: filePath,
      name: 'file',
      header: { 'Authorization': 'Bearer ' + (wx.getStorageSync('token') || '') },
      success(res) {
        wx.hideLoading();
        try {
          const data = JSON.parse(res.data);
          if (data.code === 200 && data.data && data.data.url) {
            that.setData({
              faceImageUrl: data.data.url,
              faceImagePreview: resolveUrl(data.data.url),
              faceUploaded: true
            });
            // 刚上传的这张也用本地路径显示，真机上才稳
            imageUtil.loadRemote(data.data.url).then((local) => {
              that.setData({ faceImagePreview: local });
            });
            wx.showToast({ title: '人脸照片已上传', icon: 'success' });
          } else {
            that.setData({ faceUploaded: false });
            wx.showToast({ title: data.message || '上传失败', icon: 'none' });
          }
        } catch (e) {
          that.setData({ faceUploaded: false });
          wx.showToast({ title: '上传失败，请重试', icon: 'none' });
        }
      },
      fail() {
        wx.hideLoading();
        that.setData({ faceUploaded: false });
        wx.showToast({ title: '网络异常，人脸照片未上传', icon: 'none' });
      }
    });
  },

  async submit() {
    if (!this.data.realName) {
      wx.showToast({ title: '请输入真实姓名', icon: 'none' });
      return;
    }
    if (!this.data.idCardLocked) {
      if (!this.data.idCard) {
        wx.showToast({ title: '请输入身份证号', icon: 'none' });
        return;
      }
      var idCardReg = /^[1-9]\d{5}(19|20)\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\d|3[01])\d{3}[\dXx]$/;
      if (!idCardReg.test(this.data.idCard)) {
        wx.showToast({ title: '身份证号格式不正确，请检查', icon: 'none', duration: 2500 });
        return;
      }
    }

    // 身份证号已锁定则不再上送，避免触发"不可修改"校验
    var payload = {
      realName: this.data.realName,
      phone: this.data.phone,
      faceImageUrl: this.data.faceImageUrl,
      gender: this.data.gender || null
    };
    if (!this.data.idCardLocked) { payload.idCard = this.data.idCard; }

    try {
      const res = await post('/api/users/profile', payload);
      if (res.code === 200) {
        wx.setStorageSync('realName', this.data.realName);
        getApp().globalData.hasProfile = true;
        wx.showToast({ title: '保存成功', icon: 'success' });
        setTimeout(() => wx.reLaunch({ url: '/pages/index/index' }), 1500);
      }
    } catch (e) {
      wx.showToast({ title: e.message || '保存失败', icon: 'none' });
    }
  }
});
