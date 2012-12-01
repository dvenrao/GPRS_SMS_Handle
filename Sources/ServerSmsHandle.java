/************************************************************************************************************
 * @(#)ServerSmsHandle.java
 *
 * ServerSmsHandle application
 * The file has 2 classes, one server main class, one worker thread class
 * Recieves the messages from remote sites and parses the message and puts the message into db & log files
 * @author :Narender Rao Saineni
 * @version 1.00 2008/8/22
 ************************************************************************************************************/
 
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.Calendar;
import java.util.concurrent.*;
import java.text.SimpleDateFormat;
import java.sql.*;
 
 
/*****************************************************************************************
 * Class			: WorkerThread
 * Description		: This class handles messages from clients, one instance one connection
 *                    the conneciton to be handled is picked up from server class messages array
 *					  once completes the job, removes the message from the array
 *
 ******************************************************************************************
 */		
  
class ModemThread extends Thread 
{
	int worker_id;					//worker thread id
    volatile long ts1,ts2,ts3;				// time stamps
    int message;
   	StringBuffer columns=new StringBuffer();
	StringBuffer values=new StringBuffer();
    Modem modem;
    
    static String driver = "com.mysql.jdbc.Driver";
    static String url = "jdbc:mysql://118.139.163.208:3306/";
	static String dbName = "csm_production";
	static String userName = "csm_production"; 
	static String password = "Csm@123";
	static Connection db_con;
    Statement smnt=null;
    Statement smnt1=null;
    Statement smnt2=null;
    boolean sendflag=true;

	//static HashMap<String,String> all_namesvalues = new HashMap<String,String>();
   
    static	String 		line_as_rcvd;
    
    static  StringBuffer line_rcvd	=new StringBuffer(); // we store the rcvd line here appending the parname prefixes if missing
    static  String sms_as_rcvd=new String(""); // we store the entire message as it is recieved w/o any change
    static  StringBuffer message_rcvd	=new StringBuffer(); // we store the entire message here which can be inserted into db

    public static String site_id="null";	
    String category=new String();
    

    
	public ModemThread(int id,String portname)
	{
		worker_id=id;
		modem=new Modem(portname);
		//modem=new Modem("COM30");


        try{
			//Load db driver
	      	Class.forName(driver).newInstance(); // attempting to load the database driver class
            db_con=get_adbconnection();
		    smnt=db_con.createStatement();
		    smnt1=db_con.createStatement(ResultSet.TYPE_SCROLL_SENSITIVE,ResultSet.CONCUR_UPDATABLE);	
		    smnt2=db_con.createStatement();	
        }
        catch(Exception e){
        	
        	System.out.println("DB connection open problem "+e);
        	System.exit(0);
        }

	}             
	
/*****************************************************************************************
 * Function			: get_adbconnection
 * Description		: connects to the data base and gives the connection object
 ******************************************************************************************
 */		

	static Connection get_adbconnection() throws Exception
	{
        //open connection to db
        
	    System.out.println("Opening a connection to db");
	    Connection con=null;
	    try 
	    {
	
	      con = DriverManager.getConnection(url+dbName,userName,password);
	      System.out.println("Connected to the database");
	      //con.close();
	      //System.out.println("Disconnected from database");
	    } 
	    catch (Exception e) 
	    {
	    	System.out.println("Exception");
	    	System.out.println(e);
	    	throw(e);
	      	//System.exit(0);
	    }
	    return con;   
	}    

//-----------------------------------------------------------------------------------
	
	public void run()
	{
	   StringBuffer line=new StringBuffer(200); // can hold one complete sms
	   MessageRecieved msg_rcvd=new MessageRecieved();
       char format; 
       boolean all_read=false;
       boolean processing_result=false;
       int i;	
                
		System.out.println("Worker Thread Started, No="+(worker_id+1));	
		if(!modem.working_ok()) return;	
	
		for(;;)
		{
		 try
		 {
			if(all_read)	{
				if(!sendflag) continue;
				//after all message read ,look for unsolicited message
				line=modem.getline(50);
				if(line==null) continue;
				//an unsolicited line from modem , means we could have recieved an sms/incall
	        	all_read=false; // need to read all messages now
			}	
			
			if(!all_read)	{
					msg_rcvd=modem.getanddelete_anynewmessage(false);// not delete now
					if(msg_rcvd.message==null) {all_read=true;continue;}
			}
			
			System.out.println("ModemThread "+(worker_id +1)+" Processing a Message !\n");
			
	        if(((i=msg_rcvd.mobile_no.length())>=10) && (msg_rcvd.mobile_no.indexOf("CMGR:")<0)){
	        	
	            HandleMessageFormat_DeviceReceiveMsg(msg_rcvd.message,msg_rcvd.mobile_no);
	        	modem.delete(msg_rcvd.rec_no);
	        	continue ;
	        }
	       //skip initial blank lines or whatever exists prior to ITAGE
	        if( (i=msg_rcvd.message.indexOf("ITAGE,"))<0){	
	        	System.out.println("Not Vijetha message");
	        	modem.delete(msg_rcvd.rec_no);continue ;
	        }

	        /*if(processing_result)*/
	         modem.delete(msg_rcvd.rec_no);
	        
		 }//try
		 catch(Exception e){
			System.out.println("Worker : "+(worker_id+1)+" Some Exception !\n");
			System.out.println(e);
			
		 	continue;
		 }
	}//for ever loop
 }//run
 
//----------------------------------------------------------------------------

void HandleMessageFormat_DeviceReceiveMsg(StringBuffer message_rcvd,String mobile_no) throws Exception 
{
		String         message=message_rcvd.toString();
		try{
			Statement stmt = db_con.createStatement();
			stmt.executeUpdate("insert into indus_sms set mobile_no='"+mobile_no+"',mc_dtime='"+ServerSmsHandle.now()+"',sr='R',message='"+message+"'");
		    stmt.close();
		    }	
		catch(Exception e)
	  	 {
			 System.out.println("Exception:"+e);
		 }      
}
}//WorkerThread class

public class ServerSmsHandle  {
  
  public static FileWriter   msg_logfile = null;
  public static FileWriter   dbinsert_logfile = null;
  
//getting system time

  public static String now() {
    Calendar cal = Calendar.getInstance();
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    return sdf.format(cal.getTime());
  }
//time convertion from timestamp to Date object
  public static java.util.Date toDate(java.sql.Timestamp timestamp) {
     long milliseconds = timestamp.getTime() + (timestamp.getNanos() / 1000000);
     return new java.util.Date(milliseconds);
	 }
    long ts2=System.nanoTime();
	public static Semaphore db_sema= new Semaphore(1);
	
    static Socket connection_socket=new Socket();
    static int message_no=0;
	static int check_count=10;

class MessagetobeSent {
	String mobile_no=null;// max 15 digits mobile no
	String message=null;// max 160 chars of message
	MessagetobeSent(){
}
	
}		
		
/*****************************************************************************************
 * Function			: main
 * Description		: The main method of the server class, creates the worker threads and delegates
 *                    the load to them ,accepts connection requests through a server socket
 *					  
 *
 ******************************************************************************************
 */		
    
    public static void main(String[] args)  {
    	
    	int i;	
    	boolean notfree_status_printed=false;
		String line_as_rcvd;
		String message;
		String mobile_nos;
		Boolean sms_flag=false;
		Calendar cal = Calendar.getInstance();
		
		
//		MessagetobeSent message1=new MesssagetobeSent();
		

	    if(args.length<1) {
	    	System.out.println("Please give portname as argument , like COM30 in windows, /dev/ttyUSB0 in linux");
	    	System.exit(0);
    	}	
	
			
		try{

			msg_logfile =new FileWriter("sms_log.txt",true); // open file for appending messages to
	        dbinsert_logfile=new FileWriter("sms_dbinserts_log.txt",true);
			
	        System.out.println("Server Started. Waiting for New SMSs..");
	        
	  		ModemThread modem1_thread=new ModemThread(0,args[0]);
	  		//ModemThread modem2_thread=new ModemThread(1);
	  
	  	    modem1_thread.start();
	  	    
			//process any messages to be sent to O&M staff
			while(true){
			Thread.sleep(1500);		
					
	  	//begin check control the site request is there**************************************
	  	   try{
			String dbselectquery=new String("select message,mobile_no,flag,id from indus_sms where flag=0 and sr='S' order by id desc limit 1");		
			ResultSet rs=modem1_thread.smnt1.executeQuery(dbselectquery);
			 
		while(rs.next())
	     {
		     	String sms=rs.getString(1);
		     	String mobile_no=rs.getString(2);
		     	String sno=rs.getString(4);
	  	    	modem1_thread.sendflag=false;
         		sms_flag=modem1_thread.modem.send_sms(mobile_no,sms);
			    modem1_thread.sendflag=true;
         		modem1_thread.smnt.executeUpdate("update indus_sms set flag=1 where id='"+sno+"'");
         		if(sms_flag)
         		System.out.println("sms sent to mobileno="+mobile_no);
         		else			
         		System.out.println("sms ** not ** sent to mobileno="+mobile_no);
	  	  	
	    	}//end of while(next)
			rs.close();	
				  	    
 		 }//try
  		 catch(Exception e){
		 System.out.println("Exception in control site request=,"+e);
		 }  	
		 	
	  	//end check control the site request is there**************************************   
				
		}//end of Main while(true)
				  	    
		}
		catch(Exception e){
			System.out.println("Exception in ServerHandleSMS Main"+e);
		}  		
			
	
      }//main

		
		
	synchronized static public void put_in_logfile(String dbquery)
	{
		try{

			//put in db query log file
			dbinsert_logfile.append("\n"+dbquery+";\n");
			dbinsert_logfile.flush();
			System.out.println(dbquery);
		
		}	
		catch (Exception e) 
	    {
	    	System.out.println("Exception while Message Insert in msgs_logfile.txt");
	      	e.printStackTrace();
	      	System.exit(0);
	    }
	}	



		
}//server class


//	public boolean  send_sms(String mobile_no,String message){








		






