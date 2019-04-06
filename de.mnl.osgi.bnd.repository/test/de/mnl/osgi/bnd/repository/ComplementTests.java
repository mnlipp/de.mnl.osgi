package de.mnl.osgi.bnd.repository;

import de.mnl.osgi.bnd.maven.MavenVersionRange;

import static org.junit.Assert.*;

import org.junit.Test;

public class ComplementTests {

    @Test
    public void testMidRangeInclusive() {
        assertEquals("[0,1),(2,)",
            new MavenVersionRange("[1,2]").complement().toString());
    }

    @Test
    public void testMidRangeExclusive() {
        assertEquals("[0,1],[2,)",
            new MavenVersionRange("(1,2)").complement().toString());
    }

    @Test
    public void testUpperOpenRange() {
        assertEquals("[0,1)",
            new MavenVersionRange("[1,)").complement().toString());
    }

    @Test
    public void testAll() {
        assertEquals("[,0)",
            new MavenVersionRange("[0,)").complement().toString());
    }

    @Test
    public void testNothing() {
        assertEquals("[0,)",
            new MavenVersionRange("[,0)").complement().toString());
    }

    @Test
    public void testCombined1() {
        assertEquals("[0,1),[1.3,1.5),[1.7,2.5),[3.1,)",
            new MavenVersionRange("[1,1.3),[1.5,1.7),[2.5,3.1)").complement()
                .toString());
    }

    @Test
    public void testCombined2() {
        assertEquals("[2.1,3)",
            new MavenVersionRange("[0,2.1),[3,)").complement().toString());
    }

}
