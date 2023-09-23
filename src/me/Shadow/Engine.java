package me.Shadow;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

public class Engine
{
	ChessGui gui;
	Board board;
	MoveGenerator moveGenGlobal;
	TranspositionTable transpositionTable;

	boolean engineSearching;
	boolean playerMoveMade;
	boolean engineIsWhite;
	int searchTime;
	String originalFEN;

	Move engineMoveOld;
	ArrayList<Move> movesOld = new ArrayList<Move>();

	public Engine()
	{
		PrecomputedData.generateData();
		Perft.runPerftSuite();
		
		gui = new ChessGui();
		gui.createGui(this);
		board = new Board();
		originalFEN = board.boardInfo.getBoardFEN();
		// originalFEN = "8/3KP3/8/8/8/8/6k1/7q b - - 0 1"; //white king + pawn vs black king + queen
		// originalFEN = "3r4/3r4/3k4/8/8/3K4/8/8 w - - 0 1"; //white king vs black king + 2 rooks
		// originalFEN = "3r4/8/3k4/8/8/3K4/8/8 w - - 0 1"; //white king vs black king + rook
		// originalFEN = "8/7k/4p3/2p1P2p/2P1P2P/8/8/7K w - - 0 1"; // king and pawns vs king and pawns

		board.loadFEN(originalFEN);
		moveGenGlobal = new MoveGenerator(board);
		
		engineIsWhite = false;
		if (engineIsWhite == board.boardInfo.isWhiteToMove())
			playerMoveMade = true;
		transpositionTable = new TranspositionTable();
		searchTime = 1000;
	}

	public static void main(String[] args)
	{
		Engine engine = new Engine();
		engine.runIt();
	}
	
	public void runIt()
	{
		Timer timer = new Timer();
		timer.schedule(new TimerTask()
		{
			public void run()
			{
				if (playerMoveMade)
				{
					playerMoveMade = false;
					if (isGameOver(board))
						timer.cancel();
					gui.message.setText("Engine is thinking");

					if (engineMoveOld != null)
					{
						gui.setColor(engineMoveOld.getTargetIndex(), 0);
						gui.setColor(engineMoveOld.getStartIndex(), 0);
					}

					board.boardInfo.setBoardFEN(board.createFEN(false));
					Move move = engineMove();

					gui.setColor(move.getStartIndex(), 1);
					gui.setColor(move.getTargetIndex(), 1);
					engineMoveOld = move;

					gui.updatePieces();
					gui.message.setText("Engine done searching!");
					if (isGameOver(board))
						timer.cancel();
				}
			}
		}, 0, 250);
	}
	
	public Move engineMove()
	{
		engineSearching = true;
		
		Board boardCopy = new Board(board.boardInfo.getBoardFEN());
		MoveGenerator moveGen = new MoveGenerator(boardCopy);
		MoveSearcher search = new MoveSearcher(boardCopy, moveGen, searchTime, transpositionTable);
		Move move = search.startSearch();
		
		engineSearching = false;
		
		System.out.print(board.boardInfo.getMoveNum() + ". ");
		if(!engineIsWhite) System.out.print("...");
		System.out.println(move.toString());
		search.printSearchStats();
		
		board.movePiece(move);
		
		return move;
	}
	
	public boolean isThreeFoldRepetition(Board board)
	{
		long zobristHash = board.boardInfo.getZobristHash();
		ArrayList<Long> positions = board.boardInfo.getPositionList();
		int duplicateCount = 0;
		for (long position : positions)
		{
			if (position == zobristHash) duplicateCount++;
		}
		
		return duplicateCount >= 3;
	}
	
	public boolean isGameOver(Board board)
	{
		if(isThreeFoldRepetition(board))
		{
			gui.message.setText("Game Over! " + "Draw by Three Times Repetition");
			return true;
		}
		
		if(board.boardInfo.getHalfMoves() >= 100)
		{
			gui.message.setText("Game Over! " + "Draw by Fifty Move Rule!");
			return true;
		}
		
		if (moveGenGlobal.generateMoves(false).length == 0)
		{
			if (moveGenGlobal.inCheck)
				gui.message.setText("Game Over! " + (board.boardInfo.isWhiteToMove() ? "Black" : "White") + " wins by checkmate!");
			else
				gui.message.setText("Game Over! " + "Draw by Stalemate!");
			return true;
		}
		
		return false;
	}
}