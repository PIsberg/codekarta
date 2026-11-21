// A relative <input>/<output> inside <diagram> must mean "relative to the module that declares it",
// as Maven's own File parameters do. Resolved against the JVM's working directory instead, which
// here is the reactor root, the input is not found (the build fails on failOnEmpty) and any output
// lands outside the module (issue #233).
File written = new File(basedir, "child/docs/diagrams/class-diagram.svg")
assert written.exists() : "the relative <output> was not resolved against the child module"
assert written.getText("UTF-8").contains("Customer") : "the relative <input> was not resolved against the child module"
assert !new File(basedir, "docs").exists() : "output was written under the reactor root"
