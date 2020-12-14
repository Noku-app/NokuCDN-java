package com.noku.utils;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class ByteListOuputStream extends OutputStream {
    private final List<Byte> data;
    public ByteListOuputStream(List<Byte> bytes){
        this.data = bytes;
    }
    
    public void write(int b) throws IOException {
        this.data.add((byte)b);
    }
    
    public void writeBytes(byte[] bytes){
    
    }
    
    public byte[] asArray(){
        byte[] ret = new byte[data.size()];
        for(int i = 0; i < ret.length; i++){
            ret[i] = data.get(i);
        }
        return ret;
    }
}
