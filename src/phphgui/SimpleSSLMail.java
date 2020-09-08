package phphgui;

import java.awt.Frame;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import static java.lang.System.getProperty;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Properties;
import javax.mail.*;
import static javax.mail.Folder.READ_ONLY;
import static javax.mail.Session.getDefaultInstance;
import javax.mail.internet.MimeMessage;
import javax.swing.JOptionPane;
import static javax.swing.JOptionPane.showMessageDialog;
import static phphgui.Settings.debug;
import static phphgui.Settings.emailServer;
import static phphgui.Settings.loginPassword;
import static phphgui.Settings.loginUsername;
import static phphgui.Settings.println;

/**
 * Author: Paul Hendrick
 * Date: 11/21/2019
 * Purpose: 0.9 - Modified base code to retrieve instead of send E-mails and
 *                changed to imaps
 *          1.0 - Loads E-mails from a server
 *          1.1 - Changed output to MimeMessage
 *          1.2 - Added ability for file input and output to aid troubleshooting
 *          1.3 - Added constructor with variables
 *
 * Requirements: Requires Javax.mail.jar found at https://javaee.github.io/javamail/
 *
 *               Requires login information stored in Settings.txt
 *
 *               For GMail, requires lowering of security for unverified publishers
 *                  which can be found at https://myaccount.google.com/lesssecureapps
 *
 * References: (19 May 2013). SimpleSSLMail.java. Retrieved from
 *                  https://github.com/SravanthiSinha/onlinejobportal/blob/master/advancd/src/SimpleSSLMail.java
 **/

public class SimpleSSLMail {
    
    private static final boolean DEBUG = debug();
    
    private static final String SMTP_HOST_NAME = emailServer();
    private static final int SMTP_HOST_PORT = 993;
    
    private static final String SMTP_AUTH_USER = loginUsername();
    private static final String SMTP_AUTH_PWD  = loginPassword();
    
    //Connects to the provided server and downloads all E-Mails w/ default info

    /**
     *
     * @return
     */
    public static MimeMessage[] connect() {
        
        String username = SMTP_AUTH_USER;
        String password = SMTP_AUTH_PWD;
        
        return connect(username, password);
    }
    
    //Connects to the provided server and downloads all E-Mails w/ provided info

    /**
     *
     * @param username
     * @param password
     * @return
     */
    public static MimeMessage[] connect(String username, String password) {
        
        Properties props = new Properties();
        
        //Build the Session
        props.put("mail.store.protocol", "imaps");
        props.put("mail.imaps.host", SMTP_HOST_NAME);
        props.put("mail.imaps.auth", "true");
        props.put("mail.imaps.quitwait", "false");
        Session mailSession = getDefaultInstance(props);
        mailSession.setDebug(DEBUG);
        
        Store store;
        Folder folder;
        Message[] messages;
        
        
        try { 
            store = mailSession.getStore("imaps"); 
        } catch (NoSuchProviderException e) {
            return fail(e);
        }
        
        //Connect to the server and set up the folder
        try {
            store.connect(SMTP_HOST_NAME, SMTP_HOST_PORT, username, password);
            folder = store.getFolder("INBOX");
        } catch (MessagingException e) {
            showMessageDialog(new Frame(), "Could not connect!");
            return fail(e);
        }
        //Print the folder information if required
        try {
            if(DEBUG){
                println(Integer.toString(folder.getMessageCount()));
                println(folder.getFullName());
            }
            folder.open(READ_ONLY);
        } catch (MessagingException e) {
            return fail(e);
        }
        
        
        //Download all the messages
        try {
            messages = folder.getMessages();
        } catch (MessagingException e) {
            return fail(e);
        }
        
        MimeMessage[] mime = new MimeMessage[messages.length];
        
        for(int x = 0; x < mime.length; x++)
            mime[x] = (MimeMessage) messages[x];
        
        //This will output the emails, showing them downloaded
        try{
            if(DEBUG)
                for (MimeMessage mime1 : mime) {
                println(mime1.getSubject());
            }
        } catch (MessagingException e) {
            return fail(e);
        }
        return mime;
    }
    
    //Saves all the provided 

    /**
     *
     * @param input
     * @return
     */
    public static int save(MimeMessage[] input){
        try {
            for(int x = 0; x < input.length; x++){
                FileOutputStream output = new FileOutputStream(new File(("Email" + x + ".eml")));
                input[x].writeTo(output);
                println("Email" + x + ".eml output successfully!");
            }
        } catch (IOException | MessagingException e) {
            fail(e);
            return 1;
        }
        return 0;
    }
    
    /**
     *
     * @return
     */
    public static MimeMessage[] load(){
        
        File[] files = new File(getProperty("user.dir")).listFiles();
        MimeMessage[] output;
        ArrayList<MimeMessage> array = new ArrayList<>();
        Session session = getDefaultInstance(new Properties(), null);
        
        for (File file : files) {
            String name = file.getName();
            println("Detected Data:   " + name);
            //Has a period, we are in buisness
            if (name.contains(".")) {
                String parsed = name.substring(name.length()-3);
                if (parsed.compareToIgnoreCase("eml") == 0) {
                    println("Detected E-Mail: " + name);
                    try {
                        FileInputStream fileInput = new FileInputStream(file);
                        array.add(new MimeMessage(session, fileInput));
                        println("Loaded E-Mail:   " + name);
                    }catch (FileNotFoundException | MessagingException e) {
                        fail(e);
                    }
                } else {
                    println("Ignoring File:   " + name);
                }
            } else {
                println("Ignoring Data:   " + file.getName());
            }
        }
        
        
        output = new MimeMessage[array.size()];
        for(int x = 0; x < array.size(); x++){
            output[x] = array.get(x);
        }
        
        return output;
    }
    
    //Makes failing easier
    private static MimeMessage[] fail(Exception e) {
        println(e.toString());
        e.printStackTrace();
        return null;
    }
}