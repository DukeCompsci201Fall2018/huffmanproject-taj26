import java.util.PriorityQueue;

/**
 * Although this class has a history of several years,
 * it is starting from a blank-slate, new and clean implementation
 * as of Fall 2018.
 * <P>
 * Changes include relying solely on a tree for header information
 * and including debug and bits read/written information
 * 
 * @author Owen Astrachan
 */

public class HuffProcessor {

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD); 
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE  = HUFF_NUMBER | 1;

	private final int myDebugLevel;
	
	public static final int DEBUG_HIGH = 4;
	public static final int DEBUG_LOW = 1;
	
	public HuffProcessor() {
		this(0);
	}
	
	public HuffProcessor(int debug) {
		myDebugLevel = debug;
	}
	
	/**
	 * Determine the frequency of each of 257 values by reading the 8-bit character chunks
	 * @param in the BitInputStream to be read
	 */
	public int[] readForCounts(BitInputStream in) {
		int[] freq = new int[257];
		for (int i = 0; i < freq.length; i ++) {
			freq[i] = 0;
		}
		int counter = 0;
		while (true) {
			int read = in.readBits(BITS_PER_WORD);
			if (read == -1) {
				break;
			}
			freq[read]++;
			counter++;
		}
		freq[PSEUDO_EOF] = 1;
		System.out.println(freq[87]);
		return freq;
	}
	
	/**
	 * Creates a Huffman Trie/Tree using a priority queue for compression
	 * A greedy algorithm
	 * Returns the root of the Huffman Trie/Tree
	 * @param counts the frequency of each alpha character
	 */
	public HuffNode makeTreeFromCounts(int[] counts) {
		PriorityQueue<HuffNode> pq = new PriorityQueue<>();
		
		for (int k = 0; k < counts.length; k++) {
			if (counts[k] != 0) { 
				//System.out.println(k + ";" + counts[k]);
				pq.add(new HuffNode(k, counts[k], null, null));
			}
		}
		while (pq.size() > 1) {
			HuffNode left = pq.remove();
			HuffNode right = pq.remove();
			HuffNode t = new HuffNode(0, left.myWeight + right.myWeight, left, right);
			pq.add(t);
		}
		HuffNode root = pq.remove();
		return root;
	}
	
	/**
	 * Recursively sets values of encoder based on the paths of the Tree and the relevant values
	 * @param root the root of the subtree
	 * @param path the String path so far to get to the root
	 * @param encoder the key for setting paths and values
	 */
	public void codingHelper(HuffNode root, String path, String[] encoder) {
		//System.out.println("hi" + path);
		if (root.myLeft == null && root.myRight == null) {
			encoder[root.myValue] = path;
			return;
		}
		codingHelper(root.myLeft,  (String)(path)+"0", encoder);
		codingHelper(root.myRight, (String)(path)+"1", encoder);
	}
	
	/**
	 * Makes a String[] of encodings based on the HuffTree
	 * @param the HuffNode root at the beginning of the encoding tree
	 */
	public String[] makeCodingsFromTree(HuffNode root) {
		String[] encoder = new String[ALPH_SIZE + 1];
		codingHelper(root, "", encoder);
		return encoder;
	}
	
	/**
	 * Writes the HuffTree to the out
	 * @param root the start of the tree to be written
	 * @param out the BitOutputStream to which the HuffTree is written
	 */
	public void writeHeader(HuffNode root, BitOutputStream out) {
		if (root.myLeft != null || root.myRight != null) {
			out.writeBits(1, 0);
			writeHeader(root.myLeft, out);
			writeHeader(root.myRight, out);
		} else {
			out.writeBits(1, 1);
			out.writeBits(BITS_PER_WORD + 1, root.myValue); /////////
		}
	}
	
	/**
	 * Writes the compressed file into the output
	 * @param codings the key for the characters and their paths
	 * @param in the BitInputStream of uncompressed data
	 * @param out the BitOutputStream to compress data to
	 */
	public void writeCompressedBits(String[] codings, BitInputStream in, BitOutputStream out) {
		in.reset();
		String code;
		while (true) {
			int val = in.readBits(BITS_PER_WORD);
			if (val == -1) {
				break;
			}
			code = codings[val];
			//System.out.println(val);
			out.writeBits(code.length(), Integer.parseInt(code, 2));
		}
		code = codings[PSEUDO_EOF];
		out.writeBits(code.length(),  Integer.parseInt(code, 2));
	}
	
	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be compressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void compress(BitInputStream in, BitOutputStream out){
		int[] counts = readForCounts(in);
		HuffNode root = makeTreeFromCounts(counts);
		String[] codings = makeCodingsFromTree(root);
		
		out.writeBits(BITS_PER_INT, HUFF_TREE);
		writeHeader(root, out);
		
		while (true) {
			int val = in.readBits(BITS_PER_WORD);
			if (val == -1) break;
			out.writeBits(BITS_PER_WORD, val);
		}
		in.reset();
		writeCompressedBits(codings, in, out);
		out.close();
	}
	/**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be decompressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void decompress(BitInputStream in, BitOutputStream out){
		int bits = in.readBits(BITS_PER_INT);
		if (bits!=HUFF_TREE) {
			throw new HuffException("illegal header starts with " + bits);
		}
		
		HuffNode root = readTreeHeader(in);
		readCompressedBits(root, in, out);
		
		while (true){
			int val = in.readBits(BITS_PER_WORD);
			if (val == -1) break;
			out.writeBits(BITS_PER_WORD, val);
		}
		out.close();
	}
	
	/**
	 * Reads a BitInputStream recursively and returns a Tree of HuffNodes that maps the key
	 * @param in a bit stream of the file to be converted to a HuffTree
	 */
	public HuffNode readTreeHeader(BitInputStream in) {
		int bit = in.readBits(1);
		if (bit == -1) {
			throw new HuffException("bad input, no PSEUDO_EOF");
		}
		if (bit == 0) {
			HuffNode left = readTreeHeader(in);
			HuffNode right = readTreeHeader(in);
			return new HuffNode(0, 0, left, right);
		} else {
			int value = in.readBits(BITS_PER_WORD + 1);
			return new HuffNode(value, 0, null, null);
		}
	}
	
	/**
	 * Reads a BitInputStream and decompresses
	 * @param root the current HuffNode in the tree
	 * @param in the compressed BitInputStream
	 * @param out the decompressed BitOutputStream
	 */
	public void readCompressedBits(HuffNode root, BitInputStream in, BitOutputStream out) {
		HuffNode current = root;
		while (true) {
			int bits = in.readBits(1);
			if (bits == -1) {
				throw new HuffException("bad input, no PSEUDO_EOF");
			} else {
				if (bits == 0) {
					current = current.myLeft;
				} else {
					current = current.myRight;
				}
				
				if (current.myLeft == null && current.myRight == null) {
					if (current.myValue == PSEUDO_EOF) {
						break;
					} else {
						out.writeBits(BITS_PER_WORD, current.myValue);
						current = root;
					}
				}
			}
		}
	}
}