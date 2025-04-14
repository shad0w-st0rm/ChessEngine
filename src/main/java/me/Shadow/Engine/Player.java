package me.Shadow.Engine;

import java.util.Timer;
import java.util.TimerTask;

public class Player
{
	private Board board;
	public MoveSearcher searcher;
	
	int searchNum;
	
	public Player(Board boardIn)
	{
		board = boardIn;
		searcher = new MoveSearcher(boardIn);
	}
	
	public short searchMove(int thinkTimeMS)
	{
		searchNum++;
		int currentSearchNum = searchNum;
		
		short move = tryGetBookMove(false);
		if (move == MoveHelper.NULL_MOVE)
		{
			Timer timer = new Timer();
			timer.schedule(new TimerTask()
			{
				@Override
				public void run()
				{
					stopSearching(currentSearchNum);
				}
			}, thinkTimeMS);
			
			move = searcher.startSearch();
			//searcher.printSearchStats();
		}
		else
		{
			//System.out.println("Found move from opening book");
		}
		
		return move;
	}
	
	private short tryGetBookMove(boolean random)
	{
		OpeningBook.OpeningMove[] openingMoves = OpeningBook.openingBook.get(board.zobristHash);
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
	
	public boolean makeMove(String moveString)
	{
		short move = Utils.getMoveFromUCINotation(board, moveString);
		if (!searcher.makeMove(move))
		{
			board.printBoard();
			System.out.println(moveString);
			return false;
		}
		return true;
	}
	
	public void stopSearching()
	{
		searcher.stopSearch();
	}
	
	public void stopSearching(int currentSearchNum)
	{
		if (searchNum == currentSearchNum)
		{
			stopSearching();
		}
	}
	
	public void loadPosition(String fen)
	{
		board.loadFEN(fen);
	}
	
	public String getPosition(boolean includeMoveNums)
	{
		return board.createFEN(includeMoveNums);
	}
	
	public void newPosition()
	{
		board = new Board();
		searcher = new MoveSearcher(board);
	}
	
	public int chooseTimeToThink(int whiteTimeMS, int blackTimeMS, int whiteIncrementMS, int blackIncrementMS)
	{
		int engineTime = board.colorToMove == PieceHelper.WHITE ? whiteTimeMS : blackTimeMS;
		int engineIncrement = board.colorToMove == PieceHelper.WHITE ? whiteIncrementMS : blackIncrementMS;
		float thinkTime = engineTime / 40.0f;
		if (engineTime > (engineIncrement * 4))	// at least 4x increment time to add increment time in search time (otherwise save the increment time)
		{
			thinkTime += engineIncrement * 0.75f;	// dont use all the increment time
		}
		
		float minimumThinkTime = Math.min(50, engineTime * 0.5f);	// minimum think time is 50 ms, or half the engines time whichever is smaller
		return (int)Math.max(minimumThinkTime, thinkTime);
	}
	
	public void printBoard()
	{
		board.printBoard();
	}
}
