package me.Shadow.Engine;

public class PrecomputedData
{
	public static final long [] KNIGHT_MOVES = new long[64];
	public static final long [] KING_MOVES = new long[64];
	public static final long [] PAWN_CAPTURES = new long[64*2];
	
	public static final int[] directionOffsets = {8, -8, -1, 1, 7, -7, 9, -9};
	
	public static final int [] numSquaresToEdge = new int[64*8];
	
	//public static final long [] rayAlignMask = new long[64*64];
	public static final long [] rayDirectionMask = new long[64*8];
	
	public static final byte [] distToCenter = new byte[64];
	public static final byte [] orthoSquaresDist = new byte[64*64];
	
	public static final long WHITE_KINGSIDE_CASTLING_CLEAR_MASK = (0b11L << 5);
	public static final long BLACK_KINGSIDE_CASTLING_CLEAR_MASK = (0b11L << 61);
	public static final long WHITE_QUEENSIDE_CASTLING_CLEAR_MASK = 0b1110L;
	public static final long WHITE_QUEENSIDE_CASTLING_SAFE_MASK = 0b1100L;
	public static final long BLACK_QUEENSIDE_CASTLING_CLEAR_MASK = (0b1110L << 56);
	public static final long BLACK_QUEENSIDE_CASTLING_SAFE_MASK = (0b1100L << 56);
	
	public static void generateData()
	{
		PieceHelper.initPieceSquareTables();
		// num squares to edge
		// knight moves
		// king moves
		for (int index = 0; index < 64; index++)
		{
			int rank = index / 8;
			int file = index % 8;
			
			int north = 7 - rank;
			int south = rank;
			int west = file;
			int east = 7 - file;
			int northwest = Math.min(north, west);
			int southeast = Math.min(south, east);
			int northeast = Math.min(north, east);
			int southwest = Math.min(south, west);
			
			numSquaresToEdge[index*8 + 0] = north;
			numSquaresToEdge[index*8 + 1] = south;
			numSquaresToEdge[index*8 + 2] = west;
			numSquaresToEdge[index*8 + 3] = east;
			numSquaresToEdge[index*8 + 4] = northwest;
			numSquaresToEdge[index*8 + 5] = southeast;
			numSquaresToEdge[index*8 + 6] = northeast;
			numSquaresToEdge[index*8 + 7] = southwest;
			
			// knightMoves[] gives the index offsets for the 8 possible knight moves
			int [] knightOffsets = {17, 15, -17, -15, 10, 6, -10, -6};
			for (int i = 0; i < knightOffsets.length; i++)
			{
				int targetIndex = index + knightOffsets[i];
				if (targetIndex < 0 || targetIndex > 63)
					continue; // skip if index is off the board
				if (Math.abs(index % 8 - targetIndex % 8) != (i < 4 ? 1 : 2))
					continue; // skip if index overflowed to next rank or file incorrectly
				
				KNIGHT_MOVES[index] |= (1l << targetIndex);
			}
			
			for (int i = 0; i < directionOffsets.length; i++)
			{
				// king can move in all sliderMoves[] directions but only one square
				int targetIndex = index + directionOffsets[i];
				if (targetIndex < 0 || targetIndex > 63) continue;
				if (Math.abs(Utils.getSquareRank(targetIndex) - Utils.getSquareRank(index)) > 1) continue;
				if (Math.abs(Utils.getSquareFile(targetIndex) - Utils.getSquareFile(index)) > 1) continue;
				
				KING_MOVES[index] |= (1l << targetIndex);
			}
			
			long pawnBitboard = 1L << index;
			long pawnAttacksWhite = ((pawnBitboard & (~MoveGenerator.A_FILE)) << 7) | ((pawnBitboard & (~MoveGenerator.H_FILE)) << 9);
			long pawnAttacksBlack = ((pawnBitboard & (~MoveGenerator.A_FILE)) >>> 9) | ((pawnBitboard & (~MoveGenerator.H_FILE)) >>> 7);
			PAWN_CAPTURES[index * 2] = pawnAttacksWhite;
			PAWN_CAPTURES[index * 2 + PieceHelper.BLACK] = pawnAttacksBlack;
			
			final int dRank = 8 - (index / 8);
			final int dFile = (index % 8) + 1;
			final byte centerDist = (byte) (Math.max(4 - dRank, dRank - 5) + Math.max(4 - dFile, dFile - 5));
			distToCenter[index] = centerDist;
			
			for (int i = 0; i < 64; i++)
			{
				final int otherRank = 8 - (i / 8);
				final int otherFile = (i % 8) + 1;
				byte squaresDist = (byte) (14 - (Math.abs(dRank - otherRank) + Math.abs(dFile - otherFile)));
				orthoSquaresDist[(index << 6) | i] = squaresDist;
			}
		}
		
		// ray direction mask
		int [] rankOffsets = {1, -1, 0, 0, 1, -1, 1, -1};
		int [] fileOffsets = {0, 0, -1, 1, -1, 1, 1, -1};
		for (int rank = 1; rank <= 8; rank++)
		{
			for (int file = 1; file <= 8; file++)
			{
				int squareIndex = Utils.getSquareIndexFromRankFile(rank, file);
				for (int dir = 0; dir < 8; dir++)
				{
					for (int i = 1; i <= 8; i++)
					{
						int newRank = rank + (rankOffsets[dir] * i);
						int newFile = file + (fileOffsets[dir] * i);
						if (Utils.isValidSquare(newRank, newFile))
						{
							int index = Utils.getSquareIndexFromRankFile(newRank, newFile);
							rayDirectionMask[squareIndex*8 + dir] |= (1l << index);
						}
					}
				}
			}
		}
		
		/*
		// ray align mask
		for (int first = 0; first < 64; first++)
		{
			for (int second = 0; second < 64; second++)
			{
				int rankOffset = (int) Math.signum(Utils.getSquareRank(second) - Utils.getSquareRank(first));
				int fileOffset = (int) Math.signum(Utils.getSquareFile(second) - Utils.getSquareFile(first));
				
				for (int i = -8; i <= 8; i++)
				{
					int newRank = (rankOffset * i) + Utils.getSquareRank(first);
					int newFile = (fileOffset * i) + Utils.getSquareFile(first);
					if (Utils.isValidSquare(newRank, newFile))
					{
						int index = Utils.getSquareIndexFromRankFile(newRank, newFile);
						rayAlignMask[first*64 + second] |= (1l << index);
					}
				}
			}
		}
		*/
		
		// printRayDirectionMask(4);
		// printRayAlignMask(8);
	}
	
	public static long getPawnCaptures(int index, int color)
	{
		return PAWN_CAPTURES[index * 2 + color];
	}
	
	public static void printRayDirectionMask(int dir)
	{
		for (int i = 0; i < 64; i++)
		{
			System.out.println("At Square " + Utils.getSquareName(i));
			long ray = rayDirectionMask[i*8 + dir];
						
			while (ray != 0)
			{
				int lsbPosition = Bitboards.getLSB(ray);
				ray = Bitboards.toggleBit(ray, lsbPosition);
				System.out.println("Ray Square " + Utils.getSquareName(lsbPosition));
			}
		}
	}
	
	/*
	public static void printRayAlignMask(int first)
	{
		System.out.println("Starting at square " + Utils.getSquareName(first));
		for (int i = 0; i < 64; i++)
		{
			System.out.println("Aligning with Square " + Utils.getSquareName(i));
			long ray = rayAlignMask[first*64 + i];
			
			while (ray != 0)
			{
				int lsbPosition = Bitboards.getLSB(ray);
				ray = Bitboards.toggleBit(ray, lsbPosition);
				System.out.println("Ray Square " + Utils.getSquareName(lsbPosition));
			}
		}
	}
	*/
}
