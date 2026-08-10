plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    application
}

kotlin {
    jvm()

    sourceSets {
        jvmMain {
            kotlin.srcDir("src/main/kotlin")
            dependencies {
                implementation(project(":common:core"))
                implementation(project(":common:ui"))
                implementation(project(":common:device-api"))
                implementation(project(":common:utils"))
                implementation(project(":platform:linux-tv"))
                implementation(project(":feature"))
                // Compose Desktop Runtime + Skiko，用于实际运行；
                // 注意：这里不使用 compose.desktop.application{} 块，
                // 因为它会与 application 插件的 installDist/run 任务冲突。
                implementation(compose.desktop.currentOs)
                implementation(compose.foundation)
                implementation(compose.material3)
            }
        }
    }
}

application {
    mainClass.set("com.tvbox.app.linuxtv.MainKt")
    applicationName = "TVBox"
}

// kotlin multiplatform 的 jvm target + application 插件组合下，
// 需显式把 jvm 运行时依赖传递进 application 插件，否则 installDist/distZip 的
// lib 目录中只会包含自身 jar，缺少所有 runtime 依赖（compose、kotlin 等）。
val jvmRuntimeConfiguration = configurations.named("jvmRuntimeClasspath")

tasks.named<JavaExec>("run") {
    classpath = files(tasks.named("jvmJar"), jvmRuntimeConfiguration)
}

application {
    distribution {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
}

tasks.withType<AbstractCopyTask>().configureEach {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.named<org.gradle.jvm.tasks.Jar>("jvmJar") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    // 没有依赖时至少保证 jar 不为空（避免构建脚本配置不完整导致的空壳发布）
    manifest {
        attributes(
            "Main-Class" to "com.tvbox.app.linuxtv.MainKt",
            "Class-Path" to configurations.jvmRuntimeClasspath.get().files.joinToString(" ") { "lib/${it.name}" }
        )
    }
}

// 显式把 jvm runtime 依赖装入 distribution
tasks.named<org.gradle.api.tasks.Sync>("installDist") {
    into("lib") {
        from(configurations.jvmRuntimeClasspath) {
            exclude("app-linux-tv-*.jar")
        }
    }
}

tasks.withType<AbstractArchiveTask>().configureEach {
    if (name in listOf("distZip", "distTar")) {
        into("lib") {
            from(configurations.jvmRuntimeClasspath) {
                exclude("app-linux-tv-*.jar")
            }
        }
    }
}
