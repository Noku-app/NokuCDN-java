package com.noku.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public class ZLibCompressor implements Compressor{
    private static final ZLibCompressor instance = new ZLibCompressor();
    public byte[] compress(byte[] bytesToCompress) {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION, false);
        deflater.setInput(bytesToCompress);
        deflater.finish();
        
        byte[] bytesCompressed = new byte[0x00FFFFFF];
        
        int numberOfBytesAfterCompression = deflater.deflate(bytesCompressed);
        
        byte[] returnValues = new byte[numberOfBytesAfterCompression];
        
        System.arraycopy(bytesCompressed, 0, returnValues, 0, numberOfBytesAfterCompression);
        
        return returnValues;
    }
    
    public byte[] decompress(byte[] bytesToDecompress) {
        byte[] returnValues = null;
        Inflater inflater = new Inflater();
        int numberOfBytesToDecompress = bytesToDecompress.length;
        
        inflater.setInput(bytesToDecompress, 0, numberOfBytesToDecompress);
        int bufferSizeInBytes = numberOfBytesToDecompress;
        
        int numberOfBytesDecompressedSoFar = 0;
        List<Byte> bytesDecompressedSoFar = new ArrayList<Byte>();
        
        try {
            while(inflater.needsInput() == false) {
                byte[] bytesDecompressedBuffer = new byte[bufferSizeInBytes];
                int numberOfBytesDecompressedThisTime = inflater.inflate(bytesDecompressedBuffer);
                numberOfBytesDecompressedSoFar += numberOfBytesDecompressedThisTime;
                
                for(int b = 0; b < numberOfBytesDecompressedThisTime; b++) {
                    bytesDecompressedSoFar.add(bytesDecompressedBuffer[b]);
                }
            }
            
            returnValues = new byte[bytesDecompressedSoFar.size()];
            for(int b = 0; b < returnValues.length; b++) {
                returnValues[b] = (byte) (bytesDecompressedSoFar.get(b));
            }
            
        } catch (DataFormatException dfe) {
            dfe.printStackTrace();
        }
        
        inflater.end();
        return returnValues;
    }
    public static Compressor getInstance(){ return instance; }
    private ZLibCompressor(){}
}
