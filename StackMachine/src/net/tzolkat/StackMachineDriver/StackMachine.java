// Main StackMachine driver program. Responsible for taking command line parameters and
//  using them to execute code in a machine runtime instance.
// Author: Jason Jones
// =========================================================================================
// Usage: StackMachine [options]
//      Options:
//        * --sourceFile, -sf
//              The source code file you want to run.
//          --debug, -d
//              Enables the stack-trace debugger.
//              Default: false
//          --color, -c
//              Display error, log, and debug messages in color.
//              Default: false
//          --inputFile, -if
//              File to get input from.
//          --outFile, -of
//              File to redirect main output to.
//          --errorFile, -ef
//              File to redirect error output to.
//          --logFile, -lf
//              File to redirect log output to.
//          --verbosity, -v
//              Defines log verbosity threshold: 0-3 or [Warning|Event|Info|Verbose].
//              Default: 0
//          --help, -h
//              Displays this help screen.
// =========================================================================================

package net.tzolkat.StackMachineDriver;

import com.beust.jcommander.JCommander;
import net.tzolkat.StackMachineRuntime.MachineRuntime;
import net.tzolkat.StackMachineRuntime.VMAssemblyException;
import net.tzolkat.StackMachineRuntime.VMRuntimeException;

public class StackMachine
{
    // main - Given an array of command line parameters, parses them and runs the VM instance.
    // -----------------------------------------------------------------------------------------
    public static void main (String[] args)
    {
        MachineArgs machineArgs = new MachineArgs();
        JCommander parser = JCommander.newBuilder().addObject(machineArgs).build();
        try
        {
            parser.setProgramName("StackMachine");
            parser.parse(args);         // Parse the command line params.

            if (machineArgs.dohelp)     // If help flag, show usage.
            {
                parser.usage();
                System.exit(0);
            }
        }
        catch (Exception e)             // Parse failed. Print error message.
        {
            System.err.println("Parameter Error: " + e.getMessage());
            parser.usage();
            System.exit(1);
        }

        // At this point the params are parsed. Use them to build the output handler and
        //  machine instance.
        try (IOHandler ioHandler = new IOHandler(machineArgs.inFile, machineArgs.outFile,
                machineArgs.errorFile, machineArgs.logFile, machineArgs.verbosity,
                machineArgs.debug, machineArgs.color))
        {
            try // This try block is just for the VM and assumes outHandler is valid.
            {
                MachineRuntime machine = new MachineRuntime(ioHandler);
                machine.assemble(machineArgs.sourceFile);

                int exitCode = machine.runProgram();
                System.exit(exitCode);
            }
            catch (VMAssemblyException|VMRuntimeException e)
            {
                ioHandler.errorObject(e.getMessage() + "\n");
            }
        }
        catch (Exception e)
        {
            System.err.println("General Error: " + e.getMessage());
        }
    }
}
