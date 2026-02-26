plugins {
    java
    `java-library`
    id("com.gradleup.shadow") version "9.0.0-beta17"
}

group = "dev.user"
version = "1.0.3"

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

    // 数据库连接池和驱动
    implementation("com.zaxxer:HikariCP:6.2.1")
    implementation("com.h2database:h2:2.3.232")
    implementation("com.mysql:mysql-connector-j:9.2.0")

    // 序列化
    implementation("com.google.code.gson:gson:2.12.1")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    sourceCompatibility = "21"
    targetCompatibility = "21"
}

tasks.shadowJar {
    archiveClassifier.set("")
    archiveFileName.set("folia_shop-${version}.jar")

    // 重定位依赖包
    relocate("org.h2", "dev.user.shop.libs.org.h2")
    relocate("com.zaxxer", "dev.user.shop.libs.com.zaxxer")
    relocate("com.mysql", "dev.user.shop.libs.com.mysql")
    relocate("com.google.gson", "dev.user.shop.libs.com.google.gson")

    // 排除不需要的文件
    exclude("META-INF/*.SF")
    exclude("META-INF/*.DSA")
    exclude("META-INF/*.RSA")
    exclude("META-INF/LICENSE*")
    exclude("LICENSE*")

    mergeServiceFiles {
        include("META-INF/services/java.sql.Driver")
    }

    minimize {
        exclude(dependency("com.h2database:h2:.*"))
        exclude(dependency("com.mysql:mysql-connector-j:.*"))
        exclude(dependency("com.zaxxer:HikariCP:.*"))
    }
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
