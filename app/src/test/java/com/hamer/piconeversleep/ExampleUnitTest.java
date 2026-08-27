package com.hamer.piconeversleep;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class ExampleUnitTest {
    @Test
    public void tilePositionIsClampedAndStable() {
        assertEquals(5, TilePositionPolicy.normalizedIndex(1, 5, 7));
        assertEquals(5, TilePositionPolicy.normalizedIndex(5, 5, 7));
        assertEquals(6, TilePositionPolicy.normalizedIndex(2, 99, 7));
        assertEquals(0, TilePositionPolicy.normalizedIndex(4, -3, 7));
        assertEquals(0, TilePositionPolicy.normalizedIndex(0, 4, 0));
    }
}