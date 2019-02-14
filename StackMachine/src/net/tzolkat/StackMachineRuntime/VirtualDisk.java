// File System Wrapper class. Implements a 'tape' of bytes. Throws VMRuntimeException.
// Author: Jason Jones
// =========================================================================================
// TODO: Document sectors.
package net.tzolkat.StackMachineRuntime;

import java.io.IOException;
import java.io.RandomAccessFile;

public class VirtualDisk
{
    // Virtual Disk reads and writes to a RandomAccessFile.
    // =========================================================================================
    private IHCIProvider ioHandler;             // IHCIProvider for logging.
    private RandomAccessFile vmFile = null;     // RandomAccessFile instance, the disk itself.
    private String diskName = "";               // Name of the currently mounted virtual disk.
    private int maxSize = 0;                    // Maximum size of the virtual disk.
    private int sectorSize = 0;                 // Size of a sector on the virtual disk.

    // Construct the virtual disk object and initialize logging support.
    // -----------------------------------------------------------------------------------------
    public VirtualDisk (IHCIProvider ioHandler)
    {
        this.ioHandler = ioHandler;
    }

    // diskMountedCheck - Throws an exception if there is no disk to perform operations on.
    // -----------------------------------------------------------------------------------------
    private void diskMountedCheck () throws VMRuntimeException
    {
        if (vmFile == null)
        {
            throw new VMRuntimeException("No disk has been mounted.", null);
        }
    }

    // filePointerCheck - Ensures the file pointer does not fall outside manually set boundary.
    //                     Throws a VMRuntimeException if it does.
    // -----------------------------------------------------------------------------------------
    private void filePointerCheck (int pos) throws VMRuntimeException
    {
        if (pos < 0 || pos >= maxSize)
        {
            throw new VMRuntimeException("File Pointer goes out of bounds.", null);
        }
    }

    // logWriteAction - Logs any write actions.
    // -----------------------------------------------------------------------------------------
    private void logWriteAction (int size) throws VMRuntimeException
    {
        ioHandler.logObject("Writing " + size + "bytes at position " + getPos() + "...\n",
                IHCIProvider.LOG_INFO);
    }

    // mount - Properly initializes the file system.
    // -----------------------------------------------------------------------------------------
    public void mount (String diskFile, int sectorSize, int numSectors) throws VMRuntimeException
    {
        if (vmFile != null)
        {
            unmount();
        }

        try
        {
            this.maxSize = sectorSize * numSectors;
            this.sectorSize = sectorSize;
            this.diskName = diskFile;

            if (sectorSize <= 0 || maxSize < sectorSize)
            {
                throw new VMRuntimeException("Invalid size specifications for virtual disk.", null);
            }

            ioHandler.logObject("Mounting virtual disk: " + diskFile +
                    "...\n", IHCIProvider.LOG_EVENT);
            ioHandler.logObject("Size: " + maxSize + ", SectorSize: " + sectorSize + ".\n",
                    IHCIProvider.LOG_INFO);

            vmFile = new RandomAccessFile(diskFile, "rwd");
            vmFile.setLength(maxSize);
            vmFile.seek(0);
        }
        catch (IOException e)
        {
            throw new VMRuntimeException("Could not mount virtual disk. " + e.getMessage(), e);
        }
    }

    // unmount - Properly unmounts the file system.
    // -----------------------------------------------------------------------------------------
    public void unmount () throws VMRuntimeException
    {
        if (vmFile != null)
        {
            try
            {
                ioHandler.logObject("Unmounting virtual disk " + diskName + "...\n",
                        IHCIProvider.LOG_EVENT);

                vmFile.close();
                vmFile = null;
            }
            catch (IOException e)
            {
                throw new VMRuntimeException("Failed to unmount virtual disk. " + e.getMessage(), e);
            }
        }
    }

    // getSectorSize - Returns the sector size for the virtual disk.
    // -----------------------------------------------------------------------------------------
    public int getSectorSize () throws VMRuntimeException
    {
        diskMountedCheck();
        return sectorSize;
    }

    // getNumSectors - Returns number of sectors on the virtual disk.
    // -----------------------------------------------------------------------------------------
    public int getNumSectors() throws VMRuntimeException
    {
        diskMountedCheck();
        return (maxSize / sectorSize);
    }

    // getPos - Gets the current location of the file pointer.
    // -----------------------------------------------------------------------------------------
    public int getPos () throws VMRuntimeException
    {
        diskMountedCheck();
        try
        {
            return (int)vmFile.getFilePointer();
        }
        catch (IOException e)
        {
            throw new VMRuntimeException("Cannot get file pointer. " + e.getMessage(), e);
        }
    }

    // getSectorPos - Gets the file pointer location for the start of the given sector.
    // -----------------------------------------------------------------------------------------
    public int getSectorPos (int sector) throws VMRuntimeException
    {
        diskMountedCheck();
        int offset = sector * sectorSize;
        filePointerCheck(offset);
        return offset;
    }

    // seekTo - Moves the read head to 'pos'.
    // -----------------------------------------------------------------------------------------
    public void seekTo (int pos) throws VMRuntimeException
    {
        diskMountedCheck();
        filePointerCheck(pos);
        try
        {
            vmFile.seek((long)pos);
        }
        catch (IOException e)
        {
            throw new VMRuntimeException("Cannot seek to position. " + e.getMessage(), e);
        }
    }

    // readBool - Reads a single boolean, starting at the file pointer's current position.
    // -----------------------------------------------------------------------------------------
    public boolean readBool () throws VMRuntimeException
    {
        diskMountedCheck();
        try
        {
            return vmFile.readBoolean();
        }
        catch (IOException e)
        {
            throw new VMRuntimeException("Unable to read BOOL. " + e.getMessage(), e);
        }
    }

    // readChar - Reads a single character, starting at the file pointer's current position.
    // -----------------------------------------------------------------------------------------
    public char readChar () throws VMRuntimeException
    {
        diskMountedCheck();
        try
        {
            return (char)vmFile.readUnsignedByte();
        }
        catch (IOException e)
        {
            throw new VMRuntimeException("Unable to read CHAR. " + e.getMessage(), e);
        }
    }

    // readInt - Reads a single integer, starting at the file pointer's current position.
    // -----------------------------------------------------------------------------------------
    public int readInt () throws VMRuntimeException
    {
        diskMountedCheck();
        try
        {
            return vmFile.readInt();
        }
        catch (IOException e)
        {
            throw new VMRuntimeException("Unable to read INT. " + e.getMessage(), e);
        }
    }

    // readFloat - Reads a single float, starting at the file pointer's current position.
    // -----------------------------------------------------------------------------------------
    public double readFloat () throws VMRuntimeException
    {
        diskMountedCheck();
        try
        {
            return vmFile.readDouble();
        }
        catch (IOException e)
        {
            throw new VMRuntimeException("Unable to read FLOAT. " + e.getMessage(), e);
        }
    }

    // readCharRange - Reads a range of characters encoded in the format i,c1,c2,..,cn, where
    //                  i represents the number of characters in the range.
    // -----------------------------------------------------------------------------------------
    public String readCharRange () throws VMRuntimeException
    {
        diskMountedCheck();
        try
        {
            int size = vmFile.readInt();
            StringBuilder sb  = new StringBuilder();
            for (int i = 0; i < size; i++)
            {
                sb.append((char)vmFile.readUnsignedByte());
            }
            return sb.toString();
        }
        catch (IOException e)
        {
            throw new VMRuntimeException("Unable to read CHAR range. " + e.getMessage(), e);
        }
    }

    // writeBool - Writes a single boolean, starting at the file pointer's current position.
    // -----------------------------------------------------------------------------------------
    public void writeBool (boolean b) throws VMRuntimeException
    {
        diskMountedCheck();
        filePointerCheck(getPos()); // Bool is only 1 byte.
        try
        {
            logWriteAction(1);
            vmFile.writeBoolean(b);
        }
        catch (IOException e)
        {
            throw new VMRuntimeException("Unable to write BOOL. " + e.getMessage(), e);
        }
    }

    // writeChar - Writes a single character, starting at the file pointer's current position.
    // -----------------------------------------------------------------------------------------
    public void writeChar (char c) throws VMRuntimeException
    {
        diskMountedCheck();
        filePointerCheck(getPos()); // Char is only 1 byte.
        try
        {
            logWriteAction(1);
            vmFile.writeByte(c);
        }
        catch (IOException e)
        {
            throw new VMRuntimeException("Unable to write CHAR. " + e.getMessage(), e);
        }
    }

    // writeInt - Writes a single integer, starting at the file pointer's current position.
    // -----------------------------------------------------------------------------------------
    public void writeInt (int i) throws VMRuntimeException
    {
        diskMountedCheck();
        filePointerCheck(getPos() + 3); // Current pos + 3 bytes.
        try
        {
            logWriteAction(4);
            vmFile.writeInt(i);
        }
        catch (IOException e)
        {
            throw new VMRuntimeException("Unable to write INT. " + e.getMessage(), e);
        }
    }

    // writeFloat - Writes a single float, starting at the file pointer's current position.
    // -----------------------------------------------------------------------------------------
    public void writeFloat (double f) throws VMRuntimeException
    {
        diskMountedCheck();
        filePointerCheck(getPos() + 7); // Current pos + 7 bytes.
        try
        {
            logWriteAction(8);
            vmFile.writeDouble(f);
        }
        catch (IOException e)
        {
            throw new VMRuntimeException("Unable to write FLOAT. " + e.getMessage(), e);
        }
    }

    // writeCharRange - Given a string, writes it as a range of characters in the format
    //                   i,c1,c2,..,cn, where i is the number of characters, starting at
    //                   the file pointer's current position.
    // -----------------------------------------------------------------------------------------
    public void writeCharRange (String range) throws VMRuntimeException
    {
        diskMountedCheck();

        // Current pos + 3 bytes [int] + length [chars].
        filePointerCheck(getPos() + 3 + range.length());
        try
        {
            logWriteAction(4 + range.length());
            vmFile.writeInt(range.length());
            vmFile.writeBytes(range);
        }
        catch (IOException e)
        {
            throw new VMRuntimeException("Unable to write CHAR range. " + e.getMessage(), e);
        }
    }
}
