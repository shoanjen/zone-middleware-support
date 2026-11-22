package com.zone.middleware.redis;

import com.zone.middleware.redis.config.ZoneRedisProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZoneRedisPropertiesTest {

    @Test
    void defaultModeShouldBeLocal() {
        ZoneRedisProperties properties = new ZoneRedisProperties();
        assertEquals(ZoneRedisProperties.DeployMode.LOCAL, properties.parsedMode());
    }

    @Test
    void invalidModeShouldThrowWithClearMessage() {
        ZoneRedisProperties properties = new ZoneRedisProperties();
        properties.setMode("CLUSTER_X");
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, properties::parsedMode);
        assertTrue(exception.getMessage().contains("CLUSTER_X"));
        assertTrue(exception.getMessage().contains("LOCAL"));
        assertTrue(exception.getMessage().contains("CLOUD"));
    }

    @Test
    void modeShouldBeCaseInsensitive() {
        ZoneRedisProperties properties = new ZoneRedisProperties();
        properties.setMode(" cloud ");
        assertEquals(ZoneRedisProperties.DeployMode.CLOUD, properties.parsedMode());
    }
}
