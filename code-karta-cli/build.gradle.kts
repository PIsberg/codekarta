plugins {
    application
    id("com.gradleup.shadow") version "9.6.1"
}

application {
    mainClass = "se.deversity.codekarta.cli.KartaCli"
}

dependencies {
    implementation(project(":code-karta-core"))
    implementation(project(":code-karta-input"))
    implementation(project(":code-karta-layout"))
    implementation(project(":code-karta-render"))
    testImplementation("com.tngtech.archunit:archunit-junit5:1.5.1")
}

// version.properties carries the project version for --version (#278), as Maven's resource
// filtering fills it in for the Maven build.
tasks.named<ProcessResources>("processResources") {
    val projectVersion = project.version.toString()
    inputs.property("projectVersion", projectVersion)
    filesMatching("**/version.properties") {
        expand("project" to mapOf("version" to projectVersion))
    }
}

// Fat JAR via shadow plugin — merges META-INF/services so ELK algorithm SPI is found.
// ./gradlew :code-karta-cli:fatJar → build/libs/code-karta-cli-1.0-SNAPSHOT-all.jar
tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveClassifier.set("all")
    mergeServiceFiles()
    manifest {
        attributes["Main-Class"] = "se.deversity.codekarta.cli.KartaCli"
    }
}

// Keep the fatJar alias so existing scripts and CI stay unchanged.
tasks.register("fatJar") {
    group = "build"
    description = "Alias for shadowJar — assembles a self-contained executable JAR."
    dependsOn(tasks.named("shadowJar"))
}

// generateDiagrams — runs the fat JAR over code-karta-cli/diagrams.txt to regenerate every
// committed diagram, as Maven's gen-diagrams execution does.
// ./gradlew :code-karta-cli:generateDiagrams
// Skip with: -PskipDiagrams
tasks.register("generateDiagrams") {
    group = "documentation"
    description = "Regenerates architecture diagrams in docs/diagrams/ using the fat JAR."
    dependsOn(tasks.named("shadowJar"))

    onlyIf { !project.hasProperty("skipDiagrams") }

    doLast {
        val jar = tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar")
            .get().archiveFile.get().asFile
        val root = project.rootDir
        // The single list of committed diagrams (#279), shared with the Maven build's gen-diagrams
        // execution: one JVM, every line of diagrams.txt in order.
        val args = listOf("java", "-jar", jar.absolutePath, "--batch", "code-karta-cli/diagrams.txt")
        val process = ProcessBuilder(args).directory(root).inheritIO().start()
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            throw GradleException("Diagram generation failed with exit code $exitCode: $args")
        }
    }
}
