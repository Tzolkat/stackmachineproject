// JCommander parameters definition for command line input.
// Author: Jason Jones
// =========================================================================================
package net.tzolkat.StackMachineDriver;

import com.beust.jcommander.Parameter;
import java.io.File;

public class MachineArgs
{
    // Required parameter indicating the source code file to use.
    // -----------------------------------------------------------------------------------------
    @Parameter(names = {"--sourceFile", "-sf"},
               description = "The source code file you want to run.",
               required = true,
               converter = FileConverter.class)
    public File sourceFile = null;

    // Optional parameter indicating the file to get input from.
    @Parameter(names = {"--inputFile", "-if"},
               description = "File to get input from.",
               converter = FileConverter.class)
    public File inFile = null;

    // Optional parameter indicating the file to direct log messages to.
    // -----------------------------------------------------------------------------------------
    @Parameter(names = {"--logFile", "-lf"},
               description = "File to redirect log output to.",
               converter = FileConverter.class)
    public File logFile = null;

    // Optional parameter indicating the file to direct error messages to.
    // -----------------------------------------------------------------------------------------
    @Parameter(names = {"--errorFile", "-ef"},
               description = "File to redirect error output to.",
               converter = FileConverter.class)
    public File errorFile = null;

    // Optional parameter indicating the file to direct output messages to.
    // -----------------------------------------------------------------------------------------
    @Parameter(names = {"--outFile", "-of"},
               description = "File to redirect main output to.",
               converter = FileConverter.class)
    public File outFile = null;

    // Optional parameter indicating the log level to run at. Takes a number between 0 and 3.
    // -----------------------------------------------------------------------------------------
    @Parameter(names = {"--verbosity", "-v"},
               description = "Defines log verbosity threshold: 0-3 or [Warning|Event|Info|Verbose].",
               converter = VerbosityConverter.class)
    public int verbosity = 0;

    // Optional parameter indicating the debugger should be active.
    // -----------------------------------------------------------------------------------------
    @Parameter(names = {"--debug", "-d"},
               description = "Enables the stack-trace debugger.")
    public boolean debug = false;

    // Optional parameter indicating the error, log, and debug messages should display in color.
    // -----------------------------------------------------------------------------------------
    @Parameter(names = {"--color", "-c"},
               description = "Display error, log, and debug messages in color.")
    public boolean color = false;

    // Optional parameter indicating that the help/usage screen should be displayed.
    // -----------------------------------------------------------------------------------------
    @Parameter(names = {"--help", "-h"},
               description = "Displays this help screen.",
               help = true)
    public boolean dohelp = false;
}
