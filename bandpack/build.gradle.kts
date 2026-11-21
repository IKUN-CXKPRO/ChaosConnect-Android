plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.chaos.bandpack"
    compileSdk = 37
    defaultConfig { minSdk = 33; buildConfigField("String", "VERSION_NAME", "\"1.4.0\"") }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_21; targetCompatibility = JavaVersion.VERSION_21 }
}
val moduleRepo = file(providers.gradleProperty("chaos.repo").getOrElse("../../Chaos-Module"))
val syncStudioAssets by tasks.registering {
    val output = layout.projectDirectory.dir("src/main/assets")
    val inputs10 = mapOf(
        "chaos_sup.ko" to File(moduleRepo,"dist/10p-3.101.043/chaos_sup.ko"),
        "chaos_icon.bin" to File(moduleRepo,"dist/10p-3.101.043/assets/chaos_icon.bin"),
        "font_pack.lua" to File(moduleRepo,"installer/font_pack.lua"),
        "icon_pack.lua" to file("src/main/pack/icon_pack.lua"),
    )
    inputs.files(inputs10.values)
    inputs.files(file("THIRD_PARTY_NOTICES.md"),file("PRIVACY_POLICY.md"),fileTree("third_party"))
    outputs.dir(output)
    doLast {
        val dest=output.asFile;dest.mkdirs()
        inputs10.forEach { (name,source) ->
            check(source.isFile) { "制作器素材缺失：$source；请先编译 Chaos-Module" }
            File(dest,"pack/$name").apply{parentFile.mkdirs();source.copyTo(this,true)}
        }
        File(dest,"legal").mkdirs()
        file("THIRD_PARTY_NOTICES.md").copyTo(File(dest,"legal/THIRD_PARTY_NOTICES.md"),true)
        file("PRIVACY_POLICY.md").copyTo(File(dest,"legal/privacy_policy.md"),true)
        file("LICENSE").copyTo(File(dest,"legal/LICENSE"),true)
        file("third_party").copyRecursively(File(dest,"legal/third_party"),true)
        listOf(File(moduleRepo,"assets/firmware_icons/stock"),File(moduleRepo,"../assets/firmware_icons/stock"))
            .firstOrNull{it.isDirectory}?.copyRecursively(File(dest,"stock_icons"),true)
    }
}
tasks.named("preBuild"){dependsOn(syncStudioAssets)}
dependencies {
    testImplementation("junit:junit:4.13.2")
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-util")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3:1.5.0-rc01")
    implementation("androidx.compose.material3:material3-window-size-class:1.5.0-rc01")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.documentfile:documentfile:1.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
}
