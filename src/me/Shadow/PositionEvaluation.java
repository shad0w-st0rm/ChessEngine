package me.Shadow;

public class PositionEvaluation
{
	private int data;
	private Move move;
	
	public PositionEvaluation(int eval, int depth, boolean lowerBound, boolean upperBound, Move moveIn)
	{
		data |= (eval & 0b111111111111111111111); // grabs only the first 21 bits
		data |= (lowerBound ? 1 : 0) << 21;
		data |= (upperBound ? 1 : 0) << 22;
		data |= (depth << 23);
		move = moveIn;
	}
	
	public int getEvaluation()
	{
		return ((data << 11) >> 11); // take advantage of sign extending here
	}
	
	public boolean isExact()
	{
		return (((data >>> 21) & 3) == 0);
	}
	
	public boolean isLowerBound()
	{
		return (((data >>> 21) & 1) == 1);
	}
	
	public boolean isUpperBound()
	{
		return (((data >>> 22) & 1) == 1);
	}
	
	public int getEvalDepth()
	{
		return (data >>> 23);
	}
	
	public Move getMove()
	{
		return move;
	}
	
	public void setMove(Move newMove)
	{
		move = newMove;
	}
	
	public String toString()
	{
		return "Evaluation: " + getEvaluation() + "\nLower Bound: " + isLowerBound() + "\nDepth: " + getEvalDepth() + "\nMove: " + move.toString();
	}
}
