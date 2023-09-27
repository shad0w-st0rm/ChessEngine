package me.Shadow;

import me.Shadow.OpeningBook.OpeningMove;

public class Player
{
	Board board;
	MoveSearcher searcher;
	
	public Player(Board boardIn, int searchTimeMS)
	{
		board = boardIn;
		searcher = new MoveSearcher(boardIn, searchTimeMS);
	}
	
	public Move searchMove()
	{
		Move move;
		if ((move = tryGetBookMove(false)) == Move.NULL_MOVE)
		{
			move = searcher.startSearch();
			searcher.printSearchStats();
		}
		else
		{
			System.out.println("Found move from opening book");
		}
		
		return move;
	}
	
	private Move tryGetBookMove(boolean random)
	{
		OpeningMove[] openingMoves = OpeningBook.openingBook.get(board.boardInfo.getZobristHash());
		if (openingMoves == null) return Move.NULL_MOVE;
		
		if (!random)
		{
			int totalMoves = 0;
			for (OpeningMove openingMove : openingMoves)
			{
				totalMoves += openingMove.getTimesPlayed();
			}
			
			int randomMove = (int)(Math.random() * totalMoves);
			totalMoves = 0;
			for (OpeningMove openingMove : openingMoves)
			{
				totalMoves += openingMove.getTimesPlayed();
				if (totalMoves >= randomMove) return openingMove.getMove();
			}
			System.out.println("Opening Book error");
			return Move.NULL_MOVE;
		}
		else
		{
			int randomIndex = (int) (Math.random() * openingMoves.length);
			return openingMoves[randomIndex].getMove();
		}
	}
	
	public void makeMove(Move move)
	{
		//Move move = Utils.getMoveFromUCINotation(board, opponentMove);
		board.movePiece(move);
	}
}
