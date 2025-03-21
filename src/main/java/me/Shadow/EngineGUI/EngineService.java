package me.Shadow.EngineGUI;

import java.util.HashMap;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.annotation.JsonProperty;

import me.Shadow.Engine.Board;
import me.Shadow.Engine.MoveHelper;
import me.Shadow.Engine.Player;

@Service
public class EngineService
{
	//private Player engine = new Player(new Board());
	private HashMap<Integer, GamePlayer> ongoingGames = new HashMap<>();
	
	private static final Logger logger = LoggerFactory.getLogger(EngineService.class);
	
	@Async
	public CompletableFuture<ResponseEntity<GamePlayer>> newGame(String command)
	{
		logger.info("Received new game command: command={}", command);
		
		Integer playerID = (int)(Math.random()*1000000000)+1000000000;
		Player player = new Player(new Board());
		GamePlayer gamePlayer = new GamePlayer(player, playerID);
		ongoingGames.put(playerID, gamePlayer);
		
		if (command.length() == 0)
		{
			player.loadPosition(Board.defaultFEN);
			return CompletableFuture.completedFuture(new ResponseEntity<GamePlayer>(gamePlayer, HttpStatus.OK));
		}
		
		String [] tokens = command.split(" ");
		
		String fen = Board.defaultFEN;
		int currentIndex = 0;
		if (tokens[currentIndex].equals("fen"))
		{
			currentIndex++;
			fen = "";
			while (currentIndex < tokens.length)
			{
				if (tokens[currentIndex].equals("moves")) break;
				
				fen += tokens[currentIndex] + " ";
				currentIndex++;
			}
		}
		else currentIndex++;
		
		player.loadPosition(fen.trim());
		
		if (currentIndex < tokens.length && tokens[currentIndex].equals("moves"))
		{
			currentIndex++;
			for (int i = currentIndex; i < tokens.length; i++)
			{
				String moveString = tokens[i];
				player.makeMove(moveString);
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
			if (gamePlayer.player.makeMove(move.trim()))
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
			short moveFound = gamePlayer.player.searchMove(movetime);
			if (moveFound != MoveHelper.NULL_MOVE)
			{
				logger.info("Sucessfully searched best move: playerID={}, move={}", playerID, MoveHelper.toString(moveFound));
				return CompletableFuture.completedFuture(new ResponseEntity<String>(MoveHelper.toString(moveFound), HttpStatus.OK));
			}
			else
			{
				logger.info("Error searching best move, invalid or no move found: playerID={}", playerID);
				return CompletableFuture.completedFuture(new ResponseEntity<String>("Invalid or No Move Found", HttpStatus.INTERNAL_SERVER_ERROR));
			}
		}
		else
		{
			logger.info("Error searching best move, invalid playerID: playerID={}", playerID);
			return CompletableFuture.completedFuture(new ResponseEntity<String>("Error: Invalid playerID: " + playerID, HttpStatus.BAD_REQUEST));
		}
	}	
	
	
	@Async
	public CompletableFuture<ResponseEntity<String>> bestMove(Integer playerID, Integer wtime, Integer winc, Integer btime, Integer binc)
	{
		GamePlayer gamePlayer = ongoingGames.get(playerID);
		if (gamePlayer != null)
		{
			Integer thinkTimeMS = gamePlayer.player.chooseTimeToThink(wtime, btime, winc, binc);
			return bestMove(playerID, thinkTimeMS);
		}
		else
		{
			logger.info("Error searching best move, invalid playerID: playerID={}", playerID);
			return CompletableFuture.completedFuture(new ResponseEntity<String>("Error: Invalid playerID: " + playerID, HttpStatus.BAD_REQUEST));
		}
	}
	
	@Async
	public CompletableFuture<ResponseEntity<String>> stopSearching(Integer playerID)
	{
		logger.info("Received stop search command: playerID={}", playerID);
		GamePlayer gamePlayer = ongoingGames.get(playerID);
		if (gamePlayer != null)
		{
			gamePlayer.player.stopSearching();
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
			gamePlayer.player = null;
			gamePlayer = null;
			return CompletableFuture.completedFuture(new ResponseEntity<String>("Player Deleted: " + playerID, HttpStatus.OK));
		}
		else
		{
			logger.info("Error ending game, invalid playerID: playerID={}", playerID);
			return CompletableFuture.completedFuture(new ResponseEntity<String>("Error: Invalid playerID: " + playerID, HttpStatus.BAD_REQUEST));
		}
	}
	
	class GamePlayer
	{
		Player player;
		@JsonProperty
		Integer playerID;
		
		public GamePlayer(Player player, Integer playerID)
		{
			this.player = player;
			this.playerID = playerID;
		}
	}
}
