package com.noku.cdn.javase.json;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class JSONUtils {
    
    public static String intArrayToJsonString(int[] data){
        StringBuilder b = new StringBuilder();
        b.append("[");
        for(int i = 0; i < data.length; i++){
            b.append(data[i]);
            if(i < data.length - 1) b.append(",");
        }
        return b.append("]").toString();
    }
    
    public static Map<String, Object> parse(final String input, int start){
        Map<String, Object> ret = new HashMap<>();
        
        final String json = input.replaceAll("\r", "");
        final int begin = Math.max(json.indexOf("{"), start);
        final int end = json.lastIndexOf("}");
        
        int cursor = begin;
        while(cursor <= end){
            char c = json.charAt(cursor);
            
            if(c == '"'){
                int endQuote = json.indexOf('"', cursor + 1);
                String name = json.substring(cursor + 1, endQuote);
                cursor = endQuote + 1;
                
                int val_start = json.indexOf(':', cursor);
                int val_end = json.indexOf(',', val_start);
                if(json.substring(val_start, val_end).contains("{")) val_end = json.indexOf("}", cursor);
                if(val_end == -1) val_end = json.indexOf("}", cursor);
                
                String value = json.substring(val_start + 1, val_end).trim();
                ret.put(name, parseType(value));
                
                cursor = val_end;
            }
            
            cursor++;
        }
        
        return ret;
    }
    
    public static Object parseType(String input){
        String value = input.trim();
        if(value.startsWith("\"")) return value.substring(1, value.length() - 1);
        if(value.startsWith("{")) { System.out.println("DATA: " + value); return parse(value, 0);}
        if(value.equals("true")) return true;
        if(value.equals("false")) return false;
        if(value.equals("null")) return null;
        if(!value.contains(".")) return Integer.parseInt(value);
        return Double.parseDouble(value);
    }
    
    public static void main(String[] args) throws IOException {
        String data = new String(Files.readAllBytes(new File("res/test.json").toPath()), StandardCharsets.UTF_8);
        printJson(parse(data, 0));
    }
    
    public static void printJson(Map<String, Object> data){
        Set<String> keys = data.keySet();
        for(String s : keys){
            Object val = data.get(s);
            System.out.print(s + ": ");
            if(val instanceof String) System.out.println("STRING(" + val + ")");
            if(val instanceof Integer) System.out.println("INT(" + val + ")");
            if(val instanceof Double) System.out.println("DOUBLE(" + val + ")");
            if(val instanceof Boolean) System.out.println("BOOLEAN(" + val + ")");
            if(data.getClass().isInstance(val)){
                System.out.print("OBJECT(");
                printJson((Map<String, Object>) val);
                System.out.println(")");
            }
        }
    }
    
    private JSONUtils(){}
}
