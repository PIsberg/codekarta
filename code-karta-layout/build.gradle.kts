dependencies {
    implementation(project(":code-karta-core"))
    implementation("org.eclipse.elk:org.eclipse.elk.core:0.12.0")
    implementation("org.eclipse.elk:org.eclipse.elk.alg.layered:0.12.0")
    // ELK 0.12.x no longer drags in xbase.lib transitively but still needs it at runtime
    implementation("org.eclipse.xtext:org.eclipse.xtext.xbase.lib:2.43.0")
}
