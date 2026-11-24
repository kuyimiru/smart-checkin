/**
 * 定位工具函数 — Haversine 距离计算、权限处理、范围检测
 */

/**
 * 获取用户当前位置（含权限引导）
 * @returns {Promise<{latitude: number, longitude: number}>}
 */
function getCurrentLocation() {
  return new Promise(function (resolve, reject) {
    wx.getLocation({
      type: 'gcj02',
      isHighAccuracy: true,
      success: function (res) {
        resolve({
          latitude: res.latitude,
          longitude: res.longitude
        });
      },
      fail: function (err) {
        if (err.errMsg && err.errMsg.indexOf('auth deny') > -1) {
          wx.showModal({
            title: '需要位置权限',
            content: '打卡需要获取您的位置信息，请在设置中开启位置权限后重试。',
            confirmText: '去设置',
            success: function (m) {
              if (m.confirm) {
                wx.openSetting({
                  success: function (settingRes) {
                    if (settingRes.authSetting['scope.userLocation']) {
                      resolve(getCurrentLocation());
                    } else {
                      reject({
                        code: 'ERROR_定位失败',
                        message: '请打开 GPS 并授权位置权限'
                      });
                    }
                  }
                });
              } else {
                reject({
                  code: 'ERROR_定位失败',
                  message: '请打开 GPS 并授权位置权限'
                });
              }
            }
          });
        } else if (err.errMsg && err.errMsg.indexOf('system permission denied') > -1) {
          wx.showModal({
            title: '系统定位未开启',
            content: '请在手机系统设置中开启定位服务（GPS）。',
            showCancel: false,
            confirmText: '知道了'
          });
          reject({
            code: 'ERROR_定位失败',
            message: '请在系统设置中开启定位服务'
          });
        } else {
          wx.showToast({ title: '获取位置失败，请检查网络和GPS', icon: 'none' });
          reject({
            code: 'ERROR_定位失败',
            message: '请打开 GPS 并授权位置权限'
          });
        }
      }
    });
  });
}

/**
 * Haversine 公式计算两点间距离（米）
 * 与后端 DistanceUtil.calculateDistance 一致
 */
function calculateDistance(lat1, lng1, lat2, lng2) {
  var R = 6371000;
  var dLat = (lat2 - lat1) * Math.PI / 180;
  var dLng = (lng2 - lng1) * Math.PI / 180;
  var a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
          Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) *
          Math.sin(dLng / 2) * Math.sin(dLng / 2);
  var c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return R * c;
}

/**
 * 检测用户是否在打卡范围内
 * @returns {{ inRange: boolean, distance: number }}
 */
function checkInRange(userLat, userLng, targetLat, targetLng, radius) {
  var distance = calculateDistance(userLat, userLng, targetLat, targetLng);
  return {
    inRange: distance <= radius,
    distance: Math.round(distance)
  };
}

/**
 * 格式化距离显示
 */
function formatDistance(meters) {
  if (meters < 1000) {
    return meters + 'm';
  }
  return (meters / 1000).toFixed(1) + 'km';
}

/**
 * 格式化坐标显示
 */
function formatCoordinate(lat, lng) {
  return lat.toFixed(6) + ', ' + lng.toFixed(6);
}

module.exports = {
  getCurrentLocation: getCurrentLocation,
  calculateDistance: calculateDistance,
  checkInRange: checkInRange,
  formatDistance: formatDistance,
  formatCoordinate: formatCoordinate
};
