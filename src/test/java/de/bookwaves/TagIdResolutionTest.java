package de.bookwaves;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TagIdResolutionTest {

    @Test
    @DisplayName("the protocol-neutral tagId is used when it is the only one given")
    void tagIdAlone() {
        assertEquals("E200AB", Main.resolveTagId("E200AB", null));
    }

    @Test
    @DisplayName("a caller sending only the legacy epc keeps working")
    void legacyEpcAlone() {
        assertEquals("E200AB", Main.resolveTagId(null, "E200AB"));
    }

    @Test
    @DisplayName("tagId wins when both are given and differ")
    void tagIdTakesPrecedence() {
        assertEquals("E200AB", Main.resolveTagId("E200AB", "E200CD"));
    }

    @Test
    @DisplayName("an empty tagId falls back to epc rather than shadowing it")
    void emptyTagIdFallsBack() {
        assertEquals("E200CD", Main.resolveTagId("", "E200CD"));
    }

    @Test
    @DisplayName("neither parameter means no tag")
    void neither() {
        assertNull(Main.resolveTagId(null, null));
    }
}
