// Every version here mirrors a <property> in pom.xml. scripts/check-build-parity.py fails the
// build when the two disagree, because CI runs the Maven and Gradle suites as separate jobs and
// a split makes them test different dependency sets without saying so.
val vibetagsVersion = "1.2.5"
val jspecifyVersion = "1.0.1"
val junitVersion = "6.1.3"

allprojects {
    group = "se.deversity.codekarta"
    version = "0.3.0" // keep in step with the Maven version — the docs name the built jar

    repositories {
        // mavenLocal() is opt-in (-PuseMavenLocal) rather than always on. With it first in the
        // list, an artifact that exists only because it was built on this machine satisfies the
        // build, so an unpublished dependency looks green locally and fails in CI. That is
        // exactly how a bump to an unpublished VibeTags 1.0.4 passed here and would not have
        // resolved on Central. Turn it on when developing the processor against this repository.
        if (providers.gradleProperty("useMavenLocal").isPresent) {
            mavenLocal()
        }
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
        "implementation"("org.jspecify:jspecify:$jspecifyVersion")
        "testImplementation"("org.junit.jupiter:junit-jupiter:$junitVersion")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher:$junitVersion")
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
