package com.noku.utils;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class TextCompressor implements Compressor{
    private static final TextCompressor instance = new TextCompressor();
    private static final int STRATEGY_WORDS = 1;
    private static final int STRATEGY_CHARS = 2;
    
    private static final ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES);
    
    private Charset charset;
    public TextCompressor(){
        charset = StandardCharsets.UTF_8;
    }
    
    public void setCharset(Charset set){
        this.charset = set;
    }
    
    @Override
    public byte[] compress(byte[] input) {
        Map<String, Integer> dictionary = new DynamicMap<>(String[]::new, Integer[]::new);
        String data = new String(input, charset);
        data = data.replace("\\", "\\\\").replace("^", "\\^").replace("#", "\\#");
    
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        
        String[] words = data.split(" ");
        WordCounter counter = new WordCounter();
        for(String s : words){
            counter.putWord(s);
        }
        counter.analyze();
    
        DynamicMap<String, Byte[]> small = getSmallReplacements(counter.top255);
        DynamicMap<String, Byte[]> large = getSmallReplacements(counter.longWorth);
        
        final byte[] space = " ".getBytes(StandardCharsets.UTF_8);
    
        try {
            for(String s : words){
                byte[] ret;
                if(small.containsKey(s)) ret = ba(small.get(s));
                else if(large.containsKey(s)) ret = ba(large.get(s));
                else ret = s.getBytes(StandardCharsets.UTF_8);
        
                out.write(ret);
                out.write(space);
            }
            
            //Create Footer Dictionary
            byte[] footer = createFooter(counter);
            out.write(footer);
            out.flush();
            return out.toByteArray();
        } catch (Exception e){
            e.printStackTrace();
        }
        return input;
    }
    
    @Override
    public byte[] decompress(byte[] input) {
        return new byte[0];
    }
    
    private byte[] createFooter(WordCounter counter){
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            out.write("^".getBytes(charset));
            DynamicMap<String, Byte[]> replacements = getSmallReplacements(counter.top255);
    
            Set<String> keys = replacements.keySet();
            for(String s : keys){
                out.write("^".getBytes(charset));
                out.write(s.getBytes(charset));
            }
    
            out.write("#".getBytes(charset));
            replacements = getLongReplacements(counter.longWorth);
    
            keys = replacements.keySet();
            for(String s : keys){
                out.write("#".getBytes(charset));
                out.write(s.getBytes(charset));
            }
        } catch (Exception e){
            e.printStackTrace();
        }
        return out.toByteArray();
    }
    
    private DynamicMap<String, Byte[]> getSmallReplacements(DynamicArray<String> top255){
        DynamicMap<String, Byte[]> ret = new DynamicMap<>(String[]::new, Byte[][]::new);
        for(int i = 0; i < top255.size(); i++){
            byte[] dat = "^".getBytes(charset);
            Byte[] repl = new Byte[dat.length + 1];
            
            int dex = 0;
            for(byte b : dat) repl[dex++] = b;
            repl[dat.length] = b(i);
            
            ret.put(top255.get(i), repl);
        }
        
        return ret;
    }
    
    private DynamicMap<String, Byte[]> getLongReplacements(DynamicArray<String> longWorth){
        DynamicMap<String, Byte[]> ret = new DynamicMap<>(String[]::new, Byte[][]::new);
        for(int i = 0; i < longWorth.size(); i++){
            byte[] dat = "#".getBytes(charset);
            Byte[] repl = new Byte[dat.length + 4];
            
            int dex = 0;
            for(byte b : dat) repl[dex++] = b;
            for(byte b : intToBytes(dex)) repl[dex++] = b;
            
            ret.put(longWorth.get(i), repl);
        }
        
        return ret;
    }
    
    public static byte[] intToBytes(int x) {
        buffer.putInt(0, x);
        return buffer.array();
    }
    
    public static int bytesToInt(byte[] bytes) {
        buffer.put(bytes, 0, bytes.length);
        buffer.flip();//need flip
        return buffer.getInt();
    }
    
    public byte b(int in){ return (byte)(in & 0xFF); }
    public byte[] ba(Byte[] arr){
        byte[] ret = new byte[arr.length];
        for(int i = 0; i < arr.length; i++) ret[i] = arr[i];
        return ret;
    }
    
    public static Compressor getInstance(){ return instance; }
    
    public static final class WordCounter{
        public final DynamicMap<String, Integer> map;
        public DynamicArray<String> top255;
        public DynamicArray<String> longWorth;
        
        public WordCounter(){
            map = new DynamicMap<>(String[]::new, Integer[]::new);
        }
        
        public void putWord(String s){
            if(map.containsKey(s)) map.put(s, map.get(s) + 1);
            else map.put(s, 1);
        }
        
        public int getCount(String word){
            if(!map.containsKey(word)) return 0;
            return map.get(word);
        }
    
        public DynamicArray<String> getTop255() {
            return top255;
        }
        
        public void analyze(){
            if(map.size() < 255) {
                top255 = map.keySetAsDynamicArray();
            } else {
                DynamicArray<String> ret = new DynamicArray<>(String[]::new);
    
                String keyMost = null;
                int maxScore = -1;
                int prevMax = -1;
    
                Set<String> keys = map.keySet();
    
                start:
                for(int i = 0; i < 255; i++) {
                    for(String s : keys) {
                        int score = (int)(Math.pow(map.get(s), 4) * s.length());
                        if(score > maxScore) {
                            keyMost = s;
                            maxScore = score;
                        }
                        if(score == prevMax && !ret.contains(s, false)) {
                            ret.add(s);
                            maxScore = -1;
                            continue start;
                        }
                    }
        
                    ret.add(keyMost);
        
                    prevMax = maxScore;
                    maxScore = -1;
                }
    
                top255 = ret;
            }
            DynamicArray<String> ret = new DynamicArray<>(String[]::new);
    
            for(String s : map.keySet()){
                if(top255.contains(s, false)) continue;
                
                int count = map.get(s);
                if(count > 3) ret.add(s);
            }
    
            longWorth = ret;
        }
    
        public void clear(){
            map.clear();
            top255.clear();
            longWorth.clear();
        }
        
        public DynamicArray<String> getLong(){
            return longWorth;
        }
        
        public DynamicArray<String> getAll(){
            return map.keySetAsDynamicArray();
        }
    }
}
