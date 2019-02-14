// Data stack wrapper class. Throws VMRuntimeException.
// Author: Jason Jones
// =========================================================================================
package net.tzolkat.StackMachineRuntime;

import java.util.LinkedList;

public class DataStack
{
    // File system static parameters.
    // =========================================================================================
    private static final int MAX_SIZE = 32768;

    // File system static parameters.
    // =========================================================================================
    private LinkedList<Object> dataStack;

    // Create and initialize the data stack.
    // -----------------------------------------------------------------------------------------
    public DataStack ()
    {
        dataStack = new LinkedList<>();
    }

    // Throws exception if the stack isn't deep enough to complete the calling operation.
    // -----------------------------------------------------------------------------------------
    private void stackUnderflowCheck (int sizeNeeded) throws VMRuntimeException
    {
        if (dataStack.size() < sizeNeeded)
        {
            throw new VMRuntimeException("Stack Underflow.", null);
        }
    }

    // Throws exception if the item is not the correct type for the calling operation.
    // -----------------------------------------------------------------------------------------
    private void typeCheck (Object o, Class type) throws VMRuntimeException
    {
        if (!type.isInstance(o))
        {
            throw new VMRuntimeException(type.getName() +" expected.", null);
        }
    }

    // toString - Returns the stack in a readable form. Reads from the bottom up.
    // -----------------------------------------------------------------------------------------
    @Override
    public String toString ()
    {
        StringBuilder sb = new StringBuilder("( ");
        for (int i = (dataStack.size() - 1); i >= 0; i--)
        {
            Object o = dataStack.get(i);
            if (o instanceof Character)
            {
                o = "'" + o.toString() + "'";
            }

            sb.append(o.toString()
                    .replaceAll("' '", "SPACE")
                    .replaceAll("'\t'", "TAB")
                    .replaceAll("'\n'", "NEWLINE"));

            if (i > 0)
            {
                sb.append(", ");
            }
        }
        sb.append(" )");
        return sb.toString();
    }

    // push - Pushes an object onto the stack. Throws an exception if the stack is at max size.
    // -----------------------------------------------------------------------------------------
    public void push (Object o) throws VMRuntimeException
    {
        if (dataStack.size() >= MAX_SIZE)
        {
            throw new VMRuntimeException("Stack overflow.", null);
        }

        dataStack.push(o);
    }

    // pushCharRange - Given a string, pushes it onto the stack as a character range.
    // -----------------------------------------------------------------------------------------
    public void pushCharRange (String range) throws VMRuntimeException
    {
        for (int i = 0; i < range.length(); i++)
        {
            push(range.charAt(i));
        }
        push(range.length());
    }

    // pop - Universal pop. Pops an object off the stack. Throws exception if stack is empty.
    // -----------------------------------------------------------------------------------------
    public Object pop () throws VMRuntimeException
    {
        stackUnderflowCheck(1);
        return dataStack.pop();
    }

    // popBool - Typesafe pop for boolean. Throws exception if the item popped is not a boolean.
    // -----------------------------------------------------------------------------------------
    public Boolean popBool () throws VMRuntimeException
    {
        Object t = pop();
        typeCheck(t, Boolean.class);
        return (Boolean)t;
    }

    // popChar - Typesafe pop for character. Throws exception if the item popped is not a char.
    // -----------------------------------------------------------------------------------------
    public Character popChar () throws VMRuntimeException
    {
        Object t = pop();
        typeCheck(t, Character.class);
        return (Character)t;
    }

    // popInt - Typesafe pop for integer. Throws exception if the item popped is not an int.
    // -----------------------------------------------------------------------------------------
    public Integer popInt () throws VMRuntimeException
    {
        Object t = pop();
        typeCheck(t, Integer.class);
        return (Integer)t;
    }

    // popFloat - Typesafe pop for float. Throws exception if the item popped is not a float.
    // -----------------------------------------------------------------------------------------
    public Double popFloat () throws VMRuntimeException
    {
        Object t = pop();
        typeCheck(t, Double.class);
        return (Double)t;
    }

    // popLabel - Typesafe pop for label. Throws exception if the item popped is not a label.
    // -----------------------------------------------------------------------------------------
    public Label popLabel () throws VMRuntimeException
    {
        Object t = pop();
        typeCheck(t, Label.class);
        return (Label)t;
    }

    // popCharRange - Safe pop for character range. Returns the range as a string or throws
    //                 an exception if it finds anything unexpected.
    // -----------------------------------------------------------------------------------------
    public String popCharRange () throws VMRuntimeException
    {
        int size = popInt();

        if (size < 1)
        {
            throw new VMRuntimeException("Range size indicator must be greater than zero.", null);
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < size; i++)
        {
            sb.append(popChar());
        }
        return sb.reverse().toString();
    }

    // dup - Duplicates the topmost item on the stack.
    // -----------------------------------------------------------------------------------------
    public void dup () throws VMRuntimeException
    {
        push(dataStack.peek());
    }

    // swap - Swaps the top two items on the stack.
    // -----------------------------------------------------------------------------------------
    public void swap () throws VMRuntimeException
    {
        stackUnderflowCheck(2);
        dataStack.add(1, dataStack.pop());
    }

    // rotate - Rotates the top n items on the stack clockwise or counterclockwise.
    // -----------------------------------------------------------------------------------------
    public void rotate (int numInvolved, boolean clockwise) throws VMRuntimeException
    {
        if (numInvolved == 0)
        {
            throw new VMRuntimeException("Number of items to rotate must be non-zero.", null);
        }

        stackUnderflowCheck(numInvolved);
        if (clockwise)
        {
            dataStack.add(numInvolved-1, dataStack.pop());
        }
        else
        {
            dataStack.push(dataStack.get(numInvolved-1));
            dataStack.remove(numInvolved);
        }
    }

    // pick - Copies nth item from the (imaginary) top of stack and pushes it to the stack.
    // -----------------------------------------------------------------------------------------
    public void pick (int numFromTop) throws VMRuntimeException
    {
        if (numFromTop < 1)
        {
            throw new VMRuntimeException("Location to pick from must be greater than zero.", null);
        }

        stackUnderflowCheck(numFromTop);
        push(dataStack.get(numFromTop-1));
    }

    // put - Replaces the item at numFromTop with the object o.
    // -----------------------------------------------------------------------------------------
    public void put (Object o, int numFromTop) throws VMRuntimeException
    {
        if (numFromTop < 1)
        {
            throw new VMRuntimeException("Location to put to must be greater than zero.", null);
        }

        stackUnderflowCheck(numFromTop);
        dataStack.set((numFromTop-1), o);
    }

    // depth - Returns the current size of the stack.
    // -----------------------------------------------------------------------------------------
    public int depth ()
    {
        return dataStack.size();
    }

    // join - Joins two stack ranges together.
    // -----------------------------------------------------------------------------------------
    public void join () throws VMRuntimeException
    {
        int size1 = popInt();

        if (size1 < 0)
        {
            throw new VMRuntimeException("Stack range size must be non-negative.", null);
        }

        stackUnderflowCheck(size1 + 1);
        Object o = dataStack.get(size1);
        typeCheck(o, Integer.class);
        int size2 = (Integer)o;

        if (size2 < 0)
        {
            throw new VMRuntimeException("Stack range size must be non-negative.", null);
        }

        stackUnderflowCheck(size1 + size2 + 1);
        dataStack.remove(size1);
        push(size1 + size2);
    }

    // split - Takes a single stack range and splits it at index.
    // -----------------------------------------------------------------------------------------
    public void split (int index) throws VMRuntimeException
    {
        int size = popInt();

        if (size < 1)
        {
            throw new VMRuntimeException("Stack range size must be greater than zero", null);
        }

        stackUnderflowCheck(size);

        if (index < 0 || index >= size)
        {
            throw new VMRuntimeException("Index must be between 0 and " + (size - 1) + ".", null);
        }

        int sizeRight = size - index;
        dataStack.add(sizeRight, index);
        push(sizeRight);
    }
}
