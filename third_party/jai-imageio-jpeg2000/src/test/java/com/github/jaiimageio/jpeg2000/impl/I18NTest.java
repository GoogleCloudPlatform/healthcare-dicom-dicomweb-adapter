package com.github.jaiimageio.jpeg2000.impl;

import static org.junit.Assert.assertNotNull;

import org.junit.Test;

public class I18NTest {

    /**
     * Test that the error messages can be loaded.
     */
    @Test
    public void testErrorMessages() {
        assertNotNull(I18N.getString("J2KReadState10"));
    }

}

