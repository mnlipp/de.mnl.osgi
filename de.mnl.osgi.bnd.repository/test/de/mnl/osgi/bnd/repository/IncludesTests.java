package de.mnl.osgi.bnd.repository;

import de.mnl.osgi.bnd.maven.MavenVersion;
import de.mnl.osgi.bnd.maven.MavenVersionRange;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

public class IncludesTests {

    @Test
    public void testMatchSingle() {
        MavenVersionRange range = new MavenVersionRange("[0,2.1),[3,)");
        assertTrue(range.includes(new MavenVersion("2.0")));
        assertFalse(range.includes(new MavenVersion("2.5")));
        assertTrue(range.includes(new MavenVersion("3.1")));
    }

    @Test
    public void testMatchRange1() {
        MavenVersionRange range = new MavenVersionRange("[0,2.1),[3,)");
        assertTrue(range.includes(new MavenVersionRange("[2.2,2.9]")));
        assertFalse(range.includes(new MavenVersionRange("[2.0,2.9]")));
    }

    @Test
    public void testMatchRange2() {
        MavenVersionRange range
            = new MavenVersionRange("[0,1.7.25),[1.7.1000,)");
        assertTrue(range.includes(new MavenVersion("1.8.0.beta4")));
    }

}
