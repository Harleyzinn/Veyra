plugins { id("com.android.library"); kotlin("android"); id("com.google.devtools.ksp") }
android {
    namespace = "app.veyra.data"
    compileSdk = 35
    defaultConfig { minSdk = 26; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
kotlin { jvmToolchain(17) }
dependencies {
    implementation(project(":core:model"))
    implementation("androidx.room:room-runtime:2.7.1")
    ksp("androidx.room:room-compiler:2.7.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
