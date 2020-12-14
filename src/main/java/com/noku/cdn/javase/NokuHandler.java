package com.noku.cdn.javase;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.*;
import java.util.Set;

public class NokuHandler implements HttpHandler {
    protected final RequestQueue queue;
    public NokuHandler(RequestQueue queue){
        this.queue = queue;
    }
    
    public void handle(HttpExchange ex) throws IOException {
        printRequestInfo(ex);
        queue.addRequest(new CDNRequest(ex));
    }
    
    private void printRequestInfo(HttpExchange ex) throws IOException{
        System.out.println("Protocol:" + ex.getProtocol());
        System.out.println("Request Method:" + ex.getRequestMethod());
        System.out.println("Request Headers:");
    
        Headers headers = ex.getRequestHeaders();
        Set<String> keys = headers.keySet();
        for(String s : keys){
            System.out.println("  " + s + ": " + headers.get(s));
        }
        
        System.out.println("Request URI: " + ex.getRequestURI());
    }
}
