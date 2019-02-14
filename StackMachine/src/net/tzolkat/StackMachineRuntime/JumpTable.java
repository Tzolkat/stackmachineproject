// Wrapper class for jump table. Used by assembler to interpret labels. Case insensitive.
// Author: Jason Jones
// =========================================================================================
package net.tzolkat.StackMachineRuntime;

import java.util.HashMap;

public class JumpTable
{
    // Internal hashmap, maps label names to label objects.
    // =========================================================================================
    private HashMap<String, Label> jumpTable;

    // Initialize the jump table.
    // -----------------------------------------------------------------------------------------
    public JumpTable ()
    {
        jumpTable = new HashMap<>();
    }

    // exists - Returns true if a label with the specified name exists.
    // -----------------------------------------------------------------------------------------
    public boolean exists (String name)
    {
        return jumpTable.containsKey(name.toUpperCase());
    }

    // get - Gets instance of the label specified by name. Throws exception if doesn't exist.
    // -----------------------------------------------------------------------------------------
    public Label get (String name) throws VMAssemblyException
    {
        if (!exists(name))
        {
            throw new VMAssemblyException("Unknown symbol: " + name.toUpperCase(), null);
        }

        return jumpTable.get(name.toUpperCase());
    }

    // add - Adds the specified label to the map. Throws an exception if it already exists.
    // -----------------------------------------------------------------------------------------
    public void add (String name, Label label) throws VMAssemblyException
    {
        if (exists(name))
        {
            throw new VMAssemblyException("Duplicate label definition: " + name.toUpperCase(), null);
        }

        jumpTable.put(name.toUpperCase(), label);
    }

    // remove - Removes the specified label from the map. Throws an exception if not exists.
    // ------------------------------------------------------------------------------------------
    public void remove (String name) throws VMAssemblyException
    {
        if (!exists(name))
        {
            throw new VMAssemblyException("Cannot remove nonexistent symbol: " + name.toUpperCase(), null);
        }

        jumpTable.remove(name.toUpperCase());
    }
}
