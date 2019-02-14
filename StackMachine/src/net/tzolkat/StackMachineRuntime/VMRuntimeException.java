// Custom exception stub for the virtual machine run time.
// Author: Jason Jones
// =========================================================================================
package net.tzolkat.StackMachineRuntime;

public class VMRuntimeException extends Exception
{
    public VMRuntimeException (String message, Exception cause)
    {
        super(message, cause);
    }
}
