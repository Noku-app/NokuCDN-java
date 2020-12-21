package com.noku.utils;

import com.kosprov.jargon2.api.Jargon2;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static com.kosprov.jargon2.api.Jargon2.jargon2Hasher;
import static com.kosprov.jargon2.api.Jargon2.jargon2Verifier;
import static com.noku.utils.SHA256.bytesToHex;

public class ImageFactoryMain {
    public static void main(String[] args) throws Exception {
        hash_main();
    }
    
    private static void hash_main() throws Exception{
        byte[] pass = new byte[]{
        //  0000  0001  0002  0003  0004  0005  0006  0007  0008  0009  000A  000B  000C  000D  000E  000F
             127,  -69,  -22,   68,   82, -110,  -32,   56,   68,   78,  -27,   56,   94,   32,   40,    8,
              -1,   19,   87,   71,   81,   64,    4,  -29,  -87,   68,   86,   60,   86,   75,   54,  -69,
             -87,    7,  -12,   24,   57,   17,  -87,  125, -128,   47,   34,  -65,   58,   47,  -44,   58,
              47,   87,  -56,   71,  -87,   17,   18,   -2,   34,  110,   17,   84,  -46,  -17,  -24,   15
        };
        System.out.println(SHA256.bytesToHex(pass));
        // 7FBBEA445292E038444EE5385E202808FF135747514004E3A944563C564B36BBA907F4183911A97D802F22BF3A2FD43A2F57C847A91112FE226E1154D2EFE80F
        // 7FBBEA445292E038444EE5385E202808FF135747514004E3A944563C564B36BBA907F4183911A97D802F22BF3A2FD43A2F57C847A91112FE226E1154D2EFE80F
        Jargon2.Hasher hasher = jargon2Hasher()
        .type(Jargon2.Type.ARGON2d) // Data-dependent hashing
        .memoryCost(65536)  // 64MB memory cost
        .timeCost(3)        // 3 passes through memory
        .parallelism(4)     // use 4 lanes and 4 threads
        .saltLength(16)     // 16 random bytes salt
        .hashLength(32);
        Jargon2.Verifier verifier = jargon2Verifier();
        
        String hash = hasher.password(pass).encodedHash();
        System.out.println("Hash: " + hash);
        System.out.println(verifier.hash(hash).password(pass).verifyEncoded());
        
        Files.write(new File("res/auth_hash.argon").toPath(), hash.getBytes(StandardCharsets.UTF_8));
        Files.write(new File("res/auth_token.raw").toPath(), pass);
    }
    
    private static void image_main() throws Exception{
        ImageFactory factory = ImageFactory.getInstance();
        factory.setCompressor(ZipCompressor.getInstance());
        byte[] dat = factory.readFromFile("res/icon.png");
        byte[] comp = factory.compress(dat);
        byte[] base64 = Base64.encode(comp);//.replace("\n", "").getBytes(StandardCharsets.UTF_8);
    
        System.out.println("Decompressed: " + new String(factory.decompress(Base64.decode(base64)), StandardCharsets.UTF_8));
    
        int progress = 0;
        for(int i = 0; i < base64.length; i++){
            int as = base64[i] + (base64[i] < 0 ? 256 : 0);
            if(progress == 0){
                if(as == 31){
                    System.out.println("");
                    progress++;
                }
            } else if(progress == 1){
                if(as == 139) progress++;
                else progress--;
            }
        
            if(progress == 2) System.out.println("Possible Index: " + (i - 1));
        }
    
        Files.write(new File("res/out.txt").toPath(), base64);
    }
    
    private static void compressor_main(){
        String data = "This is some test data we are going to compress and compare Java and PHP.";
        byte[] raw = data.getBytes(StandardCharsets.UTF_8);
        ImageFactory factory = ImageFactory.getInstance();
        
        factory.writeToFile("res/test/raw.bin", raw);
        factory.setCompressor(ZipCompressor.getInstance());
        
        byte[] compressed = factory.compress(raw);
        byte[] decomp = factory.decompress(compressed);
        factory.writeToFile("res/test/java.bin", raw);
        
        System.out.println("Original Message: " + data);
        System.out.println("Raw: " + bytesToHex(raw));
        System.out.println("Compressed: " + bytesToHex(compressed));
        System.out.println("Decompressed: " + bytesToHex(decomp));
        System.out.println("Retrieved: " + new String(decomp, StandardCharsets.UTF_8));
    }
    
    private static void text_compressor_main(){
        ImageFactory factory = ImageFactory.getInstance();
    
        String data = new String(factory.readFromFile("res/test/icon.png"), StandardCharsets.UTF_8);
        byte[] raw = data.getBytes(StandardCharsets.UTF_8);
        
        factory.writeToFile("res/test/raw.bin", raw);
        
        //byte[] decomp = factory.decompress(compressed);
        factory.writeToFile("res/test/java_zlib.bin", ZipCompressor.getInstance().compress(raw));
        factory.writeToFile("res/test/java_gzip.bin", GZCompressor.getInstance().compress(raw));
        
        //System.out.println("Original Message: " + data);
        //System.out.println("Compressed: " + new String(compressed, StandardCharsets.UTF_8));
        //System.out.println("Decompressed: " + new String(decomp));
    }
}
