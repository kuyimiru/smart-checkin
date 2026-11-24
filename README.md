# 智能打卡小程序 · Smart Check-in

> 基于**人脸识别 + 地理围栏**的考勤打卡系统。Spring Boot 后端 + 微信小程序前端，覆盖打卡、请假、排班、活动管理全流程。

一个面向学生组织 / 小团队的轻量考勤系统：用手机完成签到，用「人脸 + 定位」双重校验防止代打卡，管理员在后台完成排班、审批与统计。

---

## ✨ 功能特性

### 考勤
- **人脸识别打卡**：集成 SeetaFace6，首次录入人脸特征，之后每次打卡做 1:1 比对
- **地理围栏校验**：打卡时校验当前位置是否在活动设定的半径范围内
- **打卡历史**：按活动 / 日期查看，支持异常状态标记

### 请假与审批
- 请假申请（事假 / 病假 / 其他，支持时间段与事由）
- 管理员审批（通过 / 驳回 + 审批意见）
- 请假记录与考勤状态联动

### 活动与排班
- 活动创建 / 编辑 / 详情 / 管理（含邀请码、打卡时间段、频率设置）
- **Excel 批量导入排班名单**
- 活动参与人管理

### 其他
- 登录鉴权（JWT）
- 个人资料与头像上传
- 数据统计与日历视图
- 定时任务：自动生成打卡提醒、活动到期处理

---

## 🧱 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Java 17、Spring Boot 2.7、MyBatis-Plus、Spring Validation |
| 数据库 | MySQL 8 |
| 人脸识别 | SeetaFace6（本地推理） |
| 文件处理 | Apache POI（Excel 导入） |
| 鉴权 | JWT |
| 前端 | 微信小程序原生（WXML / WXSS / JavaScript） |
| 部署 | 可执行 fat jar，`start.bat` / `start.sh` 一键启动 |

---

## 🏗 架构

```
┌─────────────────────────┐
│   微信小程序（15 个页面）  │
│  打卡 / 请假 / 排班 / 活动 │
└───────────┬─────────────┘
            │ HTTP + JSON（JWT）
┌───────────▼─────────────┐
│   Spring Boot 后端        │
│  ├─ Controller 层         │
│  ├─ Service 层            │
│  ├─ Mapper（MyBatis-Plus）│
│  └─ 定时任务 / 文件上传     │
└───────┬──────────┬──────┘
        │          │
   ┌────▼───┐  ┌───▼──────────┐
   │ MySQL  │  │ SeetaFace6   │
   │ 8 张表  │  │ 本地人脸推理   │
   └────────┘  └──────────────┘
```

---

## 📁 项目结构

```
smart-checkin/
├── backend/                       Spring Boot 后端
│   ├── src/main/java/com/checkin/
│   │   ├── controller/            接口层
│   │   ├── service/               业务逻辑
│   │   ├── mapper/                数据访问
│   │   ├── entity/                实体类
│   │   ├── config/                配置（鉴权 / 跨域 / 定时任务）
│   │   └── util/                  工具类（人脸、文件、日期）
│   ├── src/main/resources/
│   │   └── application.yml        配置（数据库 / 上传路径 / 模型路径）
│   └── pom.xml
├── miniprogram/                   微信小程序
│   ├── pages/                     15 个页面
│   ├── utils/                     请求封装、定位、主题
│   └── app.js / app.json
├── docs/                          开发说明与文档
├── smart_checkin.sql              建表脚本
├── migration_v2_features.sql      v2 增量迁移
└── start.bat / start.sh           一键启动
```

---

## 🚀 快速开始

### 1. 初始化数据库

```sql
mysql -uroot -p
CREATE DATABASE smart_checkin DEFAULT CHARSET utf8mb4;
USE smart_checkin;
source smart_checkin.sql;
```

### 2. 配置

编辑 `backend/src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/smart_checkin?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai
    username: ${DB_USER:root}
    password: ${DB_PASSWORD:请填入你的密码}
```

> 密码支持通过环境变量 `DB_USER` / `DB_PASSWORD` 覆盖，不要直接提交真实密码。

### 3. 启动后端

```bash
# Windows
start.bat

# Linux / macOS
bash start.sh
```

启动后访问 `http://localhost:8080`。

### 4. 打开小程序

微信开发者工具 → 导入项目 → 选择 `miniprogram` 目录 → 修改 `utils/request.js` 里的后端地址。

---

## 🔍 实现要点

**为什么用「人脸 + 定位」双重校验**
只靠定位可以代打卡（手机交给别人），只靠人脸可以远程伪造。两者同时校验，成本低且能拦住大部分代打场景。

**人脸识别如何落地**
SeetaFace6 是纯本地的 C++ 推理库，通过 Java 调用。首次录入时提取特征存库，打卡时提取当前帧特征做比对，阈值可配置。整个链路不依赖云端，适合校园/内网环境。

**Excel 排班导入**
用 Apache POI 解析上传的表格，逐行校验学号/姓名，冲突时返回具体行号与原因，避免整批失败。

**定时任务**
基于 Spring 的 `@Scheduled`：每日生成打卡提醒、清理过期活动状态，避免手动维护。

---

## 📸 截图

> 建议补充以下截图到 `docs/` 目录，并在 README 顶部引用：
> - 打卡页（含定位与人脸框）
> - 请假审批列表
> - 活动详情
> - 统计看板

---

## 📄 说明

- 本项目为个人独立完成的课程实践项目，前后端与数据库设计均由本人实现
- 详细的开发与部署说明见 [docs/开发说明.md](docs/开发说明.md)
