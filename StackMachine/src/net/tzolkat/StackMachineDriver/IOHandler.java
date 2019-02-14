// IOHandler - Provides intelligent handling of output.
// Author: Jason Jones
// =========================================================================================
package net.tzolkat.StackMachineDriver;

import static org.fusesource.jansi.Ansi.*;
import org.fusesource.jansi.AnsiConsole;
import net.tzolkat.StackMachineRuntime.IHCIProvider;
import net.tzolkat.StackMachineRuntime.DataStack;
import net.tzolkat.StackMachineRuntime.Instruction;
import java.nio.charset.StandardCharsets;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Scanner;

public class IOHandler implements IHCIProvider, AutoCloseable
{
    // System attributes for the output handler.
    // -----------------------------------------------------------------------------------------
    private Scanner inStream;           // Wrapper around the input stream for this instance.
    private PrintStream outStream;      // Contains the output stream for this instance.
    private PrintStream errStream;      // Contains the error output stream for this instance.
    private PrintStream logStream;      // Contains the log output stream for this instance.
    private int verbosity;              // (0-3) Print log messages at or below this level.
    private boolean debugFlag;          // If TRUE, the stack-trace debugger is active.
    private boolean colorErr;           // If TRUE, colorize the error output.
    private boolean colorLog;           // If TRUE, colorize the log output.
    // -----------------------------------------------------------------------------------------

    // Constructs and initializes the output handler.
    // -----------------------------------------------------------------------------------------
    public IOHandler(File inFile, File outFile, File errFile, File logFile,
                     int verbosity, boolean debugFlag, boolean showColor) throws IOException
    {
        this.verbosity = verbosity;       // Set log level and flags.
        this.debugFlag = debugFlag;
        colorErr = showColor;
        colorLog = showColor;

        if (showColor)                  // Activate color support if color flag is set.
        {
            AnsiConsole.systemInstall();
            colorErr = (errFile == null);
            colorLog = (logFile == null);
        }
                                        // Initialize the input stream.
        inStream = (inFile != null) ? new Scanner(inFile, StandardCharsets.US_ASCII) :
                new Scanner(System.in, StandardCharsets.US_ASCII);

                                        // Initialize the main output stream.
        outStream = (outFile != null) ? new PrintStream(outFile) : System.out;

                                        // Initialize the error output stream.
        errStream = (errFile != null) ? new PrintStream(errFile) : System.err;

                                        // Initialize the log output stream.
        logStream = (logFile != null) ? new PrintStream(logFile) : System.out;
    }

    // close - Flush all the output stream buffers and close them.
    // -----------------------------------------------------------------------------------------
    @Override
    public void close ()
    {
        outStream.flush();
        errStream.flush();
        logStream.flush();

        outStream.close();
        errStream.close();
        logStream.close();

        inStream.close();
    }

    // setDebug - Turns the debug output on or off.
    // -----------------------------------------------------------------------------------------
    public void setDebug (boolean on)
    {
        debugFlag = on;
    }

    // getLine - Gets a single line of input from the user or input file.
    // -----------------------------------------------------------------------------------------
    public String getLine ()
    {
        return inStream.nextLine();
    }

    // printObject - Outputs the string representation of an object o to the main output stream.
    // -----------------------------------------------------------------------------------------
    public void printObject (Object o)
    {
        outStream.print(o.toString());
    }

    // errorObject - Outputs the string representation of an object o to the error stream.
    // -----------------------------------------------------------------------------------------
    public void errorObject (Object o)
    {
        if (colorErr)
        {
            errStream.print(ansi().fgBrightRed().a(o.toString()).reset());
        }
        else
        {
            errStream.print(o.toString());
        }
    }

    // logObject - If allowed by verbosity, outputs the string representation of an object o
    //              to the log stream.
    // -----------------------------------------------------------------------------------------
    public void logObject (Object o, int level)
    {
        if (level <= verbosity)
        {
            if (colorLog)
            {
                logStream.print(ansi().fgCyan().a(o.toString()).reset());
            }
            else
            {
                logStream.print(o.toString());
            }
        }
    }

    // debug - Given a reference to the data stack and current instruction, if the debug flag
    //          is set, prints the stack-debug line for the current instruction to the log
    //          stream.
    // -----------------------------------------------------------------------------------------
    public void debug (DataStack stack, Instruction current)
    {
        if (debugFlag)
        {
            if (colorLog)
            {
                logStream.print(ansi().fgDefault().a(stack.toString() + ": ")
                        .fgBrightYellow().a(current.getName())
                        .reset().a(System.lineSeparator()));
            }
            else
            {
                logStream.print(stack.toString() + ": " + current.getName() +
                        System.lineSeparator());
            }
        }
    }
}
