package io.wrtn.util;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

public class ValueConverter {

    public static byte[] floatArrayToByteArray(float[] floatArray) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(floatArray.length * Float.BYTES);
        for (float value : floatArray) {
            byteBuffer.putFloat(value);
        }
        return byteBuffer.array();
    }

    public static float[] byteArrayToFloatArray(byte[] byteArray) {
        FloatBuffer floatBuffer = ByteBuffer.wrap(byteArray).asFloatBuffer();
        float[] floatArray = new float[floatBuffer.remaining()];
        floatBuffer.get(floatArray);
        return floatArray;
    }
}
