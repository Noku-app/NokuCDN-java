package com.noku.cdn.javase;

import com.noku.utils.JTextAreaPrintStream;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;

public class Main {
    private static boolean gui = true;
    private CDNServer server;
    
    public Main(Properties props) throws Exception{
        if(gui){
            Font monospace = Font.createFont(Font.TRUETYPE_FONT, new File("res/monospace.ttf")).deriveFont(12F);
            
            JFrame f = new JFrame("NokuCDN");
            f.setIconImage(ImageIO.read(new File("res/icon.png")));
            
            JPanel panel = new JPanel();
            panel.setLayout(new GridBagLayout());
            
            JTextArea output = new JTextArea();
            output.setEditable(false);
            output.setFont(monospace);
            System.setOut(new JTextAreaPrintStream(output));
            
            GridBagConstraints gbc_out = new GridBagConstraints();
            gbc_out.gridwidth = 2;
            gbc_out.fill = GridBagConstraints.BOTH;
            gbc_out.weightx = 1;
            gbc_out.weighty = 1;
            
            panel.add(new JScrollPane(output), gbc_out);
    
            JButton stop = new JButton("Stop");
            stop.addActionListener((e) -> server.stop());
    
            GridBagConstraints gbc_stop = new GridBagConstraints();
            gbc_stop.gridy = 1;
            gbc_stop.weightx = 1;
            gbc_stop.fill = GridBagConstraints.BOTH;
    
            panel.add(stop, gbc_stop);
    
            JButton clear = new JButton("Clear");
            clear.addActionListener((e) -> output.setText(""));
    
            GridBagConstraints gbc_clear = new GridBagConstraints();
            gbc_clear.gridy = 1;
            gbc_clear.gridx = 1;
            gbc_clear.weightx = 0;
            gbc_clear.fill = GridBagConstraints.BOTH;
    
            panel.add(clear, gbc_clear);
            
            Dimension s = new Dimension(600, 400);
            panel.setMinimumSize(s);
            panel.setMaximumSize(s);
            panel.setPreferredSize(s);
            f.setContentPane(panel);
            f.pack();
            f.setLocationRelativeTo(null);
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.setVisible(true);
        }
        server = new CDNServer(props);
        server.start();
    }
    
    public static void main(String[] args) throws Exception{
        String filename = "res/db.properties";
    
        if(args.length == 0) gui = true;
        if(args.length > 0 && args[0].equalsIgnoreCase("-nogui")) gui = false;
        if(args.length > 1) filename = args[1];
    
        Properties props = new Properties();
        props.load(new FileInputStream(new File(filename)));
    
        new Main(props);
    }
}
