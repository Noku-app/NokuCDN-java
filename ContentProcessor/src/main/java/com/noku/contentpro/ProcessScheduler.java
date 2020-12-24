package com.noku.contentpro;

import ws.schild.jave.Encoder;

import java.io.InputStream;
import java.util.PriorityQueue;

public class ProcessScheduler {
    private PriorityQueue<ProcessRequest> queue;
    /* package */ ProcessScheduler(PriorityQueue<ProcessRequest> queue){
        this.queue = queue;
    }
    
    public void resizeVideo(InputStream input, int width, int height){
    
    }
}
