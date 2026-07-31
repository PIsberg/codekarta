allprojects {
    group = "se.deversity.codekarta"
    version = "0.2.0" // keep in step with the Maven version — the docs name the built jar

    repositories {
        mavenLocal()
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
        "implementation"("org.jspecify:jspecify:1.0.0")
        "testImplementation"("org.junit.jupiter:junit-jupiter:6.1.2")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher:6.1.2")
        "compileOnly"("se.deversity.vibetags:vibetags-annotations:1.0.0-RC3")
        "testCompileOnly"("se.deversity.vibetags:vibetags-annotations:1.0.0-RC3")
        "annotationProcessor"("se.deversity.vibetags:vibetags-processor:1.0.0-RC3")
    }

    tasks.named<Test>("test") {
        useJUnitPlatform()
    }
}
