package me.Shadow.EngineGUI;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import me.Shadow.Engine.Board;
import me.Shadow.Engine.MoveGenerator;
import me.Shadow.Engine.MoveHelper;
import me.Shadow.Engine.PrecomputedData;
import me.Shadow.Engine.PrecomputedMagicNumbers;
import me.Shadow.Engine.Utils;

public class EngineTester
{
	public static void main(String[] args)
	{
		EngineTester tester = new EngineTester();
		tester.testEngines(args);

	}

	public void testEngines(String[] args)
	{
		if (args.length < 5)
		{
			System.out.println(
					"Usage: java EngineTester <engine-name>:<path-to-first-engine> <engine-name>:<path-to-second-engine> <think-time-ms> <number-of-games>:<path-to-positions-file> <path-to-pgn-output>");
			return;
		}

		String[] firstEngine = args[0].split(":", 2);
		String[] secondEngine = args[1].split(":", 2);
		int thinkTimeMS = Integer.parseInt(args[2]);
		String[] positions = args[3].split(":");
		String pgnOutputPath = args[4];

		if (firstEngine.length < 2 || secondEngine.length < 2 || positions.length < 2)
		{
			System.out.println(
					"Correct Usage: java EngineTester <engine-name>:<path-to-first-engine> <engine-name>:<path-to-second-engine> <think-time-ms> <number-of-games>:<path-to-positions-file> <path-to-pgn-output>");
			return;
		}

		int numGames = Integer.parseInt(positions[0]);

		if (numGames <= 0)
		{
			System.out.println("Must run at least one game!");
			return;
		}

		if (thinkTimeMS < 10)
		{
			System.out.println("Minimum thinking time is 10 ms");
			return;
		}

		PrecomputedData.generateData();
		PrecomputedMagicNumbers.precomputeMagics();
		System.out.println("Precomputed magic numbers!");
		
		EnginePlayer engineOne = null;
		EnginePlayer engineTwo = null;

		try
		{
			engineOne = initializeEngine(firstEngine[0], firstEngine[1]);
			engineTwo = initializeEngine(secondEngine[0], secondEngine[1]);
			System.out.println("Both engines initialized!");
			runMatch(engineOne, engineTwo, thinkTimeMS, numGames, pgnOutputPath);
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		finally
		{
			engineOne.cleanup();
			engineTwo.cleanup();
		}
	}

	public EnginePlayer initializeEngine(String engineName, String enginePath) throws IOException
	{
		//System.out.println("Initializing engine " + engineName + " located at " + enginePath.trim());
		
		// Start the processes for both engines
		ProcessBuilder pb = new ProcessBuilder("java", "-jar", enginePath);
		Process engineProcess = pb.start();
		EnginePlayer engine = new EnginePlayer(engineProcess, engineName);

		// Initialize UCI protocol for both engines
		//System.out.println("Sent uci command to " + engineName);
		engine.sendCommand("uci");
		engine.waitForResponse("uciok");
		//System.out.println("Received uciok command from " + engineName);

		// Tell engines to be ready
		engine.sendCommand("isready");
		engine.waitForResponse("readyok");

		//System.out.println("Engine " + engineName + " initialized successfully.");

		return engine;
	}

	public void runMatch(EnginePlayer engineOne, EnginePlayer engineTwo, int thinkTimeMS, int numGames,
			String pgnOutputPath) throws IOException
	{
		String[] positions = new String[numGames];
		for (int i = 0; i < numGames; i++)
		{
			// logic here to grab numGames random positions from the FENs file
			positions[i] = Board.defaultFEN;
		}

		List<GameResult> games = new ArrayList<GameResult>();
		int round = 1;
		for (int i = 0; i < numGames; i++)
		{
			// Run each position twice, each engine plays as both white and black
			GameResult firstResult = runGame(engineOne, engineTwo, thinkTimeMS, positions[i], round);
			games.add(firstResult);
			System.out.println("Round " + round + ": Completed first game for position: " + positions[i] + "\n");
			System.out.println(firstResult.getPgn() + "\n\n");
			round++;
			
			GameResult secondResult = runGame(engineTwo, engineOne, thinkTimeMS, positions[i], round);
			games.add(secondResult);
			System.out.println("Round " + round + ": Completed second game for position: " + positions[i] + "\n");			
			System.out.println(secondResult.getPgn() + "\n\n");
			round++;
		}

		HashMap<EnginePlayer, Float> points = new HashMap<EnginePlayer, Float>();
		HashMap<String, Integer> gameOverReasons = new HashMap<String, Integer>();

		File pgnFile = new File(pgnOutputPath);
		pgnFile.createNewFile();
		BufferedWriter pgnWriter = new BufferedWriter(new FileWriter(pgnFile));

		for (GameResult game : games)
		{
			points.put(game.getWhite(), points.getOrDefault(game.getWhite(), 0f) + game.getWhitePoints());
			points.put(game.getBlack(), points.getOrDefault(game.getBlack(), 0f) + game.getBlackPoints());

			gameOverReasons.put(game.getGameOverReason(), gameOverReasons.getOrDefault(game.getGameOverReason(), 0) + 1);

			pgnWriter.append(game.getPgn() + " \n\n");
		}
		
		for (Entry<String, Integer> entry : gameOverReasons.entrySet())
		{
			System.out.println(entry.getKey() + ": " + entry.getValue());
		}

		pgnWriter.close();
	}

	public GameResult runGame(EnginePlayer engineWhite, EnginePlayer engineBlack, int thinkTimeMS, String fenPosition,
			int round) throws IOException
	{
		// Set up a new game
		engineWhite.sendCommand("ucinewgame");
		engineBlack.sendCommand("ucinewgame");

		Board board = new Board(fenPosition);
		MoveGenerator moveGen = new MoveGenerator(board);

		engineWhite.sendCommand("position " + fenPosition);
		engineBlack.sendCommand("position " + fenPosition);

		int originalMoveNum = board.boardInfo.getMoveNum();
		boolean whiteToMove = board.boardInfo.isWhiteToMove();
		EnginePlayer currentPlayer = whiteToMove ? engineWhite : engineBlack;

		String gameOverReason = "";
		String result = "";
		float whitePoints = 0;
		float blackPoints = 0;
		List<String> movesList = new ArrayList<String>();

		while (true)
		{
			final short[] moves = moveGen.generateMoves(false);
			if (moves.length == 0)
			{

				if (moveGen.inCheck)
				{
					gameOverReason = currentPlayer.getName() + "-checkmated";
					result = whiteToMove ? "0-1" : "1-0";
					whitePoints = whiteToMove ? 0 : 1;
					blackPoints = 1 - whitePoints;
					if (whiteToMove)
					{

					}

					else
						result = "1-0";
				}
				else
				{
					gameOverReason = currentPlayer.getName() + "-stalemated";
					result = "1/2-1/2";
					whitePoints = blackPoints = 0.5f;
				}
				break;
			}
			if (board.isDuplicatePosition())
			{
				gameOverReason = "repetition";
				result = "1/2-1/2";
				whitePoints = blackPoints = 0.5f;
				break;
			}
			if (board.boardInfo.getHalfMoves() >= 100)
			{
				gameOverReason = "fifty-move-rule";
				result = "1/2-1/2";
				whitePoints = blackPoints = 0.5f;
				break;
			}

			// Tell engine to start calculating
			currentPlayer.sendCommand("go movetime " + thinkTimeMS);

			// Wait for the best move response
			String bestMove = currentPlayer.waitForBestMove();
			movesList.add(bestMove);

			short move = Utils.getMoveFromUCINotation(board, bestMove);
			if (move != MoveHelper.NULL_MOVE)
				board.movePiece(move);
			else
			{
				gameOverReason = "search-error";
				result = "*";
				break;
			}

			engineWhite.sendCommand("move " + bestMove);
			engineBlack.sendCommand("move " + bestMove);
			//System.out.println("Move played: " + bestMove);

			whiteToMove = board.boardInfo.isWhiteToMove();
			currentPlayer = whiteToMove ? engineWhite : engineBlack;
		}

		String pgn = createPGN(round, engineWhite.getName(), engineBlack.getName(), result, gameOverReason, fenPosition,
				originalMoveNum, movesList);
		return new GameResult(engineWhite, engineBlack, whitePoints, blackPoints, gameOverReason, pgn);
	}

	private String createPGN(int round, String whiteName, String blackName, String result, String gameOverReason,
			String startingFEN, int moveNum, List<String> moves)
	{
		String header = "[Round \"" + round + "\"]\n";
		header += "[White \"" + whiteName + "\"]\n";
		header += "[Black \"" + blackName + "\"]\n";
		header += "[Result \"" + result + "\"]\n";
		header += "[Termination \"" + gameOverReason + "\"]\n";
		header += "[SetUp \"1\"]\n";
		header += "[FEN \"" + startingFEN + "\"]";

		String gameBody = "";
		boolean whiteToMove = startingFEN.contains("w");

		for (String move : moves)
		{
			// output move number if white's turn
			if (whiteToMove)
				gameBody += moveNum + ". ";
			else
				moveNum++; // else increment move number to precede the next turn

			gameBody += move + " ";
			whiteToMove = !whiteToMove;
		}

		gameBody += result;

		return header + "\n\n" + gameBody;
	}

	class GameResult
	{
		private EnginePlayer white;
		private EnginePlayer black;
		float whitePoints;
		float blackPoints;
		String gameOverReason;
		private String pgn;

		public GameResult(EnginePlayer white, EnginePlayer black, float whitePoints, float blackPoints,
				String gameOverReason, String pgn)
		{
			this.white = white;
			this.black = black;
			this.whitePoints = whitePoints;
			this.blackPoints = blackPoints;
			this.gameOverReason = gameOverReason;
			this.pgn = pgn;
		}

		private EnginePlayer getWhite()
		{
			return white;
		}

		private EnginePlayer getBlack()
		{
			return black;
		}

		private float getWhitePoints()
		{
			return whitePoints;
		}

		private float getBlackPoints()
		{
			return blackPoints;
		}

		private String getGameOverReason()
		{
			return gameOverReason;
		}

		private String getPgn()
		{
			return pgn;
		}
	}

	class EnginePlayer
	{
		private Process engine;
		private BufferedReader engineIn;
		private BufferedWriter engineOut;
		private String name;

		public EnginePlayer(Process engine, String name)
		{
			this.engine = engine;
			// Set up communication channels with the engines
			engineIn = new BufferedReader(new InputStreamReader(engine.getInputStream()));
			engineOut = new BufferedWriter(new OutputStreamWriter(engine.getOutputStream()));
			this.name = name;
		}

		private String getName()
		{
			return name;
		}

		private void sendCommand(String command) throws IOException
		{
			engineOut.write(command + "\n");
			engineOut.flush();
			//System.out.println("Sent command [" + command + "] to " + name);
		}

		private void waitForResponse(String expected) throws IOException
		{
			String line;
			while ((line = engineIn.readLine()) != null)
			{
				//System.out.println("Received line [" + line + "] from " + name);
				if (line.contains(expected))
				{
					return;
				}
			}
		}

		private String waitForBestMove() throws IOException
		{
			String line;
			String bestMove = MoveHelper.toString(MoveHelper.NULL_MOVE);

			while ((line = engineIn.readLine()) != null)
			{
				//System.out.println("Received line [" + line + "] from " + name);
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

		private void cleanup()
		{
			try
			{
				if (engineOut != null)
				{
					sendCommand("quit");
				}

				if (engineIn != null)
					engineIn.close();
				if (engineOut != null)
					engineOut.close();
				if (engine != null)
					engine.destroyForcibly();
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}
	}
}