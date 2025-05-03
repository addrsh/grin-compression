package edu.grinnell.csc207.compression;

import java.util.Map;
import java.util.PriorityQueue;
import java.util.Comparator;
import java.io.IOException;
import java.util.HashMap;

/**
 * A HuffmanTree derives a space-efficient coding of a collection of byte
 * values.
 *
 * The huffman tree encodes values in the range 0--255 which would normally
 * take 8 bits. However, we also need to encode a special EOF character to
 * denote the end of a .grin file. Thus, we need 9 bits to store each
 * byte value. This is fine for file writing (modulo the need to write in
 * byte chunks to the file), but Java does not have a 9-bit data type.
 * Instead, we use the next larger primitive integral type, short, to store
 * our byte values.
 */
public class HuffmanTree {
    private static final short EOF = 256;
    private Node root;

    /**
     * A Node in the Huffman tree. Can be either a leaf node (containing a value)
     * or an internal node (containing children).
     */
    private static class Node {
        private final Short value; // null for internal nodes
        private final int frequency;
        private final Node left;
        private final Node right;

        // leaf nodes constructor
        public Node(Short value, int frequency) {
            this.value = value;
            this.frequency = frequency;
            this.left = null;
            this.right = null;
        }

        // internal nodes constructor
        public Node(Node left, Node right) {
            this.value = null;
            this.frequency = left.frequency + right.frequency;
            this.left = left;
            this.right = right;
        }

        // checks if the node is a leaf
        public boolean isLeaf() {
            return value != null;
        }
    }

    /**
     * Constructs a new HuffmanTree from a frequency map.
     * 
     * @param freqs a map from 9-bit values to frequencies.
     */
    public HuffmanTree(Map<Short, Integer> freqs) {
        // Add EOF character with frequency 1
        freqs.put(EOF, 1);

        // Create a priority queue ordered by frequency
        PriorityQueue<Node> pq = new PriorityQueue<>(
                Comparator.comparingInt(node -> node.frequency));

        // Create leaf nodes for each character and add to priority queue
        for (Map.Entry<Short, Integer> entry : freqs.entrySet()) {
            pq.add(new Node(entry.getKey(), entry.getValue()));
        }

        // Build the tree by repeatedly combining the two lowest frequency nodes
        while (pq.size() > 1) {
            Node left = pq.poll();
            Node right = pq.poll();
            pq.add(new Node(left, right));
        }

        // The last remaining node is the root
        root = pq.poll();
    }

    /**
     * Constructs a new HuffmanTree from the given file.
     * 
     * @param in the input file (as a BitInputStream)
     * @throws IOException if there is an error reading from the input stream
     */
    public HuffmanTree(BitInputStream in) throws IOException {
        root = readNode(in);
    }

    /**
     * recursively reads a node from the input stream.
     * 
     * @param in the input stream
     * @return the constructed node
     * @throws IOException if an error reading from the input stream
     */
    private Node readNode(BitInputStream in) throws IOException {
        int bit = in.readBit();
        if (bit == -1) {
            throw new IOException("Unexpected end of input stream");
        }

        if (bit == 1) {
            Node left = readNode(in);
            Node right = readNode(in);
            return new Node(left, right);
        } else {
            int value = in.readBits(9);
            if (value == -1) {
                throw new IOException("Unexpected end of input stream");
            }
            return new Node((short) value, 0); // Frequency doesn't matter for deserialization
        }
    }

    /**
     * Writes this HuffmanTree to the given file as a stream of bits in a
     * serialized format.
     * 
     * @param out the output file as a BitOutputStream
     * @throws IOException if there is an error writing to the output stream
     */
    public void serialize(BitOutputStream out) throws IOException {
        serializeNode(root, out);
    }

    /**
     * recursively serializes a node to the output stream.
     * 
     * @param node the node to serialize
     * @param out  the output stream
     * @throws IOException if there is an error writing to the output stream
     */
    private void serializeNode(Node node, BitOutputStream out) throws IOException {
        if (node.isLeaf()) {
            out.writeBit(0);
            out.writeBits(node.value, 9);
        } else {
            out.writeBit(1);
            serializeNode(node.left, out);
            serializeNode(node.right, out);
        }
    }

    /**
     * Encodes the file given as a stream of bits into a compressed format
     * using this Huffman tree. The encoded values are written, bit-by-bit
     * to the given BitOuputStream.
     * 
     * @param in  the file to compress.
     * @param out the file to write the compressed output to.
     * @throws IOException if there is an error reading from input or writing to
     *                     output
     */
    public void encode(BitInputStream in, BitOutputStream out) throws IOException {
        Map<Short, String> codes = new HashMap<>();
        buildCodes(root, "", codes);

        while (true) {
            int value = in.readBits(8);
            if (value == -1) {
                break;
            }
            String code = codes.get((short) value);
            for (char bit : code.toCharArray()) {
                out.writeBit(bit - '0');
            }
        }

        String eofCode = codes.get(EOF);
        for (char bit : eofCode.toCharArray()) {
            out.writeBit(bit - '0');
        }
    }

    /**
     * recursively builds Huffman codes for each value.
     * 
     * @param node  current node in the tree
     * @param code  current code being built
     * @param codes map to store the codes
     */
    private void buildCodes(Node node, String code, Map<Short, String> codes) {
        if (node.isLeaf()) {
            codes.put(node.value, code);
        } else {
            buildCodes(node.left, code + "0", codes);
            buildCodes(node.right, code + "1", codes);
        }
    }

    /**
     * Decodes a stream of huffman codes from a file given as a stream of
     * bits into their uncompressed form, saving the results to the given
     * output stream. Note that the EOF character is not written to out
     * because it is not a valid 8-bit chunk (it is 9 bits).
     * 
     * @param in  the file to decompress.
     * @param out the file to write the decompressed output to.
     * @throws IOException if there is an error reading from input or writing to
     *                     output
     */
    public void decode(BitInputStream in, BitOutputStream out) throws IOException {
        Node current = root;

        while (true) {
            int bit = in.readBit();
            if (bit == -1) {
                throw new IOException("Unexpected end of input stream");
            }

            // Move down the tree based on the bit
            current = (bit == 0) ? current.left : current.right;

            // If we've reached a leaf node
            if (current.isLeaf()) {
                // If we've reached EOF, we're done
                if (current.value == EOF) {
                    break;
                }
                // Otherwise write the value (8 bits) and continue
                out.writeBits(current.value, 8);
                current = root;
            }
        }
    }
}
