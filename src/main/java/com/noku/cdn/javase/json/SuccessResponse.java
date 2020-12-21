package com.noku.cdn.javase.json;

import java.nio.charset.StandardCharsets;

public class SuccessResponse implements Response{
    private final String data;
    /**
     * Creates a new SuccessResponse with the provided message.
     * @param data json formatted message.
     */
    public SuccessResponse(String data){
        this.data =
        "{\n" +
        "    \"error\":false,\n" +
        "    \"data\":" + data + "\n" +
        "}";
    }
    
    public String toJSON() {
        return data;
    }
    
    public byte[] getResponseBody() {
        return data.getBytes(StandardCharsets.UTF_8);
    }
    
    public int getHTMLCode() {
        return 200;
    }
}
