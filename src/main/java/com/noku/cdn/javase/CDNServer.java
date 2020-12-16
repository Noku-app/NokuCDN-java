package com.noku.cdn.javase;


import com.noku.base.ColumnValuePair;
import com.noku.base.Condition;
import com.noku.base.javase.NokuBase;
import com.noku.base.javase.NokuResult;
import com.noku.hpp.HTMLProcessor;
import com.noku.utils.Base64;
import com.noku.utils.ImageFactory;
import com.noku.utils.SHA256;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;

import javax.net.ssl.*;
import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.security.KeyStore;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;


public final class CDNServer implements Runnable{
    private volatile boolean running = false;
    private volatile Queue<CDNRequest> requests = new CDNQueue<>();
    private final ImageFactory factory = ImageFactory.getInstance();
    private final int CDN_PORT;
    private HttpsServer httpsServer;
    private NokuBase base;
    private Thread t;
    
    private final byte[] icon;
    
    public CDNServer(Properties pros){
        int dat;
        try {
            dat = Integer.parseInt(pros.getProperty("noku.cdn.port"));
        } catch (NumberFormatException e){
            e.printStackTrace();
            dat = 42069;
        }
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
            char[] password = "simulator".toCharArray();
            KeyStore ks = KeyStore.getInstance("JKS");
            FileInputStream fis = new FileInputStream("res/lig.keystore");
            ks.load(fis, password);
        
            // Set up the key manager factory
            KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
            kmf.init(ks, password);
        
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
            System.out.println("Failed to create HTTPS server on port " + CDN_PORT + " of localhost");
        }
    }
    
    public void loop() throws Exception{
        CDNRequest request = null;
        while((request = requests.poll()) != null) {
            HttpExchange ex = request.getEx();
            String[] data = request.getUrlAsArray();
            String url = request.getUrl();
            if(data.length < 1) {
                respond404(ex);
                continue;
            }
    
            if(url.endsWith(".css") || url.endsWith(".png")){
                respondCSS(ex, url);
            } else if(data[0].equals("upload")){
                respondHTML(ex, "res/upload.nml");
            }  else if(data[0].equals("recent")){
                respondRecent(ex);
            } else if(data[0].contains("favicon")){
                respondFavicon(ex);
            } else if(data[0].equals("")){
                respondIndex(ex);
            } else if(data[0].equals("post")){
                if(request.method.equalsIgnoreCase("POST")){
                    CDNRequest.MultiPart uid = request.getData("uid");
                    CDNRequest.MultiPart file = request.getData("file");
                    CDNRequest.MultiPart original = request.getData("original");
                    
                    if(uid == null || file == null || file.bytes == null){
                        respondHTML(ex, "res/error.nml", l("message"), l("Data not filled out."));
                    } else {
                        if(original != null) System.out.println("Original: " + original.value);
                        
                        byte[] fileData = file.bytes;
                        String contentType = file.contentType;
                        byte[] fin = null;
                        if(contentType.contains("image") && (original == null || original.value.equals("off"))){
                            contentType = "image/jpg";
                            fin = factory.toJpgByteArray(fileData, 0.7F);
                        }
                        if(fin == null) fin = fileData;
                        
                        String b64 = Base64.encodeToString(factory.compress(fin));
                        byte[] hash = SHA256.hash(fin);
                        String hex = SHA256.bytesToHex(hash);
                        
                        if(base.insert("cdn",
                            ColumnValuePair.from("uid", uid.value),
                            ColumnValuePair.from("data", b64),
                            ColumnValuePair.from("hash", hex),
                            ColumnValuePair.from("mime_type", contentType)
                        )){
                            respondHTML(ex, "res/done.nml", l("title", "message"), l("successful!", "Yr meme posted b, here is a link: <a href=\"/" + hex + "\">MEME</a>"));
                        } else {
                            respondHTML(ex, "res/done.nml", l("title", "message"), l("failed!", "Yr meme wack b"));
                        }
                    }
                }
            } else {
                respondContent(ex, data);
            }
    
            // Finished with request, cleaning up
            request.close();
            System.out.println("Request dir: " + request.getUrl());
            if(requests.size() == 0) System.out.println("Looking for requests.");
        }
        
        //Hard work has been done, time for rest.
        Thread.sleep(1000);
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
    
    private void respondContent(HttpExchange ex, String[] data) throws Exception{
        Condition con = new Condition(data[0].length() == 64 ? "hash" : "id", data[0]);
        ResultSet res = base.queryRaw("SELECT data, mime_type FROM cdn WHERE " + con.buildPrepared(), con.preparedValues());
        if(res.next()) respondContent(res, ex);
        else respond404(ex);
    }
    private void respondContent(ResultSet set, HttpExchange ex) throws Exception {
        ResultSetMetaData md = set.getMetaData();
        Instant instant = Instant.now().plusSeconds(86400);
        
        ex.getResponseHeaders().add("Pragma", "public");
        ex.getResponseHeaders().add("Cache-Control", "max-age=86400");
        ex.getResponseHeaders().add("Expires", instant.toString());
        ex.getResponseHeaders().add("Content-Type", set.getString("mime_type"));
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
    
    private void respond404(HttpExchange ex) throws IOException{
        ex.sendResponseHeaders(404, 0);
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
    
    public static String[] l(String... items){
        return items;
    }
}
