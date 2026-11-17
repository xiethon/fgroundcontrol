import java.util.Properties

plugins { id("com.android.application"); id("org.jetbrains.kotlin.plugin.compose") }
// 本地地图配置不入库；缺少配置时仍允许构建并在页面填写。
val localMapProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.isFile) file.inputStream().use { load(it) }
}
fun localMapKey(name: String): String {
    val value = localMapProperties.getProperty(name, "").trim()
    require(value.isEmpty() || value.matches(Regex("[A-Za-z0-9_-]{8,256}"))) { "Invalid map key format in local.properties: $name" }
    return "\"$value\""
}
android {
    namespace = "com.fairland.fgroundcontrol"
    compileSdk = 36
    ndkVersion = "28.2.13676358"
    sourceSets.getByName("main").jniLibs.directories.add(layout.buildDirectory.dir("generated/cxx-runtime").get().asFile.path)
    sourceSets.getByName("main").assets.directories.add(layout.buildDirectory.dir("generated/runtime-notices").get().asFile.path)
    defaultConfig { testInstrumentationRunner = "com.fairland.fgroundcontrol.SdkProbeInstrumentation"; applicationId = "com.fairland.fgroundcontrol"; minSdk = 28; targetSdk = 36; versionCode = 1; versionName = "0.1.0-alpha.1"
        buildConfigField("String", "TIANDITU_TOKEN", localMapKey("maps.tianditu.token"))
        buildConfigField("String", "MAPTILER_KEY", localMapKey("maps.maptiler.key"))
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies {
    implementation("org.maplibre.gl:android-sdk:13.7.1")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.material3:material3:1.3.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("io.mavsdk:mavsdk:4.1.0")
    implementation("io.mavsdk:mavsdk-server:4.1.0")
    testImplementation("junit:junit:4.13.2")
}

// MAVSDK 4.1.0 AAR 依赖共享 C++ 运行库但未包含它；从固定 NDK 可复现打包。
val packageCxxRuntime by tasks.registering(Sync::class) {
    val ndkRoot = androidComponents.sdkComponents.sdkDirectory.map { it.dir("ndk/28.2.13676358") }
    val host = if (System.getProperty("os.name").startsWith("Mac")) "darwin-x86_64" else if (System.getProperty("os.name").startsWith("Windows")) "windows-x86_64" else "linux-x86_64"
    val triples = mapOf("arm64-v8a" to "aarch64-linux-android", "armeabi-v7a" to "arm-linux-androideabi", "x86" to "i686-linux-android", "x86_64" to "x86_64-linux-android")
    triples.forEach { (abi, triple) ->
        from(ndkRoot.map { it.file("toolchains/llvm/prebuilt/$host/sysroot/usr/lib/$triple/libc++_shared.so") }) { into(abi) }
    }
    into(layout.buildDirectory.dir("generated/cxx-runtime"))
    doFirst {
        check(ndkRoot.get().asFile.isDirectory) { "Install Android NDK 28.2.13676358" }
        triples.values.forEach { triple ->
            check(ndkRoot.get().file("toolchains/llvm/prebuilt/$host/sysroot/usr/lib/$triple/libc++_shared.so").asFile.isFile) { "Incomplete NDK C++ runtime: $triple" }
        }
    }
}
tasks.named("preBuild") { dependsOn(packageCxxRuntime) }

val packageRuntimeNotices by tasks.registering(Sync::class) {
    from(androidComponents.sdkComponents.sdkDirectory.map { it.file("ndk/28.2.13676358/NOTICE.toolchain") })
    into(layout.buildDirectory.dir("generated/runtime-notices"))
}
tasks.named("preBuild") { dependsOn(packageRuntimeNotices) }
