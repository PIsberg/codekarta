// A module whose diagram is a deliverable turns "nothing was drawn" into a build failure.
// The message has to name the ambiguity, because parsers never throw: a source tree that
// failed to parse looks exactly like one with nothing in it.
String log = new File(basedir, "build.log").getText("UTF-8")

assert log.contains("produced no diagram") : "the failure did not say what happened"
assert log.contains("parsing failed") : "the failure did not name the ambiguity"
assert log.contains("BUILD FAILURE") : "failOnEmpty did not fail the build"
