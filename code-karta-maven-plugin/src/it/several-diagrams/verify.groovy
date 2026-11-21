// Three <diagram> entries, three files, each inheriting what it did not set. This is the part
// no unit test sees: Maven's own configurator populating a List of nested beans.
File dir = new File(basedir, "target/code-karta")

["classes.svg", "graph.json", "order-flow.svg"].each { name ->
    assert new File(dir, name).exists() : "the <diagrams> list did not produce " + name
}

String json = new File(dir, "graph.json").getText("UTF-8")
assert json.contains("nodes") : "format=json on one entry did not produce the IR"
assert !json.contains("<svg") : "the json entry produced an SVG"

String classes = new File(dir, "classes.svg").getText("UTF-8")
assert classes.contains("<svg ") : "the entry that set only a name did not inherit format=svg"
assert classes.contains("Customer") : "the class diagram is missing a class that was in the source"
assert !classes.contains("OrderFixture") :
        "the goal-level <excludes> list was accepted but never applied"

// A ${project.basedir} in a nested <diagram><input> has to be interpolated by Maven before the
// mojo ever sees it. An uninterpolated value would be a path that does not exist, which the
// plugin treats as an ordinary empty module rather than an error, so assert the file instead.
String flow = new File(dir, "order-flow.svg").getText("UTF-8")
assert flow.contains("<svg ") : "the per-diagram input path did not resolve"
