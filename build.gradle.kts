allprojects {
    group = "se.deversity.codekarta"
    version = "1.0-SNAPSHOT"

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
