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
    testImplementation("com.tngtech.archunit:archunit-junit5:1.5.0")
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

// generateDiagrams — runs the fat JAR to regenerate docs/diagrams/.
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
        val out  = File(root, "docs/diagrams")

        listOf(
            // 1. Module diagram — example shipping system
            listOf("--input", "$root/example-shipping-system/src/main/java/module-info.java",
                   "--output", "$out"),
            // 2. Class diagram — core IR model
            listOf("--input", "$root/code-karta-core/src/main/java/se/deversity/codekarta/core/model",
                   "--output", "$out"),
            // 3. Exception-flow sequence — CLI entry point
            listOf("--input", "$root/code-karta-cli/src/main/java/se/deversity/codekarta/cli/KartaCli.java",
                   "--output", "$out"),
            // 4. Call-sequence-only — demonstrates --sequence-only flag
            listOf("--input", "$root/code-karta-input/src/main/java/se/deversity/codekarta/input/parser/CallSequenceParser.java",
                   "--output", "$out", "--sequence-only"),
            // 5. Multi-file stitched sequence — input parsers with ELK layout
            listOf("--input", "$root/code-karta-input/src/main/java/se/deversity/codekarta/input",
                   "--output", "$out", "--sequence-only", "--layout", "elk")
        ).forEach { args ->
            val pb = ProcessBuilder(listOf("java", "-jar", jar.absolutePath) + args)
                .directory(root)
                .inheritIO()
            val process = pb.start()
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                throw GradleException("Diagram generation failed with exit code $exitCode for arguments: $args")
            }
        }
    }
}
