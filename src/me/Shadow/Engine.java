package me.Shadow;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

public class Engine
{
	ChessGui gui;
	Board board;
	Player enginePlayer;

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
		PrecomputedMagicNumbers.precomputeMagics();
		try
		{
			String openingBookPath = "src/me/Shadow/LichessOpeningBookBinary.dat";
			int moveCount = OpeningBook.createBookFromBinary(openingBookPath);
			System.out.println("Opening Book Stats\nPositions: " + OpeningBook.openingBook.size() + " Recorded Moves: " + moveCount);
			System.out.println("Estimated size: " + (OpeningBook.openingBook.size() * 9 + moveCount*6) / 1024.0 + " kb");
		}
		catch (IOException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		// Perft.runPerftSuite();
				
		gui = new ChessGui();
		gui.createGui(this);
		
		originalFEN = Board.defaultFEN;
		// originalFEN = "8/3KP3/8/8/8/8/6k1/7q b - - 0 1"; //white king + pawn vs black king + queen
		// originalFEN = "3r4/3r4/3k4/8/8/3K4/8/8 w - - 0 1"; //white king vs black king + 2 rooks
		// originalFEN = "3r4/8/3k4/8/8/3K4/8/8 w - - 0 1"; //white king vs black king + rook
		// originalFEN = "8/7k/4p3/2p1P2p/2P1P2P/8/8/7K w - - 0 1"; // king and pawns vs king and pawns

		board = new Board(originalFEN);
		
		engineIsWhite = false;
		playerMoveMade = (engineIsWhite == board.boardInfo.isWhiteToMove());
		searchTime = 500;
		
		enginePlayer = new Player(new Board(board), searchTime);
	}

	public static void main(String[] args)
	{
		Engine engine = new Engine();
		engine.runIt();
	}
	
	public void runIt()
	{
		while (!gui.guiReady)
		{
			try
			{
				Thread.sleep(100);
			}
			catch (InterruptedException e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
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
		Move move = enginePlayer.searchMove();
		engineSearching = false;

		makeMove(move);
		
		return move;
	}
	
	public void makeMove(Move move)
	{
		System.out.print(board.boardInfo.getMoveNum() + ". ");
		if(!board.boardInfo.isWhiteToMove()) System.out.print("...");
		System.out.println(move.toString());
				
		board.movePiece(move);
		enginePlayer.makeMove(move);
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
		
		MoveGenerator moveGen = new MoveGenerator(board);
		if (moveGen.generateMoves(false).length == 0)
		{
			if (moveGen.inCheck)
				gui.message.setText("Game Over! " + (board.boardInfo.isWhiteToMove() ? "Black" : "White") + " wins by checkmate!");
			else
				gui.message.setText("Game Over! " + "Draw by Stalemate!");
			return true;
		}
		
		return false;
	}
}