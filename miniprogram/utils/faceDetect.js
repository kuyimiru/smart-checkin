/**
 * 人脸检测工具 — 基于微信官方 wx.faceDetect（免费）
 *
 * ⚠️ 重要限制：
 * 1. wx.faceDetect 只接受「相机实时帧」(frameBuffer)，
 *    不能对 wx.chooseMedia 拍好的静态照片做检测。
 *    所以检测发生在实时取景阶段，而不是"拍照后"。
 * 2. 该接口可能有类目/资质限制，正式上线前需在 mp.weixin.qq.com
 *    后台确认是否对你开放；不可用时调用方会自动降级（跳过检测）。
 */

function isAvailable() {
  return typeof wx !== 'undefined' && typeof wx.faceDetect === 'function';
}

/**
 * 检测一帧图像中是否有人脸
 * @param {ArrayBuffer} frameBuffer 相机帧数据（onCameraFrame 的 frame.data）
 * @param {number} width 帧宽
 * @param {number} height 帧高
 * @returns {Promise<{hasFace: boolean, faces: Array, raw: Object}>}
 */
function detect(frameBuffer, width, height) {
  return new Promise(function (resolve, reject) {
    if (!isAvailable()) {
      reject({ code: 'UNAVAILABLE', message: 'wx.faceDetect 不可用' });
      return;
    }
    wx.faceDetect({
      frameBuffer: frameBuffer,
      width: width,
      height: height,
      enablePoint: true,
      enableConf: true,
      enableAngle: false,
      enableMultiFace: false,
      success: function (res) {
        var faces = res.faceInfo || [];
        resolve({ hasFace: faces.length > 0, faces: faces, raw: res });
      },
      fail: function (err) {
        reject(err);
      }
    });
  });
}

module.exports = {
  isAvailable: isAvailable,
  detect: detect
};
