package me.Shadow.Engine;

public class TranspositionTable
{
	static final int LOOKUP_FAILED = Integer.MIN_VALUE;
	
	static final int EXACT_BOUND = 0;
	static final int LOWER_BOUND = 1;
	static final int UPPER_BOUND = 2;
	
	static final int DEFAULT_TABLE_SIZE_MB = 128;
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
	static final int POSITION_MASK = DEPTH_MASK | BOUND_MASK | EVAL_MASK;
	static final long PARTIAL_KEY_MASK = 0x1FFFFFFFFFFL;
	
	static final int DEPTH_SHIFT = 41;
	static final int BOUND_SHIFT = DEPTH_SHIFT + 6;
	static final int EVAL_SHIFT = BOUND_SHIFT + 2;
	static final int ZOBRIST_SHIFT = 64 - DEPTH_SHIFT;

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

	long size;
	long indexBitMask = 0;
	public int typeTwoCollisions;
	public int positionsStored;
	public int lookups;

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
	}
	
	public void clearTable()
	{
		positionTable = new long[moveTable.length];
		moveTable = new int[positionTable.length];
		typeTwoCollisions = 0;
		positionsStored = 0;
		lookups = 0;
	}

	
	public void setObsoleteFlag(int numWhitePawns, int numBlackPawns, byte castlingRights)
	{
		numWhitePawns = (short) (Math.min(1, numWhitePawns) - 1);
		numBlackPawns = (short) (Math.min(1, numBlackPawns) - 1);
		currentObsoleteFlag = (numBlackPawns << BPAWNS_SHIFT) | (numWhitePawns << WPAWNS_SHIFT) | castlingRights;
	}
	
	public int lookupEvaluation(long zobristKey, int depth, int alpha, int beta)
	{
		lookups++;
		long entry = positionTable[(int)(zobristKey & indexBitMask)];
		if ((entry & PARTIAL_KEY_MASK) == (zobristKey >>> ZOBRIST_SHIFT))
		{
			if (((entry >>> DEPTH_SHIFT) & DEPTH_MASK) >= depth) // isolate depth bits
			{
				int evaluation = (int) (entry >> EVAL_SHIFT);	// take advantage of sign extending here
				long bound = ((entry >>> BOUND_SHIFT) & BOUND_MASK); // shift 41 times and then isolate last 2 bits
				
				if (bound == EXACT_BOUND) return evaluation;
				else if (bound == LOWER_BOUND && evaluation >= beta) return evaluation;
				else if (bound == UPPER_BOUND && evaluation <= alpha) return evaluation;
			}
		}
		
		return LOOKUP_FAILED;
	}
	
	public short lookupMove(long zobristKey)
	{
		long posEntry = positionTable[(int)(zobristKey & indexBitMask)];

		if ((posEntry & PARTIAL_KEY_MASK) == (zobristKey >>> ZOBRIST_SHIFT))
		{
			int moveEntry = moveTable[(int)(zobristKey & indexBitMask)];
			return (short) ((moveEntry >>> MOVE_SHIFT) & PARTIAL_KEY_MASK);
		}
		return MoveHelper.NULL_MOVE;
	}
	
	public void storeEvaluation(long zobristKey, int evaluation, int depth, int bound, short move)
	{
		int index = (int)(zobristKey & indexBitMask);
		long storedEntry = positionTable[index];
		if (storedEntry != 0 && (storedEntry & PARTIAL_KEY_MASK) != (zobristKey >>> ZOBRIST_SHIFT))
		{
			// Entry exists and is of a different zobrist key
			int moveEntry = moveTable[index];
			if ((((moveEntry & CASTLING_MASK) & ~(currentObsoleteFlag & CASTLING_MASK)) == 0) && ((moveEntry & WPAWNS_MASK) <= (currentObsoleteFlag & WPAWNS_MASK)) && ((moveEntry & BPAWNS_MASK) <= (currentObsoleteFlag & BPAWNS_MASK)))
			{
				// Stored transposition is not clearly obsolete so compare further
				// for now simply keep old position
				return;
			}
		}
		else if (storedEntry != 0)
		{
			// entry exists and is of the same key (still perhaps a different position, rare type 1 error)
			int storedEntryDepth = (int)((storedEntry >>> DEPTH_SHIFT) & DEPTH_MASK);
			int storedEntryBound = (int)((storedEntry >>> BOUND_SHIFT) & BOUND_MASK);
			if (storedEntryDepth > depth || (storedEntryDepth == depth && storedEntryBound < bound))
			{
				return; // favor higher depth entries or better bounded equal depth entries
			}
		}

		long posEntry = ((long)evaluation) << EVAL_SHIFT;
		posEntry |= ((long)bound) << BOUND_SHIFT;
		posEntry |= ((long)depth) << DEPTH_SHIFT;
		posEntry |= (zobristKey >>> ZOBRIST_SHIFT);

		int moveEntry = move << MOVE_SHIFT;
		moveEntry |= currentObsoleteFlag;

		positionTable[index] = posEntry;
		moveTable[index] = moveEntry;
	}
}
