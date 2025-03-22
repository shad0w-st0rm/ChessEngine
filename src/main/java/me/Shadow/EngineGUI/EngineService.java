package me.Shadow.EngineGUI;

import java.util.HashMap;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import me.Shadow.Engine.Board;
import me.Shadow.Engine.MoveHelper;

@Service
public class EngineService
{
	private HashMap<Integer, GamePlayer> ongoingGames = new HashMap<>();

	private static final Logger logger = LoggerFactory.getLogger(EngineService.class);

	@Async
	public CompletableFuture<ResponseEntity<GamePlayer>> newGame(String command)
	{
		logger.info("Received new game command: command={}", command);

		Integer playerID = (int) (Math.random() * 1000000000) + 1000000000;
		GamePlayer gamePlayer = GamePlayer.createPlayer(playerID);

		ongoingGames.put(playerID, gamePlayer);

		if (command.length() == 0)
		{
			GamePlayer.loadPosition(gamePlayer, Board.defaultFEN);
			return CompletableFuture.completedFuture(new ResponseEntity<GamePlayer>(gamePlayer, HttpStatus.OK));
		}

		String[] tokens = command.split(" ");

		String fen = Board.defaultFEN;
		int currentIndex = 0;
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
		
		GamePlayer.loadPosition(gamePlayer, fen.trim());

		if (currentIndex < tokens.length && tokens[currentIndex].equals("moves"))
		{
			currentIndex++;
			for (int i = currentIndex; i < tokens.length; i++)
			{
				String moveString = tokens[i];
				GamePlayer.makeMove(gamePlayer, moveString);
			}
		}

		logger.info("Sucessfully created a new game: playerID={}", playerID);
		return CompletableFuture.completedFuture(new ResponseEntity<GamePlayer>(gamePlayer, HttpStatus.OK));
	}

	@Async
	public CompletableFuture<ResponseEntity<String>> playerMove(Integer playerID, String move)
	{
		logger.info("Received player move command: playerID={}, move={}", playerID, move);

		GamePlayer gamePlayer = ongoingGames.get(playerID);
		if (gamePlayer != null)
		{
			GamePlayer.updateActivity(gamePlayer);
			if (GamePlayer.makeMove(gamePlayer, move.trim()))
			{
				logger.info("Sucessfully made player move: playerID={}, move={}", playerID, move);
				return CompletableFuture.completedFuture(new ResponseEntity<String>("Move successfully made", HttpStatus.OK));
			}
			else
			{
				logger.info("Error making player move: move={}", move);
				return CompletableFuture.completedFuture(new ResponseEntity<String>("Error: Invalid move: " + move, HttpStatus.BAD_REQUEST));
			}
		}
		else
		{
			logger.info("Error making player move, invalid playerID: playerID={}", playerID);
			return CompletableFuture.completedFuture(new ResponseEntity<String>("Error: Invalid playerID: " + playerID, HttpStatus.BAD_REQUEST));
		}
	}

	@Async
	public CompletableFuture<ResponseEntity<String>> bestMove(Integer playerID, Integer movetime)
	{
		GamePlayer gamePlayer = ongoingGames.get(playerID);
		if (gamePlayer != null)
		{
			GamePlayer.updateActivity(gamePlayer);
			return validateMove(playerID, GamePlayer.searchMove(gamePlayer, movetime));
		}
		else
		{
			logger.info("Error searching best move, invalid playerID: playerID={}", playerID);
			return CompletableFuture.completedFuture(new ResponseEntity<String>("Error: Invalid playerID: " + playerID, HttpStatus.BAD_REQUEST));
		}
	}

	@Async
	public CompletableFuture<ResponseEntity<String>> bestMove(Integer playerID, Integer wtime, Integer winc,
			Integer btime, Integer binc)
	{
		GamePlayer gamePlayer = ongoingGames.get(playerID);
		if (gamePlayer != null)
		{
			return validateMove(playerID, GamePlayer.searchMove(gamePlayer, wtime, btime, winc, binc));
		}
		else
		{
			logger.info("Error searching best move, invalid playerID: playerID={}", playerID);
			return CompletableFuture.completedFuture(new ResponseEntity<String>("Error: Invalid playerID: " + playerID, HttpStatus.BAD_REQUEST));
		}
	}
	
	private CompletableFuture<ResponseEntity<String>> validateMove(Integer playerID, short move)
	{
		if (move != MoveHelper.NULL_MOVE)
		{
			logger.info("Sucessfully searched best move: playerID={}, move={}", playerID, MoveHelper.toString(move));
			return CompletableFuture.completedFuture(new ResponseEntity<String>(MoveHelper.toString(move), HttpStatus.OK));
		}
		else
		{
			logger.info("Error searching best move, invalid or no move found: playerID={}", playerID);
			return CompletableFuture.completedFuture(new ResponseEntity<String>("Invalid or No Move Found", HttpStatus.INTERNAL_SERVER_ERROR));
		}
	}

	@Async
	public CompletableFuture<ResponseEntity<String>> stopSearching(Integer playerID)
	{
		logger.info("Received stop search command: playerID={}", playerID);
		GamePlayer gamePlayer = ongoingGames.get(playerID);
		if (gamePlayer != null)
		{
			GamePlayer.updateActivity(gamePlayer);
			GamePlayer.stopSearching(gamePlayer);
			logger.info("Sucessfully stopped search: playerID={}", playerID);
			return CompletableFuture.completedFuture(new ResponseEntity<String>("Stopped Searching", HttpStatus.OK));
		}
		else
		{
			logger.info("Error stopping search, invalid playerID: playerID={}", playerID);
			return CompletableFuture.completedFuture(new ResponseEntity<String>("Error: Invalid playerID: " + playerID, HttpStatus.BAD_REQUEST));
		}
	}

	@Async
	public CompletableFuture<ResponseEntity<String>> endGame(Integer playerID)
	{
		logger.info("Received end game command: playerID={}", playerID);
		GamePlayer gamePlayer = ongoingGames.remove(playerID);
		if (gamePlayer != null)
		{
			logger.info("Sucessfully ended game: playerID={}", playerID);
			return CompletableFuture.completedFuture(new ResponseEntity<String>("Player Deleted: " + playerID, HttpStatus.OK));
		}
		else
		{
			logger.info("Error ending game, invalid playerID: playerID={}", playerID);
			return CompletableFuture.completedFuture(new ResponseEntity<String>("Error: Invalid playerID: " + playerID, HttpStatus.BAD_REQUEST));
		}
	}
	
	@Async
	public CompletableFuture<ResponseEntity<String>> keepAlive(Integer playerID)
	{
		logger.info("Received keep alive command: playerID={}", playerID);
		GamePlayer gamePlayer = ongoingGames.get(playerID);
		if (gamePlayer != null)
		{
			GamePlayer.updateActivity(gamePlayer);
			logger.info("Sucessfully kept player alive: playerID={}", playerID);
			return CompletableFuture.completedFuture(new ResponseEntity<String>("Player Deleted: " + playerID, HttpStatus.OK));
		}
		else
		{
			logger.info("Error keeping player alive, invalid playerID: playerID={}", playerID);
			return CompletableFuture.completedFuture(new ResponseEntity<String>("Error: Invalid playerID: " + playerID, HttpStatus.BAD_REQUEST));
		}
	}

	@Scheduled(fixedRate = 30 * 1000) // Run every 30 seconds
	public void cleanUpInactiveGames()
	{
		long INACTIVITY_TIMEOUT = 60 * 1000; // 1 minute in milliseconds

		ongoingGames.entrySet().removeIf(entry ->
		{
			GamePlayer gamePlayer = entry.getValue();
			if (GamePlayer.isInactive(gamePlayer, INACTIVITY_TIMEOUT))
			{
				logger.info("Cleaning up inactive game: playerID={}", entry.getKey());
				return true; // Remove the game from ongoingGames
			}
			return false;
		});
	}
}
