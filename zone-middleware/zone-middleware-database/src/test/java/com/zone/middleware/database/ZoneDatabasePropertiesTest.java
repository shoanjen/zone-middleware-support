package com.zone.middleware.database;

import com.zone.middleware.database.config.ZoneDatabaseProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZoneDatabasePropertiesTest {

    @Test
    void defaultsShouldBeLocalAndHikari() {
        ZoneDatabaseProperties properties = new ZoneDatabaseProperties();
        assertEquals(ZoneDatabaseProperties.DeployMode.LOCAL, properties.parsedMode());
        assertEquals(ZoneDatabaseProperties.PoolType.HIKARI, properties.parsedPoolType());
    }

    @Test
    void localModeShouldEnableLeakDetection() {
        ZoneDatabaseProperties properties = new ZoneDatabaseProperties();
        assertEquals(10_000L, properties.resolvedHikariLeakDetection());
        assertEquals(5_000L, properties.resolvedHikariConnectionTimeout());
        assertEquals(0L, properties.resolvedHikariKeepalive());
        assertEquals(5_000L, properties.resolvedDruidMaxWait());
        assertEquals(false, properties.resolvedDruidKeepAlive());
    }

    @Test
    void cloudModeShouldApplyConservativePresets() {
        ZoneDatabaseProperties properties = new ZoneDatabaseProperties();
        properties.setMode("CLOUD");
        assertEquals(15_000L, properties.resolvedHikariConnectionTimeout());
        assertEquals(0L, properties.resolvedHikariLeakDetection());
        assertEquals(120_000L, properties.resolvedHikariKeepalive());
        assertEquals(15_000L, properties.resolvedDruidMaxWait());
        assertEquals(true, properties.resolvedDruidKeepAlive());
    }

    @Test
    void explicitValuesShouldOverridePresets() {
        ZoneDatabaseProperties properties = new ZoneDatabaseProperties();
        properties.setMode("CLOUD");
        properties.getHikari().setConnectionTimeout(8_000L);
        properties.getHikari().setLeakDetectionThreshold(30_000L);
        assertEquals(8_000L, properties.resolvedHikariConnectionTimeout());
        assertEquals(30_000L, properties.resolvedHikariLeakDetection());
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

    @Test
    void invalidPoolTypeShouldThrowWithClearMessage() {
        ZoneDatabaseProperties properties = new ZoneDatabaseProperties();
        properties.setPoolType("DBCP");
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, properties::parsedPoolType);
        assertTrue(exception.getMessage().contains("DBCP"));
        assertTrue(exception.getMessage().contains("HIKARI"));
        assertTrue(exception.getMessage().contains("DRUID"));
    }
}
