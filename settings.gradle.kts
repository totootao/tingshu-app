// CI 环境(GitHub Actions runner 在海外)走官方仓库;本地开发走阿里云镜像加速。
// 注意:pluginManagement 块会被 Gradle 提取到脚本最前执行,
// 块外声明的变量在其中不可见,因此各块内独立求值。
pluginManagement {
    val onCi = System.getenv("GITHUB_ACTIONS") == "true"
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
    val onCi = System.getenv("GITHUB_ACTIONS") == "true"
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
