package com.noku.cdn.javase;

import com.noku.utils.ImageFactory;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

public class Main {
    private static boolean gui = false;
    private CDNServer server;
    
    public Main(Properties props){
        server = new CDNServer(props);
        server.start();
    }
    
    public static void main(String[] args) throws Exception{
        boolean test = false;
        if(!test) {
            String filename = "res/db.properties";
            if(args != null && args.length > 0) filename = args[0];
            if(args != null && args.length > 1) gui = Boolean.parseBoolean(args[1]);
    
            Properties props = new Properties();
            props.load(new FileInputStream(new File(filename)));
    
            new Main(props);
        } else {
            ImageFactory factory = ImageFactory.getInstance();
            String s = new String(factory.toBase64(factory.readFromFile("res/noku-shadow.png"), 0.6F), StandardCharsets.UTF_8);
            s = s.replace("\n", "");
            System.out.print(s);
        }
    }
}
