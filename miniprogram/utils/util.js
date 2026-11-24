const formatDate = (date) => {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
};

const formatTime = (date) => {
  const h = String(date.getHours()).padStart(2, '0');
  const m = String(date.getMinutes()).padStart(2, '0');
  return `${h}:${m}`;
};

/**
 * 打卡按钮状态。
 *
 * 注意顺序：**先判断活动本身是否有效（未开始/已结束/已关闭），再判断个人打卡状态**。
 * 早期版本没有这一步，导致活动结束好几天了，详情页的"去打卡"按钮还是亮的。
 *
 * @param {object} activity   活动对象（含 displayStatus / checkInStartTime）
 * @param {object} todayRecord 今日打卡记录，可为 null
 * @param {object} leaveInfo   今日已批准的请假，可为 null
 */
const getCheckInButtonState = (activity, todayRecord, leaveInfo) => {
  activity = activity || {};
  const ds = activity.displayStatus;

  // ① 活动维度
  if (ds === 'closed') {
    return { text: '活动已关闭', shortText: '已关闭', bgColor: '#BFBFBF', disabled: true, status: 'closed' };
  }
  if (ds === 'archived' || ds === 'expired') {
    return { text: '活动已结束', shortText: '已结束', bgColor: '#BFBFBF', disabled: true, status: 'expired' };
  }
  if (ds === 'pending') {
    return { text: '活动未开始', shortText: '未开始', bgColor: '#BFBFBF', disabled: true, status: 'pending' };
  }

  // ② 个人维度
  if (leaveInfo) {
    return { text: '今日已请假', shortText: '已请假', bgColor: '#BFBFBF', disabled: true, status: 'leave' };
  }
  if (todayRecord) {
    switch (todayRecord.status) {
      case 'normal':
        return { text: '今日已打卡', shortText: '已打卡', bgColor: '#52C41A', disabled: true, status: 'normal' };
      case 'late':
        return { text: '已迟到补签', shortText: '已补签', bgColor: '#FAAD14', disabled: true, status: 'late' };
      case 'abnormal':
        return { text: '今日异常', shortText: '异常', bgColor: '#F5222D', disabled: true, status: 'abnormal' };
      default:
        break;
    }
  }

  // ③ 时间维度
  const now = new Date();
  const cm = now.getHours() * 60 + now.getMinutes();
  const startTime = activity.checkInStartTime || '00:00';
  const parts = String(startTime).split(':');
  const sm = parseInt(parts[0] || '0', 10) * 60 + parseInt(parts[1] || '0', 10);
  if (cm < sm) {
    return { text: '等待打卡 ' + startTime, shortText: '未到时间', bgColor: '#BFBFBF', disabled: true, status: 'waiting' };
  }
  return { text: '去打卡', shortText: '去打卡', bgColor: '#1890FF', disabled: false, status: 'available' };
};

const getDaysInMonth = (year, month) => new Date(year, month, 0).getDate();
const getFirstDayOfMonth = (year, month) => new Date(year, month - 1, 1).getDay();

/** 活动展示状态的文字，供各页面统一使用 */
const activityStatusText = (ds) => {
  switch (ds) {
    case 'active': return '进行中';
    case 'pending': return '未开始';
    case 'closed': return '已关闭';
    case 'archived': return '已归档';
    case 'expired': return '已结束';
    default: return '已结束';
  }
};

module.exports = {
  formatDate, formatTime, getCheckInButtonState,
  getDaysInMonth, getFirstDayOfMonth, activityStatusText
};
