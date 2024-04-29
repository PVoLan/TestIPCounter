package ru.pvolan.ipcounter;

import java.io.*;
import java.util.BitSet;

public class Main
{
    public static void main(String[] args) {
        System.out.println("IPCounter start");

        StopWatch totalStopWatch = new StopWatch();
        LongerBitSet bitset = new LongerBitSet();

        String fileName = ArgsHelper.getFileNameFromArgs(args);


        StopWatch emptyReadStopWatch = new StopWatch();
        doEmptyRead(fileName);
        System.out.println("IPCounter - emptyRead done in " + emptyReadStopWatch.measure() + " ms");

        StopWatch parsingStopWatch = new StopWatch();
        doRead(fileName, bitset);
        System.out.println("IPCounter - parsing done in " + parsingStopWatch.measure() + " ms");

        StopWatch countingStopWatch = new StopWatch();
        doCount(bitset);
        System.out.println("IPCounter - counting done in " + countingStopWatch.measure() + " ms");

        System.out.println("IPCounter - all done in " + totalStopWatch.measure() + " ms");
        System.out.println("IPCounter end");
    }


    private static void doRead(String fileName, LongerBitSet bitset) {
        System.out.println("File to read: " + fileName);

        try (InputStream inputStream = new FileInputStream(fileName)) {

            Parser parser = new Parser();
            long lineCounter = 0;

            int bufSizeToRead = 102400;
            int bufSizeMaxRemainder = 20;
            byte[] buffer1 = new byte[bufSizeToRead + bufSizeMaxRemainder];
            byte[] buffer2 = new byte[bufSizeToRead + bufSizeMaxRemainder];

            byte[] currentBuffer = buffer1;
            byte[] anotherBuffer = buffer2;
            int currentRemainderSize = 0;

            ParserOutput parserOutput = new ParserOutput();

            while (true) {

                boolean lastIteration = false;
                int bytesRead = inputStream.read(currentBuffer, currentRemainderSize, bufSizeToRead);
                //System.out.println("New bytes read: " + bytesRead);
                if(bytesRead == -1){
                    lastIteration = true;
                    bytesRead = 0;
                }
                int bytesAvailable = currentRemainderSize + bytesRead;

                int currentBufferOffset = 0;
                while (true){
                    parser.parseLine(currentBuffer, currentBufferOffset, bytesAvailable, parserOutput);
                    //System.out.println(String.format("Parsed line \"%08x\", bytespassed=%d, nothingToParse=%s", parserOutput.result, parserOutput.bytesPassed, parserOutput.nothingToParse));

                    currentBufferOffset += parserOutput.bytesPassed;
                    if(parserOutput.nothingToParse) break;
                    if(parserOutput.emptyLine) {
                        continue;
                    }

                    bitset.set(parserOutput.result, true);

                    lineCounter++;
                    if(lineCounter % 10000000 == 0) {
                        System.out.println("Parsed " + lineCounter + " lines");
                    }
                }

                if(lastIteration) break;

                int remainderLen = bytesAvailable - currentBufferOffset;
                System.arraycopy(currentBuffer, currentBufferOffset, anotherBuffer, 0, remainderLen);

                currentRemainderSize = remainderLen;

                //Swap buffers
                byte[] tmp = currentBuffer;
                currentBuffer = anotherBuffer;
                anotherBuffer = tmp;
            }

        } catch (IOException e){
            throw new RuntimeException(e.getMessage(), e);
        }

        System.out.println("File read completed");
    }



    private static void doEmptyRead(String fileName) {
        System.out.println("File to do empty read: " + fileName);

        try {
            InputStream is = new FileInputStream(fileName);
            long totalRead = 0;

            byte[] buffer = new byte[10240];
            for (int length; (length = is.read(buffer)) != -1; ) {
                totalRead += length;
            }

            System.out.println("Read " + totalRead + " bytes");

        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }


    private static void doCount(LongerBitSet bitset) {
        long count = bitset.cardinality();
        System.out.println("Unique address count: " + count);
    }
}



class LongerBitSet
{
    private static final int BITSET_ID_00 = 0x00000000;
    private static final int BITSET_ID_01 = 0x40000000;
    private static final int BITSET_ID_10 = 0x80000000;
    private static final int BITSET_ID_11 = 0xC0000000;

    private BitSet bitSet00 = new BitSet(1024*1024*1024);
    private BitSet bitSet01 = new BitSet(1024*1024*1024);
    private BitSet bitSet10 = new BitSet(1024*1024*1024);
    private BitSet bitSet11 = new BitSet(1024*1024*1024);

    boolean get(int index){
        BitSet bitset = getBitSet(index);
        int internalIndex = getIndexInsideBitSet(index);
        return bitset.get(internalIndex);
    }

    void set(int index, boolean value){
        BitSet bitset = getBitSet(index);
        int internalIndex = getIndexInsideBitSet(index);
        bitset.set(internalIndex, value);
    }

    long cardinality(){
        long res = 0;
        res += bitSet00.cardinality();
        res += bitSet01.cardinality();
        res += bitSet10.cardinality();
        res += bitSet11.cardinality();
        return res;
    }

    private BitSet getBitSet(int index){
        int bitSetIndex = index & 0xC0000000;
        if(bitSetIndex == BITSET_ID_00) return bitSet00;
        if(bitSetIndex == BITSET_ID_01) return bitSet01;
        if(bitSetIndex == BITSET_ID_10) return bitSet10;
        if(bitSetIndex == BITSET_ID_11) return bitSet11;
        throw new RuntimeException("Invalid bitSetIndex " + bitSetIndex);
    }

    private int getIndexInsideBitSet(int index){
        return index & 0x3FFFFFFF;
    }
}


class ParserOutput{
    int result;
    int bytesPassed;
    boolean nothingToParse;
    boolean emptyLine;
}

class Parser
{
    byte DOT = 0x2E;
    byte D0 = 0x30;
    byte D1 = 0x31;
    byte D2 = 0x32;
    byte D3 = 0x33;
    byte D4 = 0x34;
    byte D5 = 0x35;
    byte D6 = 0x36;
    byte D7 = 0x37;
    byte D8 = 0x38;
    byte D9 = 0x39;
    byte CR = 0x0D;
    byte LF = 0x0A;


    public void parseLine(byte[] buffer, int offset, int bytesAvailable, ParserOutput parserOutput){

        int res = 0;

        int tmpValue = 0;

        int currByte = 3;
        boolean newOctet = true;

        int currentOffset = offset;


        while (true) {

            if(currentOffset >= bytesAvailable){

                parserOutput.result = 0;
                parserOutput.nothingToParse = true;
                parserOutput.bytesPassed = 0;
                parserOutput.emptyLine = false;

                return;
            }

            byte b = buffer[currentOffset];

            //Note that CR+LF combinations will provide fake empty "lines", but this is not a big issue for us
            if (b == CR || b == LF){
                int bytesPassed = currentOffset - offset + 1;

                res = addByteToRes(newOctet, tmpValue, currByte, res);

                parserOutput.result = res;
                parserOutput.nothingToParse = false;
                parserOutput.bytesPassed = bytesPassed;
                parserOutput.emptyLine = bytesPassed == 1;

                return;
            }

            if (b == DOT) {
                res = addByteToRes(newOctet, tmpValue, currByte, res);

                currByte--;
                tmpValue = 0;
                newOctet = true;
            } else {
                int digit = byteToDigit(b);

                tmpValue *= 10;
                tmpValue += digit;
                newOctet = false;
            }

            currentOffset++;
        }
    }

    private int byteToDigit(byte b) {
        //Maybe return (b-D0) would work faster, not sure
        if(b == D0) return 0;
        if(b == D1) return 1;
        if(b == D2) return 2;
        if(b == D3) return 3;
        if(b == D4) return 4;
        if(b == D5) return 5;
        if(b == D6) return 6;
        if(b == D7) return 7;
        if(b == D8) return 8;
        if(b == D9) return 9;
        throw new RuntimeException("Invalid byte " + b);
    }

    private static int addByteToRes(boolean newOctet, int tmpValue, int currByte, int res) {
        if (newOctet || tmpValue < 0L || tmpValue > 255L || currByte < 0) {
            throw new RuntimeException();
        }

        int addition = tmpValue << (8* currByte);
        res |= addition;
        return res;
    }
}



class StopWatch
{
    private long startTime = System.currentTimeMillis();

    public long measure(){
        return System.currentTimeMillis() - startTime;
    }
}


class ArgsHelper
{
    public static String getFileNameFromArgs(String[] args) {
        String fallback = "ip.txt";
        if(args.length == 0) return fallback;
        return args[0];
    }
}