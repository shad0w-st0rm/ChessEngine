package me.Shadow.EngineGUI;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

import me.Shadow.Engine.Board;
import me.Shadow.Engine.MoveGenerator;
import me.Shadow.Engine.MoveHelper;
import me.Shadow.Engine.PrecomputedData;
import me.Shadow.Engine.PrecomputedMagicNumbers;
import me.Shadow.Engine.Utils;

public class EngineTester
{
	private Process whiteEngine;
	private Process blackEngine;
	private BufferedReader whiteIn;
	private BufferedWriter whiteOut;
	private BufferedReader blackIn;
	private BufferedWriter blackOut;

	private static final int MOVE_TIME_MS = 100; // Time each engine gets to compute a move
	private static final int MAX_MOVES = 200; // Maximum moves before declaring a draw

	public static void main(String[] args)
	{
		/*
		 * if (args.length < 1) {
		 * System.out.println("Usage: java EngineTester <path-to-jar-file>"); return; }
		 * 
		 * String jarPath = args[0];
		 */
		String jarPath = "C:\\Users\\tanmaye\\eclipse-workspace\\ChessWorkspace\\ChessEngine\\src\\main\\resources\\ChessEngineUCI-1.0.0.jar";
		EngineTester tester = new EngineTester();

		try
		{
			tester.initializeEngines(jarPath);
			tester.runGames(1);
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		finally
		{
			tester.cleanup();
		}
	}

	public void initializeEngines(String jarPath) throws IOException
	{
		// Start the processes for both engines
		ProcessBuilder pb = new ProcessBuilder("java", "-jar", jarPath);

		whiteEngine = pb.start();
		blackEngine = pb.start();

		// Set up communication channels with the engines
		whiteIn = new BufferedReader(new InputStreamReader(whiteEngine.getInputStream()));
		whiteOut = new BufferedWriter(new OutputStreamWriter(whiteEngine.getOutputStream()));

		blackIn = new BufferedReader(new InputStreamReader(blackEngine.getInputStream()));
		blackOut = new BufferedWriter(new OutputStreamWriter(blackEngine.getOutputStream()));

		// Initialize UCI protocol for both engines
		sendCommand(whiteOut, "uci");
		waitForResponse(whiteIn, "uciok");

		sendCommand(blackOut, "uci");
		waitForResponse(blackIn, "uciok");

		// Set options if needed (uncomment and modify if required)
		// sendCommand(whiteOut, "setoption name Threads value 1");
		// sendCommand(blackOut, "setoption name Threads value 1");

		// Tell engines to be ready
		sendCommand(whiteOut, "isready");
		waitForResponse(whiteIn, "readyok");

		sendCommand(blackOut, "isready");
		waitForResponse(blackIn, "readyok");
		
		PrecomputedData.generateData();
		PrecomputedMagicNumbers.precomputeMagics();

		System.out.println("Both engines initialized successfully.");
	}

	public void runGames(int numGames) throws IOException
	{
		for (int i = 0; i < numGames; i++)
		{
			System.out.println(runGame(Board.defaultFEN));
		}
	}

	public String runGame(String fenPosition) throws IOException
	{
		// Set up a new game
		sendCommand(whiteOut, "ucinewgame");
		sendCommand(blackOut, "ucinewgame");

		Board board = new Board(fenPosition);
		MoveGenerator moveGen = new MoveGenerator(board);
		String position = fenPosition;
		sendCommand(whiteOut, "position " + position);
		sendCommand(blackOut, "position " + position);

		boolean whiteToMove = board.boardInfo.isWhiteToMove();
		Process currentEngine = whiteToMove ? whiteEngine : blackEngine;
		BufferedReader currentIn = whiteToMove ? whiteIn : blackIn;
		BufferedWriter currentOut = whiteToMove ? whiteOut : blackOut;

		while (true)
		{
			final short[] moves = moveGen.generateMoves(false);
			if (moves.length == 0)
			{
				if (moveGen.inCheck)
					return "checkmate";
				else
					return "draw";
			}

			if (isDuplicatePosition(board) || board.boardInfo.getHalfMoves() >= 100)
				return "draw";

			// Tell engine to start calculating
			sendCommand(currentOut, "go movetime " + MOVE_TIME_MS);

			// Wait for the best move response
			String bestMove = waitForBestMove(currentIn);

			short move = Utils.getMoveFromUCINotation(board, bestMove);
			if (move != MoveHelper.NULL_MOVE)
				board.movePiece(move);
			else
			{
				System.out.println("Engine Search Error:" + bestMove);
				board.printBoard();
				return "error";
			}

			sendCommand(whiteOut, "move " + bestMove);
			sendCommand(blackOut, "move " + bestMove);
			System.out.println("Move played: " + bestMove);

			whiteToMove = board.boardInfo.isWhiteToMove();
			currentEngine = whiteToMove ? whiteEngine : blackEngine;
			currentIn = whiteToMove ? whiteIn : blackIn;
			currentOut = whiteToMove ? whiteOut : blackOut;
		}
	}

	public boolean isDuplicatePosition(Board board)
	{
		if (board.boardInfo.getHalfMoves() < 4)
			return false;

		final long zobristHash = board.boardInfo.getZobristHash();
		final ArrayList<Long> positions = board.boardInfo.getPositionList();

		int index = positions.size() - 5;
		final int minIndex = Math.max(index - board.boardInfo.getHalfMoves() + 1, 0);
		while (index >= minIndex)
		{
			if (positions.get(index) == zobristHash)
			{
				return true;
			}
			index -= 2;
		}
		return false;
	}

	private void sendCommand(BufferedWriter out, String command) throws IOException
	{
		out.write(command + "\n");
		out.flush();
	}

	private void waitForResponse(BufferedReader in, String expected) throws IOException
	{
		String line;
		while ((line = in.readLine()) != null)
		{
			// For debugging purposes
			if (line.contains(expected))
			{
				return;
			}
		}
	}

	private String waitForBestMove(BufferedReader in) throws IOException
	{
		String line;
		String bestMove = MoveHelper.toString(MoveHelper.NULL_MOVE);

		while ((line = in.readLine()) != null)
		{
			if (line.startsWith("bestmove"))
			{
				String[] parts = line.split("\\s+");
				if (parts.length >= 2)
				{
					bestMove = parts[1];
				}
				break;
			}
		}

		return bestMove;
	}

	public void cleanup()
	{
		try
		{
			// Send quit command to engines
			if (whiteOut != null)
			{
				sendCommand(whiteOut, "quit");
			}
			if (blackOut != null)
			{
				sendCommand(blackOut, "quit");
			}

			// Close resources
			try
			{
				if (whiteIn != null)
					whiteIn.close();
				if (whiteOut != null)
					whiteOut.close();
				if (blackIn != null)
					blackIn.close();
				if (blackOut != null)
					blackOut.close();
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}

			// Destroy processes
			if (whiteEngine != null)
			{
				whiteEngine.destroyForcibly();
			}
			if (blackEngine != null)
			{
				blackEngine.destroyForcibly();
			}

			System.out.println("Resources cleaned up.");
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
}