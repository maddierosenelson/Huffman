import java.util.PriorityQueue;

public class HuffProcessor implements Processor {
	int[] myFreqs;
	String[] myExtract = new String[ALPH_SIZE + 1];
	PriorityQueue<HuffNode> myPQ;

	public void compress(BitInputStream in, BitOutputStream out) {
		CountFreq(in);
		BuildTree();
		extractCodes(myPQ.peek(), "");
		out.writeBits(BITS_PER_INT, HUFF_NUMBER);
		writeHeader(myPQ.peek(), out);
		writeBody(in, out);
		pseudoEOF(out);
	}

	public int[] CountFreq(BitInputStream in) {
		/**
		 * The index value will correlate to the specific character while the
		 * value at that index will correlate to the number of times the
		 * character occurs in the file. It is possible for a character to never
		 * occur. If this happens, the value will simply be 0 at that index.
		 */
		myFreqs = new int[ALPH_SIZE];
		int cur = in.readBits(BITS_PER_WORD);
		// We go until cur is != -1 because this is when there will be no more
		// bits to read.
		while (cur != -1) {
			myFreqs[cur]++;
			cur = in.readBits(BITS_PER_WORD);
		}
		// We reset here because we must reread the in later in the writeBody
		// method.
		in.reset();
		return myFreqs;
	}

	public void BuildTree() {
		myPQ = new PriorityQueue<HuffNode>();
		for (int i = 0; i < ALPH_SIZE; i++) {
			if (myFreqs[i] != 0) {
				myPQ.add(new HuffNode(i, myFreqs[i]));
			}
		}
		// Add the Pseudo EOF so that decompress knows to decompress it.
		myPQ.add(new HuffNode(PSEUDO_EOF, 0));
		while (myPQ.size() > 1) {
			HuffNode node1 = myPQ.poll();
			HuffNode node2 = myPQ.poll();
			// Put a new node back into the queue that is the sum of its two
			// children with the two children as left and right.
			myPQ.add(new HuffNode(-1, node1.weight() + node2.weight(), node1, node2));
		}
	}

	/*
	 * This method works recursively until you are at a leaf child, the base
	 * case. Each step that it takes to get to that leaf child you either add a
	 * 0 or a 1 depending on if they are moving to the right or to the left.
	 */
	private void extractCodes(HuffNode current, String path) {
		if (current.left() == null && current.right() == null) {
			myExtract[current.value()] = path;
			return;
		}
		extractCodes(current.left(), path + 0);
		extractCodes(current.right(), path + 1);
	}

	// Once again, this method is recursive and works towards the base case of
	// finding a leaf node.
	private void writeHeader(HuffNode current, BitOutputStream out) {
		if (current.left() == null && current.right() == null) {
			out.writeBits(1, 1);
			out.writeBits(9, current.value());
			return;
		}
		out.writeBits(1, 0);
		writeHeader(current.left(), out);
		writeHeader(current.right(), out);
	}

	private void writeBody(BitInputStream in, BitOutputStream out) {
		int cur = in.readBits(BITS_PER_WORD);
		while (cur != -1) {
			String code = myExtract[cur];
			// The 2 is used to signal that we are using binary.
			out.writeBits(code.length(), Integer.parseInt(code, 2));
			cur = in.readBits(BITS_PER_WORD);
		}
		in.reset();
	}

	private void pseudoEOF(BitOutputStream out) {
		out.writeBits(myExtract[PSEUDO_EOF].length(), Integer.parseInt(myExtract[PSEUDO_EOF], 2));
	}

	// The second half of this class is now about writing out the compressed
	// code.
	public void decompress(BitInputStream in, BitOutputStream out) {
		if (in.readBits(BITS_PER_INT) != HUFF_NUMBER) {
			throw new HuffException("");
		}
		HuffNode root = readHeader(in);
		parseDecompress(root, in, out);
	}

	private HuffNode readHeader(BitInputStream in) {
		if (in.readBits(1) == 0) {
			/*
			 * Because we compressed the left before the right, we must use this
			 * same order when decompressing our text.
			 */
			HuffNode left = readHeader(in);
			HuffNode right = readHeader(in);
			return new HuffNode(-1, 0, left, right);
		} else {
			return new HuffNode(in.readBits(9), 0);
		}
	}

	private void parseDecompress(HuffNode root, BitInputStream in, BitOutputStream out) {
		HuffNode curNode = root;
		/*
		 * Because the values have already been compressed, it is important to
		 * note that now we are reading in only 1 bit at a time.
		 */
		int curVal = in.readBits(1);
		/*
		 * In this while loop we are updating two things: the pointer node and
		 * also the part of the compressed code that we are reading in 1 bit at
		 * a time.
		 */
		while (curVal != -1) {
			if (curVal == 1) {
				curNode = curNode.right();
			} else {
				curNode = curNode.left();
			}
			if (curNode.left() == null && curNode.right() == null) {
				if (curNode.value() == PSEUDO_EOF) {
					break;
				} else
					out.writeBits(BITS_PER_WORD, curNode.value());
				curNode = root;
			}
			curVal = in.readBits(1);
		}
	}
}
