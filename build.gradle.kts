plugins {
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
}

group = "com.particlelife"
version = "1.0.0"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

javafx {
    version = "21.0.4"
    modules = listOf("javafx.controls", "javafx.graphics")
}

dependencies {
    implementation("org.slf4j:slf4j-api:2.0.13")
    runtimeOnly("ch.qos.logback:logback-classic:1.5.6")

    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.xerial:sqlite-jdbc:3.46.0.0")

    // LWJGL: GLFW + OpenGL bindings for the GPU compute force pass.
    implementation(platform("org.lwjgl:lwjgl-bom:3.3.4"))
    implementation("org.lwjgl:lwjgl")
    implementation("org.lwjgl:lwjgl-glfw")
    implementation("org.lwjgl:lwjgl-opengl")
    implementation("org.lwjgl:lwjgl-egl")
    runtimeOnly("org.lwjgl:lwjgl::natives-linux")
    runtimeOnly("org.lwjgl:lwjgl-glfw::natives-linux")
    runtimeOnly("org.lwjgl:lwjgl-opengl::natives-linux")

    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.mockito:mockito-core:5.12.0")
}

application {
    mainClass = "com.particlelife.app.ParticleLifeApplication"
}

tasks.test {
    useJUnitPlatform {
        excludeTags("performance")
    }
    maxHeapSize = "2g"
}

// Performance benchmarks are opt-in: ./gradlew perfTest
tasks.register<Test>("perfTest") {
    description = "Runs performance benchmark tests."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform {
        includeTags("performance")
    }
    maxHeapSize = "4g"
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.javadoc {
    (options as StandardJavadocDocletOptions).apply {
        encoding = "UTF-8"
        addBooleanOption("Xdoclint:none", true)
    }
}
