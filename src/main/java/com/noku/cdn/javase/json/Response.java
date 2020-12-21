package com.noku.cdn.javase.json;

public interface Response extends JsonParsable{
    public int getHTMLCode();
    public byte[] getResponseBody();
}
