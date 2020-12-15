package com.noku.utils;

import java.nio.charset.StandardCharsets;

/**
 * A normal implementation of SHA-256 using Java ints.
 */
public final class SHA256 {
    private SHA256(){}
    
    private static final int[] K256 = {
    0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
    0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
    0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
    0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
    0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
    0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
    0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
    0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2,
    };
    
    private static final int[] INITIAL_HASH = {
    0x6A09E667, 0xBB67AE85, 0x3C6EF372, 0xA54FF53A,
    0x510E527F, 0x9B05688C, 0x1F83D9AB, 0x5BE0CD19,
    };
    
    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder();
        for (byte b : bytes) {
            char u = HEX_ARRAY[(b >> 4) & 0x0F];
            char l = HEX_ARRAY[b & 0x0F];
            builder.append(u);
            builder.append(l);
        }
        return builder.toString();
    }
    
    public static byte[] hash(byte[] input) {
        int[] hash = INITIAL_HASH;
        for (int firstByteIndex = 0;; firstByteIndex += 64) {
            int remaining = input.length - firstByteIndex;
            if (remaining >= 64) {
                // Full block
                processBlock(hash, input, firstByteIndex);
            } else {
                // Partial block; needs padding
                int minLength = remaining + 1 + 8;
                int l = input.length * 8;
                if (minLength <= 64) {
                    byte[] lastBlock = new byte[64];
                    System.arraycopy(input, firstByteIndex, lastBlock, 0, remaining);
                    lastBlock[remaining] = (byte) 0x80;
                    lastBlock[60] = (byte) (l >>> 24);
                    lastBlock[61] = (byte) (l >>> 16);
                    lastBlock[62] = (byte) (l >>> 8);
                    lastBlock[63] = (byte) l;
                    processBlock(hash, lastBlock, 0);
                } else {
                    // Padding must spill over into another block.
                    byte[] lastTwoBlocks = new byte[128];
                    System.arraycopy(input, firstByteIndex, lastTwoBlocks, 0, remaining);
                    lastTwoBlocks[remaining] = (byte) 0x80;
                    lastTwoBlocks[124] = (byte) (l >>> 24);
                    lastTwoBlocks[125] = (byte) (l >>> 16);
                    lastTwoBlocks[126] = (byte) (l >>> 8);
                    lastTwoBlocks[127] = (byte) l;
                    processBlock(hash, lastTwoBlocks, 0);
                    processBlock(hash, lastTwoBlocks, 32);
                }
                break;
            }
        }
        
        byte[] hashBytes = new byte[32];
        for (int i = 0; i < hash.length; ++i) {
            hashBytes[i * 4] = (byte) (hash[i] >>> 24);
            hashBytes[i * 4 + 1] = (byte) (hash[i] >>> 16);
            hashBytes[i * 4 + 2] = (byte) (hash[i] >>> 8);
            hashBytes[i * 4 + 3] = (byte) hash[i];
        }
        return hashBytes;
    }
    
    private static void processBlock(int[] hash, byte[] source, int start) {
        int[] inputWords = new int[16];
        for (int i = 0; i < 16; ++i) {
            inputWords[i] = (source[start + i * 4] & 0xFF) << 24
            | (source[start + i * 4 + 1] & 0xFF) << 16
            | (source[start + i * 4 + 2] & 0xFF) << 8
            | (source[start + i * 4 + 3] & 0xFF);
        }
        
        int[] w = new int[64];
        System.arraycopy(inputWords, 0, w, 0, 16);
        int a = hash[0], b = hash[1], c = hash[2], d = hash[3],
        e = hash[4], f = hash[5], g = hash[6], h = hash[7];
        for (int t = 16; t < 64; ++t) {
            w[t] = unsignedAdd(gamma1(w[t - 2]), w[t - 7], gamma0(w[t - 15]), w[t - 16]);
        }
        
        for (int t = 0; t < 64; ++t) {
            int tmp1 = unsignedAdd(h, sigma1(e), ch(e, f, g), K256[t], w[t]);
            int tmp2 = unsignedAdd(sigma0(a), maj(a, b, c));
            h = g;
            g = f;
            f = e;
            e = unsignedAdd(d, tmp1);
            d = c;
            c = b;
            b = a;
            a = unsignedAdd(tmp1, tmp2);
        }
        
        hash[0] = unsignedAdd(hash[0], a);
        hash[1] = unsignedAdd(hash[1], b);
        hash[2] = unsignedAdd(hash[2], c);
        hash[3] = unsignedAdd(hash[3], d);
        hash[4] = unsignedAdd(hash[4], e);
        hash[5] = unsignedAdd(hash[5], f);
        hash[6] = unsignedAdd(hash[6], g);
        hash[7] = unsignedAdd(hash[7], h);
    }
    
    private static int unsignedAdd(int... words) {
        long sum = 0;
        for (int w : words) {
            sum += Integer.toUnsignedLong(w);
        }
        return (int) (sum & 0xFFFFFFFFL);
    }
    
    private static int sigma0(int x) {
        return rotr(x, 2) ^ rotr(x, 13) ^ rotr(x, 22);
    }
    
    private static int sigma1(int x) {
        return rotr(x, 6) ^ rotr(x, 11) ^ rotr(x, 25);
    }
    
    private static int gamma0(int x) {
        return rotr(x, 7) ^ rotr(x, 18) ^ x >>> 3;
    }
    
    private static int gamma1(int x) {
        return rotr(x, 17) ^ rotr(x, 19) ^ x >>> 10;
    }
    
    /** Rotate right */
    private static int rotr(int x, int n) {
        return x >>> n | x << 32 - n;
    }
    
    private static int ch(int x, int y, int z) {
        return x & y ^ ~x & z;
    }
    
    /** Majority of three inputs */
    private static int maj(int x, int y, int z) {
        return x & y ^ x & z ^ y & z;
    }
}