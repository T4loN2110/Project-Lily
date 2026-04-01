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
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    implementation("io.qdrant:client:1.17.0")
    implementation("com.google.guava:guava:33.4.0-jre")

    implementation("org.nirmato.ollama:nirmato-ollama-client-ktor:0.2.0")
    implementation("io.ktor:ktor-client-cio:3.1.3")
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}