package com.noku.utils;

public abstract class Compressor {
    private static Compressor instance;
    public abstract byte[] compress(byte[] input);
    public abstract byte[] decompress(byte[] input);
}
