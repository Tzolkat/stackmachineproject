// IHCIProvider - Interface that defines the functions needed by the VM for non-file I/O.
// Author: Jason Jones
// =========================================================================================
package net.tzolkat.StackMachineRuntime;

public interface IHCIProvider
{
    int LOG_VERBOSE = 3;                        // Named constants for log level.
    int LOG_INFO = 2;
    int LOG_EVENT = 1;
    int LOG_WARNING = 0;

    String getLine ();                          // Gets a line of text from the user.

    void printObject (Object o);                // Prints an object to main output.
    void errorObject (Object o);                // Prints an object to error output.
    void logObject (Object o, int level);       // Prints an object to log output at level.
                                                // Prints debug for current instruction.
    void debug (DataStack stack, Instruction current);

    void setDebug (boolean on);                 // Turns the debugger on/off.
}
