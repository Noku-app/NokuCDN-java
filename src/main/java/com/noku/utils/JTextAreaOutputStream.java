package com.noku.utils;

import javax.swing.*;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;

public class JTextAreaOutputStream extends OutputStream {
    private final JTextArea area;
    
    /* package */ JTextAreaOutputStream(JTextArea area){
        this.area = area;
    }
    
    public void write(int i) throws IOException {
        char c = (char) i;
        area.append(c + "");
    }
}
