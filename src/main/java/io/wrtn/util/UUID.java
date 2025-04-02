package io.wrtn.util;

import java.nio.ByteBuffer;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.RandomStringUtils;

public final class UUID {

    // 62 characters [0-9a-zA-Z]
    private static final String ALPHANUMERIC = "a9bcdJX0efKAghLB1YiMjklCN2mnODopZP3EqQrstR4FuSv8wTx5UyzGV67HIW";

    public static String generateUUID() {
        return java.util.UUID.randomUUID().toString();
    }

    public static String generateShortUUID(int length) {
        return RandomStringUtils.randomAlphanumeric(length);
    }

    public static String shortenUuid(String uuid, long number) {
        // Encode UUID part as Base64
        java.util.UUID restoredUuid = java.util.UUID.fromString(uuid);
        ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
        bb.putLong(restoredUuid.getMostSignificantBits());
        bb.putLong(restoredUuid.getLeastSignificantBits());
        String encodedUuid = Base64.encodeBase64URLSafeString(bb.array());

        // Encode number as Base62 (0-9, a-z, A-Z)
        String encodedNumber = numberToBase62(number);

        return encodedUuid + encodedNumber;
    }

    private static String numberToBase62(long number) {
        if (number == 0) {
            return "0";
        }
        StringBuilder sb = new StringBuilder();
        while (number > 0) {
            sb.append(ALPHANUMERIC.charAt((int) (number % 62)));
            number /= 62;
        }
        return sb.reverse().toString();
    }

}
