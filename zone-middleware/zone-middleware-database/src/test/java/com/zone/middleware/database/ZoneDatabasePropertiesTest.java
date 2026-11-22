package com.zone.middleware.database;

import com.zone.middleware.database.config.ZoneDatabaseProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZoneDatabasePropertiesTest {

    @Test
    void defaultsShouldBeLocal() {
        ZoneDatabaseProperties properties = new ZoneDatabaseProperties();
        assertEquals(ZoneDatabaseProperties.DeployMode.LOCAL, properties.parsedMode());
    }

    @Test
    void invalidModeShouldThrowWithClearMessage() {
        ZoneDatabaseProperties properties = new ZoneDatabaseProperties();
        properties.setMode("MARS");
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, properties::parsedMode);
        assertTrue(exception.getMessage().contains("MARS"));
        assertTrue(exception.getMessage().contains("LOCAL"));
    }
}
