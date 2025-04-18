package me.Shadow.Engine;

public class TranspositionTable
{
	static final int LOOKUP_FAILED = Integer.MIN_VALUE;

	static final int DEFAULT_TABLE_SIZE_MB = 8;
	static final int BYTES_PER_ENTRY = 8;

	static final int MOVE_MASK = 0x7FFF;
	static final int MOVE_SHIFT = 0;

	static final int DEPTH_MASK = 0x3F;
	static final int BOUND_MASK = 0x3;
	static final int EVAL_MASK = 0x7FFF;
	static final long PARTIAL_KEY_MASK = 0x1FFFFFFFFFFL;

	static final int DEPTH_SHIFT = 41;
	static final int BOUND_SHIFT = DEPTH_SHIFT + 6;
	static final int EVAL_SHIFT = BOUND_SHIFT + 2;
	static final int ZOBRIST_SHIFT = 64 - DEPTH_SHIFT;

	static final int EVAL_INDEX = 0;
	static final int DEPTH_INDEX = 1;
	static final int BOUND_INDEX = 2;
	static final int MOVE_INDEX = 3;

	static final long PAWNS_KEY_MASK = 0x000FFFFFFFFFFFFFL;
	static final long PAWNS_INDEX_MASK = 0xFFF;
	static final int PAWNS_EVAL_SHIFT = 52;
	static final int PAWNS_ZOBRIST_SHIFT = 64 - PAWNS_EVAL_SHIFT;

	// 23 highest order bits represent the transposition
	// first 15 bits is the evaluation
	// next 2 bits represents the bound
	// final 6 bits is the depth
	// last 41 bits represent the highest 41 order bits of the key
	// the index to the table is the lowest n order bits of the key where n is the
	// amount of bits in the size
	long[] positionTable;

	// first 17 bits are unused
	// last 15 bits represent the move
	short[] moveTable;

	long[] pawnsTable;

	long indexBitMask = 0;
	
	public TranspositionTable()
	{
		this(DEFAULT_TABLE_SIZE_MB);
	}

	public TranspositionTable(int sizeMB)
	{
		int numEntries = sizeMB * 1024 * (1024 / BYTES_PER_ENTRY);
		numEntries = Integer.highestOneBit(numEntries); // essentially floor of base 2 log
		positionTable = new long[numEntries];
		moveTable = new short[numEntries];
		indexBitMask = numEntries - 1; // sets all lower bits to 1 for mask (10...00 -> 011...11)
		pawnsTable = new long[1 << 12];
	}

	public void clearTable()
	{
		positionTable = new long[moveTable.length];
		moveTable = new short[positionTable.length];
		pawnsTable = new long[1 << 12];
	}

	public short[] getEntry(long zobristKey)
	{
		int index = (int) (zobristKey & indexBitMask);
		long entry = positionTable[index];
		if ((entry & PARTIAL_KEY_MASK) == (zobristKey >>> ZOBRIST_SHIFT))
		{
			// lookupHits++;
			short depth = (short) ((entry >>> DEPTH_SHIFT) & DEPTH_MASK);
			short evaluation = (short) (entry >> EVAL_SHIFT); // take advantage of sign extending here
			short bound = (short) ((entry >>> BOUND_SHIFT) & BOUND_MASK); // shift 41 times and then isolate last 2 bits
			long moveEntry = moveTable[(int) (zobristKey & indexBitMask)];
			short move = (short) ((moveEntry >>> MOVE_SHIFT) & MOVE_MASK);

			return new short[] { evaluation, depth, bound, move };
		}

		return null;
	}

	public int getPawnsEval(long pawnsKey)
	{
		int index = (int) (pawnsKey & PAWNS_INDEX_MASK);
		long entry = pawnsTable[index];
		if ((entry & PAWNS_KEY_MASK) == (pawnsKey >>> PAWNS_ZOBRIST_SHIFT))
		{
			int evaluation = (int) (entry >> PAWNS_EVAL_SHIFT); // take advantage of sign extending here
			return evaluation;
		}
		return LOOKUP_FAILED;
	}

	public void storeEvaluation(long zobristKey, int evaluation, int depth, int bound, short move)
	{
		int index = (int) (zobristKey & indexBitMask);

		long posEntry = ((long) evaluation) << EVAL_SHIFT;
		posEntry |= ((long) bound) << BOUND_SHIFT;
		posEntry |= ((long) depth) << DEPTH_SHIFT;
		posEntry |= (zobristKey >>> ZOBRIST_SHIFT);

		//int moveEntry = move << MOVE_SHIFT;

		positionTable[index] = posEntry;
		moveTable[index] = move;
	}

	public void storePawnsEvaluation(long pawnsKey, int evaluation)
	{
		int index = (int) (pawnsKey & PAWNS_INDEX_MASK);
		long pawnsEntry = ((long) evaluation) << PAWNS_EVAL_SHIFT;
		pawnsEntry |= (pawnsKey >>> PAWNS_ZOBRIST_SHIFT);
		pawnsTable[index] = pawnsEntry;
	}
	
	/*
	// transposition table test using perft
	// use positionTable to store full zobrist key (just for ease of use, only non key bits are required)
	// use moveTable to stores movesCount
	public void perftStore(long zobristKey, int depth, long movesCount)
	{
		int index = (int) (zobristKey & indexBitMask);
		long posEntry = ((long) depth) << DEPTH_SHIFT;
		posEntry |= (zobristKey >>> ZOBRIST_SHIFT);
		positionTable[index] = posEntry;
		moveTable[index] = movesCount;
	}
	
	public long perftLookup(long zobristKey, int depth)
	{
		int index = (int) (zobristKey & indexBitMask);
		long entry = positionTable[index];
		if ((entry & PARTIAL_KEY_MASK) == (zobristKey >>> ZOBRIST_SHIFT))
		{
			int depthStored = (int) ((entry >>> DEPTH_SHIFT) & DEPTH_MASK);
			if (depthStored == depth)
			{
				return moveTable[index];
			}
		}
		return LOOKUP_FAILED;
	}
	*/
}
