package osp.Threads;
import java.util.Vector;
import java.util.Enumeration;
import osp.Utilities.*;
import osp.IFLModules.*;
import osp.Tasks.*;
import osp.EventEngine.*;
import osp.Hardware.*;
import osp.Devices.*;
import osp.Memory.*;
import osp.Resources.*;

/**
   This class is responsible for actions related to threads, including
   creating, killing, dispatching, resuming, and suspending threads.

We will be using Multi-level feedback queue scheduling.


   @OSPProject Threads
*/
public class ThreadCB extends IflThreadCB 
{
    
/*creating a variable to hold the ready queue*/
	private static GenericList readyQueue;

/**
       The thread constructor. Must call 

       	   super();

       as its first statement.

       @OSPProject Threads
    */
    public ThreadCB()
    {
	super (); //calling super in constructor.
    }

    /**
       This method will be called once at the beginning of the
       simulation. The student can set up static variables here.
       
       @OSPProject Threads
    */
    public static void init()
    {
	/*Will be inititlaizing a the queue here, where ready to run threads will wait here for the CPU.*/
	readyQueue = new GenericList();

    }

    /** 
        Sets up a new thread and adds it to the given task. 
        The method must set the ready status 
        and attempt to add thread to task. If the latter fails 
        because there are already too many threads in this task, 
        so does this method, otherwise, the thread is appended 
        to the ready queue and dispatch() is called.

	The priority of the thread can be set using the getPriority/setPriority methods. However, OSP itself doesn't care what the actual value of the priority is. These methods are just provided in case priority scheduling is required.

	@return thread or null

        @OSPProject Threads
    */
    static public ThreadCB do_create(TaskCB task)
    {
	
	MyOut.print("osp.Threads.ThreadCB", "Entering do_create()");

	/*null check*/
      if(task == null)
	{
	dispatch();
	return null;
	}

	/*lets check if adding a new thread will exceed the number  	of permitted threads for the task and if it will then we dipatch*/

	if (task.getThreadCount() >= MaxThreadsPerTask) {		   
	    MyOut.print("osp.Threads.ThreadCB",
			"new thread creation failed because"
			+ " the maximum number of threads for "
			+ task + " has reached");
	    dispatch();
	    return null;
	}	

	/*we create a new thread here*/
	ThreadCB threadNew = new ThreadCB();
	
	/*print thread created line*/
	MyOut.print("osp.Threads.ThreadCB", "New thread created is:  "+threadNew);

	/*setting up the newly created thread*/
	threadNew.setPriority(task.getPriority());/*set Priority*/
	threadNew.setStatus(ThreadReady); /*set status of thread as 	ready*/

	/*setting the task*/
	threadNew.setTask(task);

	/*next we add the newly created thread to the task*/
if (task.addThread(threadNew ) != SUCCESS) {
	    MyOut.print("osp.Threads.ThreadCB",
			" Failed to add thread "+ threadNew +" to task "+task);
	    dispatch();
	    return null;
	}

/*new thread is in ready state so we append it to the readyQueue*/
readyQueue.append(threadNew);

MyOut.print("osp.Threads.ThreadCB",
	"Thread was successfully added "+threadNew+" to "+task);
		
	dispatch();

/*return the newly created thread which was also added to the task*/
return threadNew;

    }

    /** 
	Kills the specified thread. 

	The status must be set to ThreadKill, the thread must be
	removed from the task's list of threads and its pending IORBs
	must be purged from all device queues.
        
	If some thread was on the ready queue, it must removed, if the 
	thread was running, the processor becomes idle, and dispatch() 
	must be called to resume a waiting thread.
	
	@OSPProject Threads
    */
    public void do_kill()
    {
        MyOut.print(this, "Entering do_kill(" +this + ")");

	/*get the current task*/
	TaskCB task = getTask();

	/*based on the status we will perform the respective 	action*/
	switch (getStatus()) {
        case ThreadReady:
	    // Delete thread from ready queue.
	    readyQueue.remove(this);
	    break;
	case ThreadRunning:
	try
     {
      if(this == MMU.getPTBR().getTask().getCurrentThread())
     {
     MMU.setPTBR(null);
     getTask().setCurrentThread(null);		
     }
     }
     catch(NullPointerException e){
	e.printStackTrace();	
	}
	break;
	default:
	
	}   

// Remove thread from task.
        if(getTask().removeThread(this) != SUCCESS) {
	    MyOut.print(this,
			"Could not remove thread "+ this+" from task 				"+task);
		    return;
	}

	 MyOut.print(this, this + " is set to be destroyed");
	
	// Change thread's status to kill.
	setStatus(ThreadKill);

	// We have only one I/O per thread, so we should just
	// cancel it for that device.
        for(int i = 0; i < Device.getTableSize(); i++) {
	    MyOut.print(this, "Removing IORBs on Device " + i);
	    Device.get(i).cancelPendingIO(this);
	}
        // release all resources owned by the thread
        ResourceCB.giveupResources(this);
	dispatch();

	/*After all threads are removed then we kill the task*/
	if (this.getTask().getThreadCount()==0) {
	    MyOut.print(this,
			"After destroying " + this + ": " + 					this.getTask()+ " has no threads left; destroying the 		task");
	    this.getTask().kill();
	}

    }

    /** Suspends the thread that is currenly on the processor on the 
        specified event. 

        Note that the thread being suspended doesn't need to be
        running. It can also be waiting for completion of a pagefault
        and be suspended on the IORB that is bringing the page in.
	
	Thread's status must be changed to ThreadWaiting or higher,
        the processor set to idle, the thread must be in the right
        waiting queue, and dispatch() must be called to give CPU
        control to some other thread.

	@param event - event on which to suspend this thread.

        @OSPProject Threads
    */
    public void do_suspend(Event event)
    {
    MyOut.print(this, "Entering suspend("+this+","+event+")");
	int status = getStatus();                                       
        if(status>=ThreadWaiting)                                       
        {
            setStatus(getStatus()+1);    
        }
	else if(status == ThreadRunning)                                
        {
try
{
if(this==MMU.getPTBR().getTask().getCurrentThread())
{
MMU.setPTBR(null);
getTask().setCurrentThread(null);
setStatus(ThreadWaiting); 
}
}
catch(NullPointerException e){
MyOut.print(this,"Null Pointer Exception in suspend");
dispatch();
}
}
if(!readyQueue.contains(this))
{
event.addThread(this);
}
else
{
readyQueue.remove(this);
}
dispatch();    
}

/** Resumes the thread.
        
	Only a thread with the status ThreadWaiting or higher
	can be resumed.  The status must be set to ThreadReady or
	decremented, respectively.
	A ready thread should be placed on the ready queue.
	
	@OSPProject Threads
    */

    public void do_resume()
    {
	
	if(getStatus() < ThreadWaiting) {
	    MyOut.print(this,
			"Thread"
			+ this + "is not waiting so failed to resume");
	    return;
	}
        MyOut.print(this, "Resuming " + this);

     // Setting thread status.
	if (getStatus() == ThreadWaiting) {
	    setStatus(ThreadReady);
	} else if (getStatus() > ThreadWaiting)
	    setStatus(getStatus()-1);

     /* if the status of the thread is ready then we have to put it in ready quequq*/
	if (getStatus() == ThreadReady)
	    readyQueue.append(this);
	dispatch();

    }

    /** 
        Selects a thread from the run queue and dispatches it. 

        If there is just one theread ready to run, reschedule the thread 
        currently on the processor.

        In addition to setting the correct thread status it must
        update the PTBR.
	
	@return SUCCESS or FAILURE

        @OSPProject Threads
    */

/*******************************Round Robing implementation***********************/

    public static int do_dispatch()
    {
	ThreadCB thread = null;
	try {
	    thread = MMU.getPTBR().getTask().getCurrentThread();
	} catch(NullPointerException e) {
	MyOut.print("osp.Threads.ThreadCB","Null pointer exception while getting current thread");
	}


// If necessary, remove current thread from processor and 
        // reschedule it.
        if(thread != null) {
	    MyOut.print("osp.Threads.ThreadCB",
			"Preempting currently running " + thread);
	    thread.getTask().setCurrentThread(null);
	    MMU.setPTBR(null);
	    thread.setStatus(ThreadReady);
	    readyQueue.append(thread);
	}

// if readyQueue is empty then there are nomore threads
		if(readyQueue.isEmpty())                                    
        {
	MyOut.print("osp.Threads.ThreadCB",
			"no more threads to dispatch");
            MMU.setPTBR(null);
            return FAILURE;
        }
//there are threads in readyQueue then put them in processor
	else
{
 thread = (ThreadCB) readyQueue.removeHead();
 MMU.setPTBR(thread.getTask().getPageTable());
 thread.getTask().setCurrentThread(thread);
 thread.setStatus(ThreadRunning);             

MyOut.print("osp.Threads.ThreadCB","Dispatching " + thread);
}

HTimer.set(50);
return SUCCESS;

    }



/*------------------------------First Com First Serve Implementation------------------*/
/*
public static int do_dispatch()
{		

TaskCB task;
ThreadCB thread;
if(readyQueue.isEmpty()) {		//Check if ready Queue is empty
if(MMU.getPTBR() == null)	
return FAILURE;
return SUCCESS;				//Continue the thread that was already running
	}
		
if(MMU.getPTBR() != null) {
//premept the thread and put it in ready queue
task = MMU.getPTBR().getTask();
task.getCurrentThread().setStatus(ThreadReady);
readyQueue.insert(task.getCurrentThread());
MMU.setPTBR(null);
task.setCurrentThread(null);
}

//The first thread is inserted from the ready queue
thread = (ThreadCB)readyQueue.removeTail();
task = thread.getTask();
thread.setStatus(ThreadRunning);
MMU.setPTBR(task.getPageTable());
task.setCurrentThread(thread);
HTimer.set(50);
return SUCCESS;
    }
*/

    /**
       Called by OSP after printing an error message. The student can
       insert code here to print various tables and data structures in
       their state just after the error happened.  The body can be
       left empty, if this feature is not used.

       @OSPProject Threads
    */
    public static void atError()
    {
        // your code goes here

    }

    /** Called by OSP after printing a warning message. The student
        can insert code here to print various tables and data
        structures in their state just after the warning happened.
        The body can be left empty, if this feature is not used.
       
        @OSPProject Threads
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
