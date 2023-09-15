package me.Shadow;

public class PositionEvaluation
{
	private int data;
	private Move move;
	
	public PositionEvaluation(int eval, int depth, boolean lowerBound, Move moveIn)
	{
		data |= (eval & 0b111111111111111111111); // grabs only the first 21 bits
		data |= (lowerBound ? 1 : 0) << 21;
		data |= (depth << 22);
		move = moveIn;
	}
	
	public int getEvaluation()
	{
		return ((data << 11) >> 11);
	}
	
	public boolean isLowerBound()
	{
		return (((data >>> 21) & 1) == 1);
	}
	
	public int getEvalDepth()
	{
		return (data >>> 22);
	}
	
	public Move getMove()
	{
		return move;
	}
	
	public void setMove(Move newMove)
	{
		move = newMove;
	}
}
