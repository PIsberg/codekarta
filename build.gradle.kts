allprojects {
    group = "com.karta"
    version = "1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java-library")

    configure<JavaPluginExtension> {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }

    dependencies {
        "testImplementation"("org.junit.jupiter:junit-jupiter:5.11.0")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    tasks.named<Test>("test") {
        useJUnitPlatform()
    }
}
