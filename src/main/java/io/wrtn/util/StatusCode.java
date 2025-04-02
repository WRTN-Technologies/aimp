package io.wrtn.util;

public enum StatusCode {

    /* 200 OK : 성공 */
    SUCCESS(200, "200 OK", "Request success"),

    /* 202 ACCEPTED */
    ACCEPTED(202, "202 ACCEPTED", "Request accepted"),

    /* 400 BAD_REQUEST : 잘못된 요청 */
    INVALID_INPUT_VALUE(400, "BAD_REQUEST", "Incorrect input"),

    BAD_REQUEST(400, "BAD_REQUEST", "Invalid request"),

    /* 401 UNAUTHORIZED : 인증되지 않은 사용자 */
    UNAUTHENTICATED(401, "UNAUTHENTICATED", "Authentication failed"),

    /* 404 NOT_FOUND */
    NOT_FOUND(404, "404 NOT_FOUND", "Resource not found"),

    /* 405 METHOD_NOT_ALLOWED : 지원하지 않는 HTTP Method */
    METHOD_NOT_ALLOWED(405, "METHOD_NOT_ALLOWED", "Method not allowed"),

    /* 409 RESOURCE_ALREADY_EXISTS */
    RESOURCE_ALREADY_EXISTS(409, "RESOURCE_ALREADY_EXISTS", "Resource already exists"),

    /* 500 INTERNAL_SERVER_ERROR */
    SERVER_ERROR(500, "INTERNAL_SERVER_ERROR", "Internal server error"),

    /* 429 TOO_MANY_REQUESTS */
    TOO_MANY_REQUESTS(429, "TOO_MANY_REQUESTS", "Too many requests");

    private final int statusCode;
    private final String statusDescription;
    private final String message;

    StatusCode(int status, String statusDescription, String message) {
        this.statusCode = status;
        this.statusDescription = statusDescription;
        this.message = message;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getStatusDescription() {
        return statusDescription;
    }

    public String getMessage() {
        return message;
    }
}
