package osp.Devices;
import java.util.*;
import osp.IFLModules.*;
import osp.Hardware.*;
import osp.Interrupts.*;
import osp.Threads.*;
import osp.Utilities.*;
import osp.Tasks.*;
import osp.Memory.*;
import osp.FileSys.*;

/**
    The disk interrupt handler.  When a disk I/O interrupt occurs,
    this class is called upon the handle the interrupt.

    @OSPProject Devices
*/
public class DiskInterruptHandler extends IflDiskInterruptHandler {
	/** 
        Handles disk interrupts. 
        
        This method obtains the interrupt parameters from the 
        interrupt vector. The parameters are IORB that caused the 
        interrupt: (IORB)InterruptVector.getEvent(), 
        and thread that initiated the I/O operation: 
        InterruptVector.getThread().
        The IORB object contains references to the memory page 
        and open file object that participated in the I/O.
        
        The method must unlock the page, set its IORB field to null,
        and decrement the file's IORB count.
        
        The method must set the frame as dirty if it was memory write 
        (but not, if it was a swap-in, check whether the device was 
        SwapDevice)

        As the last thing, all threads that were waiting for this 
        event to finish, must be resumed.

        @OSPProject Devices 
    */
	public void do_handleInterrupt() {
		//Obtain information about the interrupt from the vector
		IORB iorb = (IORB) InterruptVector.getEvent();
		//open file handle associated must be decremented
		iorb.getOpenFile().decrementIORBCount();

		//if open file has closepending flag set and iorbcount is 0 then the file is to be closed
		if (iorb.getOpenFile().closePending && iorb.getOpenFile().getIORBCount() == 0) iorb.getOpenFile().close();

		//page associated with iorb is to be unlocked.
		iorb.getPage().unlock();

		//if status of thread is kill then we stop execution
		if (iorb.getThread().getStatus() == ThreadKill) return;

		//if i/o operation is not a swap 
		if (iorb.getDeviceID() != SwapDeviceID) {

			//set the frame associated with the page is set to true
			iorb.getPage().getFrame().setReferenced(true);

			//if iotype is write
			if (iorb.getIOType() == MemoryWrite)
			//then we set the frame as clean                        
			iorb.getPage().getFrame().setDirty(true);
		} else
		//if i/o was directed to the swap device and iorb 
		{
			//if thread status is kill
			if (iorb.getThread().getStatus() != ThreadKill)
			//then set the frame as clean                    
			iorb.getPage().getFrame().setDirty(false);
		}

		//if thread status is live
		if (iorb.getThread().getTask().getStatus() != TaskLive)
		//then set the frame of the page as unreseerved, for the thread's task          
		iorb.getPage().getFrame().setUnreserved(iorb.getThread().getTask());

		//then threads waiting on the iorb must be awake        
		iorb.notifyThreads();

		//the device is set to idle
		Device dev = Device.get(iorb.getDeviceID());
		dev.setBusy(false);

		//the device is set to service a new I/O request by dequeing.
		iorb = dev.dequeueIORB();
		if (iorb != null) {
			dev.startIO(iorb);
		}
		//Finally, a new thread is to be dispatched.
		ThreadCB.dispatch();
	}


	/*
       Feel free to add methods/fields to improve the readability of your code
    */

}

/*
      Feel free to add local classes to improve the readability of your code
*/