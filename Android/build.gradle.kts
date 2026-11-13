// Zenvex Online — Android modülü: API 26–37 (Android 8–17), arm64-v8a / armeabi-v7a / x86_64, Rust .so cargo-ndk ile.
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    // arayüz ekran görüntüsü testleri (emülatörsüz, JVM'de layoutlib ile): ./gradlew :Android:recordPaparazziDebug
    id("app.cash.paparazzi")
}

val nativeDir: Directory = rootProject.layout.projectDirectory.dir("Native")
val jniOut = layout.buildDirectory.dir("generated/jniLibs")

android {
    namespace = "com.zenvex.online"
    // Android 8 (API 26) ve sonrası; Android 17 (API 37) SDK'sıyla derlenir ve Android 17 davranışları hedeflenir
    // (oyun kategorisi: büyük ekranda yatay kilit korunur; arka planda ses çalınmaz; CT ve ECH varsayılan açık)
    compileSdk = 37
    ndkVersion = "30.0.16248370"

    defaultConfig {
        applicationId = "com.zenvex.online"
        minSdk = 26
        targetSdk = 37
        versionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()
        versionName = System.getenv("VERSION_NAME") ?: "0.4.0-beta"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
        // Supabase: ortam değişkeni / supabase.properties ile değiştirilebilir. Yayınlanabilir anahtar herkese açıktır;
        // veritabanı yalnızca zx_* fonksiyonlarına izin verir (Supabase/Migrations).
        val sb = Properties()
        rootProject.file("supabase.properties").takeIf { it.exists() }?.inputStream()?.use { sb.load(it) }
        fun sbv(k: String, d: String) = (System.getenv(k)?.takeIf { it.isNotBlank() } ?: sb.getProperty(k) ?: d).replace("\"", "")
        buildConfigField("String", "SB_URL", "\"${sbv("SB_URL", "https://blvstsvmvmdydyrnecpo.supabase.co")}\"")
        // Google ile giriş: OAuth "Web application" istemci kimliği (ortam değişkeni / supabase.properties / GitHub secret)
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${sbv("GOOGLE_WEB_CLIENT_ID", "145695347343-afg8kme2oe2ir1hlie4f36pi1f7ilf3m.apps.googleusercontent.com")}\"")
        buildConfigField("String", "SB_KEY", "\"${sbv("SB_KEY", "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJsdnN0c3Ztdm1keWR5cm5lY3BvIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTIzMzY2ODcsImV4cCI6MjEwNzkxMjY4N30.DVhL21Y_iaEdhwf_bBsJ2Fv8O1ej-1f8RxW-8wFw40k")}\"")
    }

    signingConfigs {
        // Sabit anahtar: telefona her yeni sürüm, eskisini silmeden üstüne kurulabilsin.
        // (Mağaza sürümü için ayrı, gizli bir anahtar kullanılacak.)
        create("shared") {
            storeFile = file("zenvex-dev.jks")
            storePassword = "zenvexdev"
            keyAlias = "zenvex"
            keyPassword = "zenvexdev"
        }
        // Mağaza anahtarı: ZENVEX_STORE_FILE / _PASSWORD / _ALIAS / _KEY_PASSWORD ortam değişkenleri verilmişse yayın bununla imzalanır
        // (anahtar depoda tutulmaz; GitHub Secrets → Engineering.md › Yayın). Verilmezse ortak geliştirme anahtarı kullanılır.
        System.getenv("ZENVEX_STORE_FILE")?.takeIf { it.isNotBlank() }?.let { f ->
            create("store") {
                storeFile = file(f)
                storePassword = System.getenv("ZENVEX_STORE_PASSWORD")
                keyAlias = System.getenv("ZENVEX_KEY_ALIAS")
                keyPassword = System.getenv("ZENVEX_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("shared")
        }
        getByName("release") {
            // R8: kod küçültme + karartma (tersine mühendisliği zorlaştırır, APK küçülür)
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("store") ?: signingConfigs.getByName("shared")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        // 16 KB sayfa hizası: yerel kütüphaneler sıkıştırılmadan, hizalı paketlenir (Android 15-17)
        jniLibs { useLegacyPackaging = false }
    }
    androidResources {
        noCompress += listOf("png", "wav", "webp", "ogg")
    }
    sourceSets {
        named("main") {
            manifest.srcFile("Source/Main/AndroidManifest.xml")
            kotlin.directories.clear()
            kotlin.directories += "Source/Main/Kotlin"
            java.directories.clear()
            res.directories.clear()
            res.directories += "Source/Main/Resources"
            assets.directories.clear()
            assets.directories += "Source/Main/Assets"
            // yasal metinlerin tek kaynağı (zx-legal web sayfaları da aynı dosyayı okur): assets/legal.json olarak paketlenir
            assets.directories += rootProject.file("Supabase/Functions/zx-legal/content").path
            resources.directories.clear()
            jniLibs.directories.clear()
            jniLibs.directories += jniOut.get().asFile.absolutePath
        }
        named("test") {
            kotlin.directories.clear()
            kotlin.directories += "Source/Test/Kotlin"
            java.directories.clear()
            resources.directories.clear()
            resources.directories += "Source/Test/Resources"
        }
    }
}

// yayın derlemesinde künye/aydınlatma bilgileri doldurulmamışsa uyar (mağazaya bu hâliyle çıkılmamalı)
val legalJson = rootProject.file("Supabase/Functions/zx-legal/content/legal.json")
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    doFirst {
        if (legalJson.readText().contains("[DOLDURULACAK")) logger.warn("UYARI: legal.json içindeki [DOLDURULACAK: …] künye bilgileri doldurulmadı; mağaza yayınından önce doldurun.")
    }
}

// ---- Rust çekirdeği (libzenvex.so) cargo-ndk ile; Android 15+ 16 KB bellek sayfası için hizalı bağlanır.
val cargoBuild = tasks.register<Exec>("cargoBuild") {
    group = "build"
    description = "Rust çekirdeğini (Native/Core.rs) Android için derler"
    mustRunAfter(":nativeLint", ":nativeTest", ":nativeBuildHost")
    workingDir(nativeDir)
    val targets = (System.getenv("RUST_ANDROID_TARGETS") ?: "arm64-v8a,armeabi-v7a,x86_64").split(",")
    val args = mutableListOf("cargo", "ndk")
    targets.forEach { args += listOf("-t", it.trim()) }
    args += listOf("-o", jniOut.get().asFile.absolutePath, "build", "--release", "--locked")
    commandLine(args)
    val pageAlign = "-C link-arg=-Wl,-z,max-page-size=16384"
    listOf("AARCH64_LINUX_ANDROID", "ARMV7_LINUX_ANDROIDEABI", "X86_64_LINUX_ANDROID").forEach {
        environment("CARGO_TARGET_${it}_RUSTFLAGS", pageAlign)
    }
    inputs.files(nativeDir.file("Core.rs"), nativeDir.file("Cargo.toml"), nativeDir.file("Cargo.lock"))
    outputs.dir(jniOut)
}
tasks.named("preBuild") { dependsOn(cargoBuild) }

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        // sıfır uyarı: CI'da her Kotlin uyarısı derlemeyi durdurur (yerelde yalnız uyarı)
        allWarningsAsErrors.set(System.getenv("CI") == "true")
    }
}

dependencies {
    implementation("com.android.billingclient:billing:9.1.0")
    implementation("androidx.credentials:credentials:1.6.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.6.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.2.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20260814")
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.work:work-runtime-ktx:2.12.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("com.squareup.okhttp3:okhttp:5.5.1")
}

// oyun ekranı görüntü testleri (GameShotsTest) Rust çekirdeğini bilgisayar için derlenmiş hâliyle yükler:
// ./gradlew nativeBuildHost → Native/target/release/libzenvex.so
tasks.withType<Test>().configureEach {
    systemProperty("java.library.path", nativeDir.dir("target/release").asFile.absolutePath)
}
