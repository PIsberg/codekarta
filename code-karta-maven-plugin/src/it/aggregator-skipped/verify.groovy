// Declaring the plugin once in a parent pom is how plugins are actually adopted. The parent has
// no sources of its own, so it must neither fail nor leave an empty output directory behind.
assert !new File(basedir, "target/code-karta").exists() :
        "an aggregator with nothing to draw still created an output directory"

String log = new File(basedir, "build.log").getText("UTF-8")
assert log.contains("skipped, packaging is pom") :
        "the aggregator was skipped without saying why"
assert log.contains("BUILD SUCCESS") : "an aggregator must not fail the build"
