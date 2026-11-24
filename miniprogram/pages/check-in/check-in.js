var request = require('../../utils/request');
var util = require('../../utils/util');
var locationUtil = require('../../utils/location');
var faceDetectUtil = require('../../utils/faceDetect');
var imageUtil = require('../../utils/image');

Page({
  data: {
    activityId: '',
    activity: null,
    checkInType: 'location',

    // 用户定位
    userLat: 0,
    userLng: 0,
    mapLat: 39.9042,
    mapLng: 116.4074,

    // 打卡点信息
    targetLat: 0,
    targetLng: 0,
    radius: 500,

    // 距离状态
    distance: -1,
    distanceText: '--',
    inRange: false,

    // 定位状态: 'idle' | 'locating' | 'ready' | 'error'
    locationState: 'idle',
    locationError: '',

    // 地图
    mapScale: 15,
    markers: [],
    circles: [],

    // 人脸
    faceImageUrl: '',      // 提交给后端的地址（上传成功后是 /uploads/xxx.jpg）
    faceImagePreview: '',  // 页面预览用：本地临时路径或拼好域名的绝对地址
    faceDetected: false,
    cameraError: false,
    // 开发者工具没有真实相机时，走"模拟取景"以便在没有真机的环境里演示/自测流程
    simulatedCamera: false,

    // 提交状态
    hasExistingRecord: false,
    submitting: false,
    submitSuccess: false,
    showConfetti: false,
    confettiList: [],

    // 动画
    animPulse: '',
    animShake: '',
    distanceAnimating: false,
    canSubmit: false,

    _wasInRange: false,
    _distTimer: null
  },

  // ═══════════════════════════════════════════════
  // 生命周期
  // ═══════════════════════════════════════════════

  onLoad: function (options) {
    var activityId = options.activityId;
    var type = options.type || 'location';
    this.setData({ activityId: activityId, checkInType: type });

    this.loadActivity();       // 加载活动详情（打卡点坐标等）
    this.checkExistingRecord(); // 检查今日是否已打卡

    // 不再自动调用定位——需要用户点击触发（微信新版隐私要求）
    if (type === 'face') {
      // 人脸打卡不需要定位，直接可用
      this.updateCanSubmit();
    }

    // 开发者工具无真实相机，走模拟取景（真机才启用实时相机 + 人脸检测）
    try {
      var sys = wx.getSystemInfoSync();
      if (sys && sys.platform === 'devtools') {
        this.setData({ cameraError: true, simulatedCamera: true });
        var that = this;
        // 模拟一次"检测到人脸"，让取景框与状态提示的交互可见
        setTimeout(function () {
          that.setData({ faceDetected: true });
          wx.showToast({ title: '模拟模式：已检测到人脸', icon: 'none', duration: 1800 });
        }, 900);
      }
    } catch (e) {}
  },

  onShow: function () {
    // 从设置页返回后，如果之前因权限被拒，重新尝试
    if (this.data.locationState === 'error' && this.data._pendingRetry) {
      this.setData({ _pendingRetry: false });
      this.startLocationFlow();
    }
  },

  onUnload: function () {
    clearTimeout(this._distTimer);
  },

  // ═══════════════════════════════════════════════
  // 数据加载
  // ═══════════════════════════════════════════════

  loadActivity: function () {
    var that = this;
    request.get('/api/activities/' + this.data.activityId).then(function (res) {
      if (res.code === 200 && res.data) {
        var act = res.data;
        var targetLat = act.locationLat || 0;
        var targetLng = act.locationLng || 0;

        that.setData({
          activity: act,
          targetLat: targetLat,
          targetLng: targetLng,
          radius: act.locationRadius || 500
        });

        // 如果活动有打卡点，以打卡点作为地图中心
        if (targetLat && targetLng) {
          that.setData({ mapLat: targetLat, mapLng: targetLng });
          that.updateMapMarkers();
        }
      }
    }).catch(function (e) {
      console.error('加载活动信息失败', e);
    });
  },

  checkExistingRecord: function () {
    var that = this;
    var today = util.formatDate(new Date());
    request.get('/api/check-ins/' + today + '?activityId=' + this.data.activityId)
      .then(function (res) {
        if (res.code === 200 && res.data) {
          that.setData({ hasExistingRecord: true });
        }
      }).catch(function () {});
  },

  // ═══════════════════════════════════════════════
  // 定位流程（由用户点击触发）
  // ═══════════════════════════════════════════════

  // 用户点击"获取位置"按钮
  startLocationFlow: function () {
    if (this.data.locationState === 'locating') return;

    var that = this;
    this.setData({ locationState: 'locating', locationError: '', _pendingRetry: false });

    locationUtil.getCurrentLocation().then(function (pos) {
      var lat = pos.latitude;
      var lng = pos.longitude;

      that.setData({
        userLat: lat,
        userLng: lng,
        mapLat: lat,
        mapLng: lng,
        locationState: 'ready',
        locationError: ''
      });

      that.calcAndUpdate();
      that.updateMapMarkers();
    }).catch(function (err) {
      that.setData({
        locationState: 'error',
        locationError: err.message || '获取位置失败，请确认已开启GPS并在设置中授权位置权限',
        _pendingRetry: true
      });
      that.updateCanSubmit();
      that.triggerShake();
    });
  },

  // 重新定位
  retryLocation: function () {
    this.startLocationFlow();
  },

  // 打开系统设置
  goToSetting: function () {
    var that = this;
    wx.openSetting({
      success: function (res) {
        if (res.authSetting['scope.userLocation']) {
          that.setData({ locationState: 'idle', locationError: '', _pendingRetry: true });
          // 重新触发定位
          setTimeout(function () { that.startLocationFlow(); }, 500);
        }
      }
    });
  },

  // ═══════════════════════════════════════════════
  // 距离计算与更新
  // ═══════════════════════════════════════════════

  calcAndUpdate: function () {
    if (!this.data.targetLat || !this.data.targetLng) {
      // 没有打卡点坐标（纯人脸打卡场景）
      this.setData({ inRange: true, distanceText: '--' });
      this.updateCanSubmit();
      return;
    }

    var result = locationUtil.checkInRange(
      this.data.userLat, this.data.userLng,
      this.data.targetLat, this.data.targetLng,
      this.data.radius
    );

    var that = this;
    var prevDistance = this.data.distance;
    var newDistance = result.distance;

    if (prevDistance !== newDistance) {
      this.setData({ distanceAnimating: true });
      clearTimeout(this._distTimer);
      this._distTimer = setTimeout(function () {
        that.setData({ distanceAnimating: false });
      }, 600);
    }

    this.setData({
      distance: newDistance,
      distanceText: locationUtil.formatDistance(newDistance),
      inRange: result.inRange
    });
    this.updateCanSubmit();

    // 首次进入范围时脉冲 + 震动
    if (result.inRange && !this.data._wasInRange) {
      this.triggerPulse();
      wx.vibrateShort({ type: 'light' });
    }
    this.setData({ _wasInRange: result.inRange });
  },

  // ═══════════════════════════════════════════════
  // 地图标记更新
  // ═══════════════════════════════════════════════

  updateMapMarkers: function () {
    var markers = [];
    var circles = [];

    // 打卡点标记
    if (this.data.targetLat && this.data.targetLng) {
      markers.push({
        id: 1,
        latitude: this.data.targetLat,
        longitude: this.data.targetLng,
        width: 1, height: 1,
        anchor: { x: 0.5, y: 0.5 },
        callout: {
          content: '打卡点',
          fontSize: 13,
          borderRadius: 20,
          bgColor: '#ffffff',
          padding: 10,
          display: 'ALWAYS',
          textAlign: 'center',
          color: '#333333'
        }
      });

      // 打卡范围圆圈
      circles.push({
        latitude: this.data.targetLat,
        longitude: this.data.targetLng,
        radius: this.data.radius,
        fillColor: 'rgba(102,126,234,0.06)',
        color: 'rgba(102,126,234,0.3)',
        strokeWidth: 2
      });
    }

    // 用户位置标记（定位成功后才显示）
    if (this.data.userLat && this.data.userLng && this.data.locationState === 'ready') {
      markers.push({
        id: 2,
        latitude: this.data.userLat,
        longitude: this.data.userLng,
        width: 1, height: 1,
        anchor: { x: 0.5, y: 0.5 },
        callout: {
          content: '我的位置',
          fontSize: 13,
          borderRadius: 20,
          bgColor: '#ffffff',
          padding: 10,
          display: 'ALWAYS',
          textAlign: 'center',
          color: '#333333'
        }
      });
    }

    this.setData({ markers: markers, circles: circles });
  },

  // ═══════════════════════════════════════════════
  // 提交状态管理
  // ═══════════════════════════════════════════════

  updateCanSubmit: function () {
    var type = this.data.checkInType;
    var can = false;

    if (type === 'location') {
      can = this.data.locationState === 'ready' && this.data.inRange;
    } else if (type === 'face') {
      can = !!this.data.faceImageUrl;
    } else if (type === 'combo') {
      can = this.data.locationState === 'ready' && this.data.inRange && !!this.data.faceImageUrl;
    }

    this.setData({ canSubmit: can });
  },

  // ═══════════════════════════════════════════════
  // 提交打卡
  // ═══════════════════════════════════════════════

  submit: function () {
    if (this.data.submitting || this.data.submitSuccess) return;

    // 验证定位
    if (this.data.checkInType === 'location' || this.data.checkInType === 'combo') {
      if (this.data.locationState !== 'ready') {
        wx.showToast({ title: '请先获取位置信息', icon: 'none' });
        return;
      }
      if (!this.data.inRange) {
        wx.showToast({ title: '超出打卡范围（' + this.data.distanceText + '），请移近打卡点', icon: 'none', duration: 2000 });
        this.triggerShake();
        return;
      }
      if (!this.data.userLat || !this.data.userLng) {
        wx.showToast({ title: '位置信息异常，请重新获取', icon: 'none' });
        return;
      }
    }

    // 验证人脸（face/combo 模式）
    if (this.data.checkInType === 'face' || this.data.checkInType === 'combo') {
      if (!this.data.faceImageUrl) {
        wx.showToast({ title: '请先拍摄人脸照片', icon: 'none' });
        return;
      }
    }

    // 重复打卡确认
    if (this.data.hasExistingRecord) {
      var that = this;
      wx.showModal({
        title: '重复打卡',
        content: '您今日已打卡，是否覆盖之前的打卡记录？',
        confirmText: '覆盖',
        confirmColor: '#FF9500',
        success: function (res) {
          if (res.confirm) {
            that.setData({ hasExistingRecord: false });
            that.doSubmit();
          }
        }
      });
      return;
    }

    this.doSubmit();
  },

  doSubmit: function () {
    var that = this;
    this.setData({ submitting: true });

    var data = {
      activityId: parseInt(this.data.activityId),
      locationLat: (this.data.userLat && this.data.locationState === 'ready')
        ? parseFloat(this.data.userLat.toFixed(7)) : null,
      locationLng: (this.data.userLng && this.data.locationState === 'ready')
        ? parseFloat(this.data.userLng.toFixed(7)) : null,
      faceImageUrl: this.data.faceImageUrl || null
    };

    request.post('/api/check-ins', data).then(function (res) {
      if (res.code === 200) {
        that.setData({ submitSuccess: true });
        that.generateConfetti();
        wx.vibrateShort({ type: 'heavy' });

        // 3秒后隐藏纸屑，2秒后返回
        setTimeout(function () { that.setData({ showConfetti: false, confettiList: [] }); }, 3000);
        setTimeout(function () { wx.navigateBack(); }, 2000);
      } else {
        wx.showToast({ title: res.message || '打卡失败', icon: 'none' });
      }
    }).catch(function (e) {
      var msg = e.message || '打卡失败，请稍后重试';
      if (e.code === 422) {
        msg = e.message || '超出打卡范围，无法打卡';
      }
      wx.showToast({ title: msg, icon: 'none', duration: 2500 });
      that.triggerShake();
    }).finally(function () {
      that.setData({ submitting: false });
    });
  },

  // ═══════════════════════════════════════════════
  // 人脸拍照
  // ═══════════════════════════════════════════════

  takePhoto: function () {
    var that = this;
    // 优先用实时相机拍照（配合人脸检测）
    if (this._cameraContext && !this.data.cameraError) {
      this._cameraContext.takePhoto({
        quality: 'high',
        success: function (res) {
          var path = res.tempImagePath;
          that.setData({ faceImageUrl: path, faceImagePreview: path });
          that.updateCanSubmit();
          that.prepareAndUpload(path);
        },
        fail: function () {
          that.chooseMediaPhoto();
        }
      });
    } else {
      this.chooseMediaPhoto();
    }
  },

  // 相机不可用时的降级拍照方案（开发者工具/无相机环境）
  chooseMediaPhoto: function () {
    var that = this;
    wx.chooseMedia({
      count: 1,
      mediaType: ['image'],
      sourceType: ['camera', 'album'],
      camera: 'front',
      success: function (res) {
        var tempFilePath = res.tempFiles[0].tempFilePath;
        that.setData({ faceImageUrl: tempFilePath, faceImagePreview: tempFilePath });
        that.updateCanSubmit();
        that.prepareAndUpload(tempFilePath);
      },
      fail: function (err) {
        if (err.errMsg && err.errMsg.indexOf('cancel') === -1) {
          wx.showToast({ title: '拍照失败，请重试', icon: 'none' });
        }
      }
    });
  },

  retakePhoto: function () {
    this.setData({ faceImageUrl: '', faceImagePreview: '', faceDetected: false });
    this.updateCanSubmit();
  },

  // ─── 实时人脸检测 ───
  onCameraInit: function () {
    this._cameraContext = wx.createCameraContext();
    var that = this;
    if (!faceDetectUtil.isAvailable()) return; // 环境不支持，跳过检测
    this._lastDetectTime = 0;
    this._cameraContext.onCameraFrame(function (frame) {
      var now = Date.now();
      if (now - that._lastDetectTime < 500) return; // 500ms 节流，降低开销
      that._lastDetectTime = now;
      that.detectFace(frame);
    });
  },

  detectFace: function (frame) {
    var that = this;
    faceDetectUtil.detect(frame.data, frame.width, frame.height).then(function (res) {
      if (res.hasFace !== that.data.faceDetected) {
        that.setData({ faceDetected: res.hasFace });
      }
    }).catch(function () {
      // 检测失败静默处理，不阻塞拍照
    });
  },

  onCameraError: function () {
    this.setData({ cameraError: true });
  },

  // ─── 人脸水印（清单 C-02：拍照并叠加水印）───
  // 先用 canvas 2d 把「活动名 / 时间 / 定位」烧进照片，再上传，防止盗用旧照片。
  prepareAndUpload: function (filePath) {
    var that = this;
    this.drawWatermark(filePath).then(function (finalPath) {
      return imageUtil.compress(finalPath, 75);
    }).catch(function () {
      // 水印失败也别不传，压一道原图兜底
      return imageUtil.compress(filePath, 75);
    }).then(function (finalPath) {
      that.uploadImage(finalPath);
    });
  },

  drawWatermark: function (filePath) {
    var that = this;
    return new Promise(function (resolve, reject) {
      wx.createSelectorQuery().in(that).select('#wmCanvas')
        .fields({ node: true }).exec(function (res) {
          var canvas = res && res[0] && res[0].node;
          if (!canvas) { reject(new Error('canvas 不可用')); return; }

          var ctx = canvas.getContext('2d');
          var img = canvas.createImage();
          img.onload = function () {
            try {
              // 限制最长边：相机原图常有 4000px，整张画进 canvas 导出的 jpg 还是好几 MB，
              // 1280px 对人脸识别与查看都足够，体积能降一个数量级
              var maxSide = 1280;
              var scale = Math.min(1, maxSide / Math.max(img.width, img.height));
              var w = Math.round(img.width * scale);
              var h = Math.round(img.height * scale);
              canvas.width = w;
              canvas.height = h;
              ctx.drawImage(img, 0, 0, w, h);

              var act = that.data.activity || {};
              var now = new Date();
              var pad = function (n) { return n < 10 ? '0' + n : '' + n; };
              var lines = [
                '活动：' + (act.name || '智能打卡'),
                '时间：' + now.getFullYear() + '-' + pad(now.getMonth() + 1) + '-' + pad(now.getDate()) +
                  ' ' + pad(now.getHours()) + ':' + pad(now.getMinutes()) + ':' + pad(now.getSeconds())
              ];
              if (that.data.userLat && that.data.locationState === 'ready') {
                lines.push('定位：' + that.data.userLat.toFixed(5) + ', ' + that.data.userLng.toFixed(5));
              }
              if (that.data.distance >= 0 && that.data.distanceText !== '--') {
                lines.push('距打卡点：' + that.data.distanceText);
              }

              var fontSize = Math.max(16, Math.floor(w / 26));
              var lineHeight = Math.floor(fontSize * 1.5);
              var margin = Math.floor(fontSize * 0.9);
              var boxH = lineHeight * lines.length + margin;
              var boxY = h - boxH;

              // 半透明渐变遮罩，保证文字在任何底图上都清晰
              var grad = ctx.createLinearGradient(0, boxY, 0, h);
              grad.addColorStop(0, 'rgba(0,0,0,0)');
              grad.addColorStop(1, 'rgba(0,0,0,0.55)');
              ctx.fillStyle = grad;
              ctx.fillRect(0, boxY, w, boxH);

              ctx.font = 'bold ' + fontSize + 'px sans-serif';
              ctx.textBaseline = 'top';
              ctx.fillStyle = '#FFFFFF';
              ctx.shadowColor = 'rgba(0,0,0,0.6)';
              ctx.shadowBlur = 4;
              for (var i = 0; i < lines.length; i++) {
                ctx.fillText(lines[i], margin, boxY + margin / 2 + i * lineHeight);
              }
              // 左下角品牌水印
              ctx.font = Math.floor(fontSize * 0.85) + 'px sans-serif';
              ctx.fillStyle = 'rgba(255,255,255,0.85)';
              ctx.fillText('智能打卡 · 防伪水印', margin, h - margin / 2 - Math.floor(fontSize * 0.85));

              wx.canvasToTempFilePath({
                canvas: canvas,
                fileType: 'jpg',
                quality: 0.9,
                success: function (r) { resolve(r.tempFilePath); },
                fail: function () { reject(new Error('水印生成失败')); }
              });
            } catch (err) {
              reject(err);
            }
          };
          img.onerror = function () { reject(new Error('图片加载失败')); };
          img.src = filePath;
        });
    });
  },

  uploadImage: function (filePath) {
    wx.showLoading({ title: '上传中...' });
    var that = this;
    wx.uploadFile({
      url: request.getBaseUrl() + '/api/upload/checkin',
      filePath: filePath,
      name: 'file',
      header: {
        'Authorization': 'Bearer ' + (wx.getStorageSync('token') || '')
      },
      success: function (res) {
        wx.hideLoading();
        try {
          var data = JSON.parse(res.data);
          if (data.code === 200 && data.data && data.data.url) {
            // 提交用相对地址；预览走 loadRemote（真机上 http 图片会被拦，先落本地）
            that.setData({
              faceImageUrl: data.data.url,
              faceImagePreview: request.resolveUrl(data.data.url)
            });
            imageUtil.loadRemote(data.data.url).then(function (local) {
              that.setData({ faceImagePreview: local });
            });
          }
        } catch (e) {
          // 保留本地路径作为 fallback
        }
      },
      fail: function () {
        wx.hideLoading();
        // 保留本地路径，打卡时后端会处理
      }
    });
  },

  // ═══════════════════════════════════════════════
  // 动画 & 音效
  // ═══════════════════════════════════════════════

  generateConfetti: function () {
    var colors = ['#FFD700', '#FF6B6B', '#4ECDC4', '#45B7D1', '#96CEB4', '#667EEA', '#F093FB'];
    var confettiList = [];
    for (var i = 0; i < 24; i++) {
      confettiList.push({
        id: i,
        color: colors[i % colors.length],
        x: (i * 41 + 7) % 100,
        delay: (i * 0.06).toFixed(2)
      });
    }
    this.setData({ showConfetti: true, confettiList: confettiList });
  },

  triggerPulse: function () {
    var that = this;
    this.setData({ animPulse: 'pulse-active' });
    setTimeout(function () { that.setData({ animPulse: '' }); }, 600);
  },

  triggerShake: function () {
    var that = this;
    this.setData({ animShake: 'shake-active' });
    setTimeout(function () { that.setData({ animShake: '' }); }, 500);
  },

  // ═══════════════════════════════════════════════
  // 导航
  // ═══════════════════════════════════════════════

  back: function () {
    wx.navigateBack();
  },

  onMapTap: function () {},

  noop: function () {}
});
