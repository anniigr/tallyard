package io.tallyard.webhooks.internal;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Stripe-style signature: {@code t=<unix seconds>,v1=<hex HMAC-SHA256(secret, t + "." + body)>}.
 * Including the timestamp lets receivers reject replays older than their tolerance.
 */
public final class WebhookSigner {

    public static final String HEADER = "X-Tallyard-Signature";

    private WebhookSigner() {}

    public static String sign(String secret, long timestampSeconds, String body) {
        return "t=" + timestampSeconds + ",v1=" + hmac(secret, timestampSeconds + "." + body);
    }

    public static boolean verify(String secret, String header, String body) {
        if (header == null) {
            return false;
        }
        long t = -1;
        String v1 = null;
        for (String part : header.split(",")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length == 2 && kv[0].equals("t")) {
                try {
                    t = Long.parseLong(kv[1]);
                } catch (NumberFormatException e) {
                    return false;
                }
            } else if (kv.length == 2 && kv[0].equals("v1")) {
                v1 = kv[1];
            }
        }
        if (t < 0 || v1 == null) {
            return false;
        }
        String expected = hmac(secret, t + "." + body);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII), v1.getBytes(StandardCharsets.US_ASCII));
    }

    static String hmac(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException(e);
        }
    }
}
