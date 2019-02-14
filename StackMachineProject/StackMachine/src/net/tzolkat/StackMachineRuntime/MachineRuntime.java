// Stack Machine Runtime - Handles assembly and execution of code.
// Author: Jason Jones
// =========================================================================================
package net.tzolkat.StackMachineRuntime;

import java.nio.charset.StandardCharsets;
import java.io.BufferedReader;
import java.io.StringReader;
import java.io.File;
import java.util.Scanner;
import java.io.IOException;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Random;

public class MachineRuntime
{
    // Named Constants.
    // -----------------------------------------------------------------------------------------
    public static final String  VERSION         = "0.3.4";  // Current VM Runtime version.
    private static final int    MAX_EXEC_DEPTH  = 16;       // Max EXECUTE recursion lvl.

    // =========================================================================================
    // Core Machine Stuff
    // =========================================================================================
    private IHCIProvider ioHandler;         // Contains everything needed for handling I/O.
    private InstructionSet instructionSet;  // Map of instructions used by the assembler.
    private CodeSegment codeSegment;        // Holds the actual instructions being executed.
    private CallStack callStack;            // Stack of saved iPointers for function calls.
    private DataStack dataStack;            // All program data is operated on via this.
    private VirtualDisk virtualDisk;        // Provides access to a 'tape' of bytes.
    private Calendar timeServer;            // Provides current system date and time.
    private Random rngServer;               // Serves up pseudo-random numbers.
    private int execDepth;                  // Current EXECUTE recursion level.
    private int iPointer;                   // List reference for the current instruction.
    private int exitCode;                   // Exit code. 0=Success, 1=Failure.
    private boolean haltFlag;               // If TRUE, the program execution has ended.

    // Initializes a Machine prior to passing a file to the assembler.
    // -----------------------------------------------------------------------------------------
    public MachineRuntime (IHCIProvider handler)
    {
        // Create and initialize all machine constructs.
        instructionSet = new InstructionSet();
        codeSegment = new CodeSegment();
        callStack = new CallStack();
        dataStack = new DataStack();
        virtualDisk = new VirtualDisk(handler);
        timeServer = new GregorianCalendar();
        rngServer = new Random();
        execDepth = 0;
        iPointer = -1;
        exitCode = 0;
        haltFlag = false;
        ioHandler = handler;

        // Build the instruction set table.
        buildInstructionSet();
    }

    // runProgram - Main program run loop. This method runs assembled code. Runtime errors
    //               encountered will throw a VMRuntimeException.
    // -----------------------------------------------------------------------------------------
    public int runProgram() throws VMRuntimeException
    {
        ioHandler.logObject("Stack machine v" + MachineRuntime.VERSION +
                ". Running assembled program...\n", IHCIProvider.LOG_EVENT);

        Instruction curInstruction = null; // Declare outside blocks for logging.

        try
        {
            do // Program execution loop. iPointer should be initialized to 'BEGIN'.
            {
                curInstruction = codeSegment.get(iPointer++);

                // Send information to the debugger.
                ioHandler.debug(dataStack, curInstruction);

                // *Note: iPointer is pointed at the instruction AFTER curInstruction when
                //        curInstruction is run. This affects the behavior of CALL.
                curInstruction.run();
            }
            while (!haltFlag);

            ioHandler.logObject("Program exited successfully with code " +
                    exitCode + ".\n", IHCIProvider.LOG_EVENT);

            return exitCode;
        }
        catch (VMRuntimeException e)
        {
            String instructionName = "";
            if (curInstruction != null)
            {
                instructionName = curInstruction.getName() + ": ";
            }

            throw new VMRuntimeException("VM FATAL: " + instructionName + e.getMessage(), e);
        }
    }

    // assemble - Given a file object, attempts to assemble the code therein and places
    //             the results into the code segment. Syntax errors will throw a
    //             VMAssemblyException.
    // -----------------------------------------------------------------------------------------
    public void assemble (File sourceFile) throws VMAssemblyException
    {
        ioHandler.logObject("Stack machine v" + MachineRuntime.VERSION +
                ". Assembling " + sourceFile.getPath() + "...\n", IHCIProvider.LOG_EVENT);

        try (Scanner codeFile = new Scanner(sourceFile, StandardCharsets.US_ASCII))
        {
            assemble(codeFile);
        }
        catch (IOException e)
        {
            throw new VMAssemblyException("VMA FATAL: Could not read source file.", e);
        }
    }

    // assemble - Given a Scanner object containing an input stream of unparsed code, attempts
    //             to parse and assemble the code and place the results in the code segment.
    //             Syntax errors will throw a VMAssemblyException.
    // -----------------------------------------------------------------------------------------
    private void assemble (Scanner codeFile) throws VMAssemblyException
    {
        try
        {
            JumpTable jumpTable = new JumpTable(); // Allows labels to be resolved by name.

            ioHandler.logObject("Begin source file parse. Pass 1 of 2.\n",
                    IHCIProvider.LOG_INFO);
            while (codeFile.hasNext())              // Pass #1. Greedy resolution of tokens.
            {
                if (codeFile.hasNext(";.*"))
                {
                    String t = codeFile.nextLine().trim();
                    ioHandler.logObject("Ignored comment: " + t + "\n",
                            IHCIProvider.LOG_INFO);
                }
                else if (codeFile.hasNext("\"\".*"))
                {
                    String t = codeFile.nextLine().replaceAll("^\\s+", "");
                    if (t.length() > 2)
                    {
                        ioHandler.logObject("Parsed string as PUSH character range: " +
                                t + "\n", IHCIProvider.LOG_INFO);
                        for (int i = 2; i < t.length(); i++)
                        {
                            codeSegment.add((t.charAt(i) == ' ') ? instructionSet.get("SPACE") :
                                    (t.charAt(i) == '\t') ? instructionSet.get("TAB") :
                                            new InstructionPUSH(t.charAt(i)));
                        }
                        codeSegment.add(new InstructionPUSH((t.length() - 2)));
                    }
                }
                else if (codeFile.hasNextBoolean())
                {
                    String t = codeFile.next();
                    ioHandler.logObject("Parsed token as PUSH boolean literal: " +
                            t + "\n", IHCIProvider.LOG_INFO);
                    codeSegment.add(new InstructionPUSH(Boolean.parseBoolean(t)));
                }
                else if (codeFile.hasNextInt())
                {
                    String t = codeFile.next();
                    ioHandler.logObject("Parsed token as PUSH integer literal: " +
                            t + "\n", IHCIProvider.LOG_INFO);
                    codeSegment.add(new InstructionPUSH(Integer.parseInt(t)));
                }
                else if (codeFile.hasNext("(0x)?[0-9A-Fa-f]{1,8}"))
                {
                    String t = codeFile.next();
                    ioHandler.logObject("Parsed token as PUSH integer[hex] literal: " +
                            t + "\n", IHCIProvider.LOG_INFO);
                    codeSegment.add(new InstructionPUSH(Integer.parseUnsignedInt(t
                            .replaceAll("0x", ""), 16)));
                }
                else if (codeFile.hasNextDouble())
                {
                    String t = codeFile.next();
                    ioHandler.logObject("Parsed token as PUSH float literal: " +
                            t + "\n", IHCIProvider.LOG_INFO);
                    codeSegment.add(new InstructionPUSH(Double.parseDouble(t)));
                }
                else if (codeFile.hasNext("'.'"))
                {
                    String t = codeFile.next();
                    ioHandler.logObject("Parsed token as PUSH character literal: " +
                            t + "\n", IHCIProvider.LOG_INFO);
                    codeSegment.add(new InstructionPUSH(t.charAt(1)));
                }
                else if (codeFile.hasNext("@[A-Za-z_]+[A-Za-z0-9_]*"))
                {
                    String t = codeFile.next();
                    ioHandler.logObject("Parsed token as DECLARE label: " +
                            t + "\n", IHCIProvider.LOG_INFO);
                    String labelName = t.substring(1);

                    // Make sure the label name isn't a reserved word or already declared.
                    if (labelName.toLowerCase().matches("(begin|true|false)") ||
                        instructionSet.exists(labelName))
                    {
                        throw new VMAssemblyException("Label name '" + labelName +
                                "' disallowed by the assembler.", null);
                    }
                    else if (jumpTable.exists(labelName))
                    {
                        throw new VMAssemblyException("Label '" + labelName +
                                "' cannot be declared more than once..", null);
                    }

                    jumpTable.add(labelName, new Label(labelName, codeSegment.getSize()));
                }
                else                                // Scanner can't easily parse. Get token.
                {
                    String token = codeFile.next();
                    if (token.toUpperCase().equals("BEGIN"))
                    {
                        ioHandler.logObject("Parsed token as ENTRY POINT: " +
                                token + "\n", IHCIProvider.LOG_INFO);
                        if (iPointer != -1)
                        {
                            throw new VMAssemblyException("BEGIN cannot be defined more than once.", null);
                        }

                        iPointer = codeSegment.getSize();
                    }
                    else if (instructionSet.exists(token))
                    {
                        ioHandler.logObject("Parsed token as INSTRUCTION: " +
                                token + "\n", IHCIProvider.LOG_INFO);
                        codeSegment.add(instructionSet.get(token));
                    }
                    else if (jumpTable.exists(token))
                    {
                        ioHandler.logObject("Parsed token as PUSH label reference: " +
                                token + "\n", IHCIProvider.LOG_INFO);
                        codeSegment.add(new InstructionPUSH(jumpTable.get(token)));
                    }
                    else
                    {
                        // Don't know what it is yet. Use a placeholder for pass #2.
                        ioHandler.logObject("Marked token for second pass: " +
                                token + "\n", IHCIProvider.LOG_INFO);
                        codeSegment.add(new PlaceholderOp(token));
                    }
                }
            }

            // The instruction pointer should be valid at this point. Make sure.
            if (iPointer < 0 || iPointer >= codeSegment.getSize())
            {
                throw new VMAssemblyException("BEGIN is undefined or out of bounds.", null);
            }

            // Pass #2. Try to resolve placeholders to valid labels and throw an exception
            //  if anything fails to resolve.
            ioHandler.logObject("Source file parse. Pass 2 of 2.\n", IHCIProvider.LOG_INFO);
            for (int i = 0; i < codeSegment.getSize(); i++)
            {
                Instruction instruction = codeSegment.get(i);
                if (instruction instanceof PlaceholderOp)
                {
                    ioHandler.logObject("Resolving label reference: " +
                            instruction.getName() + "\n", IHCIProvider.LOG_INFO);
                    codeSegment.replace(i,
                            new InstructionPUSH(jumpTable.get(instruction.getName())));
                }
            }

            ioHandler.logObject("Assembly completed successfully.\n", IHCIProvider.LOG_EVENT);
        }
        catch (VMAssemblyException|VMRuntimeException e)
        {
            throw new VMAssemblyException("VMA FATAL: " + e.getMessage(), e);
        }
    }

    // buildInstructionSet - Add each valid word, sans PUSH which is implicit, to the
    //                        instruction set such that the assembler can use it later.
    // -----------------------------------------------------------------------------------------
    private void buildInstructionSet ()
    {
        // Stack Instructions. Do NOT include PUSH.
        // -------------------------------------------------------------------------------------
        instructionSet.add("POP",           new InstructionPOP());
        instructionSet.add("POPN",          new InstructionPOPN());
        instructionSet.add("DUP",           new InstructionDUP());
        instructionSet.add("DUPN",          new InstructionDUPN());
        instructionSet.add("SWAP",          new InstructionSWAP());
        instructionSet.add("ROTATE",        new InstructionROTATE());
        instructionSet.add("PICK",          new InstructionPICK());
        instructionSet.add("PUT",           new InstructionPUT());
        instructionSet.add("DEPTH",         new InstructionDEPTH());
        instructionSet.add("JOIN",          new InstructionJOIN());
        instructionSet.add("SPLIT",         new InstructionSPLIT());

        // Control Flow Instructions. BEGIN is a special label, do NOT include it.
        // -------------------------------------------------------------------------------------
        instructionSet.add("EXIT",          new InstructionEXIT());
        instructionSet.add("ABORT",         new InstructionABORT());
        instructionSet.add("JUMP",          new InstructionJUMP());
        instructionSet.add("CJUMP",         new InstructionCJUMP());
        instructionSet.add("CALL",          new InstructionCALL());
        instructionSet.add("RETURN",        new InstructionRETURN());
        instructionSet.add("SLEEP",         new InstructionSLEEP());
        instructionSet.add("EXECUTE",       new InstructionEXECUTE());

        // Virtual Disk Instructions.
        // -------------------------------------------------------------------------------------
        instructionSet.add("MOUNT",         new InstructionMOUNT());
        instructionSet.add("UNMOUNT",       new InstructionUNMOUNT());
        instructionSet.add("VDINFO",        new InstructionVDINFO());
        instructionSet.add("VDPOS",         new InstructionVDPOS());
        instructionSet.add("SECTOR",        new InstructionSECTOR());
        instructionSet.add("SEEK",          new InstructionSEEK());
        instructionSet.add("READB",         new InstructionREADB());
        instructionSet.add("READC",         new InstructionREADC());
        instructionSet.add("READI",         new InstructionREADI());
        instructionSet.add("READF",         new InstructionREADF());
        instructionSet.add("READSTR",       new InstructionREADSTR());
        instructionSet.add("WRITEB",        new InstructionWRITEB());
        instructionSet.add("WRITEC",        new InstructionWRITEC());
        instructionSet.add("WRITEI",        new InstructionWRITEI());
        instructionSet.add("WRITEF",        new InstructionWRITEF());
        instructionSet.add("WRITESTR",      new InstructionWRITESTR());

        // Input/Output Instructions.
        // -------------------------------------------------------------------------------------
        instructionSet.add("PRINT",         new InstructionPRINT());
        instructionSet.add("ERROR",         new InstructionERROR());
        instructionSet.add("LOG",           new InstructionLOG());
        instructionSet.add("PRINTSTR",      new InstructionPRINTSTR());
        instructionSet.add("ERRORSTR",      new InstructionERRORSTR());
        instructionSet.add("LOGSTR",        new InstructionLOGSTR());
        instructionSet.add("GETLINE",       new InstructionGETLINE());
        instructionSet.add("DEBUG",         new InstructionDEBUG());
        instructionSet.add("NEWLINE",       new InstructionNEWLINE());
        instructionSet.add("TAB",           new InstructionTAB());
        instructionSet.add("SPACE",         new InstructionSPACE());
        instructionSet.add("LOGWARNING",    new InstructionLOGWARNING());
        instructionSet.add("LOGEVENT",      new InstructionLOGEVENT());
        instructionSet.add("LOGINFO",       new InstructionLOGINFO());
        instructionSet.add("LOGVERBOSE",    new InstructionLOGVERBOSE());

        // Conversion Instructions
        // -------------------------------------------------------------------------------------
        instructionSet.add("BTOI",          new InstructionBTOI());
        instructionSet.add("BTOF",          new InstructionBTOF());
        instructionSet.add("ITOB",          new InstructionITOB());
        instructionSet.add("ITOF",          new InstructionITOF());
        instructionSet.add("FTOB",          new InstructionFTOB());
        instructionSet.add("FTOI",          new InstructionFTOI());
        instructionSet.add("STRTOB",        new InstructionSTRTOB());
        instructionSet.add("STRTOI",        new InstructionSTRTOI());
        instructionSet.add("STRTOF",        new InstructionSTRTOF());
        instructionSet.add("HEXTOI",        new InstructionHEXTOI());
        instructionSet.add("ITOHEX",        new InstructionITOHEX());
        instructionSet.add("TOSTRING",      new InstructionTOSTRING());
        instructionSet.add("CTOIR",         new InstructionCTOIR());
        instructionSet.add("IRTOC",         new InstructionIRTOC());
        instructionSet.add("TOUPPER",       new InstructionTOUPPER());
        instructionSet.add("TOLOWER",       new InstructionTOLOWER());

        // Logic Instructions
        // -------------------------------------------------------------------------------------
        instructionSet.add("AND",           new InstructionAND());
        instructionSet.add("OR",            new InstructionOR());
        instructionSet.add("XOR",           new InstructionXOR());
        instructionSet.add("NOT",           new InstructionNOT());

        // Bitwise Instructions
        // -------------------------------------------------------------------------------------
        instructionSet.add("BITAND",        new InstructionBITAND());
        instructionSet.add("BITOR",         new InstructionBITOR());
        instructionSet.add("BITXOR",        new InstructionBITXOR());
        instructionSet.add("SHIFTL",        new InstructionSHIFTL());
        instructionSet.add("SHIFTR",        new InstructionSHIFTR());

        // Comparison Instructions
        // -------------------------------------------------------------------------------------
        instructionSet.add("ISBOOL",        new InstructionISBOOL());
        instructionSet.add("ISCHAR",        new InstructionISCHAR());
        instructionSet.add("ISINT",         new InstructionISINT());
        instructionSet.add("ISFLOAT",       new InstructionISFLOAT());
        instructionSet.add("STRISBOOL",     new InstructionSTRISBOOL());
        instructionSet.add("STRISINT",      new InstructionSTRISINT());
        instructionSet.add("STRISHEX",      new InstructionSTRISHEX());
        instructionSet.add("STRISFLOAT",    new InstructionSTRISFLOAT());
        instructionSet.add("CEQUALS",       new InstructionCEQUALS());
        instructionSet.add("CGREATER",      new InstructionCGREATER());
        instructionSet.add("CGREATEREQ",    new InstructionCGREATEREQ());
        instructionSet.add("CLESS",         new InstructionCLESS());
        instructionSet.add("CLESSEQ",       new InstructionCLESSEQ());
        instructionSet.add("IEQUALS",       new InstructionIEQUALS());
        instructionSet.add("IGREATER",      new InstructionIGREATER());
        instructionSet.add("IGREATEREQ",    new InstructionIGREATEREQ());
        instructionSet.add("ILESS",         new InstructionILESS());
        instructionSet.add("ILESSEQ",       new InstructionILESSEQ());
        instructionSet.add("FEQUALS",       new InstructionFEQUALS());
        instructionSet.add("FGREATER",      new InstructionFGREATER());
        instructionSet.add("FGREATEREQ",    new InstructionFGREATEREQ());
        instructionSet.add("FLESS",         new InstructionFLESS());
        instructionSet.add("FLESSEQ",       new InstructionFLESSEQ());

        // Math Instructions
        // -------------------------------------------------------------------------------------
        instructionSet.add("IADD",          new InstructionIADD());
        instructionSet.add("ISUB",          new InstructionISUB());
        instructionSet.add("IMULT",         new InstructionIMULT());
        instructionSet.add("IDIV",          new InstructionIDIV());
        instructionSet.add("IPOW",          new InstructionIPOW());
        instructionSet.add("ISQRT",         new InstructionISQRT());
        instructionSet.add("IABS",          new InstructionIABS());
        instructionSet.add("FADD",          new InstructionFADD());
        instructionSet.add("FSUB",          new InstructionFSUB());
        instructionSet.add("FMULT",         new InstructionFMULT());
        instructionSet.add("FDIV",          new InstructionFDIV());
        instructionSet.add("FPOW",          new InstructionFPOW());
        instructionSet.add("FSQRT",         new InstructionFSQRT());
        instructionSet.add("FABS",          new InstructionFABS());
        instructionSet.add("MOD",           new InstructionMOD());
        instructionSet.add("RAND",          new InstructionRAND());
        instructionSet.add("FRAND",         new InstructionFRAND());
        instructionSet.add("ROUND",         new InstructionROUND());
        instructionSet.add("FLOOR",         new InstructionFLOOR());
        instructionSet.add("CEIL",          new InstructionCEIL());
        instructionSet.add("LOG10",         new InstructionLOG10());
        instructionSet.add("NEXP",          new InstructionNEXP());
        instructionSet.add("NLOG",          new InstructionNLOG());
        instructionSet.add("PI",            new InstructionPI());
        instructionSet.add("SIN",           new InstructionSIN());
        instructionSet.add("COS",           new InstructionCOS());
        instructionSet.add("TAN",           new InstructionTAN());
        instructionSet.add("ASIN",          new InstructionASIN());
        instructionSet.add("ACOS",          new InstructionACOS());
        instructionSet.add("ATAN",          new InstructionATAN());
        instructionSet.add("TODEG",         new InstructionTODEG());
        instructionSet.add("TORAD",         new InstructionTORAD());

        // Time Instructions
        // -------------------------------------------------------------------------------------
        instructionSet.add("GETTIME",       new InstructionGETTIME());
        instructionSet.add("GETDATE",       new InstructionGETDATE());

    }

    // =========================================================================================
    // Runnable classes that are stored in the code segment representing each instruction.
    // =========================================================================================

    // Stack Manipulation Instructions.
    // =========================================================================================

    // PUSH [o] ( -- o ) Pushes a constant onto the stack. Implicitly invoked whenever a
    //                    literal is found.
    // -----------------------------------------------------------------------------------------
    private class InstructionPUSH extends Instruction
    {
        private Object data;
        public InstructionPUSH (Object constToPush)
        {
            data = constToPush;
        }

        public String getName()
        {
            return "PUSH[" + ((data instanceof Character) ?
                    "'" + data.toString() + "'" : data.toString()) + "]";
        }
        public void run() throws VMRuntimeException
        {
            dataStack.push(data);
        }
    }

    // POP ( o -- ) Removes an item from the stack.
    // -----------------------------------------------------------------------------------------
    private class InstructionPOP extends Instruction
    {
        public String getName()
        {
            return "POP";
        }
        public void run() throws VMRuntimeException
        {
            dataStack.pop();
        }
    }

    // POPN ( o1..on i -- ) Removes the top i items from the stack.
    // -----------------------------------------------------------------------------------------
    private class InstructionPOPN extends Instruction
    {
        public String getName()
        {
            return "POPN";
        }
        public void run() throws VMRuntimeException
        {
            int num = dataStack.popInt();
            if (num < 1)
            {
                throw new VMRuntimeException("Number of items must be greater than zero.", null);
            }
            for (int i = 0; i < num; i++)
            {
                dataStack.pop();
            }
        }
    }

    // DUP ( o -- o o ) Duplicates the item on top of the stack.
    // -----------------------------------------------------------------------------------------
    private class InstructionDUP extends Instruction
    {
        public String getName()
        {
            return "DUP";
        }
        public void run() throws VMRuntimeException
        {
            dataStack.dup();
        }
    }

    // DUPN ( o1..on i -- * * ) Duplicates the top i items on the stack.
    // -----------------------------------------------------------------------------------------
    private class InstructionDUPN extends Instruction
    {
        public String getName()
        {
            return "DUPN";
        }
        public void run() throws VMRuntimeException
        {
            int num = dataStack.popInt();
            if (num < 1)
            {
                throw new VMRuntimeException("Number of items must be greater than zero.", null);
            }
            for (int i = 0; i < num; i++)
            {
                dataStack.pick(num);
            }
        }
    }

    // SWAP ( o1 o2 -- o2 o1 ) Swaps the top two items on the stack.
    // -----------------------------------------------------------------------------------------
    private class InstructionSWAP extends Instruction
    {
        public String getName()
        {
            return "SWAP";
        }
        public void run() throws VMRuntimeException
        {
            dataStack.swap();
        }
    }

    // ROTATE ( o1..on i -- * ) Rotates the top i items on the stack.
    //                          +i = clockwise, -i = counterclockwise.
    // -----------------------------------------------------------------------------------------
    private class InstructionROTATE extends Instruction
    {
        public String getName()
        {
            return "ROTATE";
        }
        public void run() throws VMRuntimeException
        {
            Integer num = dataStack.popInt();
            dataStack.rotate(Math.abs(num), (num > 0));
        }
    }

    // PICK ( o1..on i -- * x ) Copies the item ith item and pushes it onto the stack.
    // -----------------------------------------------------------------------------------------
    private class InstructionPICK extends Instruction
    {
        public String getName()
        {
            return "PICK";
        }
        public void run() throws VMRuntimeException
        {
            dataStack.pick(dataStack.popInt());
        }
    }

    // PUT ( o1..on o i -- * ) Replaces the ith item on the stack with o.
    // -----------------------------------------------------------------------------------------
    private class InstructionPUT extends Instruction
    {
        public String getName()
        {
            return "PUT";
        }
        public void run() throws VMRuntimeException
        {
            int num = dataStack.popInt();
            dataStack.put(dataStack.pop(), num);
        }
    }

    // DEPTH ( -- i ) Pushes an integer representing the number of items on the stack.
    // -----------------------------------------------------------------------------------------
    private class InstructionDEPTH extends Instruction
    {
        public String getName()
        {
            return "DEPTH";
        }
        public void run() throws VMRuntimeException
        {
            dataStack.push(dataStack.depth());
        }
    }

    // JOIN	( o1..on i -- * ) Joins two stack ranges into one. For char ranges, this behaves
    //                         like string concatenation.
    // -----------------------------------------------------------------------------------------
    private class InstructionJOIN extends Instruction
    {
        public String getName()
        {
            return "JOIN";
        }
        public void run() throws VMRuntimeException
        {
            dataStack.join();
        }
    }

    // SPLIT ( o1..on i i1 -- * ) Splits a stack range into two, starting at the specified
    //                             index, i1.
    // -----------------------------------------------------------------------------------------
    private class InstructionSPLIT extends Instruction
    {
        public String getName()
        {
            return "SPLIT";
        }
        public void run() throws VMRuntimeException
        {
            dataStack.split(dataStack.popInt());
        }
    }

    // Control Flow Instructions.
    // =========================================================================================

    // EXIT	( i -- ) Causes the program to halt with the code given by i.
    // -----------------------------------------------------------------------------------------
    private class InstructionEXIT extends Instruction
    {
        public String getName()
        {
            return "EXIT";
        }
        public void run() throws VMRuntimeException
        {
            exitCode = dataStack.popInt();
            haltFlag = true;
        }
    }

    // ABORT ( c1..cn i -- ) Causes the program to abort with the specified range of chars as
    //                        the exception message.
    // -----------------------------------------------------------------------------------------
    private class InstructionABORT extends Instruction
    {
        public String getName()
        {
            return "ABORT";
        }
        public void run() throws VMRuntimeException
        {
            throw new VMRuntimeException(dataStack.popCharRange(), null);
        }
    }

    // JUMP	( l -- ) Unconditionally jumps to the position marked by label l.
    // -----------------------------------------------------------------------------------------
    private class InstructionJUMP extends Instruction
    {
        public String getName()
        {
            return "JUMP";
        }
        public void run() throws VMRuntimeException
        {
            iPointer = dataStack.popLabel().getPointer();
        }
    }

    // CJUMP ( b l -- )	If b is TRUE, jumps to the position marked by label l.
    // -----------------------------------------------------------------------------------------
    private class InstructionCJUMP extends Instruction
    {
        public String getName()
        {
            return "CJUMP";
        }
        public void run() throws VMRuntimeException
        {
            Integer jumpTo = dataStack.popLabel().getPointer();
            if (dataStack.popBool())
            {
                iPointer = jumpTo;
            }
        }
    }

    // CALL	( l -- ) Pushes current iPointer to the call stack, then jumps to the position
    //               marked by label l.
    // -----------------------------------------------------------------------------------------
    private class InstructionCALL extends Instruction
    {
        public String getName()
        {
            return "CALL";
        }
        public void run() throws VMRuntimeException
        {
            callStack.push(iPointer);
            iPointer = dataStack.popLabel().getPointer();
        }
    }

    // RETURN ( -- ) Jumps to the instruction referenced by the item on top of the call stack.
    // -----------------------------------------------------------------------------------------
    private class InstructionRETURN extends Instruction
    {
        public String getName()
        {
            return "RETURN";
        }
        public void run() throws VMRuntimeException
        {
            iPointer = callStack.pop();
        }
    }

    // SLEEP ( i -- ) Pauses execution of the program for i milliseconds.
    // -----------------------------------------------------------------------------------------
    private class InstructionSLEEP extends Instruction
    {
        public String getName()
        {
            return "SLEEP";
        }
        public void run() throws VMRuntimeException
        {
            try // Attempt to sleep. Treat interrupt exception as a desire to halt early.
            {
                int ms = dataStack.popInt();
                if (ms < 1)
                {
                    throw new VMRuntimeException("Sleep time must be greater than zero.", null);
                }
                Thread.sleep(ms);
            }
            catch (InterruptedException e)
            {
                exitCode = 1;
                haltFlag = true;
            }
        }
    }

    // EXECUTE ( c1..cn i -- i ) Assembles and runs the given character range as code.
    // -----------------------------------------------------------------------------------------
    private class InstructionEXECUTE extends Instruction
    {
        public String getName()
        {
            return "EXECUTE";
        }
        public void run() throws VMRuntimeException
        {
            if (execDepth >= MAX_EXEC_DEPTH) // Limit recursion to avoid crashes.
            {
                throw new VMRuntimeException("Maximum EXECUTE depth exceeded.", null);
            }

            CodeSegment saveSegment = codeSegment;
            CallStack saveStack = callStack;
            int savePointer = iPointer;

            codeSegment = new CodeSegment();
            callStack = new CallStack();
            iPointer = -1;

            String t = dataStack.popCharRange();

            try (Scanner codeFile = new Scanner(new BufferedReader(new StringReader(t))))
            {
                ioHandler.logObject("Stack machine v" + MachineRuntime.VERSION +
                        ". Assembling code from character range...\n", IHCIProvider.LOG_EVENT);

                assemble(codeFile);
                execDepth++;
                dataStack.push(runProgram());
            }
            catch (VMAssemblyException|VMRuntimeException e)
            {
                ioHandler.errorObject("VM FATAL: " + e.getMessage() + "\n");
            }
            finally
            {
                execDepth--;
                codeSegment = saveSegment;
                callStack = saveStack;
                iPointer = savePointer;
                exitCode = 0;
                haltFlag = false;
            }
        }
    }

    // Virtual Disk Instructions
    // =========================================================================================

    // MOUNT ( c1..cn i i1 i2 -- ) Mounts virtual disk with name specified by char range,
    //                              sector size i1, and number of sectors i2.
    // -----------------------------------------------------------------------------------------
    private class InstructionMOUNT extends Instruction
    {
        public String getName()
        {
            return "MOUNT";
        }
        public void run() throws VMRuntimeException
        {
            int sectorSize = dataStack.popInt();
            int numSectors = dataStack.popInt();
            virtualDisk.mount(dataStack.popCharRange(), sectorSize, numSectors);
        }
    }

    // UNMOUNT ( -- ) Unmounts the currently mounted virtual disk.
    // -----------------------------------------------------------------------------------------
    private class InstructionUNMOUNT extends Instruction
    {
        public String getName()
        {
            return "UNMOUNT";
        }
        public void run() throws VMRuntimeException
        {
            virtualDisk.unmount();
        }
    }

    // VDINFO ( -- i i ) Pushes the sector size and number of sectors to the stack.
    // -----------------------------------------------------------------------------------------
    private class InstructionVDINFO extends Instruction
    {
        public String getName()
        {
            return "VDINFO";
        }
        public void run() throws VMRuntimeException
        {
            dataStack.push(virtualDisk.getSectorSize());
            dataStack.push(virtualDisk.getNumSectors());
        }
    }

    // VDPOS ( -- i ) Pushes the current read-write head location onto the stack.
    // -----------------------------------------------------------------------------------------
    private class InstructionVDPOS extends Instruction
    {
        public String getName()
        {
            return "VDPOS";
        }
        public void run() throws VMRuntimeException
        {
            dataStack.push(virtualDisk.getPos());
        }
    }

    // SECTOR ( i -- i ) Pushes the start location for the given sector number onto the stack.
    // -----------------------------------------------------------------------------------------
    private class InstructionSECTOR extends Instruction
    {
        public String getName()
        {
            return "SECTOR";
        }
        public void run() throws VMRuntimeException
        {
            dataStack.push(virtualDisk.getSectorPos(dataStack.popInt()));
        }
    }

    // SEEK ( i -- ) Moves the read-write head to the position represented by i.
    // -----------------------------------------------------------------------------------------
    private class InstructionSEEK extends Instruction
    {
        public String getName()
        {
            return "SEEK";
        }
        public void run() throws VMRuntimeException
        {
            virtualDisk.seekTo(dataStack.popInt());
        }
    }

    // READB ( -- b ) Reads a boolean, starting at the read head's current position.
    // -----------------------------------------------------------------------------------------
    private class InstructionREADB extends Instruction
    {
        public String getName()
        {
            return "READB";
        }
        public void run() throws VMRuntimeException
        {
            dataStack.push(virtualDisk.readBool());
        }
    }

    // READC ( -- c ) Reads a character, starting at the read head's current position.
    // -----------------------------------------------------------------------------------------
    private class InstructionREADC extends Instruction
    {
        public String getName()
        {
            return "READC";
        }
        public void run() throws VMRuntimeException
        {
            dataStack.push(virtualDisk.readChar());
        }
    }

    // READI ( -- i ) Reads an integer, starting at the read head's current position.
    // -----------------------------------------------------------------------------------------
    private class InstructionREADI extends Instruction
    {
        public String getName()
        {
            return "READI";
        }
        public void run() throws VMRuntimeException
        {
            dataStack.push(virtualDisk.readInt());
        }
    }

    // READF ( -- f ) Reads a float, starting at the read head's current position.
    // -----------------------------------------------------------------------------------------
    private class InstructionREADF extends Instruction
    {
        public String getName()
        {
            return "READF";
        }
        public void run() throws VMRuntimeException
        {
            dataStack.push(virtualDisk.readFloat());
        }
    }

    // READSTR ( -- c1..cn i ) Reads a range of characters, starting at the read head's current
    //                         position.
    // -----------------------------------------------------------------------------------------
    private class InstructionREADSTR extends Instruction
    {
        public String getName()
        {
            return "READSTR";
        }
        public void run() throws VMRuntimeException
        {
            dataStack.pushCharRange(virtualDisk.readCharRange());
        }
    }

    // WRITEB ( b -- ) Writes a boolean, starting at the write head's current position.
    // -----------------------------------------------------------------------------------------
    private class InstructionWRITEB extends Instruction
    {
        public String getName()
        {
            return "WRITEB";
        }
        public void run() throws VMRuntimeException
        {
            virtualDisk.writeBool(dataStack.popBool());
        }
    }

    // WRITEC ( c -- ) Writes a character, starting at the write head's current position.
    // -----------------------------------------------------------------------------------------
    private class InstructionWRITEC extends Instruction
    {
        public String getName()
        {
            return "WRITEC";
        }
        public void run() throws VMRuntimeException
        {
            virtualDisk.writeChar(dataStack.popChar());
        }
    }

    // WRITEI ( i -- ) Writes an integer, starting at the write head's current position.
    // -----------------------------------------------------------------------------------------
    private class InstructionWRITEI extends Instruction
    {
        public String getName()
        {
            return "WRITEI";
        }
        public void run() throws VMRuntimeException
        {
            virtualDisk.writeInt(dataStack.popInt());
        }
    }

    // WRITEF ( f -- ) Writes a float, starting at the write head's current position.
    // -----------------------------------------------------------------------------------------
    private class InstructionWRITEF extends Instruction
    {
        public String getName()
        {
            return "WRITEF";
        }
        public void run() throws VMRuntimeException
        {
            virtualDisk.writeFloat(dataStack.popFloat());
        }
    }

    // WRITESTR ( c1..cn i -- ) Writes a range of characters, starting at the write head's
    //                          current position.
    // -----------------------------------------------------------------------------------------
    private class InstructionWRITESTR extends Instruction
    {
        public String getName()
        {
            return "WRITESTR";
        }
        public void run() throws VMRuntimeException
        {
            virtualDisk.writeCharRange(dataStack.popCharRange());
        }
    }

    // Input/Output Instructions
    // =========================================================================================

    // PRINT ( o -- ) Prints a single item to the main output stream. Default System.out.
    // -----------------------------------------------------------------------------------------
    private class InstructionPRINT extends Instruction
    {
        public String getName()
        {
            return "PRINT";
        }
        public void run() throws VMRuntimeException
        {
            ioHandler.printObject(dataStack.pop());
        }
    }

    // ERROR ( o -- ) Prints a single item to the error output stream. Default System.err.
    // -----------------------------------------------------------------------------------------
    private class InstructionERROR extends Instruction
    {
        public String getName()
        {
            return "ERROR";
        }
        public void run() throws VMRuntimeException
        {
            ioHandler.errorObject(dataStack.pop());
        }
    }

    // LOG ( o i -- ) Prints a single item to the log output stream at level i.
    //                 Default System.out.
    // -----------------------------------------------------------------------------------------
    private class InstructionLOG extends Instruction
    {
        public String getName()
        {
            return "LOG";
        }
        public void run() throws VMRuntimeException
        {
            int level = dataStack.popInt();
            if (level < 0 || level > 3)
            {
                throw new VMRuntimeException("Log level must be between 0 and 3.", null);
            }
            ioHandler.logObject(dataStack.pop(), level);
        }
    }

    // PRINTSTR ( c1..cn i -- ) Prints a character range to the main output stream.
    //                          Default System.out.
    // -----------------------------------------------------------------------------------------
    private class InstructionPRINTSTR extends Instruction
    {
        public String getName()
        {
            return "PRINTSTR";
        }
        public void run() throws VMRuntimeException
        {
            ioHandler.printObject(dataStack.popCharRange());
        }
    }

    // ERRORSTR ( c1..cn i -- ) Prints a character range to the error output stream.
    //                          Default System.err.
    // -----------------------------------------------------------------------------------------
    private class InstructionERRORSTR extends Instruction
    {
        public String getName()
        {
            return "ERRORSTR";
        }
        public void run() throws VMRuntimeException
        {
            ioHandler.errorObject(dataStack.popCharRange());
        }
    }

    // LOGSTR ( c1..cn i1 i2 -- ) Prints a character range to the log output stream at level i2.
    //                            Default System.out.
    // -----------------------------------------------------------------------------------------
    private class InstructionLOGSTR extends Instruction
    {
        public String getName()
        {
            return "LOGSTR";
        }
        public void run() throws VMRuntimeException
        {
            int level = dataStack.popInt();
            if (level < 0 || level > 3)
            {
                throw new VMRuntimeException("Log level must be between 0 and 3.", null);
            }
            ioHandler.logObject(dataStack.popCharRange(), level);
        }
    }

    // GETLINE ( -- c1..cn i ) Fetches a line of input from the input stream as a range of
    //                          chars. Default System.in.
    // -----------------------------------------------------------------------------------------
    private class InstructionGETLINE extends Instruction
    {
        public String getName()
        {
            return "GETLINE";
        }
        public void run() throws VMRuntimeException
        {
            dataStack.pushCharRange(ioHandler.getLine());
        }
    }

    // DEBUG ( b -- ) If TRUE, turns the debug log output on. If FALSE, turns the debugger off.
    // -----------------------------------------------------------------------------------------
    private class InstructionDEBUG extends Instruction
    {
        public String getName()
        {
            return "DEBUG";
        }
        public void run() throws VMRuntimeException
        {
            ioHandler.setDebug(dataStack.popBool());
        }
    }

    // NEWLINE ( -- c ) Pushes a newline character onto the stack.
    // -----------------------------------------------------------------------------------------
    private class InstructionNEWLINE extends Instruction
    {
        public String getName()
        {
            return "NEWLINE";
        }
        public void run() throws VMRuntimeException
        {
            dataStack.push('\n');
        }
    }

    // TAB ( -- c ) Pushes a tab character onto the stack.
    // -----------------------------------------------------------------------------------------
    private class InstructionTAB extends Instruction
    {
        public String getName()
        {
            return "TAB";
        }
        public void run() throws VMRuntimeException
        {
            dataStack.push('\t');
        }
    }

    // SPACE ( -- c ) Pushes a space character onto the stack, since ' ' confuses the assembler.
    // -----------------------------------------------------------------------------------------
    private class InstructionSPACE extends Instruction
    {
        public String getName()
        {
            return "SPACE";
        }
        public void run() throws VMRuntimeException
        {
            dataStack.push(' ');
        }
    }

    // LOGWARNING ( -- i ) Pushes the integer corresponding to the LOG_WARNING (0) level to
    //                      the stack.
    // -----------------------------------------------------------------------------------------
    private class InstructionLOGWARNING extends Instruction
    {
        public String getName()
        {
            return "LOGWARNING";
        }
        public void run() throws VMRuntimeException
        {
            dataStack.push(IHCIProvider.LOG_WARNING);
        }
    }

    // LOGEVENT ( -- i ) Pushes the integer corresponding to the LOG_EVENT (1) level to the
    //                    stack.
    // -----------------------------------------------------------------------------------------
    private class InstructionLOGEVENT extends Instruction
    {
        public String getName()
        {
            return "LOGEVENT";
        }
        public void run() throws VMRuntimeException
        {
            dataStack.push(IHCIProvider.LOG_EVENT);
        }
    }

    // LOGINFO ( -- i ) Pushes the integer corresponding to the LOG_INFO (2) level to the stack.
    // -----------------------------------------------------------------------------------------
    private class InstructionLOGINFO extends Instruction
    {
        public String getName()
        {
            return "LOGINFO";
        }
        public void run() throws VMRuntimeException
        {
            dataStack.push(IHCIProvider.LOG_INFO);
        }
    }

    // LOGVERBOSE ( -- i ) Pushes the integer corresponding to the LOG_VERBOSE (3) level to
    //                      the stack.
    // -----------------------------------------------------------------------------------------
    private class InstructionLOGVERBOSE extends Instruction
    {
        public String getName()
        {
            return "LOGVERBOSE";
        }
        public void run() throws VMRuntimeException
        {
            dataStack.push(IHCIProvider.LOG_VERBOSE);
        }
    }

    // Conversion Instructions
    // =========================================================================================

    // BTOI ( b -- i ) Converts a boolean to an integer, where 0 is false and 1 is true.
    // -----------------------------------------------------------------------------------------
    private class InstructionBTOI extends Instruction
    {
        public String getName()
        {
            return "BTOI";
        }
        public void run() throws VMRuntimeException
        {
            dataStack.push(dataStack.popBool() ? 1 : 0);
        }
    }

    // BTOF ( b -- f ) Converts a boolean to a float, where 0.0 is false and 1.0 is true.
    // -----------------------------------------------------------------------------------------
    private class InstructionBTOF extends Instruction
    {
        public String getName()
        {
            return "BTOF";
        }
        public void run() throws VMRuntimeException
        {
            dataStack.push(dataStack.popBool() ? 1.0 : 0.0);
        }
    }

    // ITOB ( i -- b ) Converts an integer to a boolean, where 0 is false and anything else
    //                  is true.
    // -----------------------------------------------------------------------------------------
    private class InstructionITOB extends Instruction
    {
        public String getName()
        {
            return "ITOB";
        }
        public void run() throws VMRuntimeException
        {
            dataStack.push(dataStack.popInt() == 0);
        }
    }

    // ITOF ( i -- f ) Converts an integer to a float. 15 = 15.0.
    // -----------------------------------------------------------------------------------------
    private class InstructionITOF extends Instruction
    {
        public String getName()
        {
            return "ITOF";
        }
        public void run() throws VMRuntimeException
        {
            dataStack.push((double)dataStack.popInt());
        }
    }

    // FTOB ( f -- b ) Converts a float to a boolean, where 0.0 is false and anything else
    //                  is true.
    // -----------------------------------------------------------------------------------------
    private class InstructionFTOB extends Instruction
    {
        public String getName()
        {
            return "FTOB";
        }
        public void run() throws VMRuntimeException
        {
            dataStack.push(dataStack.popFloat() == 0.0);
        }
    }

    // FTOI ( f -- i ) Converts a float to an integer. 39.9 = 39.
    // -----------------------------------------------------------------------------------------
    private class InstructionFTOI extends Instruction
    {
        public String getName()
        {
            return "FTOI";
        }
        public void run() throws VMRuntimeException
        {
            dataStack.push((int)dataStack.popFloat().doubleValue());
        }
    }

    // STRTOB ( c1..cn i -- b ) Converts a range of chars to a boolean, where
    //                          ('t' 'r' 'u' 'e' 4) is true.
    // -----------------------------------------------------------------------------------------
    private class InstructionSTRTOB extends Instruction
    {
        public String getName()
        {
            return "STRTOB";
        }
        public void run() throws VMRuntimeException
        {
            dataStack.push(Boolean.parseBoolean(dataStack.popCharRange()));
        }
    }

    // STRTOI ( c1..cn i -- i ) Converts a range of chars to an integer. ('1' '5' 2) = 15.
    // -----------------------------------------------------------------------------------------
    private class InstructionSTRTOI extends Instruction
    {
        public String getName()
        {
            return "STRTOI";
        }
        public void run() throws VMRuntimeException
        {
            String t = dataStack.popCharRange();
            try
            {
                dataStack.push(Integer.parseInt(t));
            }
            catch (NumberFormatException e)
            {
                throw new VMRuntimeException(
                        "Character range does not represent a valid integer.", e);
            }
        }
    }

    // STRTOF ( c1..cn i -- f ) Converts a range of chars to a float. ('2' '.' '3' 3) = 2.3.
    // -----------------------------------------------------------------------------------------
    private class InstructionSTRTOF extends Instruction
    {
        public String getName()
        {
            return "STRTOF";
        }
        public void run() throws VMRuntimeException
        {
            String t = dataStack.popCharRange();
            try
            {
                dataStack.push(Double.parseDouble(t));
            }
            catch (NumberFormatException e)
            {
                throw new VMRuntimeException(
                        "Character range does not represent a valid float.", e);
            }
        }
    }

    // HEXTOI ( c1..cn i -- i ) Converts a range of chars depicting an integer in hexadecimal
    //                           to an integer.
    // -----------------------------------------------------------------------------------------
    private class InstructionHEXTOI extends Instruction
    {
        public String getName()
        {
            return "HEXTOI";
        }
        public void run() throws VMRuntimeException
        {
            String t = dataStack.popCharRange().replaceAll("0x", "");
            try
            {
                dataStack.push(Integer.parseUnsignedInt(t, 16));
            }
            catch (NumberFormatException e)
            {
                throw new VMRuntimeException(
                        "Character range does not represent valid hexadecimal.", e);
            }
        }
    }

    // ITOHEX ( i -- c1..cn i ) Converts an integer to a range of chars depicting its
    //                          hexadecimal representation.
    // -----------------------------------------------------------------------------------------
    private class InstructionITOHEX extends Instruction
    {
        public String getName()
        {
            return "ITOHEX";
        }
        public void run() throws VMRuntimeException
        {
            dataStack.pushCharRange(Integer.toHexString(dataStack.popInt()));
        }
    }

    // TOSTRING ( o -- c1..cn i ) Converts an object to a range of chars in the format
    //                            'c1 c2 ... cn numberOfChars'.
    // -----------------------------------------------------------------------------------------
    private class InstructionTOSTRING extends Instruction
    {
        public String getName()
        {
            return "TOSTRING";
        }
        public void run() throws VMRuntimeException
        {
            dataStack.pushCharRange(dataStack.pop().toString());
        }
    }

    // CTOIR ( c -- i ) Converts a character into the integer representing its ASCII value.
    // -----------------------------------------------------------------------------------------
    private class InstructionCTOIR extends Instruction
    {
        public String getName()
        {
            return "CTOIR";
        }
        public void run() throws VMRuntimeException
        {
            dataStack.push((int)dataStack.popChar());
        }
    }

    // IRTOC ( i -- c ) Converts an integer into an ASCII character.
    // -----------------------------------------------------------------------------------------
    private class InstructionIRTOC extends Instruction
    {
        public String getName()
        {
            return "IRTOC";
        }
        public void run() throws VMRuntimeException
        {
            dataStack.push((char)dataStack.popInt().intValue());
        }
    }

    // TOUPPER ( c -- c ) Converts a character to its upper case form.
    // -----------------------------------------------------------------------------------------
    private class InstructionTOUPPER extends Instruction
    {
        public String getName()
        {
            return "TOUPPER";
        }
        public void run() throws VMRuntimeException
        {
            dataStack.push(Character.toUpperCase(dataStack.popChar()));
        }
    }

    // TOLOWER	( c -- c ) Converts a character to its lower case form.
    // -----------------------------------------------------------------------------------------
    private class InstructionTOLOWER extends Instruction
    {
        public String getName()
        {
            return "TOLOWER";
        }
        public void run() throws VMRuntimeException
        {
            dataStack.push(Character.toLowerCase(dataStack.popChar()));
        }
    }

    // Logic Instructions
    // =========================================================================================

    // AND ( b1 b2 -- b ) Given two bools, if both are TRUE, result is TRUE, otherwise result
    //                     is FALSE.
    // -----------------------------------------------------------------------------------------
    private class InstructionAND extends Instruction
    {
        public String getName()
        {
            return "AND";
        }
        public void run() throws VMRuntimeException
        {
            dataStack.push((dataStack.popBool() && dataStack.popBool()));
        }
    }

    // OR ( b1 b2 -- b ) Given two bools, if at least one is TRUE, result is TRUE.
    // -----------------------------------------------------------------------------------------
    private class InstructionOR extends Instruction
    {
        public String getName()
        {
            return "OR";
        }
        public void run() throws VMRuntimeException
        {
            dataStack.push((dataStack.popBool() || dataStack.popBool()));
        }
    }

    // XOR ( b1 b2 -- b ) Given two bools, if one and only one is TRUE, result is TRUE.
    // -----------------------------------------------------------------------------------------
    private class InstructionXOR extends Instruction
    {
        public String getName()
        {
            return "XOR";
        }
        public void run() throws VMRuntimeException
        {
            dataStack.push((dataStack.popBool() ^ dataStack.popBool()));
        }
    }

    // NOT ( b -- b ) Result is the inverse of the supplied boolean.
    // -----------------------------------------------------------------------------------------
    private class InstructionNOT extends Instruction
    {
        public String getName()
        {
            return "NOT";
        }
        public void run() throws VMRuntimeException
        {
            dataStack.push((!dataStack.popBool()));
        }
    }

    // Bitwise Instructions
    // =========================================================================================

    // BITAND ( i1 i2 -- i ) Performs a bitwise AND operation on the given two integers.
    // -----------------------------------------------------------------------------------------
    private class InstructionBITAND extends Instruction
    {
        public String getName()
        {
            return "BITAND";
        }
        public void run() throws VMRuntimeException
        {
            dataStack.push((dataStack.popInt() & dataStack.popInt()));
        }
    }

    // BITOR ( i1 i2 -- i ) Performs a bitwise OR operation on the given two integers.
    // -----------------------------------------------------------------------------------------
    private class InstructionBITOR extends Instruction
    {
        public String getName()
        {
            return "BITOR";
        }
        public void run() throws VMRuntimeException
        {
            dataStack.push((dataStack.popInt() | dataStack.popInt()));
        }
    }

    // BITXOR ( i1 i2 -- i ) Performs a bitwise XOR operation on the given two integers.
    // -----------------------------------------------------------------------------------------
    private class InstructionBITXOR extends Instruction
    {
        public String getName()
        {
            return "BITXOR";
        }
        public void run() throws VMRuntimeException
        {
            dataStack.push((dataStack.popInt() ^ dataStack.popInt()));
        }
    }

    // SHIFTL ( i1 i2 -- i ) Performs a left bitshift operation on the first int by the
    //                        amount of the second int.
    // -----------------------------------------------------------------------------------------
    private class InstructionSHIFTL extends Instruction
    {
        public String getName()
        {
            return "SHIFTL";
        }
        public void run() throws VMRuntimeException
        {
            int shift = dataStack.popInt();
            dataStack.push((dataStack.popInt() << shift));
        }
    }

    // SHIFTR ( i1 i2 -- i ) Performs a right bitshift operation on the first int by the
    //                        amount of the second int.
    // -----------------------------------------------------------------------------------------
    private class InstructionSHIFTR extends Instruction
    {
        public String getName()
        {
            return "SHIFTR";
        }
        public void run() throws VMRuntimeException
        {
            int shift = dataStack.popInt();
            dataStack.push((dataStack.popInt() >> shift));
        }
    }

    // Comparison Instructions
    // =========================================================================================

    // ISBOOL ( o -- b ) Result is TRUE if the item on the top of the stack is a boolean.
    // -----------------------------------------------------------------------------------------
    private class InstructionISBOOL extends Instruction
    {
        public String getName()
        {
            return "ISBOOL";
        }
        public void run() throws VMRuntimeException
        {
            dataStack.push(dataStack.pop() instanceof Boolean);
        }
    }

    // ISCHAR ( o -- b ) Result is TRUE if the item on the top of the stack is a character.
    // -----------------------------------------------------------------------------------------
    private class InstructionISCHAR extends Instruction
    {
        public String getName()
        {
            return "ISCHAR";
        }
        public void run() throws VMRuntimeException
        {
            dataStack.push(dataStack.pop() instanceof Character);
        }
    }

    // ISINT ( o -- b ) Result is TRUE if the item on the top of the stack is an integer.
    // -----------------------------------------------------------------------------------------
    private class InstructionISINT extends Instruction
    {
        public String getName()
        {
            return "ISINT";
        }
        public void run() throws VMRuntimeException
        {
            dataStack.push(dataStack.pop() instanceof Integer);
        }
    }

    // ISFLOAT ( o -- b ) Result is TRUE if the item on the top of the stack is a float.
    // -----------------------------------------------------------------------------------------
    private class InstructionISFLOAT extends Instruction
    {
        public String getName()
        {
            return "ISFLOAT";
        }
        public void run() throws VMRuntimeException
        {
            dataStack.push(dataStack.pop() instanceof Double);
        }
    }

    // STRISBOOL ( c1..cn i -- b ) Result is TRUE if the character range represents a boolean.
    // -----------------------------------------------------------------------------------------
    private class InstructionSTRISBOOL extends Instruction
    {
        public String getName()
        {
            return "STRISBOOL";
        }
        public void run() throws VMRuntimeException
        {
            dataStack.push(dataStack.popCharRange().toLowerCase()
                    .matches("(true|false)"));
        }
    }

    // STRISINT ( c1..cn i -- b ) Result is TRUE if the character range represents an integer.
    // -----------------------------------------------------------------------------------------
    private class InstructionSTRISINT extends Instruction
    {
        public String getName()
        {
            return "STRISINT";
        }
        public void run() throws VMRuntimeException
        {
            dataStack.push(dataStack.popCharRange().toLowerCase()
                    .matches("-?[0-9]{1,10}"));
        }
    }

    // STRISHEX ( c1..cn i -- b ) Result is TRUE if the character range represents an integer
    //                             in hexadecimal format.
    // -----------------------------------------------------------------------------------------
    private class InstructionSTRISHEX extends Instruction
    {
        public String getName()
        {
            return "STRISHEX";
        }
        public void run() throws VMRuntimeException
        {
            dataStack.push(dataStack.popCharRange().toLowerCase()
                    .matches("(0x)?[0-9a-f]{1,8}"));
        }
    }

    // STRISFLOAT ( c1..cn i -- b ) Result is TRUE if the character range represents a float.
    // -----------------------------------------------------------------------------------------
    private class InstructionSTRISFLOAT extends Instruction
    {
        public String getName()
        {
            return "STRISFLOAT";
        }
        public void run() throws VMRuntimeException
        {
            try
            {
                Double.parseDouble(dataStack.popCharRange());
                dataStack.push(true);
            }
            catch (NumberFormatException e)
            {
                dataStack.push(false);
            }
        }
    }

    // CEQUALS ( c1 c2 -- b ) Result is TRUE if c1 and c2 are the same.
    // -----------------------------------------------------------------------------------------
    private class InstructionCEQUALS extends Instruction
    {
        public String getName()
        {
            return "CEQUALS";
        }
        public void run() throws VMRuntimeException
        {
            dataStack.push(dataStack.popChar().equals(dataStack.popChar()));
        }
    }

    // CGREATER ( c1 c2 -- b ) Result is TRUE if c1 occurs after c2.
    // -----------------------------------------------------------------------------------------
    private class InstructionCGREATER extends Instruction
    {
        public String getName()
        {
            return "CGREATER";
        }
        public void run() throws VMRuntimeException
        {
            char c = dataStack.popChar();
            dataStack.push(dataStack.popChar() > c);
        }
    }

    // CGREATEREQ ( c1 c2 -- b ) Result is TRUE if c1 occurs after or is equal to c2.
    // -----------------------------------------------------------------------------------------
    private class InstructionCGREATEREQ extends Instruction
    {
        public String getName()
        {
            return "CGREATEREQ";
        }
        public void run() throws VMRuntimeException
        {
            char c = dataStack.popChar();
            dataStack.push(dataStack.popChar() >= c);
        }
    }

    // CLESS ( c1 c2 -- b ) Result is TRUE if c1 occurs before c2.
    // -----------------------------------------------------------------------------------------
    private class InstructionCLESS extends Instruction
    {
        public String getName()
        {
            return "CLESS";
        }
        public void run() throws VMRuntimeException
        {
            char c = dataStack.popChar();
            dataStack.push(dataStack.popChar() < c);
        }
    }

    // CLESSEQ ( c1 c2 -- b ) Result is TRUE if c1 occurs before or is equal to c2.
    // -----------------------------------------------------------------------------------------
    private class InstructionCLESSEQ extends Instruction
    {
        public String getName()
        {
            return "CLESSEQ";
        }
        public void run() throws VMRuntimeException
        {
            char c = dataStack.popChar();
            dataStack.push(dataStack.popChar() <= c);
        }
    }

    // IEQUALS ( i1 i2 -- b ) Result is TRUE if i1 is equal to i2.
    // -----------------------------------------------------------------------------------------
    private class InstructionIEQUALS extends Instruction
    {
        public String getName()
        {
            return "IEQUALS";
        }
        public void run() throws VMRuntimeException
        {
            dataStack.push(dataStack.popInt().equals(dataStack.popInt()));
        }
    }

    // IGREATER ( i1 i2 -- b ) Result is TRUE if i1 is greater than i2.
    // -----------------------------------------------------------------------------------------
    private class InstructionIGREATER extends Instruction
    {
        public String getName()
        {
            return "IGREATER";
        }
        public void run() throws VMRuntimeException
        {
            int i = dataStack.popInt();
            dataStack.push(dataStack.popInt() > i);
        }
    }

    // IGREATEREQ ( i1 i2 -- b ) Result is TRUE if i1 is greater than or equal to i2.
    // -----------------------------------------------------------------------------------------
    private class InstructionIGREATEREQ extends Instruction
    {
        public String getName()
        {
            return "IGREATEREQ";
        }
        public void run() throws VMRuntimeException
        {
            int i = dataStack.popInt();
            dataStack.push(dataStack.popInt() >= i);
        }
    }

    // ILESS ( i1 i2 -- b ) Result is TRUE if i1 is less than i2.
    // -----------------------------------------------------------------------------------------
    private class InstructionILESS extends Instruction
    {
        public String getName()
        {
            return "ILESS";
        }
        public void run() throws VMRuntimeException
        {
            int i = dataStack.popInt();
            dataStack.push(dataStack.popInt() < i);
        }
    }

    // ILESSEQ ( i1 i2 -- b ) Result is TRUE if i1 is less than or equal to i2.
    // -----------------------------------------------------------------------------------------
    private class InstructionILESSEQ extends Instruction
    {
        public String getName()
        {
            return "ILESSEQ";
        }
        public void run() throws VMRuntimeException
        {
            int i = dataStack.popInt();
            dataStack.push(dataStack.popInt() <= i);
        }
    }

    // FEQUALS ( f1 f2 -- b ) Result is TRUE if f1 is equal to f2.
    // -----------------------------------------------------------------------------------------
    private class InstructionFEQUALS extends Instruction
    {
        public String getName()
        {
            return "FEQUALS";
        }
        public void run() throws VMRuntimeException
        {
            dataStack.push(dataStack.popFloat().equals(dataStack.popFloat()));
        }
    }

    // FGREATER ( f1 f2 -- b ) Result is TRUE if f1 is greater than f2.
    // -----------------------------------------------------------------------------------------
    private class InstructionFGREATER extends Instruction
    {
        public String getName()
        {
            return "FGREATER";
        }
        public void run() throws VMRuntimeException
        {
            double f = dataStack.popFloat();
            dataStack.push(dataStack.popFloat() > f);
        }
    }

    // FGREATEREQ ( f1 f2 -- b ) Result is TRUE if f1 is greater than or equal to f2.
    // -----------------------------------------------------------------------------------------
    private class InstructionFGREATEREQ extends Instruction
    {
        public String getName()
        {
            return "FGREATEREQ";
        }
        public void run() throws VMRuntimeException
        {
            double f = dataStack.popFloat();
            dataStack.push(dataStack.popFloat() >= f);
        }
    }

    // FLESS ( f1 f2 -- b ) Result is TRUE if f1 is less than f2.
    // -----------------------------------------------------------------------------------------
    private class InstructionFLESS extends Instruction
    {
        public String getName()
        {
            return "FLESS";
        }
        public void run() throws VMRuntimeException
        {
            double f = dataStack.popFloat();
            dataStack.push(dataStack.popFloat() < f);
        }
    }

    // FLESSEQ ( f1 f2 -- b ) Result is TRUE if f1 is less than or equal to f2.
    // -----------------------------------------------------------------------------------------
    private class InstructionFLESSEQ extends Instruction
    {
        public String getName()
        {
            return "FLESSEQ";
        }
        public void run() throws VMRuntimeException
        {
            double f = dataStack.popFloat();
            dataStack.push(dataStack.popFloat() <= f);
        }
    }

    // Math Instructions
    // =========================================================================================

    // IADD ( i1 i2 -- i ) Performs addition on two integers.
    // -----------------------------------------------------------------------------------------
    private class InstructionIADD extends Instruction
    {
        public String getName()
        {
            return "IADD";
        }
        public void run() throws VMRuntimeException
        {
            dataStack.push(dataStack.popInt() + dataStack.popInt());
        }
    }

    // ISUB ( i1 i2 -- i ) Performs subtraction, subtracting i2 from i1.
    // -----------------------------------------------------------------------------------------
    private class InstructionISUB extends Instruction
    {
        public String getName()
        {
            return "ISUB";
        }
        public void run() throws VMRuntimeException
        {
            int i2 = dataStack.popInt();
            dataStack.push(dataStack.popInt() - i2);
        }
    }

    // IMULT ( i1 i2 -- i ) Performs multiplication on two integers.
    // -----------------------------------------------------------------------------------------
    private class InstructionIMULT extends Instruction
    {
        public String getName()
        {
            return "IMULT";
        }
        public void run() throws VMRuntimeException
        {
            dataStack.push(dataStack.popInt() * dataStack.popInt());
        }
    }

    // IDIV ( i1 i2 -- i ) Performs integer division, dividing i1 by i2.
    // -----------------------------------------------------------------------------------------
    private class InstructionIDIV extends Instruction
    {
        public String getName()
        {
            return "IDIV";
        }
        public void run() throws VMRuntimeException
        {
            int i2 = dataStack.popInt();
            if (i2 == 0)
            {
                throw new VMRuntimeException("Cannot divide by zero.", null);
            }
            dataStack.push(dataStack.popInt() / i2);
        }
    }

    // IPOW ( i1 i2 -- i ) Raises i1 to the i2nd power.
    // -----------------------------------------------------------------------------------------
    private class InstructionIPOW extends Instruction
    {
        public String getName()
        {
            return "IPOW";
        }
        public void run() throws VMRuntimeException
        {
            int exp = dataStack.popInt();
            dataStack.push((int)Math.round(Math.pow(dataStack.popInt(), exp)));
        }
    }

    // ISQRT ( i -- i ) Finds the integer square root of i.
    // -----------------------------------------------------------------------------------------
    private class InstructionISQRT extends Instruction
    {
        public String getName()
        {
            return "ISQRT";
        }
        public void run() throws VMRuntimeException
        {
            dataStack.push((int)Math.sqrt(dataStack.popInt()));
        }
    }

    // IABS ( i -- i ) Finds the absolute value of i.
    // -----------------------------------------------------------------------------------------
    private class InstructionIABS extends Instruction
    {
        public String getName()
        {
            return "IABS";
        }
        public void run() throws VMRuntimeException
        {
            dataStack.push(Math.abs(dataStack.popInt()));
        }
    }

    // FADD ( f1 f2 -- f ) Performs addition on two floats.
    // -----------------------------------------------------------------------------------------
    private class InstructionFADD extends Instruction
    {
        public String getName()
        {
            return "FADD";
        }
        public void run() throws VMRuntimeException
        {
            dataStack.push(dataStack.popFloat() + dataStack.popFloat());
        }
    }

    // FSUB ( f1 f2 -- f ) Performs subtraction, subtracting f2 from f1.
    // -----------------------------------------------------------------------------------------
    private class InstructionFSUB extends Instruction
    {
        public String getName()
        {
            return "FSUB";
        }
        public void run() throws VMRuntimeException
        {
            double f2 = dataStack.popFloat();
            dataStack.push(dataStack.popFloat() - f2);
        }
    }

    // FMULT ( f1 f2 -- f ) Performs multiplication on two floats.
    // -----------------------------------------------------------------------------------------
    private class InstructionFMULT extends Instruction
    {
        public String getName()
        {
            return "FMULT";
        }
        public void run() throws VMRuntimeException
        {
            dataStack.push(dataStack.popFloat() * dataStack.popFloat());
        }
    }

    // FDIV ( f1 f2 -- f ) Performs floating point division, dividing f1 by f2.
    // -----------------------------------------------------------------------------------------
    private class InstructionFDIV extends Instruction
    {
        public String getName()
        {
            return "FDIV";
        }
        public void run() throws VMRuntimeException
        {
            double f2 = dataStack.popFloat();
            if (f2 == 0.0f)
            {
                throw new VMRuntimeException("Cannot divide by zero.", null);
            }
            dataStack.push(dataStack.popFloat() / f2);
        }
    }

    // FPOW ( f1 f2 -- f ) Raises f1 to the f2nd power.
    // -----------------------------------------------------------------------------------------
    private class InstructionFPOW extends Instruction
    {
        public String getName()
        {
            return "FPOW";
        }
        public void run() throws VMRuntimeException
        {
            double exp = dataStack.popFloat();
            dataStack.push(Math.pow(dataStack.popFloat(), exp));
        }
    }

    // FSQRT ( f -- f ) Finds the approximate square root of f.
    // -----------------------------------------------------------------------------------------
    private class InstructionFSQRT extends Instruction
    {
        public String getName()
        {
            return "FSQRT";
        }
        public void run() throws VMRuntimeException
        {
            dataStack.push(Math.sqrt(dataStack.popFloat()));
        }
    }

    // FABS ( f -- f ) Finds the absolute value of f.
    // -----------------------------------------------------------------------------------------
    private class InstructionFABS extends Instruction
    {
        public String getName()
        {
            return "FABS";
        }
        public void run() throws VMRuntimeException
        {
            dataStack.push(Math.abs(dataStack.popFloat()));
        }
    }

    // MOD ( i1 i2 -- i ) Performs a modulus operation on two integers. Result is the remainder
    //                     from integer division.
    // -----------------------------------------------------------------------------------------
    private class InstructionMOD extends Instruction
    {
        public String getName()
        {
            return "MOD";
        }
        public void run() throws VMRuntimeException
        {
            int i2 = dataStack.popInt();
            if (i2 == 0)
            {
                throw new VMRuntimeException("Cannot divide by zero.", null);
            }
            dataStack.push(dataStack.popInt() % i2);
        }
    }

    // RAND ( i -- i ) Generates a random number between [0, i2).
    // -----------------------------------------------------------------------------------------
    private class InstructionRAND extends Instruction
    {
        public String getName()
        {
            return "RAND";
        }
        public void run() throws VMRuntimeException
        {
            int bound = dataStack.popInt();
            if (bound < 1)
            {
                throw new VMRuntimeException("Upper bound must be greater than 0.", null);
            }
            dataStack.push(rngServer.nextInt(bound));
        }
    }

    // FRAND ( -- f ) Generates a random number between [0.0, 1.0).
    // -----------------------------------------------------------------------------------------
    private class InstructionFRAND extends Instruction
    {
        public String getName()
        {
            return "FRAND";
        }
        public void run() throws VMRuntimeException
        {
            dataStack.push(rngServer.nextDouble());
        }
    }

    // ROUND ( f -- i ) Rounds f up or down according to standard rounding rules.
    // -----------------------------------------------------------------------------------------
    private class InstructionROUND extends Instruction
    {
        public String getName()
        {
            return "ROUND";
        }
        public void run() throws VMRuntimeException
        {
            dataStack.push(Math.round(dataStack.popFloat()));
        }
    }

    // FLOOR ( f -- f ) Calculates the floor of f, equivalent to rounding down or truncating.
    // -----------------------------------------------------------------------------------------
    private class InstructionFLOOR extends Instruction
    {
        public String getName()
        {
            return "FLOOR";
        }
        public void run() throws VMRuntimeException
        {
            dataStack.push(Math.floor(dataStack.popFloat()));
        }
    }

    // CEIL ( f -- f ) Calculates the ceiling of f, equivalent to rounding up.
    // -----------------------------------------------------------------------------------------
    private class InstructionCEIL extends Instruction
    {
        public String getName()
        {
            return "CEIL";
        }
        public void run() throws VMRuntimeException
        {
            dataStack.push(Math.ceil(dataStack.popFloat()));
        }
    }

    // LOG10 ( f -- f ) Calculates the base10 logarithm of f.
    // -----------------------------------------------------------------------------------------
    private class InstructionLOG10 extends Instruction
    {
        public String getName()
        {
            return "LOG10";
        }
        public void run() throws VMRuntimeException
        {
            dataStack.push(Math.log10(dataStack.popFloat()));
        }
    }

    // NEXP ( f -- f ) Calculates euler's number, e, raised to the power of f.
    // -----------------------------------------------------------------------------------------
    private class InstructionNEXP extends Instruction
    {
        public String getName()
        {
            return "NEXP";
        }
        public void run() throws VMRuntimeException
        {
            dataStack.push(Math.exp(dataStack.popFloat()));
        }
    }

    // NLOG ( f -- f ) Calculates the natural logarithm of f.
    // -----------------------------------------------------------------------------------------
    private class InstructionNLOG extends Instruction
    {
        public String getName()
        {
            return "NLOG";
        }
        public void run() throws VMRuntimeException
        {
            dataStack.push(Math.log(dataStack.popFloat()));
        }
    }

    // PI ( -- f ) Pushes PI to the stack.
    // -----------------------------------------------------------------------------------------
    private class InstructionPI extends Instruction
    {
        public String getName()
        {
            return "PI";
        }
        public void run() throws VMRuntimeException
        {
            dataStack.push(Math.PI);
        }
    }

    // SIN ( f -- f ) Calculates the trigonometric sine of an angle f.
    // -----------------------------------------------------------------------------------------
    private class InstructionSIN extends Instruction
    {
        public String getName()
        {
            return "SIN";
        }
        public void run() throws VMRuntimeException
        {
            dataStack.push(Math.sin(dataStack.popFloat()));
        }
    }

    // COS ( f -- f ) Calculates the trigonometric cosine of an angle f.
    // -----------------------------------------------------------------------------------------
    private class InstructionCOS extends Instruction
    {
        public String getName()
        {
            return "COS";
        }
        public void run() throws VMRuntimeException
        {
            dataStack.push(Math.cos(dataStack.popFloat()));
        }
    }

    // TAN ( f -- f ) Calculates the trigonometric tangent of an angle f.
    // -----------------------------------------------------------------------------------------
    private class InstructionTAN extends Instruction
    {
        public String getName()
        {
            return "TAN";
        }
        public void run() throws VMRuntimeException
        {
            dataStack.push(Math.tan(dataStack.popFloat()));
        }
    }

    // ASIN ( f -- f ) Calculates the arc sine of f, in the range -pi/2 to pi/2.
    // -----------------------------------------------------------------------------------------
    private class InstructionASIN extends Instruction
    {
        public String getName()
        {
            return "ASIN";
        }
        public void run() throws VMRuntimeException
        {
            dataStack.push(Math.asin(dataStack.popFloat()));
        }
    }

    // ACOS ( f -- f ) Calculates the arc cosine of f, in the range 0.0 to pi.
    // -----------------------------------------------------------------------------------------
    private class InstructionACOS extends Instruction
    {
        public String getName()
        {
            return "ACOS";
        }
        public void run() throws VMRuntimeException
        {
            dataStack.push(Math.acos(dataStack.popFloat()));
        }
    }

    // ATAN ( f -- f ) Calculates the arc tangent of f, in the range -pi/2 to pi/2.
    // -----------------------------------------------------------------------------------------
    private class InstructionATAN extends Instruction
    {
        public String getName()
        {
            return "ATAN";
        }
        public void run() throws VMRuntimeException
        {
            dataStack.push(Math.atan(dataStack.popFloat()));
        }
    }

    // TODEG ( f -- f ) Converts f from radians to degrees.
    // -----------------------------------------------------------------------------------------
    private class InstructionTODEG extends Instruction
    {
        public String getName()
        {
            return "TODEG";
        }
        public void run() throws VMRuntimeException
        {
            dataStack.push(Math.toDegrees(dataStack.popFloat()));
        }
    }

    // TORAD ( f -- f ) Converts f from degrees to radians.
    // -----------------------------------------------------------------------------------------
    private class InstructionTORAD extends Instruction
    {
        public String getName()
        {
            return "TORAD";
        }
        public void run() throws VMRuntimeException
        {
            dataStack.push(Math.toRadians(dataStack.popFloat()));
        }
    }

    // Time Instructions
    // =========================================================================================

    // GETTIME ( -- i i i ) Fetches the current time in the format (hours, minutes, seconds).
    // -----------------------------------------------------------------------------------------
    private class InstructionGETTIME extends Instruction
    {
        public String getName()
        {
            return "GETTIME";
        }
        public void run() throws VMRuntimeException
        {
            dataStack.push(timeServer.get(Calendar.HOUR_OF_DAY));
            dataStack.push(timeServer.get(Calendar.MINUTE));
            dataStack.push(timeServer.get(Calendar.SECOND));
        }
    }

    // GETDATE ( -- i i i ) Fetches the current date in the format (year, month, day).
    // -----------------------------------------------------------------------------------------
    private class InstructionGETDATE extends Instruction
    {
        public String getName()
        {
            return "GETDATE";
        }
        public void run() throws VMRuntimeException
        {
            dataStack.push(timeServer.get(Calendar.YEAR));
            dataStack.push((timeServer.get(Calendar.MONTH) + 1));
            dataStack.push(timeServer.get(Calendar.DAY_OF_MONTH));
        }
    }

    // Misc and Special Instructions
    // =========================================================================================

    // Special placeholder instruction to be replaced by a label push.
    // -----------------------------------------------------------------------------------------
    private class PlaceholderOp extends Instruction
    {
        private String labelName;

        public PlaceholderOp (String labelName)
        {
            this.labelName = labelName;
        }

        public String getName ()
        {
            return labelName;
        }
        public void run () throws VMRuntimeException
        {
            throw new VMRuntimeException(
                    "Program did not assemble correctly, placeholder run.", null);
        }
    }
}
