// Wrapper class for instruction set. Maps instruction names to single instruction objects.
//  This excludes push, which is both implicit and not a singleton. Names are case
//  insensitive.
// Author: Jason Jones
// =========================================================================================
package net.tzolkat.StackMachineRuntime;

import java.util.HashMap;

public class InstructionSet
{
    // Internal hashmap, maps string name of instruction to the instruction instance.
    // =========================================================================================
    private HashMap<String, Instruction> instructionSet;

    // Initialize the instruction set.
    // -----------------------------------------------------------------------------------------
    public InstructionSet ()
    {
        instructionSet = new HashMap<>();
    }

    // exists - Returns true if an instruction with the specified name exists.
    // -----------------------------------------------------------------------------------------
    public boolean exists (String name)
    {
        return instructionSet.containsKey(name.toUpperCase());
    }

    // get - Gets instance of the instruction represented by name. Throws an exception if
    //        the instruction doesn't exist.
    // -----------------------------------------------------------------------------------------
    public Instruction get (String name) throws VMAssemblyException
    {
        if (!exists(name))
        {
            throw new VMAssemblyException("Unknown symbol: " + name.toUpperCase(), null);
        }

        return instructionSet.get(name.toUpperCase());
    }

    // add - Adds the specified instruction instance and name if it isn't already there.
    // -----------------------------------------------------------------------------------------
    public void add (String name, Instruction instruction)
    {
        instructionSet.putIfAbsent(name.toUpperCase(), instruction);
    }
}
