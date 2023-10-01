package me.Shadow.EngineTesting;

import java.io.IOException;
import java.util.ArrayList;

import me.Shadow.EngineV1.*;

public class EngineTesting
{
	public static void main (String [] args)
	{
		EngineTesting et = new EngineTesting();
		et.runMatch(50, 100);
	}
	
	public void setupEngines()
	{
		me.Shadow.EngineV1.PrecomputedData.generateData();
		me.Shadow.EngineV1.PrecomputedMagicNumbers.precomputeMagics();
		
		try
		{
			OpeningBook.createBookFromBinary("resources/LichessOpeningBookBinary.dat");
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		
		/*
		me.Shadow.EngineV1.PrecomputedData.generateData();
		me.Shadow.EngineV1.PrecomputedMagicNumbers.precomputeMagics();
		
		try
		{
			OpeningBook.createBookFromBinary(Thread.currentThread().getContextClassLoader().getResource("resources/LichessOpeningBookBinary.dat").toURI());
		}
		catch (IOException | URISyntaxException e)
		{
			e.printStackTrace();
		}
		*/
	}
	
	public void runMatch(int numGames, int timeLimitMS)
	{
		me.Shadow.EngineV1.PrecomputedData.generateData();
		me.Shadow.EngineV1.PrecomputedMagicNumbers.precomputeMagics();
		
		EnginePlayer one = new EnginePlayerV1(Board.defaultFEN, timeLimitMS);
		EnginePlayer two = new EnginePlayerV1(Board.defaultFEN, timeLimitMS);
		for (int i = 0; i < numGames; i++)
		{
			String fen = Board.defaultFEN;
			
			one.resetForNewPosition(fen, timeLimitMS);
			two.resetForNewPosition(fen, timeLimitMS);
			Board board = new Board(fen);
			runGame(one, two, one, board);
			
			one.resetForNewPosition(fen, timeLimitMS);
			two.resetForNewPosition(fen, timeLimitMS);
			board = new Board(fen);
			runGame(one, two, two, board);
		}
		
		System.out.println("Player One Wins: " + one.getWins());
		System.out.println("Player Two Wins: " + two.getWins());
		System.out.println("Draws: " + one.getDraws());
		
		if (one.getDraws() != two.getDraws() || one.getWins() != two.getLosses() || one.getLosses() != two.getWins() || ((one.getWins() + one.getDraws() + one.getLosses()) != (numGames*2)))
		{
			System.out.println("Inconsistency!");
		}
	}
	
	public void runGame(EnginePlayer one, EnginePlayer two, EnginePlayer playerToMove, Board board)
	{
		while (true)
		{
			int result = isGameOver(board);
			if (result != -1)
			{
				System.out.println("Game over! Result: " + result + " White to Move? " + board.boardInfo.isWhiteToMove() + " Game Length: " + board.boardInfo.getMoveNum());
				board.printBoard();
				if (result == 0)
				{
					playerToMove.addLoss();
					EnginePlayer winner = playerToMove == one ? two : one;
					winner.addWin();
				}
				else
				{
					one.addDraw();
					two.addDraw();
				}
				
				return;
			}
			short nextMove = Utils.getMoveFromUCINotation(board, playerToMove.searchMove());
			if (nextMove == MoveHelper.NULL_MOVE) board.printBoard();
			//System.out.println(nextMove);
			one.makeMove(MoveHelper.toString(nextMove));
			two.makeMove(MoveHelper.toString(nextMove));
			board.movePiece(nextMove);
			playerToMove = playerToMove == one ? two : one;
		}
	}
	
	public int isGameOver(Board board)
	{
		if(isThreeFoldRepetition(board))
		{
			return 2;
		}
		
		if(board.boardInfo.getHalfMoves() >= 100)
		{
			return 3;
		}
		
		MoveGenerator moveGen = new MoveGenerator(board);
		if (moveGen.generateMoves(new short[MoveGenerator.MAXIMUM_LEGAL_MOVES], false) == 0)
		{
			if (moveGen.inCheck)
				return 0;
			else
				return 1;
		}
		
		return -1;
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
}
