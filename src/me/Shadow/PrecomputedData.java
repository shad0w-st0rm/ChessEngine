package me.Shadow;

public class PrecomputedData
{
	public static long [] KNIGHT_MOVES = new long[64];
	public static long [] KING_MOVES = new long[64];
	
	public static int[] directionOffsets = {8, -8, -1, 1, 7, -7, 9, -9};
	
	public static int [][] numSquaresToEdge = new int[64][8];
	
	static
	{
		
	}
}
