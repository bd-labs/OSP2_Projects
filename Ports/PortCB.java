package osp.Ports;

import java.util.*;
import osp.IFLModules.*;
import osp.Threads.*;
import osp.Tasks.*;
import osp.Memory.*;
import osp.Utilities.*;

/**
   The studends module for dealing with ports. The methods 
   that have to be implemented are do_create(), 
   do_destroy(), do_send(Message msg), do_receive(). 


   @OSPProject Ports
*/

public class PortCB extends IflPortCB
{
//maintain an In and out buffer
private int bufferIn;
    private int bufferOut;
    /**
       Creates a new port. This constructor must have

	   super();

       as its first statement.

       @OSPProject Ports
    */
    public PortCB()
    {
        // your code goes here
	
	//calling the super method in the PortCB constructor
//PortCB constructor called
	super();
    }

    /**
       This method is called once at the beginning of the
       simulation. Can be used to initialize static variables.

       @OSPProject Ports
    */
    public static void init()
    {
        // your code goes here
	System.out.println("calling the init method for PortCB class");

    }

    /** 
        Sets the properties of a new port, passed as an argument. 
        Creates new message buffer, sets up the owner and adds the port to 
        the task's port list. The owner is not allowed to have more 
        than the maximum number of ports, MaxPortsPerTask.

        @OSPProject Ports
    */
    public static PortCB do_create()
    {
        // your code goes here

System.out.println("PortCB do_create called");
// A new port is being created here
PortCB newPort = new PortCB();

TaskCB currentTask = null;                                                                 
        try {
	//get the requesting thread
        currentTask = MMU.getPTBR().getTask();                                            
         } catch (NullPointerException e){        
System.out.println("Null pointer exception while getching the current task"+e);
         }
        
//get the port count of the current task
        int currentPortNum = currentTask.getPortCount();

//Check if adding a new port will exceed the allowed maximum
        if(currentPortNum == MaxPortsPerTask)
{
 
System.out.println("Maximum ports exceeded");
              return null;
}

//Check if adding the port to the task failed return null
        if( currentTask.addPort(newPort) == FAILURE){
	System.out.println("Adding port failed");
               return null;
           }
        
	//set the port to the current task
        newPort.setTask(currentTask);

	//set the port status as live
        newPort.setStatus(PortLive);
        

	//initialize the new port buffers
        newPort.bufferIn = 0;
        newPort.bufferOut = 0;

	//return the newly created port
        return newPort; 

    }

    /** Destroys the specified port, and unblocks all threads suspended 
        on this port. Delete all messages. Removes the port from 
        the owners port list.
        @OSPProject Ports
    */
    public void do_destroy()
    {
        // your code goes here
System.out.println("PortCB do_destroy called");

//set status of the port as destroyed	
this.setStatus(PortDestroyed);
        
//notify the threads
        this.notifyThreads();
        
//remove the port from the task
        this.getTask().removePort(this);

//port's owner task is set to null 
        this.setTask(null);

    }

    /**
       Sends the message to the specified port. If the message doesn't fit,
       keep suspending the current thread until the message fits, or the
       port is killed. If the message fits, add it to the buffer. If 
       receiving threads are blocked on this port, resume them all.

       @param msg the message to send.

       @OSPProject Ports
    */
    public int do_send(Message msg)
    {
System.out.println("PortCB do_send called");
        // your code goes here
//Here we check if the message is well-formed or not
if( msg == null || (msg.getLength() > PortBufferLength)){
System.out.println("message is not well formed");
               return FAILURE;
          }
       
//SystemEvent constructor is used to create a new system event
       SystemEvent newEvent = new SystemEvent("send_msg_suspension");
              
       TaskCB currentTask = null;                                                                 //get the requesting thread
       ThreadCB currentThread = null;

//getting the current task
       try {
          currentTask = MMU.getPTBR().getTask();                                            
          currentThread = currentTask.getCurrentThread();
         }catch (NullPointerException e){        
System.out.println("Null pointer error in getting the current task");
         }       

//current thread is suspened here
       currentThread.suspend(newEvent);
       
       int bufferRoom;

//set suspendMsg flag here
       boolean suspendMsg = true;
       while(suspendMsg)   {
            
//if the destination thread does not have enough room in the //message buffer, the thread has to be suspended on that port 

            if( this.bufferIn < this.bufferOut){
                   bufferRoom = this.bufferOut - this.bufferIn;
              }
            else if( this.bufferIn == this.bufferOut){
                   if(this.isEmpty()){
                         bufferRoom = PortBufferLength;
                      }
                   else{
                         bufferRoom = 0;
                      }
              }
            else{
                   bufferRoom = PortBufferLength + this.bufferOut - this.bufferIn;
              }
            
//suspendmsg flag is false
            if( msg.getLength() <= bufferRoom){
                   suspendMsg = false;
              }
            else{
//suspend the current thread
                   currentThread.suspend(this);
              }
            
//if status is kill then remove thread
            if( currentThread.getStatus() == ThreadKill){
		System.out.println("Current thread status is kill");
                   this.removeThread(currentThread);
                   return FAILURE;
              }

            if( this.getStatus() != PortLive){
                  newEvent.notifyThreads();
                  return FAILURE;
              }
 
          }

//update the message buffer of the port       
       this.appendMessage(msg);

//notify the threads that are waiting on the 
       this.notifyThreads();

//if buffer was previously empty then notify the threads that are waiting on the port in receive mode
       this.bufferIn = (this.bufferIn + msg.getLength()) % PortBufferLength;

       newEvent.notifyThreads();

System.out.println("message successfully sent");

//return success if everything is done correctly
       return SUCCESS;
    }

    /** Receive a message from the port. Only the owner is allowed to do this.
        If there is no message in the buffer, keep suspending the current 
	thread until there is a message, or the port is killed. If there
	is a message in the buffer, remove it from the buffer. If 
	sending threads are blocked on this port, resume them all.
	Returning null means FAILURE.

        @OSPProject Ports
    */
    public Message do_receive() 
    {
System.out.println("PortCB do_recieve called");

        // your code goes here
TaskCB currentTask = null;                                                                 //get the requesting thread
       ThreadCB currentThread = null;

//get the current task from the pagetable
       try {
          currentTask = MMU.getPTBR().getTask();                                            
          currentThread = currentTask.getCurrentThread();
         }catch (NullPointerException e){
System.out.println("Null pointer when fetching the current thread"+e);        
         }

//if the task is not current
       if(this.getTask() != currentTask){
              return null;
         }       
 
//create a new SystemEvent
       SystemEvent newEvent = new SystemEvent("receive_msg_suspension");

//suspend thread on the newly created event
       currentThread.suspend(newEvent);
       
       boolean suspendMsg = true;
       while(suspendMsg){
           if(this.isEmpty()){
                 currentThread.suspend(this);
//suspend the current thread
             }
           else{
                 suspendMsg = false;
             }

//if status is kill then remove the thread and notify the thread of the event
            if( currentThread.getStatus() == ThreadKill){
                   this.removeThread(currentThread);
                   newEvent.notifyThreads();
                   return null;
              }

            if( this.getStatus() != PortLive){
                  newEvent.notifyThreads();
                  return null;
              }   
          }
       
//if everything goes right then consume a message from the port buffer 
       Message currentMsg = this.removeMessage();
       this.bufferOut = (this.bufferOut + currentMsg.getLength()) % PortBufferLength; 

//notify the threads waiting on that port
       this.notifyThreads();
       newEvent.notifyThreads();

System.out.println("PortCB message receive called: "+currentMsg);

//return the current message from the 
       return currentMsg; 

    }

    /** Called by OSP after printing an error message. The student can
	insert code here to print various tables and data structures
	in their state just after the error happened.  The body can be
	left empty, if this feature is not used.
	
	@OSPProject Ports
    */
    public static void atError()
    {
        // your code goes here

    }

    /** Called by OSP after printing a warning message. The student
	can insert code here to print various tables and data
	structures in their state just after the warning happened.
	The body can be left empty, if this feature is not used.
     
	@OSPProject Ports
    */
    public static void atWarning()
    {
        // your code goes here

    }


    /*
       Feel free to add methods/fields to improve the readability of your code
    */

}

/*
      Feel free to add local classes to improve the readability of your code
*/