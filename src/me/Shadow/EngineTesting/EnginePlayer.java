package me.Shadow.EngineTesting;

public interface EnginePlayer
{
	public void resetForNewPosition(String fen, int searchTimeMS);
	public String searchMove();
	public void makeMove(String move);
	public int getWins();
	public int getDraws();
	public int getLosses();
	public void addWin();
	public void addDraw();
	public void addLoss();
}
