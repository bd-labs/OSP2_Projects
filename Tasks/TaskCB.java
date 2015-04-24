package osp.Tasks;

import java.util.Vector;
import osp.IFLModules.*;
import osp.Threads.*;
import osp.Ports.*;
import osp.Memory.*;
import osp.FileSys.*;
import osp.Utilities.*;
import osp.Hardware.*;
import osp.FileSys.*;


/**
    The student module dealing with the creation and killing of
    tasks. A task acts primarily as a container for threads and as a holder of resources.  
    Execution is associated entirely with threads.  
    The primary methods that the student will implement are do_create(TaskCB) and do_kill(TaskCB). 
    The student can choose how to keep track of which threads are part of a task.  In this
    implementation, an array is used.

    @OSPProject Tasks
*/
public class TaskCB extends IflTaskCB
{

/*variable declaration */

int pid; 	 	 /*task id*/

/*PageTable class variable */
static PageTable pPageTable; 	 	

private OpenFile pSwapFile = null;


/*GenericList class variable declaration*/
private GenericList pThreadList; /*will contain threads*/
private GenericList pPortList; 	 /*will be used for ports*/	
private GenericList pFileList; /*will hold the file list*/


public TaskCB()
    {
/**   The task constructor. Must have super()as its first statement.*/
		super();
		pid = getID(); /*get the task's ID number. */

        /* pagetable creates and allocates space in memory*/
        pPageTable = new PageTable(this);

        pThreadList = new GenericList();
        pPortList = new GenericList();
        pFileList = new GenericList();

		/*create file system entry*/
        FileSys.create(SwapDeviceMountPoint+String.valueOf(pid),
                (int) Math.pow(2,MMU.getVirtualAddressBits()));

		/*open mount point for swap*/
        pSwapFile = OpenFile.open(SwapDeviceMountPoint +
                String.valueOf(pid), this);

        /*here we set the swap file*/
        setSwapFile(pSwapFile);
        
    }

    /**
       This method is called once at the beginning of the
       simulation. Can be used to initialize static variables.

       @OSPProject Tasks
    */
    public static void init()
    {
        // your code goes here
    }

    /** 
        Sets the properties of a new task, passed as an argument. 
        
        Creates a new thread list, sets TaskLive status and creation time,
        creates and opens the task's swap file of the size equal to the size
	(in bytes) of the addressable virtual memory.

	@return task or null

        @OSPProject Tasks
    */
    static public TaskCB do_create()
    {

    /*Create a new task*/	
	TaskCB task = new TaskCB();
	
	/*set pagetable entry for that task*/
	task.setPageTable(pPageTable); 	 	
	
	/*gets the hardclock (simulated) time and sets the task creation time as that*/
	task.setCreationTime(HClock.get());
	
	/*check if the task's swap file is null, if it is null we dispatch and return null*/
	/*Basically this is a null pointer checker*/
	if (task.getSwapFile() == null) { 	 	
	ThreadCB.dispatch(); 	 	
	return null; 	 	
	} 	 	
	
	/*create the task in thread*/
	ThreadCB.create(task); 	 	
	ThreadCB.dispatch(); 	 	
	
	/*sets the status of the task as live*/
	task.setStatus(TaskLive); 	 	
	
	/*return the newly created task*/
	return task; 	 
    }

    /**
       Kills the specified task and all of it threads. 
       Sets the status TaskTerm, frees all memory frames 
       (reserved frames may not be unreserved, but must be marked 
       free), deletes the task's swap file.
	
       @OSPProject Tasks
    */
    public void do_kill()
    {

        /*fetches all the threads and removes*/
        for (int i=do_getThreadCount() - 1; i >= 0; i--) {
            do_removeThread((ThreadCB)pThreadList.getAt(i));
        }

        /* fetches all the ports and clears them */
        for (int i=do_getPortCount() - 1; i >= 0; i--) {
            do_removePort((PortCB)pPortList.getAt(i));
        }

        /*fetches all the open files and closes them and removes the entry*/
        for (int i=do_getFilesCount() - 1; i >= 0; i--) {
            do_removeFile((OpenFile)pFileList.getAt(i));
        }

        /* Swap the file*/
        this.pSwapFile.close();
        
        /*delete the file system entry*/
        FileSys.delete(SwapDeviceMountPoint + String.valueOf(this.pid));
        
        /*sets the status of the task as terminated*/
        this.setStatus(TaskTerm);

        /*deallocates the memory for the page table*/
        pPageTable.deallocateMemory();
    }

    /** 
	Returns a count of the number of threads in this task. 
	
	@OSPProject Tasks
    */
    public int do_getThreadCount()
    {
    	return pThreadList.length();
    }

/**
    Returns a count of the number of open files in this task.
    */
    public int do_getFilesCount()
    {
        return pFileList.length();
    }

    /**
       Adds the specified thread to this task. e
       @return FAILURE, if the number of threads exceeds MaxThreadsPerTask;
       SUCCESS otherwise.
       
       @OSPProject Tasks
    */
    public int do_addThread(ThreadCB thread)
    {
    	/*checks if the current thread count is less that that of the maximum allowed threads
    	 * if it is less then we append the thread to the Thread List and then return success
    	 * else we return failure*/
   
         if (do_getThreadCount() < ThreadCB.MaxThreadsPerTask) {
            pThreadList.append((ThreadCB) thread);
            return SUCCESS;
        } else {
            return FAILURE;
        }

    }

    /**
       Removes the specified thread from this task. 		

       @OSPProject Tasks
    */
    public int do_removeThread(ThreadCB thread)
    {
    	
    	/*checks if the list contains the thread, if yes then we remove the thread from the list 
    	 * else we return failure
    	 */
    	
    	if (pThreadList.contains((ThreadCB) thread)) {
            pThreadList.remove((ThreadCB) thread);
            return SUCCESS;
        } else {
            return FAILURE;
        }

    }

    /**
       Return number of ports currently owned by this task. 

       @OSPProject Tasks
    */
    public int do_getPortCount()
    {
    	return pPortList.length();
    }

    /**
       Add the port to the list of ports owned by this task.
	
       @OSPProject Tasks 
    */ 
    public int do_addPort(PortCB newPort)
    {
     /*if number of ports is less than the maximum allowed then we add the new port to the list
      * else we return failure
      */
    	if (do_getPortCount() < PortCB.MaxPortsPerTask) {
            pPortList.append((PortCB) newPort);
            return SUCCESS;
        } else {
            return FAILURE;
        }

    }

    /**
       Remove the port from the list of ports owned by this task.

       @OSPProject Tasks 
    */ 
    public int do_removePort(PortCB oldPort)
    {
    	/* if the port is in the list we remove the port from the list
    	 * or we return failure 
    	 */
         if (pPortList.contains((PortCB) oldPort)) {
            pPortList.remove((PortCB) oldPort);
            return SUCCESS;
        } else {
            return FAILURE;
        }

    }

    /**
       Insert file into the open files table of the task.

       @OSPProject Tasks
    */
    public void do_addFile(OpenFile file)
    {
        pFileList.append((OpenFile) file);
    }

    /** 
	Remove file from the task's open files table.

	@OSPProject Tasks
    */
    public int do_removeFile(OpenFile file)
    {
     
    	/*check if the file is in the list then remove if exists*/
    	if (pFileList.contains((OpenFile) file)) {
            pFileList.remove((OpenFile) file);
            return SUCCESS;
        } else {
            return FAILURE;
        }

    }

    /**
       Called by OSP after printing an error message. The student can
       insert code here to print various tables and data structures
       in their state just after the error happened.  The body can be
       left empty, if this feature is not used.
       
       @OSPProject Tasks
    */
    public static void atError()
    {
        // your code goes here
    }

    /**
       Called by OSP after printing a warning message. The student
       can insert code here to print various tables and data
       structures in their state just after the warning happened.
       The body can be left empty, if this feature is not used.
       
       @OSPProject Tasks
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
