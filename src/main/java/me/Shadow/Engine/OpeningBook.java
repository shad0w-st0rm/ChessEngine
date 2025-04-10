package me.Shadow.Engine;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public class OpeningBook
{
	public static HashMap<Long, OpeningMove[]> openingBook = new HashMap<>();
		
	public static int createBookFromBinary(String inputPath) throws IOException
	{
		HashMap<Long, OpeningMove[]> bookMap = new HashMap<>();
		byte [] bytes = Thread.currentThread().getContextClassLoader().getResourceAsStream(inputPath).readAllBytes();
		int index = 0;
		int moveCount = 0;
		while ((index + 9) < bytes.length) // if no counter + hash can fit in the rest of the file, ignore it
		{
			int positionsToFollow = bytes[index];
			long hash = 0;
			for (int i = 1; i <= 8; i++)
			{
				hash |= ((bytes[index + i] & 0xFFL) << (64 - i*8));
			}
			index += 9; // now points to the first move
			
			OpeningMove[] moves = new OpeningMove[positionsToFollow];
			for (int i = 0; i < positionsToFollow; i++)
			{
				short moveData = (short) ((bytes[index] << 8) & 0xFF00);
				moveData |= (short) (bytes[index + 1] & 0xFF);
				index += 2;
				int timesPlayed = 0;
				for (int j = 0; j < 4; j++)
				{
					int fragment = bytes[index + j] & 0xFF;
					timesPlayed |= fragment << (24 - j*8);
				}
				index += 4;
				moves[i] = new OpeningMove(moveData, timesPlayed);
			}
			
			bookMap.put(hash, moves);
			moveCount += positionsToFollow;
		}
		openingBook = bookMap;
		return moveCount;
	}
	
	public static void sortAndWriteBinaryBook(HashMap<Long, OpeningMove[]> book, String outputPath) throws IOException
	{
		ArrayList<Map.Entry<Long, OpeningMove[]>> entries = new ArrayList<>(book.entrySet());
		Collections.sort(entries, new Comparator<Entry<Long, OpeningMove[]>>() {
			@Override
			public int compare(Entry<Long, OpeningMove[]> o1, Entry<Long, OpeningMove[]> o2)
			{
				OpeningMove[] oneMoves = o1.getValue();
				OpeningMove[] twoMoves = o2.getValue();
				int count = 0;
				for (OpeningMove move : oneMoves)
				{
					count += move.getTimesPlayed();
				}
				
				for (OpeningMove move : twoMoves)
				{
					count -= move.getTimesPlayed();
				}
				return -count;	// reverse to sort by descending order
			}
		});
		
		File binaryFile = new File(outputPath);
		binaryFile.createNewFile();
		Path path = Paths.get(outputPath);
		
		for (Map.Entry<Long, OpeningMove[]> entry : entries)
		{
			long hash = entry.getKey();
			OpeningMove[] moves = entry.getValue();
			int bytesTotal = 9 + 6*moves.length; // 1 for number of following moves + 8 for the hash + 6 bytes per move
			byte[] bytes = new byte[bytesTotal];
			bytes[0] = (byte) moves.length;	// number of following moves (after the hash)
			for (int i = 0; i < 8; i++)
			{
				bytes[i + 1] = (byte) ((hash >>> (56 - i*8)) & 0xFF);
			}
			int byteIndex = 9; // current index of array of bytes
			for (OpeningMove move : moves)
			{
				short moveData = move.getMove();
				bytes[byteIndex] = (byte) ((moveData >>> 8) & 0xFF);
				bytes[byteIndex + 1] = (byte) (moveData & 0xFF);
				byteIndex += 2;
				int timesPlayed = move.getTimesPlayed();
				for (int i = 1; i <= 4; i++)
				{
					bytes[byteIndex] = (byte) ((timesPlayed >>> (32 - i*8)) & 0xFF);
					byteIndex++;
				}
			}
			
			Files.write(path, bytes, StandardOpenOption.APPEND);
		}
	}
		
	public static void sortAndWritePlainTextBook(HashMap<Long, OpeningMove[]> book, String outputPath) throws IOException
	{
		ArrayList<Map.Entry<Long, OpeningMove[]>> entries = new ArrayList<>(book.entrySet());
		Collections.sort(entries, new Comparator<Entry<Long, OpeningMove[]>>() {
			@Override
			public int compare(Entry<Long, OpeningMove[]> o1, Entry<Long, OpeningMove[]> o2)
			{
				OpeningMove[] oneMoves = o1.getValue();
				OpeningMove[] twoMoves = o2.getValue();
				int count = 0;
				for (OpeningMove move : oneMoves)
				{
					count += move.getTimesPlayed();
				}
				
				for (OpeningMove move : twoMoves)
				{
					count -= move.getTimesPlayed();
				}
				return -count;	// reverse to sort by descending order
			}
		});
		
		File plainTextFile = new File(outputPath);
		plainTextFile.createNewFile();
		BufferedWriter plainTextWriter = new BufferedWriter(new FileWriter(plainTextFile));
		for (Map.Entry<Long, OpeningMove[]> entry : entries)
		{
			long hash = entry.getKey();
			OpeningMove[] moves = entry.getValue();
			plainTextWriter.append("zobrist " + Long.toString(hash) + "\n");
			for (OpeningMove move : moves)
			{
				plainTextWriter.append(MoveHelper.toString(move.getMove()) + " " + move.getTimesPlayed() + "\n");
			}
		}
		plainTextWriter.close();
	}
	
	public static HashMap<Long, OpeningMove[]> convertAlgebraicMoves(String inputPath) throws IOException
	{
		HashMap<Long, HashMap<Short, Integer>> openingBookSetup = new HashMap<>();
		
		File gamesFile = new File(inputPath);
		BufferedReader reader = new BufferedReader(new FileReader(gamesFile));
		
		String pgn = reader.readLine();
		while (pgn != null)
		{
			pgn = pgn.trim();
			String [] algebraicMoves = pgn.split(" ");
			Board board = new Board();
			int ply = 0;
			for (String algebraicMove : algebraicMoves)
			{
				algebraicMove = algebraicMove.trim();
				short move = Utils.getMoveFromAlgebraicNotation(board, algebraicMove);
				if (move == MoveHelper.NULL_MOVE) break;
				
				HashMap<Short, Integer> movesInPosition = openingBookSetup.getOrDefault(board.boardInfo.getZobristHash(), new HashMap<Short, Integer>());
				int newPlayCount = movesInPosition.getOrDefault(move, 0) + 1;
				movesInPosition.put(move, newPlayCount);
				openingBookSetup.put(board.boardInfo.getZobristHash(), movesInPosition);
				
				board.movePiece(move);
				
				ply++;
				if (ply >= 20) break;
			}
			
			pgn = reader.readLine();
		}
		reader.close();
		
		HashMap<Long, OpeningMove[]> openingBook = new HashMap<>();
		int totalMoves = 0;
		int totalPositions = 0;
		for (long zobristHash : openingBookSetup.keySet())
		{
			HashMap<Short, Integer> movesInPosition = openingBookSetup.get(zobristHash);
			OpeningMove [] moves = new OpeningMove[movesInPosition.size()];
			int cumulativeMoves = 0;
			int index = 0;
			for (short move : movesInPosition.keySet())
			{
				moves[index] = new OpeningMove(move, movesInPosition.get(move));
				cumulativeMoves += moves[index].getTimesPlayed();
				index++;
			}
			
			if (cumulativeMoves < 50) continue;
			
			totalPositions += cumulativeMoves;
			totalMoves += moves.length;
			Arrays.sort(moves, Comparator.comparing(OpeningMove::getTimesPlayed).reversed());
			openingBook.put(zobristHash, moves);
		}
		
		System.out.println(openingBook.size() + " " + totalMoves + " " + totalPositions);
		
		return openingBook;
	}
	
	public static void cleanCombinedFile(String inputPath, String outputPath) throws IOException
	{
		File combinedFile = new File(inputPath);
		BufferedReader reader = new BufferedReader(new FileReader(combinedFile));
		File cleanedFile = new File(outputPath);
		BufferedWriter writer = new BufferedWriter(new FileWriter(cleanedFile));
		
		String gamePGN = reader.readLine();
		int totalGames = 0;
		while (gamePGN != null)
		{
			gamePGN = gamePGN.trim();
			String [] moves = gamePGN.split("[0-9]+\\.");
			String cleanedPGN = "";
			for (String move : moves)
			{
				move = move.trim();
				if (move.length() != 0) cleanedPGN += move + " ";
			}
			cleanedPGN = cleanedPGN.replace("1-0", "").replace("0-1", "").replace("1/2-1/2", "").replace("*", "");
			cleanedPGN = cleanedPGN.trim();
			if (cleanedPGN.length() != 0)
			{
				cleanedPGN += "\n";
				writer.append(cleanedPGN);
				totalGames++;
			}
			
			gamePGN = reader.readLine();
		}
		
		reader.close();
		writer.close();
		
		System.out.println(totalGames);
	}
	
	public static void combineAllFiles(String inputPath, String outputPath) throws IOException
	{
		File tournamentsFolder = new File(inputPath);
		File [] tournamentPGNs = tournamentsFolder.listFiles();
		File outputFile = new File(outputPath);
		outputFile.createNewFile();
		
		BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile));
		
		for (File pgn : tournamentPGNs)
		{
			BufferedReader reader = new BufferedReader(new FileReader(pgn));
			String line = reader.readLine();
			boolean withinPGN = false;
			String currentPGN = "";
			
			while (line != null)
			{
				if (line.length() == 0 && withinPGN)
				{
					currentPGN += "\n";
					writer.append(currentPGN);
					withinPGN = false;
					currentPGN = "";
				}
				else if (line.length() != 0 && line.charAt(0) != '[')
				{
					line = line.trim();
					if (withinPGN) currentPGN += " ";
					currentPGN += line;
					withinPGN = true;
				}
				line = reader.readLine();
			}
			
			reader.close();
		}
		
		writer.close();
	}
	
	public static class OpeningMove
	{
		private short move;
		private int timesPlayed;
		
		public OpeningMove(short move, int timesPlayed)
		{
			this.move = move;
			this.timesPlayed = timesPlayed;
		}
		
		public short getMove()
		{
			return move;
		}
		
		public int getTimesPlayed()
		{
			return timesPlayed;
		}
	}
}
