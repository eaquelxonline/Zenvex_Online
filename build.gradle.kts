// Zenvex Online — kök derleme. Doğrulanmış araç zinciri (Engineering.md › Araç zinciri):
// Gradle 9.9.1 · AGP 9.5.1 · Kotlin 2.4.21 · Rust 1.100.0 · JDK 25 · NDK 30.0.16248370
plugins {
    base
    id("com.android.application") version "9.5.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
    id("app.cash.paparazzi") version "2.0.0-alpha05.1" apply false
}

val nativeDir: Directory = layout.projectDirectory.dir("Native")
val serverDir: Directory = layout.projectDirectory.dir("Server")

// Cargo yerel kodun tek doğruluk kaynağıdır; Gradle yalnız yönetir.
val nativeLint = tasks.register<Exec>("nativeLint") {
    group = "verification"
    description = "Rust çekirdeği ve sunucu: clippy, uyarı = hata."
    workingDir(nativeDir)
    commandLine("cargo", "clippy", "--all-targets", "--locked", "--", "-D", "warnings")
}

val nativeTest = tasks.register<Exec>("nativeTest") {
    group = "verification"
    description = "Rust çekirdeği testleri (oyun kuralları + 3B modeller)."
    mustRunAfter(nativeLint)
    workingDir(nativeDir)
    commandLine("cargo", "test", "--release", "--locked")
}

// Paparazzi oyun ekranı testleri çekirdeği bilgisayar için derlenmiş hâliyle yükler: Native/target/release/libzenvex.so
val nativeBuildHost = tasks.register<Exec>("nativeBuildHost") {
    group = "build"
    description = "Rust çekirdeğini derleme makinesi için derler (JVM testleri yükler)."
    mustRunAfter(nativeLint, nativeTest)
    workingDir(nativeDir)
    commandLine("cargo", "build", "--release", "--locked")
}

val serverLint = tasks.register<Exec>("serverLint") {
    group = "verification"
    description = "Oyun sunucusu: clippy, uyarı = hata."
    workingDir(serverDir)
    commandLine("cargo", "clippy", "--all-targets", "--locked", "--", "-D", "warnings")
}

val serverTest = tasks.register<Exec>("serverTest") {
    group = "verification"
    description = "Oyun sunucusu testleri."
    mustRunAfter(serverLint)
    workingDir(serverDir)
    commandLine("cargo", "test", "--release", "--locked")
}

tasks.named("check") {
    dependsOn(nativeLint, nativeTest, serverLint, serverTest)
}
