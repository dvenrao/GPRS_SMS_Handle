	import java.io.*;
	import java.util.*;
	import gnu.io.*; // for rxtxSerial library
//	import Parsesms.class;

	
	public class Modem  implements Runnable
	{
	   String       	defaultPort=new String("COM30");
	   
	   InputStream          inputStream;
       BufferedInputStream bis;

	   SerialPort           serialPort;
	   byte[] readBuffer = new byte[1024];
	   
	   int read_idx,c,n,m,idle_time;
	
	   OutputStream      outputStream;
	   boolean        outputBufferEmptyFlag = false;
	   StringBuffer line_read=new StringBuffer(1024);
       
	   final int MAX_LINES= 12;
	    
		StringBuffer line_inread= new StringBuffer("");
		StringBuffer linegot;
		boolean  lineavailable=false;

		
		char x;
		public int last_line_read;
		boolean overflowed,readingdone;
        boolean workingok=false;


	   public Modem(String portname_given) {
	   	
	      read_idx=0;
	      int i,j;
	      String portname;
	      
	      StringBuffer line;
	      
	      try{
	      
		  System.out.println("Port given: "+portname_given);
		  
	      // determine the name of the serial port on several operating systems
	      /*
	      String osname = System.getProperty("os.name","").toLowerCase();
	      if ( osname.startsWith("windows") ) {
	         // windows
	         defaultPort = "COM30";
	      } else if (osname.startsWith("linux")) {
	         // linux
	        defaultPort = "/dev/ttyUSB0";
	      } else {
	         System.out.println("Sorry, your operating system is not supported");
	         return null;
	      }
		  */
		  
	      
	      portname=portname_given;
	      serialPort=getport(portname_given); 
	      	
		  if (serialPort!=null)     System.out.println("Using  port "+portname_given+" Initing !");
          else {
          	System.out.println("Port could not be opened !");
          	return;
          }
          
          
		
		  try {
		     inputStream = serialPort.getInputStream();
		  } catch (IOException e) {}
		  
		  try {
		     // set port parameters
		     serialPort.setSerialPortParams(9600, SerialPort.DATABITS_8, 
		                 SerialPort.STOPBITS_1, 
		                 SerialPort.PARITY_NONE);
		  } catch (UnsupportedCommOperationException e) {System.out.println(e);}
		  
		   try {   
		   	//AT+IFC=0,0 ;// no flow control command to the modem
	
			  //serialPort.setFlowControlMode(   
			  //      SerialPort.FLOWCONTROL_NONE);   
			  // OR   
			  // If CTS/RTS is needed   
			  serialPort.setFlowControlMode( SerialPort.FLOWCONTROL_RTSCTS_IN |  SerialPort.FLOWCONTROL_RTSCTS_OUT );   
			} catch (UnsupportedCommOperationException ex) {   		System.err.println(ex.getMessage());   		}
	      
	      
	      
	      
	      //Get Input and Output Streams 	
	      bis=new BufferedInputStream(inputStream,8*1024);

	      //String line=new String();
	      
	      try {
	         // get the outputstream
	         outputStream = serialPort.getOutputStream();
	      } catch (IOException e) {
		  	System.out.println("Exception in Modem constructor"+e);
		  	System.exit(0);
	      	
	      	}
	      
  		  readinit();
  		  Thread t=new Thread(this,"ReaderThread");
  		  t.start();

	      this.write("AT\r\n");
		  while(!readingdone()) Thread.sleep(10);
		  
	      this.write("ATE0\r\n");
	      while(!readingdone()) Thread.sleep(10);
	      line= this.getline(2000);
	      
	      this.write("AT+CMGF=1\r\n");
	      while(!readingdone()) Thread.sleep(10);
	      line= this.getline(2000);
	      
	      /*
	 	  System.out.println("Delete All Read Messages !");
	 	  
      	  this.write("AT+CMGDA=\"DEL ALL\"\r\n");
      	  Thread.sleep(2000);
		  */
	      
	      this.write("ATE0\r\n");
	      while(!readingdone()) Thread.sleep(10);
	      line= this.getline(2000);
	      
	      /*
	      if( line.toString()!="OK") {
	      	System.out.println("No reponse to ATE0 !");
		    workingok=false;
		    return;
	      }
	      */
		  workingok=true;
	   }
		  catch(Exception e){
		  	System.out.println("Exception in Modem constructor"+e);
		  	System.exit(0);
		  }
		  
	   }//constructor
//-------------------------------------------------------------------------

		public boolean working_ok(){
			return workingok;
		}


//-------------------------------------------------------------------------
	SerialPort getport(String port_given){
	   CommPortIdentifier portId=null;
	   Enumeration        portList;
      boolean           portFound = false;

		  SerialPort serialPort=null;

	          
	      
		  // parse ports and if the default port is found, initialized the reader
	      portList = CommPortIdentifier.getPortIdentifiers();
	      while (portList.hasMoreElements()) {
	         portId = (CommPortIdentifier) portList.nextElement();
	         if (portId.getPortType() == CommPortIdentifier.PORT_SERIAL) {
	            if (portId.getName().equals(port_given)) {
	               System.out.println("Found port: "+port_given);
	               portFound = true;
	            } 
	         } 
	         
	      } 
	      if (!portFound) {
	         System.out.println("port " + port_given + " not found.");
	         return null;
	      } 
	
	  // open the port and check it out
		  try {
		     serialPort = (SerialPort) portId.open("SimpleReadApp", 2000);
		  } catch (PortInUseException e) {
		  	System.out.println("Selected Port in use !"); 
		  	serialPort=null;
		  	System.exit(0);
		  		
		  }
		  
		  return serialPort;
		  
	
}


//------------------------------------------------------------------------------	

	
	public MessageRecieved getanddelete_anynewmessage(boolean delete){
		
		StringBuffer sms_rcvd=new StringBuffer(1024);	
		String rec_no_string=new String();	
		int i,j,rec_no=0;
		boolean unreadsms_present,messageline=false;
		StringBuffer line=new StringBuffer(200); // can hole one complete sms
		
		MessageRecieved msg_rcvd=new MessageRecieved();
		
	
	      try {
	      	unreadsms_present=false;
	  		while (!readingdone()) Thread.sleep(100);
	  		this.write("AT+CMGL=\"ALL\"\r\n");
	  		//this.write("AT+CMGL=\"REC UNREAD\"\r\n");

//example response to AT+CMGL=ALL
//+CMGL: 1,"REC UNREAD","+919701447819",,"09/03/03,20:14:41+22"
//Deviceeinfo,dd,ww
//examle response to AT+CMGR=1
//+CMGR: "REC READ","+919701447819",,"09/03/03,20:14:41+22"
//Deviceeinfo,dd,ww


	  		//wait for REC UNREAD line & then capture it, ignore other lines
	  		while(true){
	        	if(readingdone()) break;
				line=getline(100);
				if(line!=null){
					System.out.println(line);
		        	if( line.indexOf("+CMGL:") < 0 ) continue;
		        	unreadsms_present=true;
		        	break;
				}
	  		}
      		if (!unreadsms_present) return msg_rcvd;
		        	
    		 i=line.indexOf(" ");
    		 j=line.indexOf(",");
    		 rec_no_string=line.substring(i+1,j);
    		 rec_no=Integer.parseInt(rec_no_string);
    		 
    		 System.out.println("Captured Rec no:"+rec_no);
    		 
    	     System.out.flush();
			 while(!readingdone()) Thread.sleep(100);
			 
			 //fetch the captured record
			 readinit();
      		 this.write("AT+CMGR="+rec_no+"\r\n");
      		 sms_rcvd=new StringBuffer("");
      		 messageline=false;
      		 while(true){
				 if (readingdone()) break;
				 if ((line=getline(100))==null) continue;
				 if (line.indexOf("CMGR:") >=0){
				 	messageline=true;
				    int n=line.indexOf(",\"+");
				    String mobile_no=new String( line.substring(n+2,n+15) );
				    System.out.println("Mobile No captured:"+mobile_no);
				    msg_rcvd.mobile_no= new String(mobile_no);
				 	continue;
				 } // lines following the CMGR line are message lines
				 
				 if ((line.indexOf("OK")>=0) && (line.indexOf(",OK")<=0) ) break;  // if we get OK line it means message ended on the prev line
				 if (messageline) { 
				 	System.out.println(line);
				 	sms_rcvd.append(line);
				 }	
      		 }
      		 
      		 while(!readingdone()) Thread.sleep(100);
      		 System.out.println("SMS Rcvd:\n"+sms_rcvd);
      		 


      		 //delete the record
      		 if(delete) {
      		 	System.out.println("Deleting RecNo:"+rec_no);
      		 	this.write("AT+CMGD="+rec_no+"\r\n");
      		    line=getline(2000);
      		 }
			 		            
			} 
		    catch (Exception e) {
		    	System.out.println(e); 
		    }
		    msg_rcvd.message=sms_rcvd;
		    msg_rcvd.rec_no=rec_no;
			return msg_rcvd;	
	}


//------------------------------------------------------------------------------	

	
	public boolean  send_sms(String mobile_no,String message){
		boolean result=false;
		StringBuffer line=new StringBuffer(200); // can hold one complete sms
		
	    try {
	    	this.write("AT+CMGF=1\r\n");
			while (!readingdone()) Thread.sleep(100);
	    	this.write("AT+CMGF?\r\n");
			while (!readingdone()) Thread.sleep(100);
			 
	  		this.write("AT+CMGS=\""+mobile_no+"\""+"\r\n");
		    while (!readingdone()) Thread.sleep(10);
	  		this.write(message);
	  		this.write("\r\n");
	  		this.write("\u001A");// control-z
	  		this.write("\r\n");
		    while (!readingdone()) Thread.sleep(100);
		    line=getline(100);
	  		
	  		//wait for response
	  		while(true){
	  			result=true;
	        	if(readingdone()) break;
				line=getline(100);
				if(line!=null){
		        	if( line.indexOf("+CMGS:") < 0 ) continue;
		        	result=true;
		        	break;
				}
	  		}
	  		return result;
		} 
		catch (Exception e) {
		    	System.out.println(e); 
		    	return false;
		}
	}


//------------------------------------------------------------------------------	
	
	public void write(String send_string) {
	  try {
	     // write string to serial port
	     
	     System.out.println("S:"+send_string);
	     outputStream.write(send_string.getBytes());
	     //outputStream.flush();
	     readingnotdone();
	  } catch (IOException e) {
	    	//System.out.println("Exception in write"+e);
	  	}
	}


//------------------------------------------------------------------------------	
	
	public void delete(int rec_no) {
	  try {
	     // write string to serial port
	     
	 	System.out.println("Deleting RecNo:"+rec_no);
	 	this.write("AT+CMGD="+rec_no+"\r\n");
	  } catch (Exception e) {
    	System.out.println("Exception in write"+e);
  	  }
	}
	
//-------------------------------------------------------------------------

	    public boolean readingdone(){
	    	return readingdone;
	    } 
//-------------------------------------------------------------------------
	    public void readingnotdone(){
	    	readingdone=false;
	    	idle_time=0;
	    }	
//-------------------------------------------------------------------------
	    public StringBuffer getline(int timeout) {
	    	
    	try{
	    	
			while(timeout>0){
				if (lineavailable) {
					 linegot=new StringBuffer(line_read);
					 lineavailable=false;
					 return linegot;
				}
				timeout -=10;
				Thread.sleep(10);				
			}
    	}		
		catch(Exception e){
			System.out.println("Exception in getline"+e);
		}		    	
			return null;		
	    }
	    
	void readinit(){
	    	
//	    	for(int i=0;i<MAX_LINES;i++){
//		    	lines_read[i]=new StringBuffer("");
//	    	}
		    overflowed=false;
		    last_line_read=-1;
 	        idle_time=0;
 	        readingdone=false;
	    	lineavailable=false;
	}

	    
	    
	public void run(){
		
	    readinit();
	    
		while (true){
	    	try{
		    	
	       		m=line_inread.length();
	       		if(m>0 & idle_time> 500){
			        System.out.println("R:"+line_inread);	
	       			if(!lineavailable) {line_read=new StringBuffer(line_inread);lineavailable=true;}
	       			line_inread=new StringBuffer("");
	       			Thread.sleep(20);
	       		}
		    	
		       n=bis.available();
		       
		       if( n< 1){
		       	Thread.sleep(10);
		       	idle_time += 10;
		       	if(!readingdone && idle_time>3000) { readingdone=true; System.out.println("Reading Done!");}
		       	continue;
		       }
		       idle_time=0;	
		       readingdone=false;
		       c =  bis.read(); 
		       if (c == -1 ) continue;
               if (c=='\r') continue;		       
		       
		       x= (char) (c&0x0ff);
		       if ( c!='\r')	line_inread.append( x);
		       	if (c=='\n')  {
		       		m=line_inread.length();
		       		if(m>0){
				        System.out.println("R:"+line_inread);	
		       			if(!lineavailable) {line_read=new StringBuffer(line_inread);lineavailable=true;}
		       			line_inread=new StringBuffer("");
		       			Thread.sleep(20);
		       		}
		       	}
		       
		   
	    }//try
	    catch(Exception e){
	    	System.out.println("Exception in read lines"+e);
			System.exit(0);
	    	
	    }		
    } //while
  }//run
    

}//class Modem




