package io.tallyard.webhooks.internal;

import static org.assertj.core.api.Assertions.assertThat;

import io.tallyard.shared.config.TallyardProperties;
import org.junit.jupiter.api.Test;

class WebhookSignerTest {

    @Test
    void signAndVerifyRoundTrip() {
        String header = WebhookSigner.sign("whsec_abc", 1_700_000_000L, "{\"a\":1}");
        assertThat(header).startsWith("t=1700000000,v1=").hasSize("t=1700000000,v1=".length() + 64);
        assertThat(WebhookSigner.verify("whsec_abc", header, "{\"a\":1}")).isTrue();
        assertThat(WebhookSigner.verify("whsec_abc", header, "{\"a\":2}")).isFalse();
        assertThat(WebhookSigner.verify("whsec_xyz", header, "{\"a\":1}")).isFalse();
        assertThat(WebhookSigner.verify("whsec_abc", null, "{\"a\":1}")).isFalse();
        assertThat(WebhookSigner.verify("whsec_abc", "garbage", "{\"a\":1}")).isFalse();
        assertThat(WebhookSigner.verify("whsec_abc", "t=x,v1=00", "{\"a\":1}")).isFalse();
    }

    @Test
    void encryptorRoundTripsAndRefusesWithoutKey() {
        TallyardProperties withKey = props("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
        SecretEncryptor enc = new SecretEncryptor(withKey);
        String ct = enc.encrypt("whsec_secret");
        assertThat(ct).isNotEqualTo("whsec_secret").doesNotContain("secret");
        assertThat(enc.decrypt(ct)).isEqualTo("whsec_secret");
        assertThat(enc.encrypt("whsec_secret")).isNotEqualTo(ct); // fresh IV every time

        SecretEncryptor none = new SecretEncryptor(props(""));
        assertThat(none.isConfigured()).isFalse();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> none.encrypt("x"))
                .hasMessageContaining("TALLYARD_ENCRYPTION_KEY");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new SecretEncryptor(props("c2hvcnQ=")))
                .hasMessageContaining("32 bytes");
    }

    private static TallyardProperties props(String key) {
        return new TallyardProperties("all", "", key, null, null, null, null, null, null, null);
    }
}
