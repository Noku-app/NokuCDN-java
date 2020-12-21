package com.noku.cdn.javase.json;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;

public class ErrorResponse implements Response{
    private final String message;
    
    /**
     * Creates a new ErrorResponse with the provided message and error code.
     * @param message String message
     * @param code error code
     */
    public ErrorResponse(String message, int code){
        this.message = "{\n" +
        "    \"error\":true,\n" +
        "    \"data\":{\n" +
        "        \"reason\":" + code + "\n" +
        "        \"message\":" + message + "\n" +
        "    }\n" +
        "}";
    }
    
    /**
     * Creates a new ErrorResponse with the provided {@link SQLException}
     * @param exception to derive message from.
     */
    public ErrorResponse(SQLException exception){
        this.message = "{\n" +
        "    \"error\":true,\n" +
        "    \"data\":{\n" +
        "        \"reason\":" + exception.getErrorCode() + "\n" +
        "        \"message\":" + exception.getMessage() + "\n" +
        "    }\n" +
        "}";
    }
    
    public String toJSON() {
        return message;
    }
    
    public byte[] getResponseBody() {
        return message.getBytes(StandardCharsets.UTF_8);
    }
    
    public int getHTMLCode() {
        return 400;
    }
}
