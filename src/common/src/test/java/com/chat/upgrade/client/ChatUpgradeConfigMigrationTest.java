package com.chat.upgrade.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class ChatUpgradeConfigMigrationTest {

    @Test
    void missingSecurityVersionMigratesAllRemoteMediaToAutomaticReveal() {
        ChatUpgradeConfig.DecodedConfig decoded = ChatUpgradeConfig.decode("""
                {
                  "chatInputPlaceholder": "securityDefaultsVersion manualImageReveal",
                  "manualImageReveal": false,
                  "manualAudioReveal": false,
                  "manualVideoReveal": false
                }
                """);

        assertTrue(decoded.corrected());
        assertFalse(decoded.config().manualImageReveal);
        assertFalse(decoded.config().manualAudioReveal);
        assertFalse(decoded.config().manualVideoReveal);
        assertTrue(decoded.config().securityDefaultsVersion == 1);
        assertTrue(decoded.config().remoteMediaNetworkMode
                == ChatUpgradeConfig.RemoteMediaNetworkMode.TRANSPARENT_PROXY);
    }

    @Test
    void currentExplicitRevealChoicesRemainUnchanged() {
        ChatUpgradeConfig.DecodedConfig decoded = ChatUpgradeConfig.decode("""
                {
                  "securityDefaultsVersion": 1,
                  "manualImageReveal": false,
                  "manualAudioReveal": true,
                  "manualVideoReveal": false
                }
                """);

        assertFalse(decoded.config().manualImageReveal);
        assertTrue(decoded.config().manualAudioReveal);
        assertFalse(decoded.config().manualVideoReveal);
    }

    @Test
    void invalidOrTruncatedJsonFailsClosed() {
        assertThrows(RuntimeException.class, () -> ChatUpgradeConfig.decode("{\"manualImageReveal\":"));
        assertThrows(RuntimeException.class, () -> ChatUpgradeConfig.decode("[]"));
    }

    @Test
    void migratedConfigIsIdempotentAfterPersistence() {
        ChatUpgradeConfig.DecodedConfig migrated = ChatUpgradeConfig.decode("{}");
        ChatUpgradeConfig.DecodedConfig persisted = ChatUpgradeConfig.decode(
                ChatUpgradeConfig.encode(migrated.config()));

        assertTrue(migrated.corrected());
        assertFalse(persisted.corrected());
        assertFalse(persisted.config().manualImageReveal);
        assertFalse(persisted.config().manualAudioReveal);
        assertFalse(persisted.config().manualVideoReveal);
        assertTrue(persisted.config().remoteMediaNetworkMode
                == ChatUpgradeConfig.RemoteMediaNetworkMode.TRANSPARENT_PROXY);
    }
}