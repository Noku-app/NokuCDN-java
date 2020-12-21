package com.noku.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

public class ZipCompressor implements Compressor{
    private static final ZipCompressor instance = new ZipCompressor();
    private static int level = Deflater.DEFAULT_COMPRESSION;
    @Override
    public byte[] compress(byte[] input) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Wrapper dos = new Wrapper(out);
            dos.setLevel(Deflater.BEST_COMPRESSION);
            dos.write(input);
            dos.flush();
            dos.close();
            out.flush();
            
            return out.toByteArray();
        } catch (Exception e){
            e.printStackTrace();
        }
        return input;
    }
    
    @Override
    public byte[] decompress(byte[] input) {
        try {
            InflaterInputStream dos = new InflaterInputStream(new ByteArrayInputStream(input));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte b = 0;
            while((b = (byte)dos.read()) != -1) out.write(b);
        
            return out.toByteArray();
        } catch (Exception e){
            e.printStackTrace();
        }
        return input;
    }
    
    private static final class Wrapper extends DeflaterOutputStream{
        public Wrapper(OutputStream o){
            super(o);
        }
        
        public void setLevel(int level){
            def.setLevel(level);
        }
    }
    
    public static Compressor getInstance(){ return instance; }
    public static Compressor getInstance(int level){
        ZipCompressor.level = level;
        return instance;
    }
    private ZipCompressor(){}
}
