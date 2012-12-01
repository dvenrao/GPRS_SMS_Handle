
import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.*;
import java.util.Formatter;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.sql.*;

class IndusMessageSend
{
    String SERVER_IP="localhost";
    static String driver = "com.mysql.jdbc.Driver";
	static String url = "jdbc:mysql://localhost:3306/";
	static String dbName = new String("csm_production");
	static String userName = new String("csm_production"); 
	static String password = new String("Csm@123");
	static Connection 			db_con=null;
	static Statement 			smnt=null;
	static String 				query=null;
    Socket s=null;
    OutputStream os=null;
    BufferedReader bis =null;

    byte[] bytearray=new byte[512];
  
    int[] dcem_data={
  	0x44,0x79,0xf9,0x9a,
  	0x41,0x9f,0x33,0x33,
  	0x44,0x79,0xf9,0x9a,
  	0x41,0x9f,0x33,0x33,
  	0x44,0x79,0xf9,0x9a,
  	0x41,0x9f,0x33,0x33,
  	0x44,0x79,0xf9,0x9a,
  	0x41,0x9f,0x33,0x33,
  	0x42,0x60,0x00,0x00
    };
  		
public void go(int port,String message) throws Exception
{
    try
 	  { 
	   s=new Socket(SERVER_IP,port);
	   os=s.getOutputStream();
	   PrintWriter ps=new PrintWriter(os);
       bis =new BufferedReader(new InputStreamReader(s.getInputStream()));

	   //Version 0 Messages	
	   ps.print(message);
	   System.out.println(message);
	   //dcem 0x01 pdu
	   /*ps.print("01");
	   ps.flush();
	   for(int i=0;i<36;i++) os.write(dcem_data[i]);
	   os.flush();
	   //dcem 0x01 pdu
	   ps.print("02");
	   ps.flush();
	   for(int i=0;i<36;i++) os.write(dcem_data[i]);
	   os.flush();
	   //dcem 0x01 pdu
	   ps.print("02");
	   ps.flush();
	   for(int i=0;i<36;i++) os.write(dcem_data[i]);
	   os.flush();
	   //dcem 0x10 pdu
	   ps.print("10");*/
	   ps.flush();
	   os.flush();

	   Thread.sleep(500);	
	   	   
  	   bis.close();
	   os.close();
	   s.close();
	 }
	 catch(Exception e)
	 { 
	   	System.out.println(e);
	   	System.exit(0);
	 }
}//go


public static void main(String args[]) throws Exception
{
	int port;
    if(args.length<1) {
      	System.out.println("java MessageSend 4510");
    	System.exit(0);
    }	
    
    int 				i;	
	int 				msg_no=0;
	String              msgno_string;
	String 				line=new String("");
	BufferedReader		ifile=null;
    BufferedReader     	ln_in=null;  
    FileReader			in1=null;
    FileWriter 			fw=null;
    boolean 			msgno_write_due=true;

	for(;;)
	{
		try
		{
        	try{
			    //Load db driver
	    	    System.out.println("Loading DB driver");
			  	Class.forName(driver).newInstance(); // attempting to load the database driver class
			  	System.out.println("Opening a connection to db");
			    db_con=DriverManager.getConnection(url+dbName,userName,password);
			    System.out.println("Connected to the database");
			    smnt=db_con.createStatement();	
	    	}
        	catch(Exception e){
        		
        		System.out.println("DB connection open problem ");
        		System.out.println(e);
        		System.exit(0);
        	}
	        
	        for(;;)
	        {
				try
				{
		  			String dbselectquery=new String("select message,id from indus_send where status=0 limit 1");				
					ResultSet rs=smnt.executeQuery(dbselectquery);
		  
		  			if(rs.next())
	      			{
		     			 System.out.println("------------ Sending Message to Server----------------");
		     			 port	=Integer.parseInt(args[0]);	
						 IndusMessageSend ims=new IndusMessageSend();
						 ims.go(port,rs.getString(1));	
						 smnt.executeUpdate("update indus_send set status=1 where id='"+rs.getString(2)+"'");
	      			}
		   			rs.close();	
		   			
		   			Thread.sleep(5000);	
 		 		}//try
  		 		catch(Exception e)
  		 		{
  		 		}  	
         	}//for;;
		}//try       	   
        catch (Exception e){
        	System.out.println("Exception from Exception Handler Encountered! ");	System.exit(0);
         }
	}//for outer loop	
}//main
}