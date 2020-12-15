package com.noku.cdn.javase;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class CDNRequest {
    public final Map<String, Object> data = new HashMap<>();
    public final String url, method;
    public final HttpExchange ex;
    public final RequestType type;
    private String requestBody;
    
    public CDNRequest(HttpExchange ex) throws IOException {
        this.ex = ex;
        
        Headers headers = ex.getRequestHeaders();
        
        if(headers.containsKey("Content-type")) {
            String contentType = headers.getFirst("Content-type");
            if(contentType.contains("multipart/form-data")) {
                //found form data
                String boundary = contentType.substring(contentType.indexOf("boundary=") + 9);
                // as of rfc7578 - prepend "\r\n--"
                byte[] boundaryBytes = ("\r\n--" + boundary).getBytes(StandardCharsets.UTF_8);
                byte[] payload = getInputAsBinary(ex.getRequestBody());
                System.out.println(new String(payload, StandardCharsets.UTF_8));
                ArrayList<MultiPart> list = new ArrayList<>();
                
                List<Integer> offsets = searchBytes(payload, boundaryBytes, 0, payload.length - 1);
                System.out.println("Offsets: " + offsets.size());
                for(int idx = 0; idx < offsets.size(); idx++) {
                    int startPart = offsets.get(idx);
                    int endPart = payload.length;
                    if(idx < offsets.size() - 1) {
                        endPart = offsets.get(idx + 1);
                    }
                    byte[] part = Arrays.copyOfRange(payload, startPart, endPart);
                    //look for header
                    int headerEnd = indexOf(part, "\r\n\r\n".getBytes(StandardCharsets.UTF_8), 0, part.length - 1);
                    if(headerEnd > 0) {
                        MultiPart p = new MultiPart();
                        byte[] head = Arrays.copyOfRange(part, 0, headerEnd);
                        String header = new String(head);
                        // extract name from header
                        int nameIndex = header.indexOf("\r\nContent-Disposition: form-data; name=");
                        if(nameIndex >= 0) {
                            int startMarker = nameIndex + 39;
                            //check for extra filename field
                            int fileNameStart = header.indexOf("; filename=");
                            if(fileNameStart >= 0) {
                                String filename = header.substring(fileNameStart + 11, header.indexOf("\r\n", fileNameStart));
                                p.filename = filename.replace('"', ' ').replace('\'', ' ').trim();
                                p.name = header.substring(startMarker, fileNameStart).replace('"', ' ').replace('\'', ' ').trim();
                                p.type = PartType.FILE;
                            } else {
                                int endMarker = header.indexOf("\r\n", startMarker);
                                if(endMarker == -1)
                                    endMarker = header.length();
                                p.name = header.substring(startMarker, endMarker).replace('"', ' ').replace('\'', ' ').trim();
                                p.type = PartType.TEXT;
                            }
                        } else {
                            // skip entry if no name is found
                            continue;
                        }
                        // extract content type from header
                        int typeIndex = header.indexOf("\r\nContent-Type:");
                        if(typeIndex >= 0) {
                            int startMarker = typeIndex + 15;
                            int endMarker = header.indexOf("\r\n", startMarker);
                            if(endMarker == -1)
                                endMarker = header.length();
                            p.contentType = header.substring(startMarker, endMarker).trim();
                        }
                        
                        //handle content
                        if(p.type == PartType.TEXT) {
                            //extract text value
                            byte[] body = Arrays.copyOfRange(part, headerEnd + 4, part.length);
                            p.value = new String(body);
                            p.bytes = body;
                        } else {
                            //must be a file upload
                            p.bytes = Arrays.copyOfRange(part, headerEnd + 4, part.length);
                        }
                        list.add(p);
                    }
                }
                
                this.type = handle(ex, list);
            } else {
                this.type = handle(ex, null);
            }
        } else {
            this.type = handle(ex, null);
        }
        
        String temp = ex.getRequestURI().toString();
        if(temp.startsWith("/")) temp = temp.substring(1);
        
        this.url = temp;
        this.method = ex.getRequestMethod();
    }
    
    private RequestType handle(HttpExchange ex, List<MultiPart> parts) throws IOException{
        if(parts != null) System.out.println("Parts Length: " + (parts.size()));
        if(parts == null){
            BufferedReader reader = new BufferedReader(new InputStreamReader(ex.getRequestBody()));
            StringBuilder builder = new StringBuilder();
            String line;
            while((line = reader.readLine()) != null) builder.append(line).append("\n");
            this.requestBody = builder.toString();
            
            return RequestType.VIEW;
        } else {
            for(MultiPart part : parts){
                data.put(part.name, part);
            }
            this.requestBody = "";
            
            return RequestType.UPLOAD;
        }
    }
    
    public MultiPart getData(String name){
        return (MultiPart) data.get(name);
    }
    
    public static byte[] getInputAsBinary(InputStream requestStream) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            byte[] buf = new byte[100000];
            int bytesRead;
            while ((bytesRead = requestStream.read(buf)) != -1){
                //while (requestStream.available() > 0) {
                //    int i = requestStream.read(buf);
                bos.write(buf, 0, bytesRead);
            }
            requestStream.close();
            bos.close();
        } catch (IOException e) {
            System.out.println("error while decoding http input stream");
        }
        
        byte[] addition = "\r\n".getBytes(StandardCharsets.UTF_8);
        
        byte[] dat = bos.toByteArray();
        byte[] ret = new byte[dat.length + addition.length];
        System.arraycopy(dat, 0, ret, addition.length, dat.length);
        System.arraycopy(addition, 0, ret, 0, addition.length);
        
        return ret;
    }
    
    public List<Integer> searchBytes(byte[] srcBytes, byte[] searchBytes, int searchStartIndex, int searchEndIndex) {
        final int destSize = searchBytes.length;
        final List<Integer> positionIndexList = new ArrayList<>();
        int cursor = searchStartIndex;
        while (cursor < searchEndIndex + 1) {
            int index = indexOf(srcBytes, searchBytes, cursor, searchEndIndex);
            if (index >= 0) {
                positionIndexList.add(index);
                cursor = index + destSize;
            } else {
                cursor++;
            }
        }
        return positionIndexList;
    }
    
    public int indexOf(byte[] srcBytes, byte[] searchBytes, int startIndex, int endIndex) {
        if (searchBytes.length == 0 || (endIndex - startIndex + 1) < searchBytes.length) {
            return -1;
        }
        int maxScanStartPosIdx = srcBytes.length - searchBytes.length;
        final int loopEndIdx;
        if (endIndex < maxScanStartPosIdx) {
            loopEndIdx = endIndex;
        } else {
            loopEndIdx = maxScanStartPosIdx;
        }
        int lastScanIdx = -1;
        label: // goto label
        for (int i = startIndex; i <= loopEndIdx; i++) {
            for (int j = 0; j < searchBytes.length; j++) {
                if (srcBytes[i + j] != searchBytes[j]) {
                    continue label;
                }
    
                lastScanIdx = i + j;
            }
            if (endIndex < lastScanIdx || lastScanIdx - i + 1 < searchBytes.length) {
                // it becomes more than the last index
                // or less than the number of search bytes
                return -1;
            }
            return i;
        }
        return -1;
    }
    
    public String getUrl(){
        return url;
    }
    
    public String[] getUrlAsArray(){
        return url.split("/");
    }
    
    public String getRequestBody(){
        return requestBody;
    }
    
    public String getMethod(){
        return method;
    }
    
    public HttpExchange getEx(){
        return ex;
    }
    
    public void close() throws IOException {
        ex.close();
    }
    
    public static class MultiPart {
        public PartType type;
        public String contentType;
        public String name;
        public String filename;
        public String value;
        public byte[] bytes;
    }
    
    public enum PartType{
        TEXT, FILE
    }
    
    public enum RequestType{
        VIEW,
        UPLOAD
    }
}
