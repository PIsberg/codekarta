// The whole claim of the zero-configuration section in docs/MAVEN-PLUGIN.md: declare the
// plugin, bind the goal, and get a diagram at a documented path without configuring anything.
File svg = new File(basedir, "target/code-karta/class-diagram.svg")
assert svg.exists() : "no diagram at the documented default path, target/code-karta/class-diagram.svg"

String content = svg.getText("UTF-8")
assert content.contains("<svg ") : "the file exists but is not an SVG"
assert content.contains("Dog") : "the diagram does not mention a class that was in the source"

// The goal binds to package, so it must not have run before the module compiled.
String text = new File(basedir, "build.log").getText("UTF-8")
assert text.indexOf("maven-compiler-plugin") < text.indexOf("code-karta:") :
        "the diagram was generated before compilation; the package binding is not holding"
