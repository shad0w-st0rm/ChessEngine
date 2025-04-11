package me.Shadow.Engine;

public class TranspositionTable
{
	static final int LOOKUP_FAILED = Integer.MIN_VALUE;
	
	static final short EXACT_BOUND = 0;
	static final short LOWER_BOUND = 1;
	static final short UPPER_BOUND = 2;
	
	static final int DEFAULT_TABLE_SIZE_MB = 8;
	static final int BYTES_PER_ENTRY = 8;

	static final int MOVE_MASK = 0x7FFF;

	static final int MOVE_SHIFT = 0;
	static final int CASTLING_SHIFT = MOVE_SHIFT + 15;
	static final int WPAWNS_SHIFT = CASTLING_SHIFT + 4;
	static final int BPAWNS_SHIFT = WPAWNS_SHIFT + 3;

	static final int CASTLING_MASK = 0xF << CASTLING_SHIFT;
	static final int WPAWNS_MASK = 0x7 << WPAWNS_SHIFT;
	static final int BPAWNS_MASK = WPAWNS_MASK << BPAWNS_SHIFT;
	static final int OBSOLETE_MASK = CASTLING_MASK | WPAWNS_MASK | BPAWNS_MASK;

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
	// 		first 15 bits is the evaluation
	//		next 2 bits represents the bound
	//		final 6 bits is the depth
	// last 41 bits represent the highest 41 order bits of the key
	// the index to the table is the lowest n order bits of the key where n is the amount of bits in the size
	long [] positionTable;

	// first 7 bits are unused
	// next 10 bits represent the "age" mask
	// 		first 3 bits is the black pawns count
	//		next 3 bits is the white pawns count
	//		final 4 bits is the castling mask
	// last 15 bits represent the move
	int [] moveTable;
	
	long [] pawnsTable;

	long size;
	long indexBitMask = 0;
	public int positionsStored;
	public int lookups = 1;
	public int typeOneCollisions;
	public int lookupHits;
	public int lookupSuccesses;
	public int typeTwoReplacements;
	public int selfReplacements;

	int currentObsoleteFlag = OBSOLETE_MASK;
	
	public TranspositionTable()
	{
		this(DEFAULT_TABLE_SIZE_MB);
	}
	
	public TranspositionTable(int sizeMB)
	{
		int totalTableBytes = sizeMB*1024*1024;
		int numEntries = (totalTableBytes / BYTES_PER_ENTRY);
		positionTable = new long[numEntries];
		moveTable = new int[numEntries];
		size = numEntries;
		for (int i = 1 ; i < 100; i++) // table should never be larger than 2^100 entries
		{
			if (Math.pow(2, i) > size)
			{
				size = (long) Math.pow(2, i-1);
				indexBitMask = (1 << (i-1)) - 1;
				break;
			}
		}
		pawnsTable = new long[1 << 12];
	}
	
	public void clearTable()
	{
		positionTable = new long[moveTable.length];
		moveTable = new int[positionTable.length];
		positionsStored = 0;
		lookups = 1;
		typeOneCollisions = 0;
	}

	
	public int setObsoleteFlag(int numWhitePawns, int numBlackPawns, byte castlingRights)
	{
		currentObsoleteFlag = createObsoleteFlag(numWhitePawns, numBlackPawns, castlingRights);
		return currentObsoleteFlag;
	}
	
	public int createObsoleteFlag(int numWhitePawns, int numBlackPawns, byte castlingRights)
	{
		numWhitePawns = (short) (Math.max(1, numWhitePawns) - 1);
		numBlackPawns = (short) (Math.max(1, numBlackPawns) - 1);
		return (numBlackPawns << BPAWNS_SHIFT) | (numWhitePawns << WPAWNS_SHIFT) | (castlingRights << CASTLING_SHIFT);
	}
	
	public short [] getEntry(long zobristKey)
	{
		int index = (int)(zobristKey & indexBitMask);
		long entry = positionTable[index];
		if ((entry & PARTIAL_KEY_MASK) == (zobristKey >>> ZOBRIST_SHIFT))
		{
			//lookupHits++;
			short depth = (short) ((entry >>> DEPTH_SHIFT) & DEPTH_MASK);
			short evaluation = (short) (entry >> EVAL_SHIFT); // take advantage of sign extending here
			short bound = (short)((entry >>> BOUND_SHIFT) & BOUND_MASK); // shift 41 times and then isolate last 2 bits
			int moveEntry = moveTable[(int)(zobristKey & indexBitMask)];
			short move = (short) ((moveEntry >>> MOVE_SHIFT) & MOVE_MASK);
			
			return new short[] {evaluation, depth, bound, move};
		}
		
		return null;
	}
	
	public int getPawnsEval(long pawnsKey)
	{
		int index = (int)(pawnsKey & PAWNS_INDEX_MASK);
		long entry = pawnsTable[index];
		if ((entry & PAWNS_KEY_MASK) == (pawnsKey >>> PAWNS_ZOBRIST_SHIFT))
		{
			int evaluation = (int) (entry >> PAWNS_EVAL_SHIFT); // take advantage of sign extending here
			return evaluation;
		}
		return LOOKUP_FAILED;
	}
	
	public void storeEvaluation(long zobristKey, int evaluation, int depth, int bound, short move, int obsoleteFlag)
	{
		int index = (int)(zobristKey & indexBitMask);
		long storedEntry = positionTable[index];
		boolean notObsolete = false;
		if (storedEntry != 0 && (storedEntry & PARTIAL_KEY_MASK) != (zobristKey >>> ZOBRIST_SHIFT))
		{
			// Entry exists and is of a different zobrist key
			int moveEntry = moveTable[index];
			if ((((moveEntry & CASTLING_MASK) & ~(currentObsoleteFlag & CASTLING_MASK)) == 0) && ((moveEntry & WPAWNS_MASK) <= (currentObsoleteFlag & WPAWNS_MASK)) && ((moveEntry & BPAWNS_MASK) <= (currentObsoleteFlag & BPAWNS_MASK)))
			{
				// Stored transposition is not clearly obsolete so compare further
				notObsolete = true;
				//selfReplacements--;
			}
			//typeTwoReplacements++;
		}
		else if (storedEntry != 0 || notObsolete)
		{
			// entry exists and is of the same key (still perhaps a different position, rare type 1 error)
			int storedEntryDepth = (int)((storedEntry >>> DEPTH_SHIFT) & DEPTH_MASK);
			int storedEntryBound = (int)((storedEntry >>> BOUND_SHIFT) & BOUND_MASK);
			if (storedEntryDepth > depth || (storedEntryDepth == depth && storedEntryBound < bound))
			{
				return; // favor higher depth entries or better bounded equal depth entries
			}
			//selfReplacements++;
		}
		else	// no entry exists yet
		{
			//positionsStored++;
		}

		long posEntry = ((long)evaluation) << EVAL_SHIFT;
		posEntry |= ((long)bound) << BOUND_SHIFT;
		posEntry |= ((long)depth) << DEPTH_SHIFT;
		posEntry |= (zobristKey >>> ZOBRIST_SHIFT);

		int moveEntry = move << MOVE_SHIFT;
		moveEntry |= obsoleteFlag;

		positionTable[index] = posEntry;
		moveTable[index] = moveEntry;
	}
	
	public void storePawnsEvaluation(long pawnsKey, int evaluation)
	{
		int index = (int)(pawnsKey & PAWNS_INDEX_MASK);
		long pawnsEntry = ((long)evaluation) << PAWNS_EVAL_SHIFT;
		pawnsEntry |= (pawnsKey >>> PAWNS_ZOBRIST_SHIFT);
		pawnsTable[index] = pawnsEntry;
	}
}
