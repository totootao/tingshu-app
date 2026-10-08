# 听书(复刻版)

[![Build APK](https://github.com/totootao/tingshu-app/actions/workflows/build.yml/badge.svg)](https://github.com/totootao/tingshu-app/actions/workflows/build.yml)

复刻自 [eprendre/tingshu](https://github.com/eprendre/tingshu)"我的听书"App 的开源插件架构,使用现代技术栈重新实现的整体功能 Android 应用。

## 与原版的关系

| 维度 | 说明 |
|---|---|
| 插件 API | `com.github.eprendre.tingshu.**` 包的类签名与原版 stub **完全一致** |
| 外置源 jar | **与原版 app 完全兼容** —— 原版的订阅接口、jar 包可直接使用 |
| 内置源 | 移植了原仓库中验证存活的 5 个源:酷我畅听、云图有声、哔哩哔哩、恋听网、LibriVox |
| 播放内核 | Media3/ExoPlayer(原版为自研 ffmpeg 内核) |
| UI | Jetpack Compose + Material3 |

## 功能清单

- ✅ 多源聚合搜索(可切换单源模式)/ 分类发现(大分类 + 子分类 + 翻页)
- ✅ 书籍详情、章节列表(支持分页章节源,"正在加载章节: x/y"提示)
- ✅ 书架收藏、播放进度记忆、播放历史
- ✅ 播放:上一/下一集、快退10s/快进30s、倍速 0.5x~3.0x、跳过静音、睡眠定时、通知栏/锁屏控制(MediaSession)
- ✅ 章节音频缓存(直链源自动预缓存后续章节,离线可听)
- ✅ WebView 音频提取/媒体流嗅探、源登录、搜索验证
- ✅ 外置源 jar 动态加载(DexClassLoader)+ 订阅接口自动更新 + 源调试页(输入 `cat` 调分类)
- ✅ UA 自定义(手机/PC)、聚合搜索开关、缓存上限设置
- ⚠️ 与原版的差异:不支持音量增强(原版依赖 ffmpeg 硬件级增益)、不支持下载管理(以缓存替代)、歌词接口(ILrc)为简化实现

## 技术栈

- Gradle 8.7 + AGP 8.4.2 + Kotlin 1.9.24 + JDK 17
- Compose BOM 2024.05 + Navigation + Material3
- Media3 1.3.1(ExoPlayer + media3-session)
- Room 2.6.1(KSP)
- 插件兼容层:Fuel 2.3.1 / Jsoup 1.13.1 / Gson 2.9.0 / OkHttp 4.10 / RxJava 2(与原版版本一致,保证外置 jar 可解析)

## 构建

### 方式一:GitHub Actions 自动编译(推荐,无需本地环境)

每次 push 到 `main` 会自动编译,APK 两种获取途径:

1. **Release 下载**:打开 [Releases](https://github.com/totootao/tingshu-app/releases) 直接下载 `tingshu-app-debug.apk`(打 tag `v*` 时自动发布);
2. **Actions Artifact**:打开 [Actions](https://github.com/totootao/tingshu-app/actions) → 选择最新一次构建 → Artifacts 下载 `tingshu-app-debug`(保留 30 天)。

也可以在本页手动触发:Actions → Build APK → Run workflow。

### 方式二:本地编译

```bash
# Android Studio 直接打开,或:
./gradlew :app:assembleDebug   # 产物: app/build/outputs/apk/debug/app-debug.apk
```

要求 JDK 17+,`local.properties` 配置 SDK 路径(或 ANDROID_HOME 环境变量)。
Gradle 发行版走腾讯镜像(`gradle/wrapper/gradle-wrapper.properties`),依赖走阿里云镜像(`settings.gradle.kts`)。

## 目录结构

```
app/src/main/java/
├── com/github/eprendre/tingshu/     # 插件 API 层(勿改包名/签名!外置 jar 依赖它)
│   ├── sources/                     # TingShu 基类、6 种 AudioUrlExtractor、各功能接口
│   ├── utils/                       # Book/Episode/BookDetail/Category/ConfigItem/SourceRuntime
│   └── extensions/                  # config/UA/Cookie/Toast/播放控制等扩展函数
└── com/totootao/tingshu/
    ├── App.kt                       # 注册 SourceRuntime 各桥
    ├── data/                        # 源仓库(jar 加载)/ 订阅管理 / Room / 设置
    ├── playback/                    # PlaybackManager(核心)/ PlayerService / 章节缓存
    ├── webview/                     # 提取/嗅探/登录/验证 WebView 容器
    ├── sources/builtin/             # 内置源(与外置源写法完全一致,可当模板)
    └── ui/                          # Compose 界面(书架/搜索/发现/详情/播放/源管理/调试/设置)
```

## 写新源

参考 `app/src/main/java/com/totootao/tingshu/sources/builtin/KuWo.kt`(最简 JSON API 源)或 `YunTuYouSheng.kt`(分页章节源)。要点:

1. `object Xxx : TingShu()`,实现 7 个抽象方法,sourceId 用 UUID;
2. 外置源:在独立 Gradle 项目里按原版 tingshu 仓库的方式打包 jar(`MY_SOURCES_PACKAGE` + d8),本 app 直接加载;
3. 订阅接口 JSON 格式与原版一致(`version/entry_package/download_url/update_msg/support_url`)。

## 免责声明

本项目仅供学习交流,请勿用于商业用途;请尊重内容版权,源站点资源归各网站所有。
