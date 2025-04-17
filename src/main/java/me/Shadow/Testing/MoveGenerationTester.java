package me.Shadow.Testing;

import me.Shadow.Engine.Board;
import me.Shadow.Engine.MoveGenerator;
import me.Shadow.Engine.MoveHelper;
import me.Shadow.Engine.MoveOrderer;
import me.Shadow.Engine.PieceHelper;

public class MoveGenerationTester
{
	public static void testCapturesSuite(String [] positions)
	{
		boolean allPassed = true;
		for (int i = 0; i < positions.length; i++)
		{
			String fen = positions[i].split(";")[0].trim();
			
			if (!testCapturesGeneration(fen))
			{
				System.out.println("Capture Moves Generation Test Failed on FEN: " + fen);
				allPassed = false;
			}
			//else System.out.println("Evaluation Test Succeeded on FEN: " + fen);
		}
		
		if (allPassed)
		{
			System.out.println("Capture Moves Generation Test Succeeded on all FENs");
		}
		else
		{
			System.out.println("Capture Moves Generation Test Failed!");
		}
	}
	
	public static boolean testCapturesGeneration(String fen)
	{
		Board board = new Board(fen);
		short [] moves = new short[MoveGenerator.MAXIMUM_LEGAL_MOVES];
		MoveGenerator moveGen = new MoveGenerator(board, moves);
		
		int numMoves = moveGen.generateMoves(MoveGenerator.ALL_MOVES, 0);
		
		short [] filteredCaptures = new short[numMoves];
		int numCaptures = 0;
		for (int i = 0; i < numMoves; i++)
		{
			short move = moves[i];
			if (isMoveCapture(board, move))
			{
				filteredCaptures[numCaptures] = move;
				numCaptures++;
			}
		}
		
		int newCaptures = moveGen.generateMoves(MoveGenerator.CAPTURES_ONLY, 0);
		if (newCaptures != numCaptures)
		{
			System.out.println("Different amount of captures generated! Filtered Captures: " + numCaptures + " Captures Only: " + newCaptures);
			return false;
		}
		else
		{
			for (int i = 0; i < numCaptures; i++)
			{
				short captureOne = filteredCaptures[i];
				short captureTwo = moves[i];
				if (captureOne != captureTwo)
				{
					System.out.println("Nonmatching capture moves at same index: " + MoveHelper.toString(captureOne) + " " + MoveHelper.toString(captureTwo));
					return false;
				}
			}
		}
		
		return true;
	}
	
	public static boolean isMoveCapture(Board board, short move)
	{
		if (MoveHelper.getEnPassantCaptureIndex(move) != -1) return true;
		if (MoveHelper.getPromotedPiece(move) == PieceHelper.QUEEN) return true;
		else return (MoveHelper.getPromotedPiece(move) == PieceHelper.NONE && board.squares[MoveHelper.getTargetIndex(move)] != PieceHelper.NONE);
	}
}
