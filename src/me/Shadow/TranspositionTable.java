package me.Shadow;

public class TranspositionTable
{
	static final int LOOKUP_FAILED = Integer.MIN_VALUE;
	
	static final int EXACT_BOUND = 0;
	static final int LOWER_BOUND = 1;
	static final int UPPER_BOUND = 2;
	
	static final int DEFAULT_TABLE_SIZE_MB = 128;
	static final int BYTES_PER_ENTRY = 6;
	
	// first 32 bits represent the transposition
	// 		first 21 bits is the evaluation
	//		next 2 bits represents the bound
	//		final 9 bits is the depth
	// next 16 bits is the move
	// 16 lowest order bits are the 16 highest order bits of the key
	// the index to the table is the lowest n order bits of the key where n is the amount of bits in the size
	long [] table;
	long size;
	
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
	}
	
	public int lookupEvaluation(long zobristKey, int depth, int alpha, int beta)
	{
		long entry = getEntry(zobristKey);
		if (getPartialKeyFromEntry(entry) == ((zobristKey >>> 48) & Short.MAX_VALUE))
		{
			if (getDepthFromEntry(entry) >= depth)
			{
				int evaluation = getEvaluationFromEntry(entry);
				int bound = getBoundFromEntry(entry);
				
				if (bound == EXACT_BOUND) return evaluation;
				else if (bound == LOWER_BOUND && evaluation >= beta) return evaluation;
				else if (bound == UPPER_BOUND && evaluation <= alpha) return evaluation;
			}
		}
		
		return LOOKUP_FAILED;
	}
	
	public Move lookupMove(long zobristKey)
	{
		long entry = getEntry(zobristKey);
		if (getPartialKeyFromEntry(entry) == ((zobristKey >>> 48) & Short.MAX_VALUE))
		{
			return new Move(getMoveFromEntry(entry));
		}
		return new Move(0);
	}
	
	public void storeEvaluation(long zobristKey, int evaluation, int depth, int bound, int move)
	{
		long entry = evaluation << 43;
		entry |= bound << 41;
		entry |= depth << 32;
		entry |= move << 16;
		entry |= (zobristKey >>> 48);
		table[getIndex(zobristKey)] = entry;
	}
	
	public int getEvaluationFromEntry(long entry)
	{
		return (int) (entry >> 43);	// take advantage of sign extending here
	}
	
	public int getBoundFromEntry(long entry)
	{
		return (int) ((entry >>> 41) & 0b11); // shift 41 times and then isolate last 2 bits
	}
	
	public int getDepthFromEntry(long entry)
	{
		return (int) ((entry >>> 32) & 0b111111111); // shift 32 times and then isolate last 9 bits
	}
	
	public int getMoveFromEntry(long entry)
	{
		return (int) ((entry >>> 16) & Short.MAX_VALUE); // shift 48 times and then isolate last 16 bits
	}
	
	public int getPartialKeyFromEntry(long entry)
	{
		return (int) (entry & Short.MAX_VALUE);
	}
	
	public long getEntry(long zobristKey)
	{
		return table[getIndex(zobristKey)];
	}
	
	private int getIndex(long zobristKey)
	{
		return (int)(zobristKey % size);
	}
}
