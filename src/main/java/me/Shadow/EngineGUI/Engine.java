package me.Shadow.EngineGUI;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import me.Shadow.Engine.*;
import me.Shadow.Testing.BestMoveTester;
import me.Shadow.Testing.EvaluationTester;
import me.Shadow.Testing.MoveGenerationTester;
import me.Shadow.Testing.MoveOrdererTester;
import me.Shadow.Testing.Perft;

public class Engine
{
	ChessGui gui;
	Board board;
	Player enginePlayer;

	boolean engineSearching;
	boolean playerMoveMade;
	int engineColor;
	int searchTime;
	String originalFEN;

	short engineMoveOld;
	ArrayList<Short> movesOld = new ArrayList<Short>();

	public Engine()
	{
		originalFEN = Board.defaultFEN;
		originalFEN = "r1b2rk1/p1q4p/2n1pp2/1p1p4/2pP4/P3QN2/1PP2PPP/R3KB1R w KQ - 1 17";	// first "balanced" position
		// originalFEN = "1k1r3q/1ppn3p/p4b2/4p3/8/P2N2P1/1PP1R1BP/2K1Q3 w - - ";	// SEE cpw test position
		// originalFEN = "8/3KP3/8/8/8/8/6k1/7q b - - 0 1"; //white king + pawn vs black king + queen
		// originalFEN = "3r4/3r4/3k4/8/8/3K4/8/8 w - - 0 1"; //white king vs black king + 2 rooks
		// originalFEN = "3r4/8/3k4/8/8/3K4/8/8 w - - 0 1"; //white king vs black king + rook
		// originalFEN = "8/7k/4p3/2p1P2p/2P1P2P/8/8/7K w - - 0 1"; // king and pawns vs king and pawns
		
		engineColor = PieceHelper.WHITE;
		searchTime = 1_000_000;
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
		
		//Perft.runPerftSuite(5);
		//Perft.runPerft("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1 ;D5 4865609 ;D6 119060324 ;D7 3195901860 ;D8 84998978956");
		//EvaluationTester.evalTest(Perft.perftsExtended);
		//BestMoveTester.runBestMoveSuite(100, 400, 2.0f, false, false);
		//MoveGenerationTester.testCapturesSuite(Perft.perftsExtended);
		//MoveOrdererTester.testSEESuite();
		
		try
		{
			int moveCount = OpeningBook.createBookFromBinary("LichessOpeningBookBinary.dat");
			System.out.println("Opening Book Stats\nPositions: " + OpeningBook.openingBook.size() + " Recorded Moves: " + moveCount);
			System.out.println("Estimated size: " + (OpeningBook.openingBook.size() * 9 + moveCount*6) / 1024.0 + " kb\n");
		}
		catch (IOException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
				
		gui = new ChessGui();
		gui.createGui(this);
		
		board = new Board(originalFEN);
		playerMoveMade = (engineColor == board.colorToMove);
		enginePlayer = new Player(new Board(originalFEN));
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
					{
						timer.cancel();
						return;
					}
					
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
		makeMove(move);
		engineSearching = false;
		
		//System.out.println("Nodes: " + enginePlayer.searcher.nodes);
		return move;
	}
	
	public void makeMove(short move)
	{
		System.out.print(board.getMoveNum() + ". ");
		if(board.colorToMove == PieceHelper.BLACK) System.out.print("...");
		System.out.println(MoveHelper.toString(move));
				
		board.movePiece(move);
		enginePlayer.makeMove(MoveHelper.toString(move));
	}
	
	public boolean isThreeFoldRepetition(Board board)
	{
		long zobristHash = board.repetitionHistory[board.repetitionIndex];
		boolean duplicateFound = false;
		for (int i = board.repetitionIndex - 2; i >= 0; i -= 2)
		{
			if (board.repetitionHistory[i] == zobristHash)
			{
				if (duplicateFound) return true;
				else duplicateFound = true;
			}
		}
		return false;
	}
	
	public boolean isGameOver(Board board)
	{
		if(isThreeFoldRepetition(board))
		{
			gui.message.setText("Game Over! " + "Draw by Three Times Repetition");
			return true;
		}
		
		if(board.halfMoves >= 100)
		{
			gui.message.setText("Game Over! " + "Draw by Fifty Move Rule!");
			return true;
		}
		
		MoveGenerator moveGen = new MoveGenerator(board, new short[MoveGenerator.MAXIMUM_LEGAL_MOVES]);
		if (moveGen.generateMoves(MoveGenerator.ALL_MOVES, 0) == 0)
		{
			if (moveGen.inCheck())
				gui.message.setText("Game Over! " + (board.colorToMove == PieceHelper.WHITE ? "Black" : "White") + " wins by checkmate!");
			else
				gui.message.setText("Game Over! " + "Draw by Stalemate!");
			return true;
		}
		
		return false;
	}
}