package me.Shadow.Engine;


public class Perft
{
	// 596055332 positions
	final static String[] perfts = {
			"5,4865609,rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
			"5,5617302,2b1b3/1r1P4/3K3p/1p6/2p5/6k1/1P3p2/4B3 w - - 0 42",
			"6,11030083,8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - -",
			"5,15587335,r3k2r/pp3pp1/PN1pr1p1/4p1P1/4P3/3P4/P1P2PP1/R3K2R w KQkq - 4 4",
			"5,89941194,rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8",
			"4,3894594,r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10",
			"5,193690690,r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq -",
			"4,497787,r3k1nr/p2pp1pp/b1n1P1P1/1BK1Pp1q/8/8/2PP1PPP/6N1 w kq - 0 1",
			"6,1134888,3k4/3p4/8/K1P4r/8/8/8/8 b - - 0 1",
			"6,1440467,8/8/1k6/2b5/2pP4/8/5K2/8 b - d3 0 1",
			"6,661072,5k2/8/8/8/8/8/8/4K2R w K - 0 1",
			"7,15594314,3k4/8/8/8/8/8/8/R3K3 w Q - 0 1",
			"4,1274206,r3k2r/1b4bq/8/8/8/8/7B/R3K2R w KQkq - 0 1",
			"5,58773923,r3k2r/8/3Q4/8/8/5q2/8/R3K2R b KQkq - 0 1",
			"6,3821001,2K2r2/4P3/8/8/8/8/8/3k4 w - - 0 1",
			"5,1004658,8/8/1P2K3/8/2n5/1q6/8/5k2 b - - 0 1",
			"6,217342,4k3/1P6/8/8/8/8/K7/8 w - - 0 1",
			"6,92683,8/P1k5/K7/8/8/8/8/8 w - - 0 1",
			"10,5966690,K1k5/8/P7/8/8/8/8/8 w - - 0 1",
			"7,567584,8/k1P5/8/1K6/8/8/8/8 w - - 0 1",
			"6,3114998,8/8/2k5/5q2/5n2/8/5K2/8 b - - 0 1",
			"5,42761834,r1bq2r1/1pppkppp/1b3n2/pP1PP3/2n5/2P5/P3QPPP/RNB1K2R w KQ a6 0 12",
			"4,3050662,r3k2r/pppqbppp/3p1n1B/1N2p3/1nB1P3/3P3b/PPPQNPPP/R3K2R w KQkq - 11 10",
			"5,10574719,4k2r/1pp1n2p/6N1/1K1P2r1/4P3/P5P1/1Pp4P/R7 w k - 0 6",
			"4,6871272,1Bb3BN/R2Pk2r/1Q5B/4q2R/2bN4/4Q1BK/1p6/1bq1R1rb w - - 0 1",
			"6,71179139,n1n5/PPPk4/8/8/8/8/4Kppp/5N1N b - - 0 1",
			"6,28859283,8/PPPk4/8/8/8/8/4Kppp/8 b - - 0 1",
			"9,7618365,8/2k1p3/3pP3/3P2K1/8/8/8/8 w - - 0 1",
			"4,28181,3r4/2p1p3/8/1P1P1P2/3K4/5k2/8/8 b - - 0 1",
			"5,6323457,8/1p4p1/8/q1PK1P1r/3p1k2/8/4P3/4Q3 b - - 0 1"
	};
	
	static long moveGenTime;
	static long boardInfoTime;
	static long movePieceTime;
	static long moveBackTime;
	
	static Board board;
	static MoveGenerator moveGen;
		
	public static void runPerftSuite(int runCount)
	{
		long totalTimeSum = 0;
		for (int i = 0; i < runCount; i++)
		{
			totalTimeSum += runPerftSuite();
		}
		System.out.println("\n\nTotal Time: " + totalTimeSum);
		System.out.println("Average time: " + totalTimeSum/runCount);
	}
	
	// average time so far is 5 seconds
	public static long runPerftSuite()
	{
		//int result = countMoves(5);
		
		boolean allTestsPassed = true;
		long time = System.currentTimeMillis();
		for (String perft : perfts)
		{
			boolean success = runPerft(perft);
			if (!success)
			{
				allTestsPassed = false;
				System.out.println("PERFT FAILED\n");
			}
		}
		
		System.out.println("\n\nTest " + (allTestsPassed ? "passed" : "failed"));
		System.out.println("Total Time Taken: " + (System.currentTimeMillis() - time) + " ms");
		System.out.println("Total Tracked Time: " + (moveGenTime + movePieceTime + moveBackTime + boardInfoTime));
		System.out.println("Move Generation Time: " + moveGenTime);
		System.out.println("BoardInfo Copy Time: " + boardInfoTime);
		System.out.println("Move Piece Time: " + movePieceTime);
		System.out.println("Move Back Time: " + moveBackTime);
		
		return (System.currentTimeMillis() - time);
	}
	
	public static boolean runPerft(String perft)
	{
		String [] perftData = perft.split(",");
		int depth = Integer.parseInt(perftData[0]);
		int expectedCount = Integer.parseInt(perftData[1]);
		String fen = perftData[2];
		
		board = new Board(fen);
		moveGen = new MoveGenerator(board);
		long startTime = System.currentTimeMillis();
		int result = countMoves(depth);
		System.out.println(expectedCount + "\t" + result + "\t" + (System.currentTimeMillis() - startTime) + " ms\n");	
		return result == expectedCount;
	}
	
	public static int countMoves(int depth)
	{
		//if (depth == 0) return 1;
		short [] moves = moveGen.generateMoves(false);
		
		if (depth == 1) return moves.length;
		
		int num = 0;
		int length = moves.length;
		for (int i = 0; i < length; i++)
		{
			short move = moves[i];
						
			BoardInfo boardInfoOld = new BoardInfo(board.boardInfo);
			int captured = board.movePiece(move);
			
			int add = countMoves(depth - 1);
			num += add;
			
			board.moveBack(move, captured, boardInfoOld);
		}
		
		return num;
	}
}
