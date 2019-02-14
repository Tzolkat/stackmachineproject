//  Wrapper class for the call stack. Throws net.tzolkat.StackMachineRuntime.VMRuntimeException.
// Author: Jason Jones
// =========================================================================================
package net.tzolkat.StackMachineRuntime;

import java.util.LinkedList;

public class CallStack
{
    // Maximum size of the call stack, to limit recursion to safe levels.
    // =========================================================================================
    private static final int MAX_SIZE = 512;

    // Internal linked list of integers that emulates the call stack.
    // =========================================================================================
    private LinkedList<Integer> callStack;

    // Initializes the call stack for use.
    // -----------------------------------------------------------------------------------------
    public CallStack()
    {
        callStack = new LinkedList<>();
    }

    // push - Pushes an instruction pointer to the stack representing the point to return to.
    // -----------------------------------------------------------------------------------------
    public void push(int iPointer) throws VMRuntimeException
    {
        if (callStack.size() >= MAX_SIZE)
        {
            throw new VMRuntimeException("Maximum recursion depth exceeded.", null);
        }

        callStack.push(iPointer);
    }

    // pop - Pops a saved instruction pointer from the call stack.
    // -----------------------------------------------------------------------------------------
    public int pop() throws VMRuntimeException
    {
        if (callStack.size() < 1)
        {
            throw new VMRuntimeException("You cannot RETURN without first making a CALL.", null);
        }

        return callStack.pop();
    }
}
