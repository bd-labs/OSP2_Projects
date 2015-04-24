package osp.Devices;

/**
    This class stores all pertinent information about a device in
    the device table.  This class should be sub-classed by all
    device classes, such as the Disk class.

    @OSPProject Devices
*/

import osp.IFLModules.*;
import osp.Threads.*;
import osp.Utilities.*;
import osp.Hardware.*;
import osp.Memory.*;
import osp.FileSys.*;
import osp.Tasks.*;
import java.util.*;

public class Device extends IflDevice {
	private GenericList iorbQueue = null;
	/**
        This constructor initializes a device with the provided parameters.
        As a first statement it must have the following:

            super(id,numberOfBlocks);

        @param numberOfBlocks -- number of blocks on device

        @OSPProject Devices
    */
	public Device(int id, int numberOfBlocks) {

		//super method is called first
		super(id, numberOfBlocks);
		//Create a new list 
		//assign the created list to the queue
		this.iorbQueue = new GenericList();
	}

	/**
       This method is called once at the beginning of the
       simulation. Can be used to initialize static variables.

       @OSPProject Devices
    */
	public static void init() {
		// your code goes here

	}

	/**
       Enqueues the IORB to the IORB queue for this device
       according to some kind of scheduling algorithm.
       
       This method must lock the page (which may trigger a page fault),
       check the device's state and call startIO() if the 
       device is idle, otherwise append the IORB to the IORB queue.

       @return SUCCESS or FAILURE.
       FAILURE is returned if the IORB wasn't enqueued 
       (for instance, locking the page fails or thread is killed).
       SUCCESS is returned if the IORB is fine and either the page was 
       valid and device started on the IORB immediately or the IORB
       was successfully enqueued (possibly after causing pagefault pagefault)
       
       @OSPProject Devices
    */
	public int do_enqueueIORB(IORB iorb) {
		//lock the page associated with the iorb
		if (iorb.getPage().lock(iorb) == FAILURE) return FAILURE;
		//increment the iorb counter of the open file handle associations
		iorb.getOpenFile().incrementIORBCount();

		//set the iorb's cylinder with the disk block
		iorb.setCylinder(iorb.getBlockNumber());

		//if the status of the thread is kill then we stop execution
		if (iorb.getThread().getStatus() == ThreadKill) return FAILURE;

		//if device is busy
		if (this.isBusy()) {
			//put the iorb on the device queue
			this.iorbQueue.append(iorb);
			return SUCCESS;
		} else {
			//if device is idle and thread is alive then we start the I/O operation immediately
			this.startIO(iorb);
			return SUCCESS;
		}
	}

	/**
       Selects an IORB (according to some scheduling strategy)
       and dequeues it from the IORB queue.

       @OSPProject Devices
    */
	public IORB do_dequeueIORB() {

		//if the iorb queue is not empty then we select the IORB from the top of the queue as per FCFS strategy
		if (this.iorbQueue.isEmpty()) return null;
		else return (IORB) this.iorbQueue.removeHead();
	}

	/**
        Remove all IORBs that belong to the given ThreadCB from 
        this device's IORB queue

        The method is called when the thread dies and the I/O 
        operations it requested are no longer necessary. The memory 
        page used by the IORB must be unlocked and the IORB count for 
        the IORB's file must be decremented.

        @param thread thread whose I/O is being canceled

        @OSPProject Devices
    */
	public void do_cancelPendingIO(ThreadCB thread) {
		//for each IORB associated with the thread we unlock the buffer page used by that IORB
		for (int i = this.iorbQueue.length(); i >= 0; ++i) {
			//get the IORB from the queue
			IORB iorb = (IORB) this.iorbQueue.getAt(i);
			if (iorb.getThread().equals(thread)) {
				//unlock the page, can also be done in the device input handler but since we are removing it from the queue here service it here                      
				iorb.getPage().unlock();

				//decrement the IORB counter  
				iorb.getOpenFile().decrementIORBCount();

				//close the open file handle associated with the IORB
				if (iorb.getOpenFile().closePending && iorb.getOpenFile().getIORBCount() == 0) iorb.getOpenFile().close();
				//finally remove it from the queue
				this.iorbQueue.remove(iorb);
			}
		}

	}

	/** Called by OSP after printing an error message. The student can
        insert code here to print various tables and data structures
        in their state just after the error happened.  The body can be
        left empty, if this feature is not used.
        
        @OSPProject Devices
     */
	public static void atError() {
		// your code goes here

	}

	/** Called by OSP after printing a warning message. The student
        can insert code here to print various tables and data
        structures in their state just after the warning happened.
        The body can be left empty, if this feature is not used.
        
        @OSPProject Devices
     */
	public static void atWarning() {
		// your code goes here

	}


	/*
       Feel free to add methods/fields to improve the readability of your code
    */

}

/*
      Feel free to add local classes to improve the readability of your code
*/
