package me.Shadow.Testing;

import me.Shadow.Engine.Board;
import me.Shadow.Engine.Evaluation;
import me.Shadow.Engine.PieceHelper;

public class EvaluationTester
{
	public static void evalTest(String [] positions)
	{
		boolean allPassed = true;
		for (int i = 0; i < positions.length; i++)
		{
			String fen = positions[i].split(";")[0].trim();
			
			if (!evaluatePositionTest(fen))
			{
				System.out.println("Evaluation Test Failed on FEN: " + fen);
				allPassed = false;
			}
			//else System.out.println("Evaluation Test Succeeded on FEN: " + fen);
		}
		
		if (allPassed)
		{
			System.out.println("Evaluation Test Succeeded on all FENs");
		}
		else
		{
			System.out.println("Evaluation Test Failed!");
		}
	}
	
	public static boolean evaluatePositionTest(String fen)
	{
		Board board = new Board(fen);
		//String boardString = board.toString();
		Evaluation eval = new Evaluation(board);
		int originalEval = eval.staticEvaluation();
		board.flipBoard();
		int flippedEval = eval.staticEvaluation();

		if (originalEval != flippedEval)
		{
			/*
			System.out.println(boardString);
			System.out.println(originalEval + "\n");
			board.printBoard();
			System.out.println(flippedEval + "\n");
			System.out.println(board.createFEN(true));
			*/
			
			return false;
		}
		
		return true;
	}
}
