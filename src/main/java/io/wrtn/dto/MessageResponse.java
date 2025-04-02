package io.wrtn.dto;

public class MessageResponse {

    String message;

    public MessageResponse() {
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    public String toString() {
        return "DefaultResponse{" +
            "message='" + message + '\'' +
            '}';
    }
}
