package se.deversity.codekarta.maven;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Asserts the generated plugin descriptor against what users are told to write.
 *
 * <p>{@link GenerateMojoTest} calls {@code execute()} directly, so it never sees the descriptor
 * that Maven actually binds a {@code <configuration>} block to. Rename a field and every one of
 * those tests still passes while every pom in the wild silently stops configuring the thing it
 * names. This reads the descriptor the build just generated and checks the two contracts that
 * a unit test cannot: the goal's identity, and that the parameter table in
 * {@code docs/MAVEN-PLUGIN.md} names every parameter that exists.
 */
class PluginDescriptorTest {

    private static final Path DESCRIPTOR =
            Path.of("target", "classes", "META-INF", "maven", "plugin.xml");
    private static final Path DOC = Path.of("..", "docs", "MAVEN-PLUGIN.md");

    private static String descriptor() throws Exception {
        assertTrue(Files.exists(DESCRIPTOR),
                "the descriptor is generated at process-classes; run through Maven, not the IDE alone");
        return Files.readString(DESCRIPTOR);
    }

    private static String tagValue(String xml, String tag) {
        Matcher m = Pattern.compile("<" + tag + ">(.*?)</" + tag + ">", Pattern.DOTALL).matcher(xml);
        assertTrue(m.find(), "descriptor has no <" + tag + ">");
        return m.group(1).trim();
    }

    @Test
    void theGoalKeepsItsPublishedIdentity() throws Exception {
        String xml = descriptor();

        assertEquals("karta", tagValue(xml, "goalPrefix"),
                "the goal prefix is what users type; changing it breaks every documented command");
        assertEquals("generate", tagValue(xml, "goal"));
        assertEquals("package", tagValue(xml, "phase"),
                "binding earlier would put diagram generation in the inner compile loop");
    }

    @Test
    void theOldestSupportedMavenIsDeclaredRatherThanImplied() throws Exception {
        assertEquals("3.9.11", tagValue(descriptor(), "requiredMavenVersion"),
                "an older Maven must be refused by name, not by a missing-API stack trace");
    }

    @Test
    void everyParameterIsDocumented() throws Exception {
        String xml = descriptor();
        String doc = Files.readString(DOC);

        // The mojo's own <parameters> block. Names inside it are what a <configuration> element
        // must be called, so each one is part of the plugin's user-facing surface.
        Matcher params = Pattern.compile("<parameters>(.*?)</parameters>", Pattern.DOTALL)
                .matcher(xml);
        assertTrue(params.find(), "descriptor declares no parameters");

        List<String> undocumented = Pattern.compile("<name>(.*?)</name>")
                .matcher(params.group(1))
                .results()
                .map(r -> r.group(1))
                .distinct()
                // "project" is injected read-only from ${project}; it is not something a pom sets.
                .filter(name -> !name.equals("project"))
                .filter(name -> !doc.contains("`" + name + "`"))
                .toList();

        assertTrue(undocumented.isEmpty(),
                "docs/MAVEN-PLUGIN.md does not name these parameters: " + undocumented);
    }

    @Test
    void theDocumentedVersionIsTheVersionBeingBuilt() throws Exception {
        // docs/MAVEN-PLUGIN.md hard-codes the version in a copyable <plugin> block and in two
        // fully qualified command lines. Nobody notices a stale one until a user copies it and
        // gets a version that predates the thing they came for, so the release checklist is not
        // the right place for this: pin it here, where a version bump cannot skip the check.
        String version = tagValue(descriptor(), "version");
        String doc = Files.readString(DOC);

        List<String> stale = Pattern.compile("code-karta-maven-plugin[:<>/a-z]*?([0-9]+[.][0-9]+[.][0-9]+)")
                .matcher(doc)
                .results()
                .map(r -> r.group(1))
                .distinct()
                .filter(found -> !found.equals(version))
                .toList();

        assertTrue(stale.isEmpty(),
                "docs/MAVEN-PLUGIN.md still names version " + stale + ", this build is " + version);
    }

    @Test
    void everyDocumentedUserPropertyExists() throws Exception {
        String xml = descriptor();
        String doc = Files.readString(DOC);

        List<String> missing = Pattern.compile("`(karta[.][A-Za-z]+)`")
                .matcher(doc)
                .results()
                .map(r -> r.group(1))
                .distinct()
                .filter(property -> !xml.contains("${" + property + "}"))
                .toList();

        assertTrue(missing.isEmpty(),
                "the docs promise these user properties, the descriptor has no such expression: "
                        + missing);
    }
}
