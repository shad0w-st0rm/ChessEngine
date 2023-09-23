package me.Shadow;

public class Square
{
	public static int getRank(int index)
	{
		return (index / 8) + 1;
	}

	public static int getFile(int index)
	{
		return (index % 8) + 1;
	}

	public static int getIndexFromRankFile(int rank, int file)
	{
		int index = ((rank - 1) * 8) + file - 1;
		return index;
	}
	
	public static boolean isValidSquare(int rank, int file)
	{
		return (rank >= 1) && (rank <= 8) && (file >= 1) && (file <= 8);
	}

	public static String getSquareName(int rank, int file)
	{
		return getSquareName(getIndexFromRankFile(rank, file));
	}

	public static String getSquareName(int index)
	{
		return ((char) ((index % 8) + 97) + "" + ((index / 8) + 1));
	}
}