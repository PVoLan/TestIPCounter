package ru.pvolan.ipcounter;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.BitSet;

public class Main {

    public static void main(String[] args) {
        System.out.println("IPCounter start");

        StopWatch parsingStopWatch = new StopWatch();
        StopWatch totalStopWatch = new StopWatch();

        LongerBitSet bitset = new LongerBitSet();

        String fileName = getFileNameFromArgs(args);

        System.out.println("IPCounter file to be used: " + fileName);

        try (BufferedReader br = new BufferedReader(new FileReader(fileName))) {
            String line;
            long lineCounter = 0;

            while ((line = br.readLine()) != null) {
                if(line.isEmpty()) continue;
                System.out.println("Parsing line " + line);
                int ip = parseLine(line);
                System.out.println(String.format("Parsed into %08x", ip));
                bitset.set(ip, true);
                lineCounter++;
            }

        } catch (IOException e){
            throw new RuntimeException(e.getMessage(), e);
        }

        System.out.println("IPCounter - parsing done in " + parsingStopWatch.measure() + " ms");

        StopWatch countingStopWatch = new StopWatch();

        /*
        long count = 0;
        for (int i = Integer.MIN_VALUE; ; i++) {
            if(bitset.get(i)) count++;
            if(i == Integer.MAX_VALUE) break;
        }*/

        long count = bitset.cardinality();

        System.out.println("Unique address count: " + count);

        System.out.println("IPCounter - counting done in " + countingStopWatch.measure() + " ms");
        System.out.println("IPCounter - all done in " + totalStopWatch.measure() + " ms");

        System.out.println("IPCounter end");
    }

    private static String getFileNameFromArgs(String[] args) {
        String fallback = "ip.txt";
        if(args.length == 0) return fallback;
        return args[0];
    }


    private static int parseLine(String line) {
        String[] octets = line.split("\\.");
        if(octets.length != 4) throw new RuntimeException("Invalid line \"" + line + "\"");

        int b0 = octetToByte(octets[0]);
        int b1 = octetToByte(octets[1]);
        int b2 = octetToByte(octets[2]);
        int b3 = octetToByte(octets[3]);

        return (b0 << 24) | (b1 << 16) | (b2 << 8) | (b3 << 0);
    }

    private static int octetToByte(String octet) {
        return Integer.parseInt(octet);
    }
}


class LongerBitSet
{
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


    private static final int BITSET_ID_00 = 0x00000000;
    private static final int BITSET_ID_01 = 0x40000000;
    private static final int BITSET_ID_10 = 0x80000000;
    private static final int BITSET_ID_11 = 0xC0000000;


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


class StopWatch
{

    private long startTime = System.currentTimeMillis();


    public long measure(){
        return System.currentTimeMillis() - startTime;
    }
}