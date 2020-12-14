package com.noku.utils;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.plugins.jpeg.JPEGImageWriteParam;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public final class ImageFactory {
    private final JPEGImageWriteParam jpegParams = new JPEGImageWriteParam(null);
    private static final ImageFactory instance = new ImageFactory();
    
    private Compressor compressor = ZLibCompressor.getInstance();
    
    private ImageFactory(){
        jpegParams.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
    }
    
    public void setCompressor(Compressor compressor){
        this.compressor = compressor;
    }
    
    public byte[] toBase64(byte[] imgBytes, float quality){
        byte[] converted = toJpgByteArray(imgBytes, quality);
        byte[] compressed = compressor.compress(converted);
        return Base64.encode(compressed);
    }
    
    public byte[] fromBase64(byte[] base64Bytes){
        byte[] decoded = Base64.decode(base64Bytes);
        return compressor.decompress(decoded);
    }
    
    public byte[] readFromFile(String path){
        byte[] data;
        try{
            data = Files.readAllBytes(new File(path).toPath());
        } catch (IOException e){
            data = null;
        }
        return data;
    }
    
    public boolean writeToFile(String path, byte... data){
        try{
            Files.write(new File(path).toPath(), data);
        } catch (IOException e){
            return false;
        }
        return true;
    }
    
    public byte[] toJpgByteArray(byte[] imageBytes, float quality){
        try {
            quality = Math.min(quality, 1F);
            jpegParams.setCompressionQuality(quality);
    
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(imageBytes));
            BufferedImage conv = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
            conv.createGraphics().drawImage(img, 0, 0, Color.white, null);
    
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            final ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
            writer.setOutput(new MemoryCacheImageOutputStream(stream));
            writer.write(null, new IIOImage(conv, null, null), jpegParams);
    
            return stream.toByteArray();
        } catch (Exception e){
            e.printStackTrace();
            return null;
        }
    }
    
    public static ImageFactory getInstance(){
        return instance;
    }
}
