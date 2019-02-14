// JCommander parameter converter for converting file names to files.
// Author: Jason Jones
// =========================================================================================
package net.tzolkat.StackMachineDriver;

import com.beust.jcommander.IStringConverter;
import java.io.File;

public class FileConverter implements IStringConverter<File>
{
    @Override
    public File convert(String fileName)
    {
        return new File(fileName);
    }
}
