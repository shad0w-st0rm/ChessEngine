package me.Shadow.EngineV1;

public class Player
{
	Board board;
	MoveSearcher searcher;
	
	public Player(Board boardIn, int searchTimeMS)
	{
		board = boardIn;
		searcher = new MoveSearcher(boardIn, searchTimeMS);
	}
	
	public short searchMove()
	{
		short move = tryGetBookMove(false);
		if (move == MoveHelper.NULL_MOVE)
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
	
	public void makeMove(short move)
	{
		//Move move = Utils.getMoveFromUCINotation(board, opponentMove);
		board.movePiece(move);
	}
}
