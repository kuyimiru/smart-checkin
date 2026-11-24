// ═══════════════════════════════════════════════
// 网络请求模块 — 支持离线开发模式
// ═══════════════════════════════════════════════
//
// 后端地址配置（改这一处即可）：
//   开发者工具模拟器 → http://localhost:8080
//   真机 + 局域网    → http://电脑的局域网IP:8080
//   线上环境         → https://your-domain.com
//
// ⚠️ 避坑：不要填 192.168.56.1 —— 那是 VirtualBox 的 Host-Only 虚拟网卡地址，
//    电脑自己能访问，手机永远访问不到（表现为"模拟器正常、真机连不上"）。
//    查真实局域网 IP：在电脑上执行 ipconfig，看「以太网 / WLAN」那一块的 IPv4 地址。
//    注意校园网/公司网是 DHCP 分配的，IP 可能变，变了就重新查一次。
//
// 不想改代码时，也可以在开发者工具 Console 里一键切换（会写进 Storage，重启仍生效）：
//   require('utils/request').setBaseUrl('http://10.80.200.51:8080')
//
var DEFAULT_BASE_URL = 'http://10.80.200.51:8080';
var BASE_URL = (function () {
  try { return wx.getStorageSync('baseUrl') || DEFAULT_BASE_URL; }
  catch (e) { return DEFAULT_BASE_URL; }
})();

// 后端是否可达（运行时探测）
var backendReachable = true;
var lastCheckTime = 0;

// ═══════════════════════════════════════════════

var isRedirecting = false;

// 启动诊断
console.log('══════════════════════════════════════');
console.log('[网络] 后端地址: ' + BASE_URL);
console.log('[网络] 运行平台: ' + wx.getSystemInfoSync().platform);
console.log('[网络] 提示: 如真机不通，请用开发者工具模拟器调试');
console.log('══════════════════════════════════════');

/**
 * 核心请求函数
 * @param {object} options - { url, method, data, force }
 *   force: true 时强制尝试（忽略 backendReachable 缓存），用于登录重试
 */
var request = function (options) {
  return new Promise(function (resolve, reject) {
    // 如果已知后端不可达且非强制模式，直接走离线
    if (!backendReachable && !options.force) {
      reject({ code: -1, message: '后端不可达', offline: true });
      return;
    }

    var token = wx.getStorageSync('token') || '';
    var url = BASE_URL + options.url;

    wx.request({
      url: url,
      method: options.method || 'GET',
      data: options.data || {},
      header: {
        'Authorization': token ? 'Bearer ' + token : '',
        'Content-Type': 'application/json'
      },
      timeout: 8000,
      success: function (res) {
        // 成功响应，恢复可达标记
        backendReachable = true;

        if (res.statusCode === 401) {
          if (!isRedirecting) {
            isRedirecting = true;
            wx.removeStorageSync('token');
            wx.removeStorageSync('userId');
            wx.removeStorageSync('nickName');
            wx.removeStorageSync('avatarUrl');
            wx.removeStorageSync('realName');
            var app = getApp();
            if (app) { app.globalData.token = ''; app.globalData.userId = 0; }
            wx.showToast({ title: '登录已过期，请重新登录', icon: 'none', duration: 2000 });
            setTimeout(function () {
              wx.reLaunch({ url: '/pages/login/login' });
              setTimeout(function () { isRedirecting = false; }, 2000);
            }, 1500);
          }
          reject({ code: 401, message: '请重新登录' });
        } else if (res.statusCode >= 200 && res.statusCode < 300) {
          resolve(res.data);
        } else {
          var msg = (res.data && res.data.message) ? res.data.message : '请求失败(' + res.statusCode + ')';
          reject(res.data || { code: res.statusCode, message: msg });
        }
      },
      fail: function (err) {
        console.warn('[网络] 请求失败: ' + url + ' — ' + (err.errMsg || ''));

        // 标记后端不可达
        backendReachable = false;

        // 构造友好错误
        var friendlyMsg = '网络不可用';
        if (err.errMsg && err.errMsg.indexOf('url not in domain list') > -1) {
          friendlyMsg = '域名未授权（请用模拟器或真机调试模式）';
        } else if (err.errMsg && err.errMsg.indexOf('timeout') > -1) {
          friendlyMsg = '连接超时';
        } else if (err.errMsg && err.errMsg.indexOf('fail') > -1) {
          friendlyMsg = '后端未启动或网络不通';
        }

        reject({
          code: -1,
          message: friendlyMsg,
          offline: true,
          originalError: err
        });
      }
    });
  });
};

// ═══ 便捷方法 ═══

var get = function (url, data) {
  return request({ url: url, method: 'GET', data: data });
};

var post = function (url, data, opts) {
  return request(Object.assign({ url: url, method: 'POST', data: data }, opts || {}));
};

var put = function (url, data) {
  return request({ url: url, method: 'PUT', data: data });
};

var getBaseUrl = function () { return BASE_URL; };

/**
 * 把服务端返回的地址补成「能直接渲染」的绝对地址。
 *
 * 后端上传接口返回的是相对地址 `/uploads/face_xxx.jpg`，而 <image src="..."> 必须是
 * 完整地址或本地临时路径 —— 直接塞相对地址是加载不出来的（表现为"上传成功但头像空白"）。
 */
var resolveUrl = function (url) {
  if (!url || typeof url !== 'string') { return ''; }
  // 已经是完整地址 / 本地临时文件 / base64 → 原样返回
  if (/^(https?:|wxfile:|blob:|data:)/i.test(url)) { return url; }
  // 相对地址（/uploads/xxx.jpg）→ 拼上后端域名
  if (url.charAt(0) === '/') { return BASE_URL + url; }
  return url;
};

/** resolveUrl 的反向操作：提交给后端前把域名去掉，还原成 /uploads/xxx.jpg */
var toServerPath = function (url) {
  if (!url || typeof url !== 'string') { return ''; }
  if (BASE_URL && url.indexOf(BASE_URL) === 0) { return url.slice(BASE_URL.length); }
  return url;
};

/**
 * 检查后端是否可达（用于诊断）
 */
var ping = function () {
  return new Promise(function (resolve) {
    wx.request({
      url: BASE_URL + '/api/auth/login',
      method: 'POST',
      data: { code: 'ping', deviceId: 'ping' },
      timeout: 5000,
      success: function () {
        backendReachable = true;
        resolve(true);
      },
      fail: function () {
        backendReachable = false;
        resolve(false);
      }
    });
  });
};

/**
 * 设置后端地址（运行时动态切换，并写入 Storage 持久化）
 */
var setBaseUrl = function (url) {
  BASE_URL = url;
  backendReachable = true;
  try { wx.setStorageSync('baseUrl', url); } catch (e) { /* 忽略 */ }
  console.log('[网络] 后端地址已切换为: ' + url);
};

/** 恢复成代码里写死的默认地址（清掉 Storage 覆盖） */
var resetBaseUrl = function () {
  try { wx.removeStorageSync('baseUrl'); } catch (e) { /* 忽略 */ }
  BASE_URL = DEFAULT_BASE_URL;
  backendReachable = true;
  console.log('[网络] 后端地址已恢复默认: ' + BASE_URL);
};

module.exports = {
  request: request,
  get: get,
  post: post,
  put: put,
  getBaseUrl: getBaseUrl,
  resolveUrl: resolveUrl,
  toServerPath: toServerPath,
  ping: ping,
  setBaseUrl: setBaseUrl,
  resetBaseUrl: resetBaseUrl,
  isBackendReachable: function () { return backendReachable; }
};
