package com.zone.middleware.redis;

import com.zone.middleware.redis.config.ZoneRedisProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZoneRedisPropertiesTest {

    @Test
    void defaultModeShouldBeLocalWithLocalPresets() {
        ZoneRedisProperties properties = new ZoneRedisProperties();
        assertEquals(ZoneRedisProperties.DeployMode.LOCAL, properties.parsedMode());
        assertEquals(Duration.ofSeconds(2), properties.resolvedConnectTimeout());
        assertEquals(Duration.ofSeconds(3), properties.resolvedCommandTimeout());
        assertEquals(false, properties.resolvedKeepAlive());
    }

    @Test
    void cloudModeShouldApplyConservativePresets() {
        ZoneRedisProperties properties = new ZoneRedisProperties();
        properties.setMode("CLOUD");
        assertEquals(ZoneRedisProperties.DeployMode.CLOUD, properties.parsedMode());
        assertEquals(Duration.ofSeconds(10), properties.resolvedConnectTimeout());
        assertEquals(Duration.ofSeconds(10), properties.resolvedCommandTimeout());
        assertEquals(true, properties.resolvedKeepAlive());
    }

    @Test
    void cloudPresetsShouldBeMoreConservativeThanLocal() {
        ZoneRedisProperties local = new ZoneRedisProperties();
        ZoneRedisProperties cloud = new ZoneRedisProperties();
        cloud.setMode("CLOUD");
        assertTrue(cloud.resolvedConnectTimeout().compareTo(local.resolvedConnectTimeout()) > 0);
        assertTrue(cloud.resolvedCommandTimeout().compareTo(local.resolvedCommandTimeout()) > 0);
    }

    @Test
    void explicitValuesShouldOverrideCloudPresets() {
        ZoneRedisProperties properties = new ZoneRedisProperties();
        properties.setMode("CLOUD");
        properties.setCommandTimeout(Duration.ofMillis(2500));
        properties.setConnectTimeout(Duration.ofSeconds(1));
        properties.setKeepAlive(false);

        assertEquals(Duration.ofMillis(2500), properties.resolvedCommandTimeout());
        assertEquals(Duration.ofSeconds(1), properties.resolvedConnectTimeout());
        assertEquals(false, properties.resolvedKeepAlive());
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
