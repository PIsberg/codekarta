package se.deversity.codekarta.cli;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SECURITY.md promises fixes on the latest minor release only, and its table said 0.3.x two
 * releases after 0.4.0 shipped (issue #280). A promise about which version is supported is only
 * worth something if it names the version actually being built, so the build checks it.
 */
class SecurityPolicyTest {

    @Test
    void theSupportedVersionsTableNamesTheReleaseLineBeingBuilt() throws Exception {
        String version = KartaCli.version();
        assertNotEquals("unknown", version, "the build did not fill in version.properties");
        String[] parts = version.split("[.-]");
        String line = parts[0] + "." + parts[1];

        // Tests run from the module directory, in both builds.
        String policy = Files.readString(Path.of("..", "SECURITY.md"));

        assertTrue(policy.contains("| " + line + ".x | Yes |"),
                "SECURITY.md must list " + line + ".x as supported; update its table with the release");
        assertTrue(policy.contains("| < " + line + " | No |"),
                "SECURITY.md must list everything before " + line + " as unsupported");
    }
}
