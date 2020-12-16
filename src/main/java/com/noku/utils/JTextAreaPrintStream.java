package com.noku.utils;

import javax.swing.*;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

public final class JTextAreaPrintStream extends PrintStream {
    public JTextAreaPrintStream(JTextArea area){
        super(new JTextAreaOutputStream(area));
    }
}
