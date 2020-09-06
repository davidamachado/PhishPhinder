package phphgui;

import javax.swing.*;
import java.awt.*;
import static java.awt.BorderLayout.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import javax.mail.*;
import static javax.swing.ScrollPaneConstants.*;
import static phphgui.Settings.loginPassword;
import static phphgui.Settings.loginUsername;
import static phphgui.Settings.println;
import static phphgui.Settings.threadcount;
import static phphgui.SimpleSSLMail.save;

/**
 * Author: Paul Hendrick
 *
 * Purpose: Graphical for the user to interact with that displays the details of each email being parsed
 *
 * Updates:
 *
 **/

public class PhPhGui extends JFrame implements ActionListener {
    
    private final int THREAD_COUNT = threadcount();
    
    private final String[] COLUMN_HEADERS = {"Feature Name:", "Output:"};
    private final String[][] LOAD_DATA = {
                                    {"",""},
                                    {"",""}
                                    };
    //class variables
    PhPh ph = null;
    private ArrayList<PhPhMessage> parsedEmails = new ArrayList();
    private JTable table = new JTable(LOAD_DATA, COLUMN_HEADERS);
    private JScrollPane scrollPane = new JScrollPane(table, VERTICAL_SCROLLBAR_ALWAYS, HORIZONTAL_SCROLLBAR_NEVER);
    private int index = 1;
    //nested class type ControlMenu
    private final ControlMenu controlMenu;
    //nested class type OutputMenu
    private final OutputMenu outputMenu;
    
    //constructor
    public PhPhGui() {

        //JFrame attributes
        this.setSize(850, 800);
        this.setResizable(true);
        this.setLocationRelativeTo(null);
        this.setTitle("Phish Phinder Tester");
        this.setLayout(new BorderLayout());
        this.setDefaultCloseOperation(EXIT_ON_CLOSE);
        
        controlMenu = new ControlMenu(this);
        outputMenu = new OutputMenu(this);

        //create JPanel
        JPanel menus = new JPanel();
        menus.setLayout(new GridLayout(1,2));
        menus.add(controlMenu);
        menus.add(outputMenu);

        //add JPanel to this JFrame
        this.add(menus, PAGE_START);
        //add scrollPane to this JFrame
        this.add(scrollPane);
    }

    public static void main(String[] args) {
        PhPhGui gui = new PhPhGui();
        gui.setVisible(true);
    }
    
    //Handles the actions and goes to the required section of code
    @Override
    public void actionPerformed(ActionEvent e) {
        
        Object source = e.getSource();
        
        if(controlMenu.connect.equals(source))
            connect();
        if(controlMenu.download.equals(source))
            download();
        if(controlMenu.load.equals(source))
            load();
        if(outputMenu.previous.equals(source))
            previous();
        if(outputMenu.next.equals(source))
            next();
    }
    
    //Controls the connect button
    void connect() {
        try {
            ph = new PhPh(THREAD_COUNT, 
                    SimpleSSLMail.connect(controlMenu.login[0].getText(),
                                          controlMenu.login[1].getText()));
            
        } catch (FileNotFoundException | MessagingException ex) {
            fail(ex);
        }
        index = 1;
        display(1);
    }
    
    //Controls the download button, also uses load button to load download.
    private void download() {
        save(SimpleSSLMail.connect(controlMenu.login[0].getText(),
                                                 controlMenu.login[1].getText()));
        load();
    }
    
    //Controls the load button
    private void load() {
        
        try {
            ph = new PhPh(THREAD_COUNT, SimpleSSLMail.load());
        } catch (FileNotFoundException | MessagingException ex) {
            fail(ex);
        }
        index = 1;
        display(1);
    }
    
    //controls the previous button
    private void previous() {
        if(index > 1)
            index--;
        display(index);
    }
    
    //controls the next button
    private void next() {
        if(index < parsedEmails.size())
            index++;
        display(index);
    }
    
    //displays index using the output window and table
    private void display(int i) {
        
        PhPhMessage messageData = null;
        HashMap<String, Integer> messageMap = null;
        
        //Load the E-mails from ph
        if(ph != null){
            parsedEmails = ph.getParsedEmails();
        } else {
            println("Displaying index: N/A - Not Loaded - index = " + index);
        }
        if(parsedEmails.isEmpty()){
            println("Displaying index: N/A - No Emails - index = " + index);
            return;
        }
        //Load the message to be displayed
        messageData = parsedEmails.get(index - 1);
        messageMap = messageData.messageMap;

        
        try {
            
            println("Displaying Index: " + index);
            
            String output = "";
            
            Address recipiants[] = messageData.getAllRecipients();
            for (Address recipiant : recipiants) {
                output += recipiant.toString() + ", ";
            }
            
            outputMenu.outputTo.setText(output);
            
            if(messageData.getSender() != null)
                outputMenu.outputFrom.setText(messageData.getSender().toString());
            else
                outputMenu.outputFrom.setText("N/A");
            
            outputMenu.outputSubject.setText(messageData.getSubject());
            outputMenu.outputDate.setText(messageData.getSentDate().toString());
            outputMenu.index.setText(index + " of " + parsedEmails.size());
        } catch (MessagingException ex) {
            fail(ex);
        }
        
        String[][] data = new String[messageMap.size()][2];
        
        Object[] keys = messageMap.keySet().toArray();
        
        
        for(int x = 0; x < messageMap.size(); x++) {
            data[x][0] = keys[x].toString();
            data[x][1] = messageMap.get(keys[x].toString()).toString();
        }
        
        this.remove(scrollPane);
        table = new JTable(data, COLUMN_HEADERS);
        scrollPane = new JScrollPane(table, VERTICAL_SCROLLBAR_ALWAYS, HORIZONTAL_SCROLLBAR_NEVER);
        this.add(scrollPane);
    }
    
    //Private class that contains all the control menu elements
    private class ControlMenu extends JPanel{
        
        JLabel[] labels = new JLabel[2];
        JTextField[] login = new JTextField[2];
        JPasswordField loginPw = new JPasswordField();
        JButton connect = new JButton("CONNECT TO EMAIL SERVER");
        JButton download = new JButton("DOWNLOAD");
        JButton load = new JButton("LOAD");

        //ControlMenu constructor
        public ControlMenu(PhPhGui parent) {

            labels[0] = new JLabel("Username: ");
            labels[1] = new JLabel("Password:  ");

            login[0] = new JTextField(20);
            login[0].setText(loginUsername());
            login[1] = new JTextField(20);
            login[1].setText(loginPassword());


            Dimension btnSize = new Dimension(5, 2);

            connect.setMaximumSize(btnSize);
            download.setMaximumSize(btnSize);
            load.setMaximumSize(btnSize);

            JPanel username = new JPanel();
            username.setLayout(new FlowLayout());
            username.add(labels[0]);
            username.add(login[0]);
            
            JPanel password = new JPanel();
            password.setLayout(new FlowLayout());
            password.add(labels[1]);
            password.add(login[1]);
            
            this.setLayout(new GridLayout(5,1));
            this.add(username);
            this.add(password);
            this.add(connect);
            this.add(download);
            this.add(load);
            
            connect.addActionListener(parent);
            download.addActionListener(parent);
            load.addActionListener(parent);
        }
    }
    
    //Private class that contains all the output menu elements
    private class OutputMenu extends JPanel {
        
        JLabel index = new JLabel("X of X");
        JButton next = new JButton("NEXT");
        JButton previous = new JButton("PREVIOUS");
        
        JLabel[] labels = new JLabel[4];
        JTextField outputTo = new JTextField(30);
        JTextField outputFrom = new JTextField(30);
        JTextField outputSubject = new JTextField(30);
        JTextField outputDate = new JTextField(30);

        //OutputMenu constructor
        public OutputMenu(PhPhGui parent){
            
            this.setLayout(new GridLayout(5,1));
            
            JPanel controls = new JPanel();
            controls.setLayout(new GridLayout(1,3));
            controls.add(previous);
            controls.add(index);
            controls.add(next);
            this.add(controls);
            
            labels[0] = new JLabel("        To:   ");
            labels[1] = new JLabel("    From:   ");
            labels[2] = new JLabel("Subject:   ");
            labels[3] = new JLabel("     Date:   ");
            
            JPanel[] output = new JPanel[4];
            
            for(int x = 0; x < output.length; x++){
                output[x] = new JPanel();
                output[x].setLayout(new FlowLayout());
                output[x].add(labels[x]);
                switch(x){
                    case 0:
                        output[x].add(outputTo);
                        break;
                    case 1:
                        output[x].add(outputFrom);
                        break;
                    case 2:
                        output[x].add(outputSubject);
                        break;
                    case 3:
                        output[x].add(outputDate);
                        break;
                }
                this.add(output[x]);
            }
            previous.addActionListener(parent);
            next.addActionListener(parent);
        }
    }
    
    //Controls what happens on fail
    private static void fail(Exception e) {
        println(e.toString());
        e.printStackTrace();
    }
}