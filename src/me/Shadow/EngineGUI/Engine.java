package me.Shadow.EngineGUI;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import me.Shadow.Engine.*;

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

	short engineMoveOld;
	ArrayList<Short> movesOld = new ArrayList<Short>();

	public Engine()
	{
		originalFEN = Board.defaultFEN;
		// originalFEN = "8/3KP3/8/8/8/8/6k1/7q b - - 0 1"; //white king + pawn vs black king + queen
		// originalFEN = "3r4/3r4/3k4/8/8/3K4/8/8 w - - 0 1"; //white king vs black king + 2 rooks
		// originalFEN = "3r4/8/3k4/8/8/3K4/8/8 w - - 0 1"; //white king vs black king + rook
		// originalFEN = "8/7k/4p3/2p1P2p/2P1P2P/8/8/7K w - - 0 1"; // king and pawns vs king and pawns
		
		engineIsWhite = false;
		searchTime = 100;
	}

	public static void main(String[] args)
	{
		Engine engine = new Engine();
		engine.gameLoop();
	}
	
	public void setupEngine()
	{
		PrecomputedData.generateData();
		PrecomputedMagicNumbers.precomputeMagics();
		PrecomputedMagicNumbers.printMagicsTableSize();
		
		try
		{
			int moveCount = OpeningBook.createBookFromBinary("resources/LichessOpeningBookBinary.dat");
			System.out.println("Opening Book Stats\nPositions: " + OpeningBook.openingBook.size() + " Recorded Moves: " + moveCount);
			System.out.println("Estimated size: " + (OpeningBook.openingBook.size() * 9 + moveCount*6) / 1024.0 + " kb\n");
		}
		catch (IOException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		//Perft.runPerftSuite();
		
		gui = new ChessGui();
		gui.createGui(this);
		
		board = new Board(originalFEN);
		playerMoveMade = (engineIsWhite == board.boardInfo.isWhiteToMove());
		enginePlayer = new Player(new Board(board));
	}
	
	public void gameLoop()
	{
		setupEngine();
		
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

					if (engineMoveOld != MoveHelper.NULL_MOVE)
					{
						gui.setColor(MoveHelper.getStartIndex(engineMoveOld), 0);
						gui.setColor(MoveHelper.getTargetIndex(engineMoveOld), 0);
					}

					short move = engineMove();

					if (move != MoveHelper.NULL_MOVE)
					{
						gui.setColor(MoveHelper.getStartIndex(move), 1);
						gui.setColor(MoveHelper.getTargetIndex(move), 1);
						engineMoveOld = move;
					}
					
					gui.updatePieces();
					gui.message.setText("Engine done searching!");
					if (isGameOver(board))
						timer.cancel();
				}
			}
		}, 0, 250);
	}
	
	public short engineMove()
	{
		engineSearching = true;
		short move = enginePlayer.searchMove(searchTime);
		engineSearching = false;

		if (move != MoveHelper.NULL_MOVE)
		{
			makeMove(move);
		}		
		
		return move;
	}
	
	public void makeMove(short move)
	{
		System.out.print(board.boardInfo.getMoveNum() + ". ");
		if(!board.boardInfo.isWhiteToMove()) System.out.print("...");
		System.out.println(MoveHelper.toString(move));
				
		board.movePiece(move);
		enginePlayer.makeMove(MoveHelper.toString(move));
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
		if (moveGen.generateMoves(new short[MoveGenerator.MAXIMUM_LEGAL_MOVES], false) == 0)
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