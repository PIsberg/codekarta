val vibetagsVersion = "1.0.0-RC8"

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
        // Mirrors <vibetags.version> in pom.xml — the two builds share one generated
        // CLAUDE.md, so a version split makes the guardrails depend on which build ran last.
        "compileOnly"("se.deversity.vibetags:vibetags-annotations:$vibetagsVersion")
        "testCompileOnly"("se.deversity.vibetags:vibetags-annotations:$vibetagsVersion")
        "annotationProcessor"("se.deversity.vibetags:vibetags-processor:$vibetagsVersion")
    }

    // No -Avibetags.root here, on purpose — but no longer because Gradle gets it wrong.
    // Until vibetags 1.0.0-RC8 the processor could not resolve modules under Gradle at all (it
    // gets a wrapped ProcessingEnvironment that Trees.instance rejects), so without the option it
    // wrote nothing ("0 active services") and with it identified modules by a content hash rather
    // than the Maven module name — appending a second set of regions instead of replacing Maven's.
    // Upstream #331 fixed the identity fallback; with the option set, Gradle now names all five
    // modules exactly as Maven does and emits a byte-identical CLAUDE.md.
    //
    // It stays off because a second writer buys nothing when the two agree, and the Trees API is
    // still unavailable under Gradle, so its rounds skip the AST import scan behind
    // @AIArchitecture's cannotReference lists. Regenerate with Maven. See docs/VIBETAGS.md.

    tasks.named<Test>("test") {
        useJUnitPlatform()
    }
}
