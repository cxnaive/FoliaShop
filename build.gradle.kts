plugins {
    java
    `java-library`
    id("com.gradleup.shadow") version "9.3.1"
}

group = "dev.user"
version = "1.2.1"

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
    compileOnly("dev.folia:folia-api:1.21.11-R0.1-SNAPSHOT")

    // XConomy 经济插件
    compileOnly("com.github.YiC200333:XConomyAPI:2.25.1")

    // CraftEngine
    compileOnly("net.momirealms:craft-engine-bukkit:0.0.67")
    compileOnly("net.momirealms:craft-engine-core:0.0.67")

    // NBT API
    compileOnly("de.tr7zw:item-nbt-api-plugin:2.15.5")

    // PlayerPoints
    compileOnly("org.black_ixx:playerpoints:3.3.3")

    // 数据库连接池和驱动 (由服务器通过 plugin.yml libraries 加载)
    compileOnly("com.zaxxer:HikariCP:6.2.1")
    compileOnly("com.h2database:h2:2.3.232")
    compileOnly("com.mysql:mysql-connector-j:9.2.0")

    // 序列化 (由服务器通过 plugin.yml libraries 加载)
    compileOnly("com.google.code.gson:gson:2.12.1")

    // LZ4 压缩 (由服务器通过 plugin.yml libraries 加载)
    compileOnly("org.lz4:lz4-java:1.8.0")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    sourceCompatibility = "21"
    targetCompatibility = "21"
}

tasks.shadowJar {
    archiveClassifier.set("")
    archiveFileName.set("folia_shop-${version}.jar")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
