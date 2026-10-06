plugins { id("com.android.library"); kotlin("android"); id("org.jetbrains.kotlin.plugin.compose") }
android {
    namespace = "app.veyra.designsystem"; compileSdk = 35
    defaultConfig { minSdk = 26 }; buildFeatures { compose = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
kotlin { jvmToolchain(17) }
dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.compose.material3:material3")
}
