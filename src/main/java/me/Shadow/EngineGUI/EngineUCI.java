package me.Shadow.EngineGUI;

import java.io.IOException;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;

import me.Shadow.Engine.Board;
import me.Shadow.Engine.MoveHelper;
import me.Shadow.Engine.OpeningBook;
import me.Shadow.Engine.Player;
import me.Shadow.Engine.PrecomputedData;
import me.Shadow.Engine.PrecomputedMagicNumbers;

public class EngineUCI
{
	Player engine;
	int commandsProcessing = 0;

	public static void main(String[] args)
	{
		setupEngine();

		EngineUCI uci = new EngineUCI();
		uci.listenToGUI();
	}

	public static void setupEngine()
	{
		PrecomputedData.generateData();
		PrecomputedMagicNumbers.precomputeMagics();
		try
		{
			OpeningBook.createBookFromBinary("resources/LichessOpeningBookBinary.dat");
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}

	public void listenToGUI()
	{
		Scanner scanner = new Scanner(System.in);
		String nextLine = "";
		while (!nextLine.equals("quit"))
		{
			nextLine = scanner.nextLine().trim();
			final String nextLineCopy = nextLine;
			commandsProcessing++;
			//new Thread(() -> commandReceived(nextLineCopy)).start();
			commandReceived(nextLineCopy);
		}
		scanner.close();
	}

	public void commandReceived(String commandLine)
	{
		String[] tokens = commandLine.split(" ");

		if (tokens[0].equals("uci"))
		{
			engine = new Player(new Board());
			sendResponse("uciok");
		}
		else if (tokens[0].equals("isready"))
		{
			while (commandsProcessing > 1) // 1 command is this isready command
			{
				// stall while not ready
				try
				{
					Thread.sleep(50);
				}
				catch (InterruptedException e)
				{
					e.printStackTrace();
				}
			}
			sendResponse("readyok");
		}
		else if (tokens[0].equals("ucinewgame"))
		{
			engine.newPosition();
		}
		else if (tokens[0].equals("position"))
		{
			parsePositionCommand(tokens);
		}
		else if (tokens[0].equals("go"))
		{
			while (commandsProcessing > 1) // 1 command is this go command
			{
				// stall while not ready (to let position command setup the board properly)
				try
				{
					Thread.sleep(10);
				}
				catch (InterruptedException e)
				{
					e.printStackTrace();
				}
			}

			String bestMove = parseGoCommand(tokens, commandLine);
			sendResponse("bestmove " + bestMove);
		}
		else if (tokens[0].equals("move"))
		{
			String moveString = tokens[1];
			engine.makeMove(moveString);
		}
		else if (tokens[0].equals("stop"))
		{
			engine.stopSearching();
		}
		else if (tokens[0].equals("quit"))
		{
			if (engine != null)
				engine.stopSearching();
		}
		else if (tokens[0].equals("printboard"))
		{
			engine.printBoard();
		}

		commandsProcessing--;
	}

	public void parsePositionCommand(String[] tokens)
	{
		String fen = Board.defaultFEN;
		int currentIndex = 1;
		if (tokens[currentIndex].equals("fen"))
		{
			currentIndex++;
			fen = "";
			while (currentIndex < tokens.length)
			{
				if (tokens[currentIndex].equals("moves"))
					break;

				fen += tokens[currentIndex] + " ";
				currentIndex++;
			}
		}
		else
			currentIndex++;

		engine.loadPosition(fen.trim());

		if (currentIndex < tokens.length && tokens[currentIndex].equals("moves"))
		{
			currentIndex++;
			for (int i = currentIndex; i < tokens.length; i++)
			{
				String moveString = tokens[i];
				engine.makeMove(moveString);
			}
		}
	}

	public String parseGoCommand(String[] tokens, String fullCommand)
	{
		short moveFound = MoveHelper.NULL_MOVE;
		if (fullCommand.contains("movetime"))
		{
			String trimmed = fullCommand.substring(fullCommand.indexOf("movetime")).trim();
			int thinkTimeMS = Integer.parseInt(trimmed.split(" ")[1]);
			moveFound = engine.searchMove(thinkTimeMS);
		}
		else
		{
			String trimmed = fullCommand.substring(fullCommand.indexOf("wtime")).trim();
			int whiteTimeMS = Integer.parseInt(trimmed.split(" ")[1]);
			trimmed = fullCommand.substring(fullCommand.indexOf("btime")).trim();
			int blackTimeMS = Integer.parseInt(trimmed.split(" ")[1]);

			int whiteIncrementMS = 0;
			if (fullCommand.contains("winc"))
			{
				trimmed = fullCommand.substring(fullCommand.indexOf("winc")).trim();
				whiteIncrementMS = Integer.parseInt(trimmed.split(" ")[1]);
			}

			int blackIncrementMS = 0;
			if (fullCommand.contains("binc"))
			{
				trimmed = fullCommand.substring(fullCommand.indexOf("binc")).trim();
				blackIncrementMS = Integer.parseInt(trimmed.split(" ")[1]);
			}

			moveFound = engine
					.searchMove(engine.chooseTimeToThink(whiteTimeMS, blackTimeMS, whiteIncrementMS, blackIncrementMS));
		}

		return MoveHelper.toString(moveFound);
	}

	public void sendResponse(String response)
	{
		System.out.println(response);
	}
}
