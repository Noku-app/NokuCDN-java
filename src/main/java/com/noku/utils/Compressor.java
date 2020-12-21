package com.noku.utils;

public interface Compressor {
    public byte[] compress(byte[] input);
    public byte[] decompress(byte[] input);
}
