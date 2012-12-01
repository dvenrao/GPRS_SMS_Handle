1. Install java and mysql in your machine

a)
Set Environment Variables of the system by going to mycomputer/properties/advanced/environment:

User Variables:
JAVA_HOME:C:\Program Files\Java\jdk1.5.0_04;
PATH:C:\Program Files\Java\jdk1.5.0_04\bin;

System Variables:

CLASSPATH:C:\Program Files\Java\jdk1.5.0_04\lib\tools.jar;C:\Program Files\Java\jdk1.5.0_04\lib\dt.jar;C:\Program Files\Java\jdk1.5.0_04\lib\comm.jar;C:\Program Files\Java\jdk1.5.0_04\jre\lib\ext\RXTXcomm.jar;

b)
COPY comm.jar     file to C:\Program Files\Java\jdk1.5.0_04\lib\comm.jar;
copy RXTXcomm.jar file to C:\Program Files\Java\jdk1.5.0_04\jre\lib\ext

2. Plug in the adaptor and find the new serial communiation port that has been created ( could be COM5 or COM29 something like that)
   Run the ServerSmsHandle java program like

   java -Djava.library.path=..\SerialDriver ServerSmsHandle COM9


