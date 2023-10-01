package me.Shadow.EngineTesting;

import me.Shadow.EngineV1.*;

public class EnginePlayerV1 implements EnginePlayer
{
	Board board;
	MoveSearcher searcher;
	int wins;
	int draws;
	int losses;
	
	public EnginePlayerV1(String fen, int searchTimeMS)
	{
		resetForNewPosition(fen, searchTimeMS);
	}
	
	public void resetForNewPosition(String fen, int searchTimeMS)
	{
		board = new Board(fen);
		searcher = new MoveSearcher(board, searchTimeMS);
	}
	
	@Override
	public String searchMove()
	{
		short move = tryGetBookMove(false);
		if (move == MoveHelper.NULL_MOVE)
		{
			move = searcher.startSearch();
			//searcher.printSearchStats();
		}
		else
		{
			//System.out.println("Found move from opening book");
		}
		
		return MoveHelper.toString(move);
	}
	
	private short tryGetBookMove(boolean random)
	{
		OpeningBook.OpeningMove[] openingMoves = OpeningBook.openingBook.get(board.boardInfo.getZobristHash());
		if (openingMoves == null) return MoveHelper.NULL_MOVE;
		
		if (!random)
		{
			int totalMoves = 0;
			for (OpeningBook.OpeningMove openingMove : openingMoves)
			{
				totalMoves += openingMove.getTimesPlayed();
			}
			
			int randomMove = (int)(Math.random() * totalMoves);
			totalMoves = 0;
			for (OpeningBook.OpeningMove openingMove : openingMoves)
			{
				totalMoves += openingMove.getTimesPlayed();
				if (totalMoves >= randomMove) return openingMove.getMove();
			}
			System.out.println("Opening Book error");
			return MoveHelper.NULL_MOVE;
		}
		else
		{
			int randomIndex = (int) (Math.random() * openingMoves.length);
			return openingMoves[randomIndex].getMove();
		}
	}

	@Override
	public void makeMove(String moveString)
	{
		short move = Utils.getMoveFromUCINotation(board, moveString);
		board.movePiece(move);
	}
	
	public int getWins() { return wins; }
	public int getDraws() { return draws; }
	public int getLosses() { return losses; }
	public void addWin() { wins++; }
	public void addDraw() { draws++;}
	public void addLoss() { losses++; }
}
