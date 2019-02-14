// JCommander parameter converter for converting log verbosity level to a range of ints as
//  specified by named constants in the IHCIProvider interface.
// Author: Jason Jones
// =========================================================================================
package net.tzolkat.StackMachineDriver;

import net.tzolkat.StackMachineRuntime.IHCIProvider;
import com.beust.jcommander.IStringConverter;

public class VerbosityConverter implements IStringConverter<Integer>
{
    // isValidIntLevel - Returns true if the parameter name was given in valid numeric
    //                    form, otherwise returns false.
    // -----------------------------------------------------------------------------------------
    private boolean isValidIntLevel (String value)
    {
        try
        {
            int level = Integer.parseInt(value);
            return !(level < 0 || level > 3);
        }
        catch (Exception e)
        {
            return false;
        }
    }

    @Override
    public Integer convert(String s)
    {
        if (isValidIntLevel(s))
        {
            return Integer.parseInt(s);
        }
        else
        {
            switch (s.toUpperCase())
            {
                case "WARNING":
                    return IHCIProvider.LOG_WARNING;
                case "EVENT":
                    return IHCIProvider.LOG_EVENT;
                case "INFO":
                    return IHCIProvider.LOG_INFO;
                case "VERBOSE":
                    return IHCIProvider.LOG_VERBOSE;
                default:
                    return IHCIProvider.LOG_WARNING;
            }
        }
    }
}
