package phphgui;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import static java.lang.Boolean.parseBoolean;
import static java.lang.Integer.parseInt;
import static java.lang.System.out;
import java.util.ArrayList;

/**
 * Author: Paul Hendrick
 * Date: 11/21/2019
 * Purpose: 1.0 Loads settings from a file and handles text output. It will load
 *              the information the first time any method is called, this allows
 *              it to set itself up without having to have an outside load
 *              method.
 *          1.1 Updated log output
 *          1.2 Added Threadcount
 **/

public class Settings {
    
    //Constant filename that settings are retrieved from
    private String fileName = "Settings.txt";
    
    //File writer for log.txt output
    private static BufferedWriter writer;
    
    //Data for the overall program
    private static boolean isLoaded = false; 
    private static boolean debug = true;
    private static int threadcount = 10;
    
    //Data for server bieng accessed
    private static String emailServer = "";
    private static String loginUsername = "";
    private static String loginPassword = "";
    
    
    //A simple main used for testing the functions of the program.
    /*public static void main(String[] args) {

        Settings.load();
        Settings.println("Success!");
    }*/
    
    //Load and intilize settings------------------------------------------------
    private static void load() {
        //Return if already ran
        if(isLoaded)
            return;
        isLoaded = true;
        //Configure file output for logging
        try {
            writer = new BufferedWriter(new FileWriter(new File("Log.txt")));
        } catch (java.io.IOException e) {
            out.println(e);
        }
        //Declare strings for the parse
        String log = "";
        String input ="";
        ArrayList<String> tokens = new ArrayList<>();
        //Read 
        try {
            BufferedReader reader = new BufferedReader(new FileReader(new File ("Settings.txt")));
            
            //Prime the reader
            input = reader.readLine();
            while(input != null){
                tokens.add(input);
                input = reader.readLine();
            }
        }  catch (java.io.FileNotFoundException e) {
            log += "ERROR - Settings.txt not found! - No settings loaded!";
        } catch (java.io.IOException e){
            out.println(e);
        }
        //Keep parsing tokens until there are none left to parse
        while(!tokens.isEmpty()) {
            //Split the token into sub tokens
            String[] parse =  tokens.remove(0).split(" = ");
            try {
                //Filter out white space or comments
                if((parse[0].length() == 0)||(parse[0].charAt(0) == '-')) {
                    continue;
                }
                switch(parse[0].toLowerCase()){
                    //Set Debug
                    case "debug":
                    debug = parseBoolean(parse[1]);
                    log += "debug = \"" + debug + "\"\n";
                    break;
                    //Set E-Mail Server Information
                    case "emailserver":
                    emailServer = parse[1];
                    log += "emailServer = \"" + emailServer + "\"\n";
                    break;
                    case "loginusername":
                    loginUsername = parse[1];
                    log += "loginUsername = \"" + loginUsername + "\"\n"; 
                    break;
                    case "loginpassword":
                    loginPassword = parse[1];
                    log += "loginPassword = \"" + loginPassword + "\"\n";
                    break;
                    case "threadcount":
                    try { 
                    threadcount = parseInt(parse[1]);
                    log += "loginPassword = \"" + loginPassword + "\"\n";
                    } catch (NumberFormatException e) {
                        log += "ERROR - Invalid number: \"" + parse[1] + "\"\n";
                    }
                    break;
                    //Default for unregistered tokens:
                    default:
                    log += "ERROR - Invalid token: \"" + parse[0] + "\"\n";
                    break;  
                }
            }   catch (IndexOutOfBoundsException e){
                out.println(e);
                log += "ERROR - Invalid token: \"" + parse[0] + "\"\n";
            }
        }
        println(log);
    }

    //Isolated text output------------------------------------------------------
    //Same as System.out.println(String);

    /**
     *
     * @param out
     */
    public static void println(String out) { 
        load();
        if(debug)
            System.out.println(out);
        try {
            writer.write(out + "\n");
            writer.flush();
        } catch (IOException e){
            System.out.println(e);
        }
    }
    
    //Same as System.out.print(String);

    /**
     *
     * @param out
     */
    public static void print(String out) {
        load();
        if(debug)
            System.out.print(out);
        try {
            writer.write(out + "\n");
            writer.flush();
        } catch (IOException e){
            System.out.println(e);
        }
    }
    
    //Overall Program Information Return Methods--------------------------------

    /**
     *
     * @return
     */
    public static boolean debug() {load(); return debug;};
    
    //Server Login Information Return Methods-----------------------------------

    /**
     *
     * @return
     */
    public static String emailServer() {load(); return emailServer;};

    /**
     *
     * @return
     */
    public static String loginUsername() {load(); return loginUsername;};

    /**
     *
     * @return
     */
    public static String loginPassword() {load(); return loginPassword;};

    /**
     *
     * @return
     */
    public static int threadcount() {load(); return threadcount;};
}

