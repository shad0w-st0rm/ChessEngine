package me.Shadow.EngineGUI;

import com.fasterxml.jackson.annotation.JsonProperty;

import me.Shadow.Engine.Board;
import me.Shadow.Engine.Player;

public class GamePlayer
{
	private Player player;
	@JsonProperty
	private Integer playerID;
	long lastActive;

	public GamePlayer(Player player, Integer playerID)
	{
		this.player = player;
		this.playerID = playerID;
		this.lastActive = System.currentTimeMillis(); // Set the last activity time to the current time
	}
	
	public static GamePlayer createPlayer(Integer playerID)
	{
		return new GamePlayer(new Player(new Board()), playerID);
	}
	
	public static Integer getPlayerID(GamePlayer player)
	{
		return player.playerID;
	}
	
	public static void loadPosition(GamePlayer player, String position)
	{
		player.player.loadPosition(position);
	}
	
	public static boolean makeMove(GamePlayer player, String moveString)
	{
		return player.player.makeMove(moveString);
	}
	
	public static short searchMove(GamePlayer player, Integer movetime)
	{
		return player.player.searchMove(movetime);
	}
	
	public static short searchMove(GamePlayer player, Integer wtime, Integer btime, Integer winc, Integer binc)
	{
		Integer thinkTimeMS = player.player.chooseTimeToThink(wtime, btime, winc, binc);
		return GamePlayer.searchMove(player, thinkTimeMS);
	}
	
	public static void stopSearching(GamePlayer player)
	{
		player.player.stopSearching();
	}
	
	public static void updateActivity(GamePlayer player)
	{
		player.lastActive = System.currentTimeMillis(); // Update the activity time
	}
	
	public static boolean isInactive(GamePlayer player, long timeoutMillis)
	{
		return System.currentTimeMillis() - player.lastActive > timeoutMillis;
	}
}