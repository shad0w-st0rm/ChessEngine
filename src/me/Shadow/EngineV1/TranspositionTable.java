package me.Shadow.EngineV1;

public class TranspositionTable
{
	static final int LOOKUP_FAILED = Integer.MIN_VALUE;
	
	static final int EXACT_BOUND = 0;
	static final int LOWER_BOUND = 1;
	static final int UPPER_BOUND = 2;
	
	static final int DEFAULT_TABLE_SIZE_MB = 128;
	static final int BYTES_PER_ENTRY = 8;
	
	static final int PARTIAL_KEY_MASK = 0xFFFF;
	
	// first 32 bits represent the transposition
	// 		first 21 bits is the evaluation
	//		next 2 bits represents the bound
	//		final 9 bits is the depth
	// next 16 bits is the move
	// 16 lowest order bits are the 16 highest order bits of the key
	// the index to the table is the lowest n order bits of the key where n is the amount of bits in the size
	long [] table;
	long size;
	long indexBitMask = 0;
	int numStored;
	
	public TranspositionTable()
	{
		this(DEFAULT_TABLE_SIZE_MB);
	}
	
	public TranspositionTable(int sizeMB)
	{
		int totalTableBytes = sizeMB*1024*1024;
		int numEntries = (totalTableBytes / BYTES_PER_ENTRY);
		table = new long[numEntries];
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
	
	public int lookupEvaluation(long zobristKey, int depth, int alpha, int beta)
	{
		long entry = getEntry(zobristKey);
		if ((entry & PARTIAL_KEY_MASK) == ((zobristKey >>> 48) & PARTIAL_KEY_MASK))
		{
			if (((entry >>> 32) & 0b111111111) >= depth) // shift 32 times and then isolate last 9 bits
			{
				int evaluation = (int) (entry >> 43);	// take advantage of sign extending here
				long bound = ((entry >>> 41) & 0b11); // shift 41 times and then isolate last 2 bits
				
				if (bound == EXACT_BOUND) return evaluation;
				else if (bound == LOWER_BOUND && evaluation >= beta) return evaluation;
				else if (bound == UPPER_BOUND && evaluation <= alpha) return evaluation;
			}
		}
		
		return LOOKUP_FAILED;
	}
	
	public short lookupMove(long zobristKey)
	{
		long entry = getEntry(zobristKey);
		if ((entry & PARTIAL_KEY_MASK) == ((zobristKey >>> 48) & PARTIAL_KEY_MASK))
		{
			return (short) ((entry >>> 16) & PARTIAL_KEY_MASK);	// shift 48 times and then isolate last 16 bits
		}
		return MoveHelper.NULL_MOVE;
	}
	
	public void storeEvaluation(long zobristKey, int evaluation, int depth, int bound, short move)
	{
		if (getEntry(zobristKey) != 0) numStored++;
		
		long entry = ((long)evaluation) << 43;
		entry |= ((long)bound) << 41;
		entry |= ((long)depth) << 32;
		entry |= (((long)move) & PARTIAL_KEY_MASK) << 16;
		entry |= (zobristKey >>> 48);
		table[getIndex(zobristKey)] = entry;
	}
	
	public long getEntry(long zobristKey)
	{
		return table[getIndex(zobristKey)];
	}
	
	private int getIndex(long zobristKey)
	{
		//assert((zobristKey & indexBitMask) == Long.remainderUnsigned(zobristKey, size));
		return (int)(zobristKey & indexBitMask);
	}
}
