package com.noku.cdn.javase;


import com.kosprov.jargon2.api.Jargon2;
import com.noku.base.ColumnValuePair;
import com.noku.base.Condition;
import com.noku.base.ConditionSet;
import com.noku.base.ConditionType;
import com.noku.base.javase.NokuBase;
import com.noku.base.javase.NokuResult;
import com.noku.hpp.HTMLProcessor;
import com.noku.utils.*;
import com.noku.utils.Base64;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;

import javax.net.ssl.*;
import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyStore;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static com.kosprov.jargon2.api.Jargon2.jargon2Hasher;
import static com.kosprov.jargon2.api.Jargon2.jargon2Verifier;
import static com.noku.cdn.javase.json.JSONUtils.intArrayToJsonString;


public final class CDNServer implements Runnable{
    private volatile boolean running = false;
    private volatile Queue<CDNRequest> requests = new CDNQueue<>();
    private final ImageFactory factory = ImageFactory.getInstance();
    private final int CDN_PORT;
    
    private final Jargon2.Verifier verifier;
    private final Jargon2.Hasher hasher;
    private final NokuBase base;
    
    private HttpsServer httpsServer;
    private Thread t;
    
    private final byte[] icon;
    private final Properties props;
    private final String jks_path;
    private final char[] jks_pass;
    
    public CDNServer(Properties pros){
        this.props = pros;
        int dat;
        String path = null;
        try {
            dat = Integer.parseInt(pros.getProperty("noku.cdn.port"));
        } catch (NumberFormatException e){
            e.printStackTrace();
            dat = 42069;
        }
        jks_path = "res/" + pros.getProperty("noku.cdn.keystore", "cdn.keystore");
        jks_pass = pros.getProperty("noku.cdn.keystore.pass", "").toCharArray();
        hasher = jargon2Hasher()
            .type(Jargon2.Type.ARGON2d) // Data-dependent hashing
            .memoryCost(65536)  // 64MB memory cost
            .timeCost(3)        // 3 passes through memory
            .parallelism(4)     // use 4 lanes and 4 threads
            .saltLength(16)     // 16 random bytes salt
            .hashLength(32);
        verifier = jargon2Verifier();
        
        CDN_PORT = dat;
        
        base = new NokuBase(pros);
        base.connect();
        NokuResult res = base.query(
        "CREATE TABLE IF NOT EXISTS `noku`.`cdn` ( \n" +
        "    `id` INT NOT NULL AUTO_INCREMENT ,\n" +
        "    `uid` INT NOT NULL ,\n" +
        "    `data` LONGTEXT NOT NULL ,\n" +
        "    `hash` VARCHAR(64) NOT NULL ,\n" +
        "    `mime_type` TEXT NOT NULL ,\n" +
        "    `creation_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ,\n" +
        "    PRIMARY KEY (`id`)\n" +
        ") ENGINE = InnoDB;");
        if(res.isSuccessful()){
            System.out.println("Database successfully created!");
        }
        
        byte[] i;
        try {
            i = Files.readAllBytes(new File("res/icon.png").toPath());
        } catch (Exception e){
            e.printStackTrace();
            i = new byte[0];
        }
        icon = i;
    }
    
    public synchronized void addRequest(CDNRequest request){
        requests.add(request);
    }
    
    public synchronized void start(){
        running = true;
        t = new Thread(this);
        t.start();
    }
    
    public synchronized void stop(){
        running = false;
    }
    
    public void run(){
        try {
            // Set up the socket address
            InetSocketAddress address = new InetSocketAddress(InetAddress.getByName("localhost"), CDN_PORT);
        
            // Initialise the HTTPS server
            httpsServer = HttpsServer.create(address, 0);
            SSLContext sslContext = SSLContext.getInstance("TLS");
        
            // Initialise the keystore
            KeyStore ks = KeyStore.getInstance("JKS");
            FileInputStream fis = new FileInputStream(jks_path);
            ks.load(fis, jks_pass);
        
            // Set up the key manager factory
            KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
            kmf.init(ks, jks_pass);
        
            // Set up the trust manager factory
            TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
            tmf.init(ks);
        
            // Set up the HTTPS context and parameters
            sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
            httpsServer.setHttpsConfigurator(new HttpsConfigurator(sslContext) {
                public void configure(HttpsParameters params) {
                    try {
                        // Initialise the SSL context
                        SSLContext c = SSLContext.getDefault();
                        SSLEngine engine = c.createSSLEngine();
                        params.setNeedClientAuth(false);
                        params.setCipherSuites(engine.getEnabledCipherSuites());
                        params.setProtocols(engine.getEnabledProtocols());
                    
                        // Get the default parameters
                        SSLParameters defaultSSLParameters = c.getDefaultSSLParameters();
                        params.setSSLParameters(defaultSSLParameters);
                    } catch (Exception ex) {
                        System.out.println("Failed to create HTTPS port");
                    }
                }
            });
            httpsServer.createContext("/", new NokuHandler(this::addRequest));
            
            httpsServer.setExecutor(new ThreadPoolExecutor(4, 8, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(100)));
            httpsServer.start();
    
            System.out.println("Looking for requests.");
            while(running){
                loop();
            }
            
            //Running has been set to false, clean up.
            httpsServer.stop(0);
            t.join();
        } catch (Exception e) {
            e.printStackTrace();
            //stop();
            //start();
        }
    }
    
    public void loop(){
        CDNRequest request = null;
        while((request = requests.poll()) != null) {
            HttpExchange ex = request.getEx();
            String[] data = request.getUrlAsArray();
            String url = request.getUrl();
            try {
                if(data.length < 1) {
                    respondError(ex, 404);
                    ex.close();
                    continue;
                }
    
                if(url.endsWith(".css") || url.endsWith(".png")) respondCSS(ex, url);
                else if(data[0].equals("upload")) respondHTML(ex, "res/upload.nml");
                else if(data[0].equals("recent")) respondRecent(ex);
                else if(data[0].equals("noku-api")) handleAPI(ex, request, data);
                else if(data[0].contains("favicon")) respondFavicon(ex);
                else if(data[0].equals("")) respondIndex(ex);
                else if(data[0].equals("post")) respondPOST(ex, request);
                else respondContent(ex, data);
            } catch (Exception e){
                e.printStackTrace();
                System.out.println("Fine, I didn't wanna send content to you anyway.");
            }
    
            // Finished with request, cleaning up
            try {
                request.close();
            } catch (Exception e){
                e.printStackTrace();
                System.out.println("Fine stay open for all I care.");
            }
            System.out.println("Request dir: " + request.getUrl());
            if(requests.size() == 0) System.out.println("Looking for requests.");
        }
        
        //Hard work has been done, time for rest.
        try {
            Thread.sleep(1000);
        } catch (Exception e){
            System.out.println("I Guess No Sleep For Me.");
        }
    }
    
    private void respondHTML(HttpExchange ex, String file) throws Exception{
        byte[] resp = HTMLProcessor.readFile(file);
        ex.getResponseHeaders().add("Pragma", "public");
        ex.getResponseHeaders().add("Content-Type", "text/html");
        ex.sendResponseHeaders(200, resp.length);
        OutputStream os = ex.getResponseBody();
        os.write(resp);
    }
    private void respondHTML(HttpExchange ex, String file, String[] args, String[] values) throws Exception{
        byte[] fin = HTMLProcessor.processHTMLFile(file, args, values);
        
        ex.getResponseHeaders().add("Pragma", "public");
        ex.getResponseHeaders().add("Content-Type", "text/html");
        ex.sendResponseHeaders(200, fin.length);
        OutputStream os = ex.getResponseBody();
        os.write(fin);
    }
    
    private void respondPOST(HttpExchange ex, CDNRequest request) throws Exception{
        if(!request.method.equalsIgnoreCase("POST")) {
            request.close();
            return;
        }
        CDNRequest.MultiPart uid = request.getData("uid");
        CDNRequest.MultiPart pass = request.getData("pass");
        CDNRequest.MultiPart original = request.getData("original");
    
        if(pass == null || !checkPassword(pass.bytes)){
            respondError(ex, 403);
            return;
        }
    
        CDNRequest.MultiPart file = request.getData("file");
        if(uid == null || file == null || file.bytes == null) {
            respondHTML(ex, "res/error.nml", l("message"), l("Data not filled out."));
            request.close();
            return;
        }
        
        //if(original != null) System.out.println("Original: " + original.value);
    
        byte[] fileData = file.bytes;
        String contentType = file.contentType;
        byte[] fin = null;
        if(contentType.contains("image") && (original == null || original.value.equals("off"))) {
            contentType = "image/jpg";
            fin = factory.toJpgByteArray(fileData, 0.7F);
        }
        if(fin == null) fin = fileData;
    
        String b64 = Base64.encodeToString(factory.compress(fin));
        byte[] hash = SHA256.hash(fin);
        String hex = SHA256.bytesToHex(hash);
    
        if(base.insert("cdn",
        ColumnValuePair.from("uid", uid.value + ""),
        ColumnValuePair.from("data", b64),
        ColumnValuePair.from("hash", hex),
        ColumnValuePair.from("mime_type", contentType)
        )) {
            ex.getResponseHeaders().add("Location", "/" + hex);
            ex.sendResponseHeaders(302, 0);
            OutputStream os = ex.getResponseBody();
            os.flush();
            os.close();
        } else {
            respondHTML(ex, "res/error.nml");
        }
    }
    private void respondContent(HttpExchange ex, String[] data) throws Exception{
        Condition con = new Condition(data[0].length() == 64 ? "hash" : "id", data[0]);
        ResultSet res = base.queryRaw("SELECT data, mime_type FROM cdn WHERE " + con.buildPrepared(), con.preparedValues());
        if(res.next()) respondContent(res, ex);
        else respondError(ex, 404);
    }
    private void respondContent(ResultSet set, HttpExchange ex) throws Exception {
        ResultSetMetaData md = set.getMetaData();
        Instant instant = Instant.now().plusSeconds(86400);
        
        ex.getResponseHeaders().add("Pragma", "public");
        ex.getResponseHeaders().add("Cache-Control", "max-age=86400");
        ex.getResponseHeaders().add("Expires", instant.toString());
        ex.getResponseHeaders().add("Content-Type", set.getString("mime_type"));
        ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        BufferedReader reader = new BufferedReader(set.getCharacterStream("data"));
    
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        String line = "";
        while((line = reader.readLine()) != null){
            baos.write(line.getBytes());
        }
        
        byte[] data = factory.fromBase64(baos.toByteArray());
        ex.sendResponseHeaders(200, data.length);
        OutputStream os = ex.getResponseBody();
        os.write(data);
    }
    private void respondCSS(HttpExchange ex, String url) throws IOException{
        byte[] index = HTMLProcessor.readFile("res/" + url);
    
        Instant instant = Instant.now().plusSeconds(5);
        ex.getResponseHeaders().add("Pragma", "public");
        ex.getResponseHeaders().add("Cache-Control", "max-age=5");
        ex.getResponseHeaders().add("Expires", instant.toString());
        ex.getResponseHeaders().add("Content-Type", "text/css");
        ex.sendResponseHeaders(200, index.length);
        OutputStream os = ex.getResponseBody();
        os.write(index);
    }
    
    private void respondError(HttpExchange ex, int error) throws IOException{
        ex.sendResponseHeaders(error, 0);
        ex.close();
    }
    private void respondFavicon(HttpExchange ex) throws IOException{
        Instant instant = Instant.now().plusSeconds(86400);
        ex.getResponseHeaders().add("Pragma", "public");
        ex.getResponseHeaders().add("Cache-Control", "max-age=86400");
        ex.getResponseHeaders().add("Expires", instant.toString());
        ex.getResponseHeaders().add("Content-Type", "image/png");
        ex.sendResponseHeaders(200, icon.length);
        OutputStream os = ex.getResponseBody();
        os.write(icon);
    }
    private void respondIndex(HttpExchange ex) throws IOException{
        byte[] index = HTMLProcessor.readFile("res/index.nml");
        
        Instant instant = Instant.now().plusSeconds(5);
        ex.getResponseHeaders().add("Pragma", "public");
        ex.getResponseHeaders().add("Cache-Control", "max-age=5");
        ex.getResponseHeaders().add("Expires", instant.toString());
        ex.getResponseHeaders().add("Content-Type", "text/html");
        ex.sendResponseHeaders(200, index.length);
        OutputStream os = ex.getResponseBody();
        os.write(index);
    }
    private void respondRecent(HttpExchange ex) throws Exception{
        ArrayList<String> cols = new ArrayList<>(Arrays.asList(l("#", "UID", "Hash", "MIME Type", "Creation Time")));
        ResultSet set = base.queryRaw("SELECT id, uid, hash, mime_type, creation_time FROM `noku`.`cdn` ORDER BY id DESC LIMIT 10");
        while(set.next()){
            cols.add(set.getString("id"));
            cols.add(set.getString("uid"));
            String hash = set.getString("hash");
            cols.add("<a href=\"/" + hash + "\">" + hash + "</a>");
            cols.add(set.getString("mime_type"));
            cols.add(set.getString("creation_time"));
        }
        
        byte[] index = HTMLProcessor.readFile("res/recent.nml");
        String table = HTMLProcessor.createTable("table table-sm", 5, cols.toArray(new String[0]));
        index = HTMLProcessor.processHTMLBytes(index, l("table"), l(table));
        
        Instant instant = Instant.now().plusSeconds(5);
        ex.getResponseHeaders().add("Pragma", "public");
        ex.getResponseHeaders().add("Cache-Control", "max-age=5");
        ex.getResponseHeaders().add("Expires", instant.toString());
        ex.getResponseHeaders().add("Content-Type", "text/html");
        ex.sendResponseHeaders(200, index.length);
        OutputStream os = ex.getResponseBody();
        os.write(index);
    }
    
    private byte[] insertIntoDB(HttpExchange ex, byte[] fin, int uid, String contentType) throws Exception{
        String b64 = Base64.encodeToString(factory.compress(fin));
        byte[] hash = SHA256.hash(fin);
        String hex = "{\"hash\":\"" + SHA256.bytesToHex(hash) + "\"}";
        byte[] data = hex.getBytes(StandardCharsets.UTF_8);
    
        if(base.insert("cdn",
        ColumnValuePair.from("uid", uid + ""),
        ColumnValuePair.from("data", b64),
        ColumnValuePair.from("hash", hex),
        ColumnValuePair.from("mime_type", contentType)
        )) {
            ex.getResponseHeaders().add("Content-Type", "text/json");
            ex.sendResponseHeaders(200, data.length);
            OutputStream os = ex.getResponseBody();
            os.write(data);
            os.flush();
            os.close();
        } else {
            ex.getResponseHeaders().add("Content-Type", "text/text");
            ex.sendResponseHeaders(400, 0);
            OutputStream os = ex.getResponseBody();
            os.close();
        }
        
        return hash;
    }
    private void handleAPI(HttpExchange ex, CDNRequest request, final String[] data) throws Exception{
        if(request.type != CDNRequest.RequestType.JSON) return;
    
        byte[] resp = null;
        
        Map<String, Object> json = request.getJson();
        switch(data[1]){
            case "latestfromuser":{
                int uid = (Integer) json.get("uid");
                int sec = (Integer) json.get("seconds");
                int lim = (Integer) json.get("limit");
                int[] dat = latestfromuser(uid, sec, lim);
        
                resp = intArrayToJsonString(dat).getBytes(StandardCharsets.UTF_8);
                break;
            }
            case "getbyhash":{
                String hash = (String) json.get("hash");
                respondContent(ex, l(hash));
                ex.close();
                return;
            }
            case "postcontent":{
                int uid = (Integer) json.get("uid");
                String encoded = (String) json.get("data");
                String mime = (String) json.get("mime_type");
                String comp = (String) json.get("compression");
                byte[] auth = Base64.decode((String)json.get("mime_type"));
                byte[] b64 = encoded.getBytes(StandardCharsets.UTF_8);
                byte[] raw = Base64.decode(b64); b64 = null; System.gc();
                byte[] decompressed;
                switch(comp){
                    case "gzip":
                        decompressed = GZCompressor.getInstance().decompress(raw);
                        break;
                    case "zip":
                        decompressed = ZipCompressor.getInstance().decompress(raw);
                        break;
                    default:
                        decompressed = raw;
                } raw = null; System.gc();
                
                if(checkPassword(auth)){
                    insertIntoDB(ex, decompressed, uid, mime);
                } else {
                    respondError(ex, 403);
                }
                
                ex.close();
                return;
            }
        }
        
        if(resp == null){
            ex.getResponseHeaders().add("Content-Type", "text/text");
            ex.sendResponseHeaders(400, 0);
            OutputStream os = ex.getResponseBody();
            os.close();
        } else {
            ex.getResponseHeaders().add("Content-Type", "text/json");
            ex.sendResponseHeaders(200, resp.length);
            OutputStream os = ex.getResponseBody();
            os.write(resp);
            os.flush();
            os.close();
        }
        ex.close();
    }
    
    private int[] latestfromuser(int uid, int seconds, int limit) throws SQLException{
        ArrayList<Integer> ids = new ArrayList<>();
        
        ConditionSet set = new ConditionSet(new Condition[]{
            new Condition("uid", uid),
            new Condition("creation_time", "DATE_SUB(NOW(), INTERVAL " + seconds + " SECOND)", ConditionType.GREATER_EQUAL)
        }, ConditionSet.Operator.AND);
        
        ResultSet res = base.queryRaw("SELECT id FROM cdn WHERE " + set.buildPrepared() + " ORDER BY creation_time ASC LIMIT " + limit, set.preparedValues());
        while(res.next()){
            ids.add(res.getInt("id"));
        }
        
        Integer[] ret = ids.toArray(new Integer[0]);
        int[] fin = new int[ret.length];
        System.arraycopy(ret, 0, fin, 0, fin.length);
        return fin;
    }
    
    private boolean checkPassword(byte[] password){
        System.out.println(SHA256.bytesToHex(password));
        System.out.println(hasher.password(password).encodedHash());
        byte[] hash;
        try{
            hash = Files.readAllBytes(new File("res/" + props.getProperty("noku.cdn.auth")).toPath());
            System.out.println(new String(hash, StandardCharsets.UTF_8));
        } catch (Exception e){
            e.printStackTrace();
            return false;
        }
        String encHash = new String(hash, StandardCharsets.UTF_8);
        return verifier.hash(encHash).password(password).verifyEncoded();
    }
    
    public static String[] l(String... items){
        return items;
    }
}
