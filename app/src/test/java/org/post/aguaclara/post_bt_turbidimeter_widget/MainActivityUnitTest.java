package org.post.aguaclara.post_bt_turbidimeter_widget;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * To work on unit tests, switch the Test Artifact in the Build Variants view.
 */
public class MainActivityUnitTest {
    @Test
    public void sanity() throws Exception {
        assertEquals("Hello world!", "Hello world!");
    }

    @Test
    public void parseWithRegex_isCorrect() throws Exception {
        assertEquals("H", MainActivity.parseWithRegex("Hello world!", "(.)"));
    }

    @Test
    public void parseNtu_isCorrect() throws Exception {
        assertEquals(8.39, MainActivity.parseNtu("Hello world! 8.39 NTU"), 0.01);
    }
}