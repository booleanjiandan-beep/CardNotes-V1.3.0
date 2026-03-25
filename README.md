# CardNote — 卡片笔记 App

基于 **Kotlin + Jetpack Compose + Room** 的 Android 卡片笔记应用。

## 功能清单

| 功能 | 说明 |
|------|------|
| 卡片浏览 | HorizontalPager 左右滑动切换笔记卡片 |
| 筛选 | 顶部复选框：已下载 / 未下载 / 全部 |
| 搜索 | 点击🔍展开，全字段模糊匹配（名称/URL/备注），300ms 防抖 |
| 新增笔记 | 右下角 FAB → BottomSheet 表单 |
| 删除笔记 | 卡片底部删除按钮 → 二次确认弹窗，同步删除关联图片文件 |
| 图片管理 | 相册多选 / 拍照，图片复制到 App 私有目录，原图删除不影响显示 |
| 单张图片删除 | 长按图片区域进入编辑模式，点击缩略图的 ✕ 删除单张图片 |
| 搜索高亮 | 匹配词高亮为金黄色 |
| 下载状态切换 | 卡片内一键切换已下载 / 未下载 |

## 图片存储说明

```
用户选图/拍照 (临时 URI)
       ↓  copyToPrivateStorage()
<filesDir>/note_images/<uuid>.jpg   ← App 私有目录
       ↓
数据库 images 列存储绝对路径
```

- 删除相册原图 → **不影响** App 显示
- 删除笔记 → **自动清理**对应图片文件
- App 卸载 → 系统自动回收 `filesDir`，无残留

---

## 上传 GitHub 前的准备步骤

### 1. 生成 `gradle-wrapper.jar`（**必须**）

项目需要 `gradle/wrapper/gradle-wrapper.jar` 才能在 CI 上编译。
由于该文件是二进制，有两种方式获得：

**方式 A（推荐）：在本机 Android Studio 中生成**
```bash
# 在项目根目录执行（需要本地已安装 Gradle 8.x）
gradle wrapper --gradle-version=8.10.2
# 执行后会自动生成 gradle/wrapper/gradle-wrapper.jar
```

**方式 B：从官方下载**
```bash
curl -L "https://raw.githubusercontent.com/gradle/gradle/v8.10.2/gradle/wrapper/gradle-wrapper.jar" \
     -o gradle/wrapper/gradle-wrapper.jar
```

**方式 C：直接用 Android Studio 打开项目**
Android Studio 会自动同步并生成所有 Gradle Wrapper 文件。

> ⚠️ `gradle-wrapper.jar` 需要提交到 Git（.gitignore 中已用 `!gradle/wrapper/gradle-wrapper.jar` 排除在忽略规则之外）。

### 2. 上传到 GitHub

```bash
git init
git add .
git commit -m "Initial commit: CardNote App"
git remote add origin https://github.com/<你的用户名>/CardNoteApp.git
git push -u origin main
```

### 3. GitHub Actions 自动编译

Push 到 `main` 或 `develop` 分支后，Actions 自动触发：
- ✅ Debug APK → Artifacts（保留 14 天）
- ✅ Release APK（仅 main 分支）→ Artifacts（保留 30 天）
- ✅ 单元测试报告

在 GitHub 仓库页面 → **Actions** → 对应 workflow → **Artifacts** 下载 APK。

也可手动触发：Actions → `Android CI — Build APK` → **Run workflow**。

---

## 本地编译

```bash
# Debug APK
./gradlew assembleDebug

# Release APK（未签名）
./gradlew assembleRelease

# 单元测试
./gradlew testDebugUnitTest
```

APK 输出路径：`app/build/outputs/apk/`

## 技术栈

- **语言**：Kotlin 2.1.0
- **UI**：Jetpack Compose + Material3
- **数据库**：Room 2.6.1（KSP 注解处理）
- **图片加载**：Coil 2.7.0
- **架构**：MVVM + Repository + StateFlow
- **CI**：GitHub Actions（`gradle/actions/setup-gradle@v4`）
