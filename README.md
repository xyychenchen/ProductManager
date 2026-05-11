# 产品管理 APP (Product Manager)

一个简洁的 Android 应用，用于记录和管理产品信息，支持字母索引快速查找。

## 功能特点

- ✅ **产品信息管理**：记录产品名称、规格、尺寸、价格（美金）
- ✅ **产品照片**：支持上传产品图片
- ✅ **字母索引栏**：右侧 A-Z 字母快速滑动查找
- ✅ **简洁主页**：直接在列表显示产品信息和价格
- ✅ **搜索功能**：按名称或规格搜索产品
- ✅ **本地存储**：数据保存在手机本地，无需联网

## 界面预览

```
┌─────────────────────────────┐
│  🔍 搜索产品...          [A]│
├─────────────────────────────┤
│ A                           │
│ ┌────┐ Product Name   $99.99│
│ │ 📷 │ 规格: Large           │
│ └────┘ 尺寸: 10x20cm        │
├─────────────────────────────┤
│ B                           │
│ ┌────┐ Bag           $49.99 │
│ │ 📷 │ 规格: Medium          │
│ └────┘                      │
├─────────────────────────────┤
│ ...                      [Z]│
│                            #│
│                  [➕ 添加]   │
└─────────────────────────────┘
```

## 使用 GitHub Actions 编译 APK

### 方法一：Fork 后自动编译

1. **Fork 本仓库**
   - 登录 GitHub
   - 点击右上角 "Fork" 按钮
   - 创建您自己的仓库副本

2. **自动编译**
   - Fork 后，GitHub Actions 会自动触发编译
   - 点击仓库的 "Actions" 标签页
   - 等待编译完成（约 5-10 分钟）

3. **下载 APK**
   - 编译完成后，点击对应的 workflow
   - 在 "Artifacts" 区域下载 `app-debug` 或 `app-release`
   - 解压后得到 APK 文件

### 方法二：手动触发编译

1. 进入仓库的 "Actions" 标签页
2. 选择 "Build Android APK" workflow
3. 点击 "Run workflow" 按钮
4. 等待编译完成后下载 APK

## 本地编译（需要 Android Studio）

如果您安装了 Android Studio，可以本地编译：

### 环境要求

- Android Studio Hedgehog (2023.1.1) 或更高版本
- JDK 17
- Android SDK 34

### 编译步骤

1. **克隆仓库**
   ```bash
   git clone https://github.com/你的用户名/ProductManager.git
   cd ProductManager
   ```

2. **打开项目**
   - 启动 Android Studio
   - 选择 "Open an Existing Project"
   - 选择 ProductManager 文件夹

3. **同步 Gradle**
   - 等待 Gradle 同步完成

4. **编译 APK**
   - 点击 `Build > Build APK(s)`
   - 或连接手机后点击运行按钮

## 项目结构

```
ProductManager/
├── app/
│   ├── src/main/
│   │   ├── java/com/productmanager/
│   │   │   ├── MainActivity.java        # 主界面
│   │   │   ├── AddProductActivity.java  # 添加/编辑产品
│   │   │   ├── Product.java             # 产品数据模型
│   │   │   ├── ProductDao.java          # 数据访问接口
│   │   │   ├── ProductDatabase.java     # Room 数据库
│   │   │   ├── ProductAdapter.java      # 列表适配器
│   │   │   └── SideIndexBar.java        # 字母索引栏
│   │   ├── res/                         # 资源文件
│   │   └── AndroidManifest.xml
│   └── build.gradle
├── gradle/wrapper/                      # Gradle 包装器
├── .github/workflows/build.yml          # GitHub Actions 配置
└── README.md
```

## 技术栈

- **语言**：Java
- **最低 SDK**：Android 7.0 (API 24)
- **目标 SDK**：Android 14 (API 34)
- **数据库**：Room Persistence Library
- **图片加载**：Glide
- **UI 组件**：Material Design Components

## 安装 APK

1. 下载 APK 文件到手机
2. 点击 APK 文件
3. 如果提示"未知来源"，请在设置中允许安装
4. 按提示完成安装

## 使用说明

### 添加产品
1. 点击右下角的 ➕ 按钮
2. 输入产品名称（必填）
3. 输入价格（必填，美金）
4. 可选：填写规格、尺寸、上传照片
5. 点击"保存"

### 查找产品
- **搜索**：在顶部搜索框输入关键词
- **字母索引**：滑动右侧字母栏快速定位

### 编辑/删除产品
- **编辑**：点击产品条目进入编辑页面
- **删除**：长按产品条目，选择删除

## 自定义修改

如需修改应用名称或包名：

1. **应用名称**：修改 `app/src/main/res/values/strings.xml` 中的 `app_name`
2. **包名**：修改 `app/build.gradle` 中的 `applicationId` 和 Java 文件的包名

## 许可证

MIT License

---

**如有问题或建议，欢迎提交 Issue 或 Pull Request！**
