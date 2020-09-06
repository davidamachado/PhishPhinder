/**
 * Author: Malachi Woodlee and David Machado
 *
 * Purpose: Controller that uses the PhPhMessage class to parse emails and place them in an ArrayList
 *
 * Updates:
 * 
 * 2019-12-08 updated csvCreate Method  malachi
 */

package phphgui;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import static java.util.concurrent.Executors.newFixedThreadPool;
import javax.mail.*;
import javax.mail.internet.*;
import javax.swing.*;
import static phphgui.PhPhCSVWriter.phPhWriter;


public class PhPh {
    //class variables
    private String status, threadMessage;
    private PhPhMessage p;
    private ThreadPoolExecutor pool;
    private ArrayList<PhPhMessage> parsedEmails = new ArrayList();
    private HashMap<Integer, PhPhMessage> messageMap;
    private MimeMessage[] messages;
    private SimpleSSLMail ssl;
    static int emailCount;
    
    //constructor
    public PhPh(int threadCount, MimeMessage[] messages) throws FileNotFoundException, MessagingException {
        //call to connect to email server with SimpleSSLMail.java
        this.messages = messages;
        //HashMap initialization
        messageMap = new HashMap<>();
        //thread pool initialization
        pool = (ThreadPoolExecutor) newFixedThreadPool(threadCount);
        //call parseEmail method 
        this.parseEmail();
    }
    //parse through each email

    /**
     *
     * @throws FileNotFoundException
     * @throws MessagingException
     */
    public void parseEmail() throws FileNotFoundException, MessagingException { 
        for(int x = 0; x < messages.length; x++) {
            emailCount = x + 1;
            
            //creates new Parser instance with MimeMessage
            p = new PhPhMessage(messages[x]);
            //initiate thread
            pool.execute(p);
            //store PhPhMessage object in ArrayList
            parsedEmails.add(p);
        }
        pool.shutdown();
        this.csvCreate();
    }

    public ArrayList<PhPhMessage> getParsedEmails() {
        return parsedEmails;
    }
    
    //method to write to csv file
    public void csvCreate() {
        phPhWriter(messageMap);
    }
}


