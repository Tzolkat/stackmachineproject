// Label Datatype. Contains label name and instruction it points to. Throws VMRuntimeException.
// Author: Jason Jones
// =========================================================================================
package net.tzolkat.StackMachineRuntime;

public class Label
{
    private String name; // Name of the label as a string.
    private int pointer; // Instruction pointer location the label represents.

    // Initialize the label, given a name and an instruction pointer.
    // -----------------------------------------------------------------------------------------
    public Label(String name, int pointer)
    {
        this.name = name;
        this.pointer = pointer;
    }

    // getName - Gets the name of the label.
    // -----------------------------------------------------------------------------------------
    public String getName ()
    {
        return name;
    }

    // getPointer - Gets the location the label points to.
    // -----------------------------------------------------------------------------------------
    public int getPointer ()
    {
        return pointer;
    }

    // toString - Returns the label in its verbose string form.
    // -----------------------------------------------------------------------------------------
    @Override
    public String toString ()
    {
        return getName() + "{" + getPointer() + "}";
    }
}
