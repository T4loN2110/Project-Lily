plugins {
    kotlin("jvm") version "2.3.10"
    id("org.jetbrains.compose") version "1.7.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0"
}

group = "com.t4lon.lily"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    google()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

dependencies {
    testImplementation(kotlin("test"))
    
    implementation(compose.desktop.currentOs)
    implementation(compose.materialIconsExtended)
    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.material3)
    implementation(compose.ui)
    implementation(compose.components.resources)

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.7.3")

    val voyagerVersion = "1.1.0-beta02"
    implementation("cafe.adriel.voyager:voyager-navigator:${voyagerVersion}")
    implementation("cafe.adriel.voyager:voyager-screenmodel:${voyagerVersion}")
    implementation("cafe.adriel.voyager:voyager-bottom-sheet-navigator:${voyagerVersion}")
    implementation("cafe.adriel.voyager:voyager-tab-navigator:${voyagerVersion}")
    implementation("cafe.adriel.voyager:voyager-transitions:${voyagerVersion}")
    implementation("cafe.adriel.voyager:voyager-koin:${voyagerVersion}")

    val koinVersion = "4.0.0"
    implementation("io.insert-koin:koin-core:$koinVersion")
    implementation("io.insert-koin:koin-compose:$koinVersion")

    val coroutinesVersion = "1.10.1"
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:$coroutinesVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    implementation("io.qdrant:client:1.17.0")
    implementation("com.google.guava:guava:33.4.0-jre")

    implementation("org.nirmato.ollama:nirmato-ollama-client-ktor:0.2.0")
    implementation("io.ktor:ktor-client-cio:3.1.3")

    implementation("io.github.givimad:whisper-jni:1.7.1")
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}