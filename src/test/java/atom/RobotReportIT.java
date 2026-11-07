package atom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jenkinschina.atom.sdk.testing.Atom;
import com.jenkinschina.atom.sdk.testing.AtomResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The robot atom, started the way the runner starts it.
 *
 * <p>These are the checks the old {@code tools/smoke.sh} made — the totals in {@code output.xml}, the report page, and
 * an empty statistics block being a refusal rather than a green build — written as JUnit.
 */
class RobotReportIT {

    private static final String OUTPUT_XML = "<robot><statistics><total><stat pass=\"8\" fail=\"1\" skip=\"2\">"
            + "All Tests</stat></total></statistics></robot>";

    private Atom atom;

    @BeforeEach
    void setUp() throws Exception {
        atom = Atom.of();
    }

    @Test
    @DisplayName("the statistics block becomes totals and a report page")
    void summarisesTheRun() throws Exception {
        AtomResult run = atom.file("output.xml", OUTPUT_XML)
                .input("pattern", "**/output.xml")
                .input("skipMarkingUnstable", "true")
                .run();

        run.assertOk();
        assertEquals("11", run.field("total"), "passed + failed + skipped");
        assertEquals("1", run.field("failed"));
        assertEquals("2", run.field("skipped"));
        assertTrue(run.exists("out/robot/index.html"), "the report page is missing:\n" + run.console());
    }

    @Test
    @DisplayName("an output.xml without statistics is refused")
    void refusesEmptyStatistics() throws Exception {
        AtomResult run = atom.file("output.xml", "<robot></robot>")
                .input("pattern", "**/output.xml")
                .run();

        run.assertRefused();
        assertEquals("the output.xml has no test statistics", run.field("message"),
                "the refusal should say what was missing:\n" + run);
    }
}
