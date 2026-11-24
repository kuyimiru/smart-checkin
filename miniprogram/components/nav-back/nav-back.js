/**
 * 常驻返回键（左上角）
 *
 * 解决的问题：部分页面进入后没有返回入口 ——
 *   1) `wx.reLaunch` 进入的页面（如登录后直接进「个人资料」），页面栈只有 1 层，原生返回箭头不显示；
 *   2) 使用 `navigationStyle: custom` 自绘导航栏的页面，根本没有原生箭头。
 *
 * 组件行为（自适应，不需要调用方判断）：
 *   - 页面栈 > 1 层 → 文案「返回」，点击 navigateBack；
 *   - 页面栈只有 1 层 → 文案「首页」，点击 switchTab 回首页（兜底 reLaunch）。
 *
 * 用法：
 *   <nav-back />                              默认导航栏页面直接用
 *   <nav-back custom-nav="{{true}}" />        自绘导航栏页面（自动避开状态栏）
 */
Component({
  properties: {
    // 页面是否 navigationStyle: custom —— 是的话需要自己避开状态栏高度
    customNav: { type: Boolean, value: false },
    // 自定义文案；不传则自动判断「返回 / 首页」
    text: { type: String, value: '' }
  },

  data: {
    label: '返回',
    top: 8,
    visible: false
  },

  lifetimes: {
    attached() {
      const stack = getCurrentPages();
      const canBack = !!stack && stack.length > 1;

      let top = 8;
      if (this.data.customNav) {
        let statusBarHeight = 20;
        try {
          const info = wx.getWindowInfo ? wx.getWindowInfo() : wx.getSystemInfoSync();
          statusBarHeight = info.statusBarHeight || 20;
        } catch (e) {
          // 取不到就用默认值，不影响功能
        }
        top = statusBarHeight + 8;
      }

      this.setData({
        top: top,
        // 关键：使用系统导航栏的页面，栈里有上一页时微信自带返回箭头 ——
        // 这时胶囊就不必出现（避免两个返回键）；只有"没有原生返回键"时才显示。
        //   自绘导航栏（customNav）→ 永远没有原生箭头 → 显示
        //   栈底页面（reLaunch 进来的）→ 原生箭头不显示 → 显示（文案"首页"）
        visible: this.data.customNav || !canBack,
        label: this.data.text || (canBack ? '返回' : '首页')
      });
    }
  },

  methods: {
    onTap() {
      const stack = getCurrentPages();
      if (stack && stack.length > 1) {
        wx.navigateBack({
          delta: 1,
          // navigateBack 在极端情况下（栈被清）会失败，兜底回首页
          fail() { wx.switchTab({ url: '/pages/index/index' }); }
        });
        return;
      }
      // 已经是栈底（reLaunch 进来的）→ 回首页
      wx.switchTab({
        url: '/pages/index/index',
        fail() { wx.reLaunch({ url: '/pages/index/index' }); }
      });
    }
  }
});
