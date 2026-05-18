plugins {
    application
}

application {
    mainClass = "com.karta.cli.KartaCli"
}

dependencies {
    implementation(project(":code-karta-core"))
    implementation(project(":code-karta-input"))
    implementation(project(":code-karta-layout"))
    implementation(project(":code-karta-render"))
}

// Fat JAR: ./gradlew :code-karta-cli:fatJar → build/libs/code-karta-cli-1.0-SNAPSHOT-all.jar
tasks.register<Jar>("fatJar") {
    group = "build"
    description = "Assembles a self-contained executable JAR with all dependencies."
    archiveClassifier = "all"
    manifest {
        attributes["Main-Class"] = "com.karta.cli.KartaCli"
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    with(tasks.jar.get())
}
