// Code segment Wrapper class. Implements a random access list of instructions.
// Supports fetching, replacement, and appending, but not insertion or removal as that
//  could break the instruction pointer of the VM.
// Author: Jason Jones
// =========================================================================================
package net.tzolkat.StackMachineRuntime;

import java.util.ArrayList;

public class CodeSegment
{
    // Internal arraylist of assembled instructions, in order of occurrence.
    // =========================================================================================
    private ArrayList<Instruction> codeSegment;

    // Initializes the code segment.
    // -----------------------------------------------------------------------------------------
    public CodeSegment ()
    {
        codeSegment = new ArrayList<>();
    }

    // get - Returns the instruction at location specified by iPointer.
    // -----------------------------------------------------------------------------------------
    public Instruction get (int iPointer) throws VMRuntimeException
    {
        if (iPointer >= codeSegment.size() || iPointer < 0)
        {
            throw new VMRuntimeException("Instruction pointer out of bounds.", null);
        }

        return codeSegment.get(iPointer);
    }

    // add - Adds an assembled instruction to the code segment.
    // -----------------------------------------------------------------------------------------
    public void add (Instruction instruction)
    {
        codeSegment.add(instruction);
    }

    // replace - Replaces an assembled instruction in the code segment with a new instruction.
    // -----------------------------------------------------------------------------------------
    public void replace (int iPointer, Instruction instruction) throws VMAssemblyException
    {
        if (iPointer >= codeSegment.size() || iPointer < 0)
        {
            throw new VMAssemblyException("No instruction exists at location: " + iPointer, null);
        }

        codeSegment.set(iPointer, instruction);
    }

    // getSize - Returns the current size of the code segment, in number of instructions.
    // -----------------------------------------------------------------------------------------
    public int getSize ()
    {
        return codeSegment.size();
    }
}
