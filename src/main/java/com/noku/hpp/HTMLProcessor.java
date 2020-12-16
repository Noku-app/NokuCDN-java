package com.noku.hpp;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public final class HTMLProcessor {
    public static final String DEP_IDENTIFIER = "<!-- Import:";
    public static final String DEP_IDENTIFIER_END = "-->";
    private HTMLProcessor() {}
    
    public static byte[] processHTMLFile(String file, String[] args, String[] values) throws IOException {
        byte[] resp = Files.readAllBytes(new File(file).toPath());
        return processHTMLBytes(resp, args, values);
    }
    
    public static byte[] readFile(String file) throws IOException {
        byte[] bytes = Files.readAllBytes(new File(file).toPath());
        // If the file ends in ".nml" we need to make sure we load dep files
        if(file.endsWith(".nml")){
            String data = new String(bytes, StandardCharsets.UTF_8);
            int cursor = 0;
            while((cursor = data.indexOf(DEP_IDENTIFIER, cursor)) != -1){
                int endIndex = data.indexOf(DEP_IDENTIFIER_END, cursor);
                
                String filename = data.substring(cursor + DEP_IDENTIFIER.length(), endIndex).trim();
                String repl = data.substring(cursor, endIndex + DEP_IDENTIFIER_END.length());
                System.out.println("Import File: " + filename);
                
                String n = new String(readFile("res/" + filename, 2), StandardCharsets.UTF_8);
                data = data.replace(repl, n);
            }
            
            bytes = data.getBytes(StandardCharsets.UTF_8);
        }
        return bytes;
    }
    
    public static byte[] readFile(String file, int indent) throws IOException {
        byte[] bytes = Files.readAllBytes(new File(file).toPath());
        // If the file ends in ".nml" we need to make sure we load dep files
        if(file.endsWith(".nml")){
            String data = new String(bytes, StandardCharsets.UTF_8).trim();
            int cursor = 0;
            while((cursor = data.indexOf(DEP_IDENTIFIER, cursor)) != -1){
                int endIndex = data.indexOf(DEP_IDENTIFIER_END, cursor);
                
                String filename = data.substring(cursor + DEP_IDENTIFIER.length(), endIndex).trim();
                String repl = data.substring(cursor, endIndex + DEP_IDENTIFIER_END.length());
                System.out.println("Import File: " + filename);
                
                String n = new String(readFile("res/" + filename, indent + 2), StandardCharsets.UTF_8);
                data = data.replace(repl, n);
            }
            
            bytes = data.getBytes(StandardCharsets.UTF_8);
        }
        String r = new String(bytes, StandardCharsets.UTF_8);
        r = HTMLProcessor.indent(indent, r);
        
        return r.getBytes(StandardCharsets.UTF_8);
    }
    
    public static byte[] processHTMLBytes(byte[] bytes, String[] args, String[] values){
        String data = new String(bytes, StandardCharsets.UTF_8);
        for(int i = 0; i < args.length; i++) data = data.replace("{" + args[i] + "}", values[i]);
        return data.getBytes(StandardCharsets.UTF_8);
    }
    
    /**
     * Creates an html table with data provided in the data param. The columns param determines
     * where data should be split into rows. The first row will be put into &#60;thead&#62; with
     * &#60;th&#62;.
     * @param clazz css classes to be applied to the table.
     * @param columns index to split columns into rows.
     * @param data data to be put in.
     * @return HTML Table in a Java {@link String}.
     */
    public static String createTable(String clazz, int columns, String... data){
        StringBuilder builder = new StringBuilder();
        
        if(clazz == null || clazz.equals("")) builder.append("<table>\n  <thead>\n    <tr>\n");
        else builder.append("<table class=\"").append(clazz).append("\">\n  <thead>\n    <tr>\n");
        for(int i = 0; i < columns; i++){
            builder.append("      <th>").append(data[i]).append("</th>\n");
        }
        builder.append("    </tr>\n  </thead>\n  <tbody>\n");
        for(int i = columns; i < data.length; i++){
            if(i % columns == 0) builder.append("    <tr>\n"); // If quotient is 0, that means its a new row.
            
            builder.append("      <td>").append(data[i]).append("</td>\n");
            
            if(i % columns == columns - 1) builder.append("    </tr>\n"); // If quotient is one less than the divisor, that means its the end of the row.
        }
        builder.append("  </tbody>\n</table>\n");
        
        return builder.toString();
    }
    
    /**
     * Creates an ordered html list &#60;ol&#62; with the elements in items parameter.
     * @param listClass css class to be applied to the list
     * @param itemClass css class to be applied to each item.
     * @param items Items to be inserted into the list.
     * @return HTML ordered list &#60;ol&#62;
     */
    public static String createOrderedList(String listClass, String itemClass, String... items){
        StringBuilder builder = new StringBuilder();
        
        if(listClass == null || listClass.equals("")) builder.append("<ol>\n");
        else builder.append("<ol class=\"").append(listClass).append("\">\n");
        
        if(itemClass == null || itemClass.equals("")) for(String s : items) builder.append("  <li>").append(s).append("</li>\n");
        else for(String s : items) builder.append("  <li class=\"").append(itemClass).append("\">").append(s).append("</li>\n");
        builder.append("</ol>\n");
        
        return builder.toString();
    }
    
    /**
     * Creates an unordered html list &#60;ul&#62; with the elements in items parameter.
     * @param listClass css class to be applied to the list
     * @param itemClass css class to be applied to each item.
     * @param items Items to be inserted into the list.
     * @return HTML unordered list &#60;ul&#62;
     */
    public static String createUnorderedList(String listClass, String itemClass, String... items){
        StringBuilder builder = new StringBuilder();
        
        if(listClass == null || listClass.equals("")) builder.append("<ul>\n");
        else builder.append("<ul class=\"").append(listClass).append("\">\n");
        
        if(itemClass == null || itemClass.equals("")) for(String s : items) builder.append("  <li>").append(s).append("</li>\n");
        else for(String s : items) builder.append("  <li class=\"").append(itemClass).append("\">").append(s).append("</li>\n");
        builder.append("</ul>\n");
        
        return builder.toString();
    }
    
    /**
     * Intents all new-lines in toIndent with number of spaces provided.
     * @param spaces number of spaces to indent with
     * @param toIndent {@link String} to indent.
     * @return an indented {@link String}.
     */
    public static String indent(int spaces, String toIndent){
        StringBuilder sp = new StringBuilder();
        for(int i = 0; i < spaces; i++) sp.append(" ");
        String spacer = sp.toString();
        
        sp = new StringBuilder();
        String[] lines = toIndent.split("\n");
        for(String s : lines) sp.append(spacer).append(s).append("\n");
        return sp.toString();
    }
}
