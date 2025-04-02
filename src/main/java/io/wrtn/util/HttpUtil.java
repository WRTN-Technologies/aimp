package io.wrtn.util;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

import java.util.Map;

public final class HttpUtil {

    public static void setCommonResponseHeaders(APIGatewayProxyResponseEvent response) {
        response.setIsBase64Encoded(false);
        response.setHeaders(Map.of("Content-Type", "application/json"));
    }
}
