package edu.grinnell.csc207.compression;

import java.util.Map;
import java.util.HashMap;
import java.io.IOException;

/**
 * The driver for the Grin compression program.
 */
public class Grin {
    private static final int MAGIC_NUMBER = 0x736; // 1846 in hex

    /**
     * Decodes the .grin file denoted by infile and writes the output to the
     * .grin file denoted by outfile.
     * 
     * @param infile  the file to decode
     * @param outfile the file to output to
     * @throws IllegalArgumentException if the input file is not a valid .grin file
     * @throws IOException              if there is an error reading from input or
     *                                  writing to output
     */
    public static void decode(String infile, String outfile) throws IOException {
        try (BitInputStream in = new BitInputStream(infile);
                BitOutputStream out = new BitOutputStream(outfile)) {

            int magic = in.readBits(32);
            if (magic != MAGIC_NUMBER) {
                throw new IllegalArgumentException("Invalid .grin file: incorrect magic number");
            }

            // Construct Huffman tree from serialized format
            HuffmanTree tree = new HuffmanTree(in);

            // Decode the payload
            tree.decode(in, out);
        }
    }

    /**
     * Creates a mapping from 8-bit sequences to number-of-occurrences of
     * those sequences in the given file. To do this, read the file using a
     * BitInputStream, consuming 8 bits at a time.
     * 
     * @param file the file to read
     * @return a frequency map for the given file
     * @throws IOException if there is an error reading from the file
     */
    public static Map<Short, Integer> createFrequencyMap(String file) throws IOException {
        Map<Short, Integer> freqs = new HashMap<>();
        try (BitInputStream in = new BitInputStream(file)) {
            while (true) {
                int value = in.readBits(8);
                if (value == -1) {
                    break;
                }
                freqs.merge((short) value, 1, Integer::sum);
            }
        }
        return freqs;
    }

    /**
     * Encodes the given file denoted by infile and writes the output to the
     * .grin file denoted by outfile.
     * 
     * @param infile  the file to encode
     * @param outfile the file to write the output to
     * @throws IOException if there is an error reading from input or writing to
     *                     output
     */
    public static void encode(String infile, String outfile) throws IOException {
        Map<Short, Integer> freqs = createFrequencyMap(infile);

        try (BitInputStream in = new BitInputStream(infile);
                BitOutputStream out = new BitOutputStream(outfile)) {

            out.writeBits(MAGIC_NUMBER, 32);

            HuffmanTree tree = new HuffmanTree(freqs);
            tree.serialize(out);

            tree.encode(in, out);
        }
    }

    /**
     * The entry point to the program.
     * 
     * @param args the command-line arguments
     */
    public static void main(String[] args) {
        if (args.length != 3 || (!args[0].equals("encode") && !args[0].equals("decode"))) {
            System.out.println("Usage: java Grin <encode|decode> <infile> <outfile>");
            return;
        }

        try {
            if (args[0].equals("encode")) {
                encode(args[1], args[2]);
            } else {
                decode(args[1], args[2]);
            }
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
}
