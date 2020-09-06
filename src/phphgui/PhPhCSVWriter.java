package phphgui;

import com.opencsv.CSVWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import static java.lang.System.out;
import java.util.HashMap;
import java.util.Arrays;

/**
 * Author: Malachi Woodlee
 *
 * Purpose: Creates a CSV file that displays info found in each parsed email
 *
 * Updates:
 *
 * 2019-12-08   added append function to phPhWriter() Malachi Woodlee
 */

public class PhPhCSVWriter {

    public static void phPhWriter(HashMap hm){
        StringBuilder keys2hdrs = new StringBuilder();
        HashMap<String, Integer> theMap = hm;
        theMap.entrySet().forEach((pair) -> {
            keys2hdrs.append(pair.getKey()).append(", ");
        });
            
        String headers = keys2hdrs.toString();
        
        final String[] CSV_HEADER = headers.split(",") ;
        
        out.println(Arrays.toString(CSV_HEADER));
        
        StringBuilder vals2data = new StringBuilder();
        
        theMap.entrySet().forEach((pair) -> {
            vals2data.append(pair.getValue()).append(", ");
        });
        
        String vals = vals2data.toString();
        
        final String[] row = vals.split(", ");
        
        CSVWriter csvWriter = null;
        
        try{
            File batch = new File("batch.csv");
            if(batch.exists() && !batch.isDirectory()){
                csvWriter = new CSVWriter(new FileWriter("batch.csv", true));
            
                //csvWriter.writeNext(CSV_HEADER);
                csvWriter.writeNext(row);
            }else{
                csvWriter = new CSVWriter(new FileWriter("batch.csv", true));
            
                csvWriter.writeNext(CSV_HEADER);
                csvWriter.writeNext(row);
            }
            
        }catch(IOException ee){
            ee.printStackTrace();
        }
        finally{
            try{
                csvWriter.close();
            }catch(Exception ee){
                ee.printStackTrace();
            }
        }
    }    
}