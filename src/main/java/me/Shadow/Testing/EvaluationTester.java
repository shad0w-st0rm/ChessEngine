package me.Shadow.Testing;

import me.Shadow.Engine.Board;
import me.Shadow.Engine.Evaluation;
import me.Shadow.Engine.PieceHelper;

public class EvaluationTester
{
	public static void evalTest()
	{
		// take the perft positions for now as a sample of positions
		String [] positions = Perft.perfts;
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
		flipBoard(board);
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
	
	public static void flipBoard(Board board)
	{
		byte [] newSquaresArray = new byte[64];
		
		for (int i = 0; i < 64; i++)
		{
			int flippedIndex = i^56;
			newSquaresArray[flippedIndex] = board.squares[i];
			if (newSquaresArray[flippedIndex] != PieceHelper.NONE)
			{
				newSquaresArray[flippedIndex] ^= PieceHelper.BLACK;
			}
		}
		byte whiteCastling = (byte) (board.castlingRights & Board.WHITE_CASTLING);
		byte blackCastling = (byte) (board.castlingRights & Board.BLACK_CASTLING);
		byte newCastling = (byte) (whiteCastling << 1 | blackCastling >> 1);
		board.squares = newSquaresArray;
		board.castlingRights = newCastling;
		if (board.enPassantIndex != -1) board.enPassantIndex = (short) (board.enPassantIndex ^ 56);
		board.colorToMove = (byte) (board.colorToMove ^ PieceHelper.BLACK);
		
		short [] newMaterial = new short[]{board.material[2], board.material[3], board.material[0], board.material[1]};
		board.material = newMaterial;
		
		board.zobristHash = board.createZobristHash();
		board.bitBoards.createBitboards(board);
	}
}
