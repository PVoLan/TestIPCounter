package ru.pvolan.ipcounter;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.BitSet;

public class Main
{
    public static void main(String[] args) {
        System.out.println("IPCounter start");

        StopWatch totalStopWatch = new StopWatch();
        StopWatch parsingStopWatch = new StopWatch();
        LongerBitSet bitset = new LongerBitSet();

        String fileName = ArgsHelper.getFileNameFromArgs(args);
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

        try (BufferedReader br = new BufferedReader(new FileReader(fileName))) {

            Parser parser = new Parser();
            String line;
            long lineCounter = 0;

            while ((line = br.readLine()) != null) {
                if(line.isEmpty()) continue;
                //System.out.println("Parsing line " + line);
                int ip = parser.parseLine(line);
                //System.out.println(String.format("Parsed into %08x", ip));
                bitset.set(ip, true);

                lineCounter++;
                if(lineCounter % 10000000 == 0) {
                    System.out.println("Parsed " + lineCounter + " lines");
                }
            }

        } catch (IOException e){
            throw new RuntimeException(e.getMessage(), e);
        }

        System.out.println("File read completed");
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



class Parser
{

    public int parseLine(String line) {

        try {
            int res = 0;

            int tmpValue = 0;

            int currByte = 3;
            boolean newOctet = true;
            int len = line.length();

            for (int i = 0; i < len; i++) {
                char c = line.charAt(i);
                if (c == '.') {
                    res = addByteToRes(newOctet, tmpValue, currByte, res);

                    currByte--;
                    tmpValue = 0;
                    newOctet = true;
                } else {
                    int digit = Character.digit(c, 10);
                    if (digit < 0) {
                        throw new RuntimeException();
                    }

                    tmpValue *= 10;
                    tmpValue += digit;
                    newOctet = false;
                }
            }

            res = addByteToRes(newOctet, tmpValue, currByte, res);

            return res;
        }
        catch (Exception e)
        {
            throw new RuntimeException("Invalid line " + line);
        }
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