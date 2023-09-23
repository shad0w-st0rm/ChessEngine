package me.Shadow;

public class PrecomputedData
{
	public static long [] KNIGHT_MOVES = new long[64];
	public static long [] KING_MOVES = new long[64];
	
	public static int[] directionOffsets = {8, -8, -1, 1, 7, -7, 9, -9};
	
	public static int [][] numSquaresToEdge;
	
	public static long [][] rayAlignMask;
	public static long [][] rayDirectionMask;
	
	public static final long WHITE_KINGSIDE_CASTLING_CLEAR_MASK = (0b11L << 5);
	public static final long BLACK_KINGSIDE_CASTLING_CLEAR_MASK = (0b11L << 61);
	public static final long WHITE_QUEENSIDE_CASTLING_CLEAR_MASK = 0b1110L;
	public static final long WHITE_QUEENSIDE_CASTLING_SAFE_MASK = 0b1100L;
	public static final long BLACK_QUEENSIDE_CASTLING_CLEAR_MASK = (0b1110L << 56);
	public static final long BLACK_QUEENSIDE_CASTLING_SAFE_MASK = (0b1100L << 56);
	
	public static void generateData()
	{
		numSquaresToEdge = new int[64][8];
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
			
			numSquaresToEdge[index][0] = north;
			numSquaresToEdge[index][1] = south;
			numSquaresToEdge[index][2] = west;
			numSquaresToEdge[index][3] = east;
			numSquaresToEdge[index][4] = northwest;
			numSquaresToEdge[index][5] = southeast;
			numSquaresToEdge[index][6] = northeast;
			numSquaresToEdge[index][7] = southwest;
			
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
				if (Math.abs(Square.getRank(targetIndex) - Square.getRank(index)) > 1) continue;
				if (Math.abs(Square.getFile(targetIndex) - Square.getFile(index)) > 1) continue;
				
				KING_MOVES[index] |= (1l << targetIndex);
			}
		}
		
		rayDirectionMask = new long[64][8];
		int [] rankOffsets = {1, -1, 0, 0, 1, -1, 1, -1};
		int [] fileOffsets = {0, 0, -1, 1, -1, 1, 1, -1};
		for (int rank = 1; rank <= 8; rank++)
		{
			for (int file = 1; file <= 8; file++)
			{
				int squareIndex = Square.getIndexFromRankFile(rank, file);
				for (int dir = 0; dir < 8; dir++)
				{
					for (int i = 1; i <= 8; i++)
					{
						int newRank = rank + (rankOffsets[dir] * i);
						int newFile = file + (fileOffsets[dir] * i);
						if (Square.isValidSquare(newRank, newFile))
						{
							int index = Square.getIndexFromRankFile(newRank, newFile);
							rayDirectionMask[squareIndex][dir] |= (1l << index);
						}
					}
				}
			}
		}
		
		rayAlignMask = new long[64][64];
		for (int first = 0; first < 64; first++)
		{
			for (int second = 0; second < 64; second++)
			{
				int rankOffset = (int) Math.signum(Square.getRank(second) - Square.getRank(first));
				int fileOffset = (int) Math.signum(Square.getFile(second) - Square.getFile(first));
				
				for (int i = -8; i <= 8; i++)
				{
					int newRank = (rankOffset * i) + Square.getRank(first);
					int newFile = (fileOffset * i) + Square.getFile(first);
					if (Square.isValidSquare(newRank, newFile))
					{
						int index = Square.getIndexFromRankFile(newRank, newFile);
						rayAlignMask[first][second] |= (1l << index);
					}
				}
			}
		}
		// printRayDirectionMask(4);
		// printRayAlignMask(8);
	}
	
	public static void printRayDirectionMask(int dir)
	{
		for (int i = 0; i < 64; i++)
		{
			System.out.println("At Square " + Square.getSquareName(i));
			long ray = rayDirectionMask[i][dir];
						
			while (ray != 0)
			{
				int lsbPosition = Bitboards.getLSB(ray);
				ray = Bitboards.toggleBit(ray, lsbPosition);
				System.out.println("Ray Square " + Square.getSquareName(lsbPosition));
			}
		}
	}
	
	public static void printRayAlignMask(int first)
	{
		System.out.println("Starting at square " + Square.getSquareName(first));
		for (int i = 0; i < 64; i++)
		{
			System.out.println("Aligning with Square " + Square.getSquareName(i));
			long ray = rayAlignMask[first][i];
			
			while (ray != 0)
			{
				int lsbPosition = Bitboards.getLSB(ray);
				ray = Bitboards.toggleBit(ray, lsbPosition);
				System.out.println("Ray Square " + Square.getSquareName(lsbPosition));
			}
		}
	}
}
