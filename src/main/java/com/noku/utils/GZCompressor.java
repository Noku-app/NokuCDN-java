package com.noku.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.Deflater;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class GZCompressor implements Compressor{
    private static final GZCompressor instance = new GZCompressor();
    public byte[] compress(byte[] input) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (Wrapper out = new Wrapper(baos)){
            out.setLevel(Deflater.BEST_COMPRESSION);
            try (ByteArrayInputStream in = new ByteArrayInputStream(input)){
                byte[] buffer = new byte[1024];
                int len;
                while((len=in.read(buffer)) != -1){
                    out.write(buffer, 0, len);
                }
                out.flush();
                out.finish();
                out.close();
                return baos.toByteArray();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (IOException e){
            e.printStackTrace();
        }
        return input;
    }
    
    public byte[] decompress(byte[] input) {
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(input))){
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()){
                byte[] buffer = new byte[1024];
                int len;
                while((len = in.read(buffer)) != -1){
                    out.write(buffer, 0, len);
                }
                
                out.flush();
                return out.toByteArray();
            } catch (Exception e){
                e.printStackTrace();
            }
        } catch (Exception e){
            e.printStackTrace();
        }
        
        return input;
    }
    
    public static final class Wrapper extends GZIPOutputStream{
        public Wrapper(OutputStream out) throws IOException {
            super(out);
        }
        
        public void setLevel(int level){
            this.def.setLevel(level);
        }
    }
    
    public static Compressor getInstance(){ return instance; }
    private GZCompressor(){}
}
