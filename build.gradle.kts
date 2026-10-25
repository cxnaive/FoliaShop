plugins {
    java
    `java-library`
    id("com.gradleup.shadow") version "9.3.1"
}

group = "dev.user"
version = "1.4.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
    maven("https://repo.codemc.io/repository/maven-public/")
    maven("https://repo.momirealms.net/releases/")
    maven("https://repo.rosewooddev.io/repository/public/")
    gradlePluginPortal()
}

dependencies {
    compileOnly("dev.folia:folia-api:26.2.build.7-beta")

    // XConomy 经济插件
    compileOnly("com.github.YiC200333:XConomyAPI:2.25.1")

    // CraftEngine
    compileOnly("net.momirealms:craft-engine-core:26.7")
    compileOnly("net.momirealms:craft-engine-bukkit:26.7")

    // NBT API
    compileOnly("de.tr7zw:item-nbt-api-plugin:2.16.1")

    // PlayerPoints
    compileOnly("org.black_ixx:playerpoints:3.3.3")

    // Aiyatsbus 更多附魔插件（软依赖，本地构建的 jar）
    // 路径相对于本项目目录；会自动匹配 Aiyatsbus-*.jar（排除 sources），忽略版本号
    compileOnly(fileTree("../aiyatsbus/plugin/build/libs") {
        include("Aiyatsbus-*.jar")
        exclude("*-sources.jar")
    })

    // 数据库连接池和驱动 (由服务器通过 plugin.yml libraries 加载)
    compileOnly("com.zaxxer:HikariCP:6.2.1")
    compileOnly("com.h2database:h2:2.3.232")
    compileOnly("com.mysql:mysql-connector-j:9.2.0")

    // 序列化 (由服务器通过 plugin.yml libraries 加载)
    compileOnly("com.google.code.gson:gson:2.12.1")

    // LZ4 压缩 (由服务器通过 plugin.yml libraries 加载)
    compileOnly("org.lz4:lz4-java:1.8.0")

    testImplementation("dev.folia:folia-api:26.2.build.7-beta")
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
    testImplementation("org.mockito:mockito-core:5.18.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(25)
}

tasks.test {
    useJUnitPlatform()
}

// 将 project.version 注入 plugin.yml 的 ${version} 占位符
tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.shadowJar {
    archiveClassifier.set("")
    archiveFileName.set("folia_shop-${version}.jar")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
