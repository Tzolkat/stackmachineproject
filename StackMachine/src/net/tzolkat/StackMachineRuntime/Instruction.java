// Abstract class that all of our instructions will inherit from.
// Author: Jason Jones
// =========================================================================================
package net.tzolkat.StackMachineRuntime;

public abstract class Instruction
{
    // Gets the name of the instruction.
    public abstract String getName ();
    // Runs the instruction's code.
    public abstract void run () throws VMRuntimeException;
}
