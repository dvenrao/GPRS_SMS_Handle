/************************************************************************************************************
 * @(#)ServerGPRSHandle.java
 *
 * ServerGPRSMessage Reciver application
 * The file has 2 classes, one server main class, one worker thread class
 * Recieves the messages from remote sites and parses the message and puts the message into db & log files
 * @author :Narender Rao Saineni.
 * @version 1.00 2008/8/22
 ************************************************************************************************************/
 
import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.*;
import java.util.Formatter;
import java.text.SimpleDateFormat;
import java.sql.*;
 
 



class IndusReciever{
	
	
    final static String Version=new String("Version Feb 8");
	
    int 		 server_port=4510;		// This Server Listens on this port by default
    
    public static final String DATE_FORMAT_NOW = "yyyy-MM-dd-HH-mm-ss";

    ServerSocket server_socket=null;

    static Socket conn_sock=null;
    
        	int i;	
		String line_as_rcvd;
		
        String driver = "com.mysql.jdbc.Driver";
	    String url ;//= "jdbc:mysql://192.168.1.101:3306/";
	    //String url = "jdbc:mysql://localhost:3306/";
		String dbName = new String("csm_production");
		String userName = new String("csm_production"); 
		String password = new String("Csm@123");
		Connection 			db_con=null;
		Statement 			smnt=null;
		String 				query=null;
        InputStream in=null;
        PrintWriter  out=null;
        StringBuffer message_rcvd=null;

   IndusReciever(){
   	
   	
   		  // determine the name the server where the data has to be sent
	      
	      String osname = System.getProperty("os.name","");
	      
	      
	      if ( osname.toLowerCase().startsWith("windows") ) {
	         // windows
	        url = new String("jdbc:mysql://localhost:3306/");
	      } else if (osname.toLowerCase().startsWith("linux")) {
	         // linux
	        url = new String("jdbc:mysql://localhost:3306/");
	      }
	      System.out.println("OS           ="+osname);
	      System.out.println("DB Server url="+url);
	      
   }
   void go(int port)
   {
   	int m,n;
   	int msgno=0;
   	int c=-1;
   	String s=null;
   	long char_recieved_time=0;
   	long newcon_time=0;
   	long time_from_lastchar,time_from_connection;
    int idx_char=0;
    boolean started=false;

        try{
		    //Load db driver
	        System.out.println("Loading DB driver");
		  	Class.forName(driver).newInstance(); // attempting to load the database driver class
		    db_con=get_adbconnection(url,dbName+"?autoReconnect=true",userName,password);
		    smnt=db_con.createStatement();	
	        db_con = DriverManager.getConnection(url+dbName,userName,password);
        }
        catch(Exception e){
        	
        	System.out.println("DB connection open problem ");
        	System.out.println(e);
        	System.exit(0);
        }

        server_port=port;
		try
		{
				
			System.out.println("Server port="+server_port);
            server_socket= new ServerSocket(server_port);
            server_socket.setReuseAddress(true);
            
			for(;;)
			{      
				
		        System.out.println("\r\n-----------Server Started Listening on "+server_port+"-----------\r\n");
	            msgno=0;

				conn_sock = server_socket.accept();
                
				//Handle the given message
				
	           	System.out.println("\r\n------------ New Connection with a Client ----------------\r\n"); 
	           		
	           	byte recieved_bytes[]=new byte[512];
	           	message_rcvd=new StringBuffer("");	
				conn_sock.setSoTimeout (1000*30 ); // 30 seconds
				in 	=conn_sock.getInputStream();
				n=0;
				out =new PrintWriter(conn_sock.getOutputStream(), true);
				m=0;
				newcon_time=System.currentTimeMillis();
				started=false;
	
			try
			{
			    //Recieve chars until long gap/socket close
		        while(true)
				{
					try{ 
						if( in.available()>0) 
						{
							c=in.read();
							s=Integer.toString(c, 16);
							System.out.print("x"+s+",");
							
							if((started==false) &&  c==0x23) {
								started=true;
								idx_char=0;
							}
							else if(!started) System.out.println();
							if(started==true)
							{
								recieved_bytes[idx_char]=(byte) (c&0xff);
								idx_char++;
							}								
							newcon_time=char_recieved_time=System.currentTimeMillis();
						}	
						else{
							Thread.sleep(10);
							time_from_lastchar=time_elapsed_secs(char_recieved_time);
							time_from_connection=time_elapsed_secs(newcon_time);
							if( started && (time_from_lastchar >= 1000) ) { 
								System.out.println("\r\nRecieved chars:"+idx_char);
								System.out.flush();
						        ParseIndusMessage(recieved_bytes,idx_char);
								idx_char=0;
								started=false;// for next message
								System.out.println();
							}
							if( time_from_connection > 10000  ) break;
						 }	
					}
			        catch(SocketException e)
			        {
			        	System.out.println("Client Quit!");
			        	break;
			    	}
		        }//while
		        
			    if(!conn_sock.isClosed() ) conn_sock.close();
		        
			}//try       	   
	        catch(SocketException e)
	        {
	        	System.out.println(e);
	        	System.out.println("waited for:"+n);
	        	continue;
	    	}
	        catch(Exception e)
	        {
	        	System.out.println(e);
	        	continue;
	    	}
	    	
	    	

		}//for
		}//try       	   
        catch(Exception e)
        {
        	System.out.println(e);
    	}
    		
}//go
      

void ParseIndusMessage(byte[] rxd_bytes,int size_rcvd) throws Exception
{
	
   float fvalue=0;
   int itemp;
   long ltemp;
   String tempString=null;
   String message_bytes=null;
   String device_bytes=null;
   StringBuffer devices=new StringBuffer("");
   StringBuffer device_offsets=new StringBuffer("");
   
   int device_offset=0;	
   	
   String[] parnames={
  	"Type",
  	"site_id",
  	"timeof_day",
  	"date",
  	"site_alarms",
  	"device_id",
  	null,null
  };
  
  long site_alarms=0; //to get unsigned int
  int device_id;
  int ndevices=0;
  String s=null;
  StringBuffer devices_query=new StringBuffer("");
  String col_name=null;	

  message_bytes=get_hexstring(rxd_bytes,0,size_rcvd);
  
  System.out.println("Message size="+size_rcvd); 



  
  String[] parvalues=new String[32];
  
  parvalues[0]=new String(rxd_bytes,2,1); 	//type
  parvalues[1]=new String(rxd_bytes,4,10) ;  //site_id 	
  parvalues[2]=new String(rxd_bytes,15,8) ;  //time 	
  parvalues[3]=new String(rxd_bytes,24,10) ;  //date
  
  
  String header=new String(rxd_bytes,0,35);  
  String header_quoted=new String("'"+header+"'");	

  site_alarms=   ((long)rxd_bytes[35] &0x0ff)<<24 |
  	             ((long)rxd_bytes[36] &0x0ff)<<16 |
  	             ((long)rxd_bytes[37] &0x0ff)<<8 |
  	             ((long)rxd_bytes[38] &0x0ff)<<0 ;
  	             	



  System.out.println();
  //for(int i=0;i<=3;i++) System.out.println(parnames[i]+"="+parvalues[i]);  	
  System.out.println(header);
  tempString= String.format("%08x,",site_alarms) ;
  System.out.println("site_alarms="+tempString );  
  	
  	
  device_offset=39;
  
  //scan devices present
  while(true){
      byte val = rxd_bytes[device_offset];
	  device_id=Integer.parseInt((Integer.toHexString(0x000000ff & val)),16);
	  
	  if (device_id==0x2e || device_id==0x00 ) break;	
	  if (device_offset>size_rcvd) break;
	  ndevices++;
      if(ndevices>25) { System.out.println("\r\nSo Many devices !");break;}	  	
	  int devdata_size=get_device_datasize(device_id); //device_id + its data
	  
  	  tempString= String.format("%02x",device_id) ;
	  
	  if(device_id>=0x01 && device_id<=0x04 ){
	  	col_name=new String("dcem");
	  }	
	  else if(device_id==0x10 ){
	  	col_name=new String("pmu");
	  }	
	  else if(device_id>=0x11 && device_id<=0x16 ){
	  	col_name=new String("acem");
	  }	
	  else if(device_id==0x17 ){
	  	col_name=new String("rms");
	  }	
	  else if(device_id==0xfd ){
	  	col_name=new String("newrms");
	  }	
	  else { 
	      col_name=new  String("newdev");
              System.out.println("\r\nNew device id="+device_id);

          }
	  
  	  col_name=new String(col_name+"_x"+tempString+"_bytes");
  	  System.out.println("\r\n"+col_name+", size="+devdata_size+", offset="+device_offset);
	  devices.append(device_id+",");	
	  device_offsets.append(device_offset+",");	
  	  device_bytes=get_hexstring(rxd_bytes,device_offset,devdata_size);
  	  System.out.println(device_bytes);
	  	
          System.out.flush();  	  
      	
          //devices_query.append(","+col_name+"="+"'"+device_bytes+"'");	
      	
	  device_offset += devdata_size;
  }	
  System.out.println("\r\ndevices="+devices);	
  System.out.println("offsets="+device_offsets);	
  String query=new String("INSERT INTO indus_values set header="+header_quoted +",mc_dtime='"+IndusMessageReciever.now()+"',site_alarms="+site_alarms 
  	+",devices='"+devices+"',device_offsets='"+device_offsets+"'"
  	+",message_bytes='"+message_bytes+"'"+devices_query.toString());
  //System.out.println("query="+query);
  smnt.executeUpdate(query);	
	
}
long get_int_from_networkbytes(byte[] bytes,int k)
{
 long x;
  //x= bytes[k+0]<<24 | bytes[k+1]<<16 | bytes[k+2]<<8 | bytes[k+3]<<0;
  x= bytes[k+3]<<24 | bytes[k+2]<<16 | bytes[k+1]<<8 | bytes[k+0]<<0;
  return x; 	
	
}

int get_device_datasize(int device_id)
{
	int size=0;
	
	switch(device_id)
	{
	 case 0x01:	
	 case 0x02:	
	 case 0x03:	
	 case 0x04:	
		 size=42;  // devid+ 4 chls * 8 bytes/chl + 4byte voltage=37 + 5 new bytes
		 break;
	 case 0x11:		// eb+dg energy meter
	 case 0x12:	
		 size=9;  
		 break;
	 case 0x13:	
	 case 0x14:	
	 case 0x15:
	 case 0x16:	
		 size=5;  // eb_kwh/dg_kwh
		 break;
	 case 0x10:	 //pmu
		 size=55;  
		 break;
	 case 0x17:	 //rms
		 size=5;  
		 break;
	 case 0xfd:	 //newrms
		 size=29;  
		 break;	 
	default:
	         size=33;// devid + 16 regs		
		
	}	
	return size;	
	
}

String get_hexstring(byte[] data,int start,int size) throws Exception
{
StringBuffer HexString=new StringBuffer("");
String tempString=null; 
       
       for(int i=0;i<size ;i++){ 
       	 tempString= String.format("%02x,",data[start+i]) ;
         HexString.append(tempString);         
       }  
       return HexString.toString();
       
}



int time_elapsed_secs(long old_time)
{
    long t,current_time=System.currentTimeMillis();
    t=current_time-old_time;
    return (int)t;	
}



void set_string_indbtable(String device_serial_quoted,String table_name,String column_name,String value) throws Exception
{
	String query=new String("update "+table_name+" set "+column_name+"='"+value+"' where device_serial="+device_serial_quoted);
	try{
		Statement stmt = db_con.createStatement();
		stmt.executeUpdate(query );
	    stmt.close();

	}		
	catch(SQLException  e)  {
       System.out.println("SQL Exception:"+ e);
       System.out.println("query given is:"+query);
       System.out.println("column_name= "+column_name +" site_id="+device_serial_quoted);
    }
		
}









/*****************************************************************************************
 * Function			: get_adbconnection
 * Description		: connects to the data base and gives the connection object
 ******************************************************************************************
 */		

Connection get_adbconnection(String url,String dbName,String userName,String password) throws Exception
{

        //open connection to db
        
	    System.out.println("Opening a connection to db");
	    Connection con=null;
	    try 
	    {
	
	      con = DriverManager.getConnection(url+dbName,userName,password);
	      //con =DriverManager.getConnection("jdbc:mysql://192.168.1.101:3306/csm_production?user=csm&password=csmpass123");
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

}// class Reciever


public class IndusMessageReciever  {
	
   	
/*****************************************************************************************
 * Function			: main
 * Description		: The main method of the server class, creates the worker threads and delegates
 *                    the load to them ,accepts connection requests through a server socket
 *					  
 *
 ******************************************************************************************
 */	
 public static final String DATE_FORMAT_NOW = "yyyy-MM-dd-HH-mm-ss";	
 
 
 public static String now() {
    Calendar cal = Calendar.getInstance();
    SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT_NOW);
    return sdf.format(cal.getTime());

  }



    public static void main(String[] args)  
    {
    	int port;
    	
        port=4525;
  		
	    if(args.length<1) {
	    	System.out.println("Invoke the command as below");
	    	System.out.println("java MessageReciever 4510");
	    	System.exit(0);
	    }	
	     	
    	System.out.println("Read port as "+args[0]);
    	System.out.flush();	
        port	=Integer.parseInt(args[0]);	

    	IndusReciever r1=new IndusReciever();
  

        	
    	r1.go(port);	
    		
    }
     	
}







