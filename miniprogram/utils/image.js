/**
 * 图片工具：压缩上传 + 网络图片落地显示
 *
 * 用法：
 *   var imageUtil = require('../../utils/image');
 *   imageUtil.compress(tempFilePath, 70).then(path => wx.uploadFile({ filePath: path, ... }));
 *   imageUtil.loadRemote(u.faceImageUrl).then(local => this.setData({ faceImagePreview: local }));
 */

var request = require('./request');

// 默认质量（0~100）。人脸识别需要保留细节，70~80 足够，再低会影响识别率。
var DEFAULT_QUALITY = 75;

/**
 * 压缩图片。任何异常情况（不支持、格式不支持、压缩失败）都 resolve 原路径，
 * 调用方永远拿得到一个可上传的路径。
 *
 * @param {String} filePath 本地临时路径
 * @param {Number} [quality] 压缩质量，默认 75
 * @return {Promise<String>} 压缩后的临时路径（失败时为原路径）
 */
function compress(filePath, quality) {
  return new Promise(function (resolve) {
    if (!filePath) { resolve(filePath); return; }
    // 环境不支持（老版本基础库/部分开发者工具）→ 原图
    if (typeof wx === 'undefined' || typeof wx.compressImage !== 'function') {
      resolve(filePath);
      return;
    }
    wx.compressImage({
      src: filePath,
      quality: quality || DEFAULT_QUALITY,
      success: function (res) {
        resolve((res && res.tempFilePath) || filePath);
      },
      fail: function () {
        // 注意：wx.compressImage 只支持 jpg，png 会走这里 —— 回退原图即可
        resolve(filePath);
      }
    });
  });
}

// 同一次运行内缓存已下载的网络图：url -> 本地临时路径
var remoteCache = {};

/**
 * 把服务端图片地址转成"真机上一定能显示"的本地路径。
 *
 * 背景：后端返回的是 http://局域网IP:8080/uploads/xxx.jpg，
 * 真机上 <image> 直接加载 http 图片会被微信拦掉（表现为头像空白），
 * 而 wx.downloadFile 在开发模式下和 wx.request 一样放行。
 *
 * 流程：相对地址补域名 → 是 http(s) 就先下载到本地临时文件 → 返回本地路径。
 * 任何一步失败都原样返回地址，交给 <image> 自己兜底，**绝不阻塞页面**。
 *
 * @param {String} url 服务端地址（/uploads/xxx.jpg 或完整地址）
 * @return {Promise<String>} 可直接给 <image src> 用的路径
 */
function loadRemote(url) {
  return new Promise(function (resolve) {
    var full = request.resolveUrl(url);
    if (!full) { resolve(''); return; }
    // 已经是本地临时路径（刚拍的照片）→ 直接用
    if (!/^https?:\/\//i.test(full)) { resolve(full); return; }
    if (remoteCache[full]) { resolve(remoteCache[full]); return; }
    if (typeof wx === 'undefined' || typeof wx.downloadFile !== 'function') { resolve(full); return; }

    wx.downloadFile({
      url: full,
      success: function (res) {
        if (res.statusCode === 200 && res.tempFilePath) {
          remoteCache[full] = res.tempFilePath;
          resolve(res.tempFilePath);
        } else {
          resolve(full);
        }
      },
      fail: function () {
        // 下载失败（比如后端没起）→ 退回原地址，让 <image> 再试一次
        resolve(full);
      }
    });
  });
}

module.exports = {
  compress: compress,
  loadRemote: loadRemote,
  DEFAULT_QUALITY: DEFAULT_QUALITY
};
