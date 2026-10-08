// CI 环境(GitHub Actions runner 在海外)走官方仓库;本地开发走阿里云镜像加速。
// 本地仍保留官方仓库兜底,镜像不可达时可自动降级。
val onCi = System.getenv("GITHUB_ACTIONS") == "true"

pluginManagement {
    repositories {
        if (onCi) {
            google()
            mavenCentral()
            gradlePluginPortal()
        } else {
            maven("https://maven.aliyun.com/repository/gradle-plugin")
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/public")
        }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        if (!onCi) {
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/public")
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "tingshu-app"
include(":app")
