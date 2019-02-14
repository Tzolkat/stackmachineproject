// Custom exception stub for the virtual machine assembler.
// Author: Jason Jones
// =========================================================================================
package net.tzolkat.StackMachineRuntime;

public class VMAssemblyException extends Exception
{
    public VMAssemblyException (String message, Exception cause)
    {
        super(message, cause);
    }
}
