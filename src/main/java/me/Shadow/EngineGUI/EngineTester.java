package me.Shadow.EngineGUI;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import me.Shadow.Engine.Bitboards;
import me.Shadow.Engine.Board;
import me.Shadow.Engine.MoveGenerator;
import me.Shadow.Engine.MoveHelper;
import me.Shadow.Engine.PieceHelper;
import me.Shadow.Engine.PrecomputedData;
import me.Shadow.Engine.PrecomputedMagicNumbers;
import me.Shadow.Engine.Utils;

public class EngineTester
{
	enum GameOverReason
	{
		WHITE_CHECKMATED, BLACK_CHECKMATED, STALEMATE, FIFTY_MOVE_RULE, THREEFOLD_REPETITION, INSUFFICIENT_MATERIAL,
		ERROR,
	}

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
					"Usage: java EngineTester <engine-name>:<path-to-first-engine> <engine-name>:<path-to-second-engine> <think-time-ms> <number-of-games>:<number-of-threads>:<path-to-positions-file> <path-to-pgn-output>");
			return;
		}

		String[] firstEngine = args[0].split(":", 2);
		String[] secondEngine = args[1].split(":", 2);
		int thinkTimeMS = Integer.parseInt(args[2]);
		String[] positions = args[3].split(":", 3);
		String pgnOutputPath = args[4];

		if (firstEngine.length < 2 || secondEngine.length < 2 || positions.length < 2)
		{
			System.out.println(
					"Correct Usage: java EngineTester <engine-name>:<path-to-first-engine> <engine-name>:<path-to-second-engine> <think-time-ms> <number-of-games>:<number-of-threads>:<path-to-positions-file> <path-to-pgn-output>");
			return;
		}

		int numGames = Integer.parseInt(positions[0]);
		int numThreads = Integer.parseInt(positions[1]);

		if (numGames <= 0)
		{
			System.out.println("Must run at least one game!");
			return;
		}

		if (numThreads <= 0)
		{
			System.out.println("Must use at least one thread!");
			return;
		}

		if (thinkTimeMS < 10)
		{
			System.out.println("Minimum thinking time is 10 ms");
			return;
		}

		PrecomputedData.generateData();
		PrecomputedMagicNumbers.precomputeMagics();

		try
		{
			runTournament(firstEngine, secondEngine, thinkTimeMS, numGames, numThreads, positions[2], pgnOutputPath);
		}
		catch (IOException | InterruptedException | ExecutionException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void runTournament(String[] engineOneParams, String[] engineTwoParams, int thinkTimeMS, int numGames,
			int numThreads, String positionsFilePath, String pgnOutputPath)
			throws IOException, InterruptedException, ExecutionException
	{
		ArrayList<String> positions = getPositionsFromFile(positionsFilePath);
		Collections.shuffle(positions, new Random(123456));
		int numGamesPerThread = numGames / numThreads; // integer division

		List<GameResult> tournamentResults = new ArrayList<GameResult>();

		ExecutorService executor = Executors.newFixedThreadPool(numThreads);
		List<Callable<List<GameResult>>> runMatchThreads = new ArrayList<>();

		for (int i = 0; i < numThreads; i++)
		{
			// choose selected positions for this match
			ArrayList<String> selectedPositions = new ArrayList<String>();
			positions.subList(i * numGamesPerThread, (i + 1) * numGamesPerThread).forEach(selectedPositions::add);
			int threadID = i + 1;

			Callable<List<GameResult>> runMatchThread = () ->
			{
				List<GameResult> matchResults = runMatch(engineOneParams, engineTwoParams, thinkTimeMS,
						selectedPositions, threadID);
				return matchResults;
			};
			runMatchThreads.add(runMatchThread);
		}

		List<Future<List<GameResult>>> matchThreadResults = executor.invokeAll(runMatchThreads);

		for (Future<List<GameResult>> matchThreadResult : matchThreadResults)
		{
			tournamentResults.addAll(matchThreadResult.get());
		}

		executor.shutdown();

		writeResults(tournamentResults, pgnOutputPath);
	}

	public List<GameResult> runMatch(String[] engineOneParams, String[] engineTwoParams, int thinkTimeMS,
			ArrayList<String> positions, int thread)
	{
		EnginePlayer engineOne = null;
		EnginePlayer engineTwo = null;

		try
		{
			engineOne = initializeEngine(engineOneParams[0], engineOneParams[1]);
			engineTwo = initializeEngine(engineTwoParams[0], engineTwoParams[1]);

			Thread.sleep((long) (Math.random() * 2000));

			List<GameResult> gameResults = runMatch(engineOne, engineTwo, thinkTimeMS, positions, thread);
			return gameResults;
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
		return null;
	}

	public List<GameResult> runMatch(EnginePlayer engineOne, EnginePlayer engineTwo, int thinkTimeMS,
			ArrayList<String> positions, int thread) throws IOException
	{
		List<GameResult> games = new ArrayList<GameResult>();
		int round = 1;
		for (String position : positions)
		{
			// Run each position twice, each engine plays as both white and black
			GameResult firstResult = runGame(engineOne, engineTwo, thinkTimeMS, position, round, thread);
			games.add(firstResult);
			System.out.println(thread + "." + round + ": Completed first game for position: " + position + "\n");
			round++;

			GameResult secondResult = runGame(engineTwo, engineOne, thinkTimeMS, position, round, thread);
			games.add(secondResult);
			System.out.println(thread + "." + round + ": Completed second game for position: " + position + "\n");
			round++;
		}

		return games;
	}

	public GameResult runGame(EnginePlayer engineWhite, EnginePlayer engineBlack, int thinkTimeMS, String fenPosition,
			int round, int thread) throws IOException
	{
		// Set up a new game
		Board board = new Board(fenPosition);
		short [] movesArray = new short[MoveGenerator.MAXIMUM_LEGAL_MOVES];
		MoveGenerator moveGen = new MoveGenerator(board, movesArray);

		List<String> movesList = new ArrayList<String>();
		int originalMoveNum = board.getMoveNum();

		// boolean whiteToMove = board.boardInfo.isWhiteToMove();
		int colorToMove = board.colorToMove;
		GameOverReason reason = null;
		String result = "";
		float whitePoints = 0;
		float blackPoints = 0;

		engineWhite.sendCommand("ucinewgame");
		engineBlack.sendCommand("ucinewgame");

		engineWhite.sendCommand("isready");
		boolean success = engineWhite.waitForResponse("readyok", 500);
		engineBlack.sendCommand("isready");
		success = success && engineBlack.waitForResponse("readyok", 500);

		if (!success)
		{
			throw new IOException("Engine non responsive during game setup!");
		}

		engineWhite.sendCommand("position fen " + fenPosition);
		engineBlack.sendCommand("position fen " + fenPosition);

		EnginePlayer currentPlayer = colorToMove == PieceHelper.WHITE ? engineWhite : engineBlack;

		while (true)
		{
			reason = isGameOver(board, moveGen, movesArray);
			if (reason != null)
			{
				if (reason == GameOverReason.WHITE_CHECKMATED || reason == GameOverReason.BLACK_CHECKMATED)
				{
					result = colorToMove == PieceHelper.WHITE ? "0-1" : "1-0";
					whitePoints = colorToMove == PieceHelper.WHITE ? 0 : 1;
					blackPoints = 1 - whitePoints;
				}
				else
				{
					result = "1/2-1/2";
					whitePoints = blackPoints = 0.5f;
				}
				break;
			}

			// Tell engine to start calculating
			currentPlayer.sendCommand("go movetime " + thinkTimeMS);

			// Wait for the best move response
			String bestMove = currentPlayer.waitForBestMove(thinkTimeMS * 10);
			movesList.add(bestMove);

			short move = Utils.getMoveFromUCINotation(board, bestMove);
			if (move != MoveHelper.NULL_MOVE)
			{
				board.movePiece(move);
				board.repetitionIndex = board.halfMoves;
			}
			else
			{
				reason = GameOverReason.ERROR;
				result = "*";
				board.printBoard();
				break;
			}

			engineWhite.sendCommand("move " + bestMove);
			engineBlack.sendCommand("move " + bestMove);
			// System.out.println("Move played: " + bestMove);

			colorToMove = board.colorToMove;
			currentPlayer = colorToMove == PieceHelper.WHITE ? engineWhite : engineBlack;
		}

		String pgn = createPGN(thread, round, engineWhite.getName(), engineBlack.getName(), result, reason, fenPosition,
				originalMoveNum, movesList);
		return new GameResult(engineWhite, engineBlack, whitePoints, blackPoints, reason, pgn);
	}

	public EnginePlayer initializeEngine(String engineName, String enginePath) throws IOException
	{
		// System.out.println("Initializing engine " + engineName + " located at " +
		// enginePath.trim());

		// Start the processes for both engines
		ProcessBuilder pb = new ProcessBuilder("java", "-jar", "-Xms64m", "-Xmx256m", enginePath);
		Process engineProcess = pb.start();
		EnginePlayer engine = new EnginePlayer(engineProcess, engineName);

		// Initialize UCI protocol for both engines
		// System.out.println("Sent uci command to " + engineName);
		engine.sendCommand("uci");
		boolean success = engine.waitForResponse("uciok", 2000);
		// System.out.println("Received uciok command from " + engineName);

		// Tell engines to be ready
		engine.sendCommand("isready");
		success = success && engine.waitForResponse("readyok", 2000);

		if (!success)
		{
			throw new IOException("Engine non responsive during initialization!");
		}

		// System.out.println("Engine " + engineName + " initialized successfully.");

		return engine;
	}

	public ArrayList<String> getPositionsFromFile(String positionsFilePath) throws IOException
	{
		BufferedReader br = new BufferedReader(new FileReader(positionsFilePath));
		String line;
		ArrayList<String> positions = new ArrayList<String>();
		while ((line = br.readLine()) != null)
		{
			positions.add(line.trim());
		}
		br.close();

		return positions;
	}

	private GameOverReason isGameOver(Board board, MoveGenerator moveGen, short [] moves)
	{
		int numMoves = moveGen.generateMoves(false, 0);
		if (numMoves == 0)
		{
			return moveGen.inCheck
					? (board.colorToMove == PieceHelper.WHITE ? GameOverReason.WHITE_CHECKMATED
							: GameOverReason.BLACK_CHECKMATED)
					: GameOverReason.STALEMATE;
		}
		else if (board.halfMoves >= 100)
		{
			return GameOverReason.FIFTY_MOVE_RULE;
		}
		else if (isDuplicatePosition(board))
		{
			return GameOverReason.THREEFOLD_REPETITION;
		}
		else if (isInsufficientMaterial(board))
		{
			return GameOverReason.INSUFFICIENT_MATERIAL;
		}
		else
			return null;
	}

	public boolean isDuplicatePosition(Board board)
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

	public boolean isInsufficientMaterial(Board board)
	{
		Bitboards bitBoards = board.bitBoards;
		long winningPieces = bitBoards.pieceBoards[PieceHelper.PAWN | PieceHelper.WHITE]
				| bitBoards.pieceBoards[PieceHelper.PAWN | PieceHelper.BLACK];
		winningPieces |= bitBoards.pieceBoards[PieceHelper.QUEEN | PieceHelper.WHITE]
				| bitBoards.pieceBoards[PieceHelper.QUEEN | PieceHelper.BLACK];
		winningPieces |= bitBoards.pieceBoards[PieceHelper.ROOK | PieceHelper.WHITE]
				| bitBoards.pieceBoards[PieceHelper.ROOK | PieceHelper.BLACK];
		if (winningPieces != 0)
			return false;

		long whiteMinorPieces = bitBoards.pieceBoards[PieceHelper.KNIGHT | PieceHelper.WHITE]
				| bitBoards.pieceBoards[PieceHelper.BISHOP | PieceHelper.WHITE];
		long blackMinorPieces = bitBoards.pieceBoards[PieceHelper.KNIGHT | PieceHelper.BLACK]
				| bitBoards.pieceBoards[PieceHelper.BISHOP | PieceHelper.BLACK];

		if (Long.bitCount(whiteMinorPieces) >= 2 || Long.bitCount(blackMinorPieces) >= 2)
			return false;

		int whiteIndex = Long
				.numberOfTrailingZeros(bitBoards.pieceBoards[PieceHelper.BISHOP | PieceHelper.WHITE]);
		int blackIndex = Long
				.numberOfTrailingZeros(bitBoards.pieceBoards[PieceHelper.BISHOP | PieceHelper.BLACK]);
		if (whiteIndex == 64 || blackIndex == 64)
			return true;

		if (((whiteIndex / 8) + (whiteIndex % 8)) % 2 == ((blackIndex / 8) + (blackIndex % 8)) % 2)
			return true;

		else
			return false;
	}

	public void writeResults(List<GameResult> results, String pgnOutputPath) throws IOException
	{
		HashMap<EnginePlayer, Float> points = new HashMap<EnginePlayer, Float>();
		HashMap<String, Integer> gameOverReasons = new HashMap<String, Integer>();

		File pgnFile = new File(pgnOutputPath);
		pgnFile.createNewFile();
		BufferedWriter pgnWriter = new BufferedWriter(new FileWriter(pgnFile));

		for (GameResult game : results)
		{
			points.put(game.getWhite(), points.getOrDefault(game.getWhite(), 0f) + game.getWhitePoints());
			points.put(game.getBlack(), points.getOrDefault(game.getBlack(), 0f) + game.getBlackPoints());

			GameOverReason reason = game.getGameOverReason();
			String reasonString = reason.toString();
			if (reason == GameOverReason.WHITE_CHECKMATED)
				reasonString = game.getWhite().getName() + "_" + reasonString;
			else if (reason == GameOverReason.BLACK_CHECKMATED)
				reasonString = game.getBlack().getName() + "_" + reasonString;

			gameOverReasons.put(reasonString, gameOverReasons.getOrDefault(reasonString, 0) + 1);

			pgnWriter.append(game.getPgn() + " \n\n");
		}

		for (Entry<String, Integer> entry : gameOverReasons.entrySet())
		{
			System.out.println(entry.getKey() + ": " + entry.getValue());
		}

		pgnWriter.close();
	}

	private String createPGN(int thread, int round, String whiteName, String blackName, String result,
			GameOverReason reason, String startingFEN, int moveNum, List<String> moves)
	{
		String header = "[Round \"" + thread + "." + round + "\"]\n";
		header += "[White \"" + whiteName + "\"]\n";
		header += "[Black \"" + blackName + "\"]\n";
		header += "[Result \"" + result + "\"]\n";
		header += "[Termination \"" + reason + "\"]\n";
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
		GameOverReason reason;
		private String pgn;

		public GameResult(EnginePlayer white, EnginePlayer black, float whitePoints, float blackPoints,
				GameOverReason reason, String pgn)
		{
			this.white = white;
			this.black = black;
			this.whitePoints = whitePoints;
			this.blackPoints = blackPoints;
			this.reason = reason;
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

		private GameOverReason getGameOverReason()
		{
			return reason;
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
			// System.out.println("Sent command [" + command + "] to " + name);
		}

		private boolean waitForResponse(String expected, int timeout) throws IOException
		{
			long startTime = System.currentTimeMillis();
			String line;
			while (((System.currentTimeMillis() - startTime) < timeout))
			{
				if (!engineIn.ready()) continue;
				line = engineIn.readLine();
				if (line == null) continue;
				
				// System.out.println("Received line [" + line + "] from " + name);
				if (line.contains(expected))
				{
					return true;
				}
			}

			return false;
		}

		private String waitForBestMove(int timeout) throws IOException
		{
			long startTime = System.currentTimeMillis();
			String line;
			String bestMove = MoveHelper.toString(MoveHelper.NULL_MOVE);

			while (((System.currentTimeMillis() - startTime) < timeout))
			{
				if (!engineIn.ready()) continue;
				line = engineIn.readLine();
				if (line == null) continue;
				
				// System.out.println("Received line [" + line + "] from " + name);
				if (line.startsWith("bestmove"))
				{
					String[] parts = line.trim().split(" ");
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