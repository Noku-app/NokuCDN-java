package com.noku.cdn.javase;


import com.noku.base.Condition;
import com.noku.base.javase.NokuBase;
import com.noku.base.javase.NokuResult;
import com.noku.utils.Base64;
import com.noku.utils.ImageFactory;
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
import java.util.Properties;
import java.util.Queue;
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
        "    `hash` TINYTEXT NOT NULL ,\n" +
        "    `link` TEXT NOT NULL ,\n" +
        "    `creation_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ,\n" +
        "    PRIMARY KEY (`id`)\n" +
        ") ENGINE = InnoDB;");
        if(res.isSuccessful()){
            System.out.println("Database successfully created!");
        }
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
            if(data.length < 1) {
                respond404(ex);
                continue;
            }
    
            if(data[0].equals("upload")){
                respondForm(ex, "res/upload.html");
                continue;
            }
    
            if(data[0].equals("post")){
                
                respondForm(ex, "res/done.html");
                continue;
            }
            
            ResultSet set = null;
    
            long b = System.currentTimeMillis();
            System.out.println("Time: 0");
            //NokuResult res = base.select("cdn", new Condition("id", data[0]), "data");
            Condition id = new Condition("id", data[0]);
            System.out.println("Time: " + (System.currentTimeMillis() - b));
            ResultSet res = base.queryRaw("SELECT data, link FROM cdn WHERE " + id.buildPrepared(), id.preparedValues());
            System.out.println("Time: " + (System.currentTimeMillis() - b));
            if(res.next()) respondImage(res, ex, b);
            else respond404(ex);
            System.out.println("Time: " + (System.currentTimeMillis() - b));
    
    
            // Finished with request, cleaning up
            request.close();
            System.out.println("Request dir: " + request.getUrl());
            if(requests.size() == 0) System.out.println("Looking for requests.");
        }
        
        //Hard work has been done, time for rest.
        Thread.sleep(1000);
    }
    
    private void respondForm(HttpExchange ex, String file) throws Exception{
        byte[] resp = Files.readAllBytes(new File(file).toPath());
        ex.getResponseHeaders().add("Pragma", "public");
        ex.getResponseHeaders().add("Content-Type", "text/html");
        ex.sendResponseHeaders(200, resp.length);
        OutputStream os = ex.getResponseBody();
        os.write(resp);
        os.close();
        ex.close();
    }
    
    private void respondImage(ResultSet set, HttpExchange ex, long b) throws Exception {
        System.out.println("Time: " + (System.currentTimeMillis() - b));
        ResultSetMetaData md = set.getMetaData();
        Instant instant = Instant.now().plusSeconds(86400);
        System.out.println("Time: " + (System.currentTimeMillis() - b));
        
        ex.getResponseHeaders().add("Pragma", "public");
        ex.getResponseHeaders().add("Cache-Control", "max-age=86400");
        ex.getResponseHeaders().add("Expires", instant.toString());
        ex.getResponseHeaders().add("Content-Type", set.getString("link"));
        BufferedReader reader = new BufferedReader(set.getCharacterStream("data"));
        System.out.println("Time: " + (System.currentTimeMillis() - b));
    
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
    
    private void respond404(HttpExchange ex) throws IOException{
        ex.sendResponseHeaders(404, 0);
        ex.close();
    }
}
