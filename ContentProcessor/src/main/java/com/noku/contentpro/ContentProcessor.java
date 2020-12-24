package com.noku.contentpro;

import java.util.PriorityQueue;

public class ContentProcessor {
    public final PriorityQueue<ProcessRequest> queue = new PriorityQueue<>();
    private final Thread t;
    private volatile boolean runTillEmpty;
    private volatile boolean running;
    private final ProcessHandler handler;
    private final ProcessScheduler scheduler;
    
    public ContentProcessor(){
        t = new Thread(this::run);
        handler = new ProcessHandler();
        scheduler = new ProcessScheduler(queue);
    }
    
    public void startAsService(){
        runTillEmpty = false;
        running = true;
        
        t.start();
    }
    
    public void runTillDone(){
        runTillEmpty = true;
        running = true;
        
        t.start();
    }
    
    public ProcessScheduler getScheduler(){
        return scheduler;
    }
    
    public synchronized void stopWhenEmpty(){
        runTillEmpty = true;
    }
    
    public synchronized void stopWhenDone(){
        running = false;
    }
    
    public void run(){
        while(running) {
            if(!queue.isEmpty()) handler.handle(queue.poll());
            if(queue.isEmpty() && runTillEmpty) running = false;
        }
        
        try{
            t.join();
        } catch (Exception e){
            e.printStackTrace();
        }
    }
}
