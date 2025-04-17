package me.Shadow.Testing;

import me.Shadow.Engine.Board;
import me.Shadow.Engine.MoveGenerator;
import me.Shadow.Engine.MoveHelper;
import me.Shadow.Engine.MoveOrderer;
import me.Shadow.Engine.PieceHelper;
import me.Shadow.Engine.Utils;

public class MoveOrdererTester
{
	public static final String [] SEEPositions = {
		"6k1/1pp4p/p1pb4/6q1/3P1pRr/2P4P/PP1Br1P1/5RKN w - -; Rfxf4; -100; P - R + B",
		"5rk1/1pp2q1p/p1pb4/8/3P1NP1/2P5/1P1BQ1P1/5RK1 b - -; Bxf4; 0; -N + B",
		"4R3/2r3p1/5bk1/1p1r3p/p2PR1P1/P1BK1P2/1P6/8 b - -; hxg4; 0;",
		"4R3/2r3p1/5bk1/1p1r1p1p/p2PR1P1/P1BK1P2/1P6/8 b - -; hxg4; 0;",
		"4r1k1/5pp1/nbp4p/1p2p2q/1P2P1b1/1BP2N1P/1B2QPPK/3R4 b - -; Bxf3; 0;",
		"2r1r1k1/pp1bppbp/3p1np1/q3P3/2P2P2/1P2B3/P1N1B1PP/2RQ1RK1 b - -; dxe5; 100; P",
		"7r/5qpk/p1Qp1b1p/3r3n/BB3p2/5p2/P1P2P2/4RK1R w - -; Re8; 0;",
		"6rr/6pk/p1Qp1b1p/2n5/1B3p2/5p2/P1P2P2/4RK1R w - -; Re8; -500; -R",
		"7r/5qpk/2Qp1b1p/1N1r3n/BB3p2/5p2/P1P2P2/4RK1R w - -; Re8; -500; -R",
		"6RR/4bP2/8/8/5r2/3K4/5p2/4k3 w - -; f8=Q; 200; B - P",
		"6RR/4bP2/8/8/5r2/3K4/5p2/4k3 w - -; f8=N; 200; N - P",
		"7R/5P2/8/8/6r1/3K4/5p2/4k3 w - -; f8=Q; 800; Q - P",
		"7R/5P2/8/8/6r1/3K4/5p2/4k3 w - -; f8=B; 200; B - P",
		"7R/4bP2/8/8/1q6/3K4/5p2/4k3 w - -; f8=R; -100; -P",
		"8/4kp2/2npp3/1Nn5/1p2PQP1/7q/1PP1B3/4KR1r b - -; Rxf1+; 0;",
		"8/4kp2/2npp3/1Nn5/1p2P1P1/7q/1PP1B3/4KR1r b - -; Rxf1+; 0;",
		"2r2r1k/6bp/p7/2q2p1Q/3PpP2/1B6/P5PP/2RR3K b - -; Qxc1; 100; R - Q + R",
		"r2qk1nr/pp2ppbp/2b3p1/2p1p3/8/2N2N2/PPPP1PPP/R1BQR1K1 w kq -; Nxe5; 100; P",
		"6r1/4kq2/b2p1p2/p1pPb3/p1P2B1Q/2P4P/2B1R1P1/6K1 w - -; Bxe5; 0;",
		"3q2nk/pb1r1p2/np6/3P2Pp/2p1P3/2R4B/PQ3P1P/3R2K1 w - h6; gxh6; 0;",
		"3q2nk/pb1r1p2/np6/3P2Pp/2p1P3/2R1B2B/PQ3P1P/3R2K1 w - h6; gxh6; 100; P",
		"2r4r/1P4pk/p2p1b1p/7n/BB3p2/2R2p2/P1P2P2/4RK2 w - -; Rxc8; 500; R",
		"2r5/1P4pk/p2p1b1p/5b1n/BB3p2/2R2p2/P1P2P2/4RK2 w - -; Rxc8; 500; R",
		"2r4k/2r4p/p7/2b2p1b/4pP2/1BR5/P1R3PP/2Q4K w - -; Rxc5; 300; B",
		"8/pp6/2pkp3/4bp2/2R3b1/2P5/PP4B1/1K6 w - -; Bxc6; -200; P - B",
		"4q3/1p1pr1k1/1B2rp2/6p1/p3PP2/P3R1P1/1P2R1K1/4Q3 b - -; Rxe4; -400; P - R",
		"4q3/1p1pr1kb/1B2rp2/6p1/p3PP2/P3R1P1/1P2R1K1/4Q3 b - -; Bxe4; 100; P",
		"3r3k/3r4/2n1n3/8/3p4/2PR4/1B1Q4/3R3K w - -; Rxd4; -100; P - R + N - P + N - B + R - Q + R",
		"1k1r4/1ppn3p/p4b2/4n3/8/P2N2P1/1PP1R1BP/2K1Q3 w - -; Nxe5; 100; N - N + B - R + N",
		"1k1r3q/1ppn3p/p4b2/4p3/8/P2N2P1/1PP1R1BP/2K1Q3 w - -; Nxe5; -200; P - N",
		"rnb2b1r/ppp2kpp/5n2/4P3/q2P3B/5R2/PPP2PPP/RN1QKB2 w Q -; Bxf6; 100; N - B + P",
		"r2q1rk1/2p1bppp/p2p1n2/1p2P3/4P1b1/1nP1BN2/PP3PPP/RN1QR1K1 b - -; Bxf3; 0; N - B",
		"r1bqkb1r/2pp1ppp/p1n5/1p2p3/3Pn3/1B3N2/PPP2PPP/RNBQ1RK1 b kq -; Nxd4; 0; P - N + N - P",
		"r1bq1r2/pp1ppkbp/4N1p1/n3P1B1/8/2N5/PPP2PPP/R2QK2R w KQ -; Nxg7; 0; B - N",
		"r1bq1r2/pp1ppkbp/4N1pB/n3P3/8/2N5/PPP2PPP/R2QK2R w KQ -; Nxg7; 300; B",
		"rnq1k2r/1b3ppp/p2bpn2/1p1p4/3N4/1BN1P3/PPP2PPP/R1BQR1K1 b kq -; Bxh2; -200; P - B",
		"rn2k2r/1bq2ppp/p2bpn2/1p1p4/3N4/1BN1P3/PPP2PPP/R1BQR1K1 b kq -; Bxh2; 100; P",
		"r2qkbn1/ppp1pp1p/3p1rp1/3Pn3/4P1b1/2N2N2/PPP2PPP/R1BQKB1R b KQq -; Bxf3; 100; N - B + P",
		"rnbq1rk1/pppp1ppp/4pn2/8/1bPP4/P1N5/1PQ1PPPP/R1B1KBNR b KQ -; Bxc3; 0; N - B",
		"r4rk1/3nppbp/bq1p1np1/2pP4/8/2N2NPP/PP2PPB1/R1BQR1K1 b - -; Qxb2; -800; P - Q",
		"r4rk1/1q1nppbp/b2p1np1/2pP4/8/2N2NPP/PP2PPB1/R1BQR1K1 b - -; Nxd5; -200; P - N",
		"1r3r2/5p2/4p2p/2k1n1P1/2PN1nP1/1P3P2/8/2KR1B1R b - -; Rxb3; -400; P - R",
		"1r3r2/5p2/4p2p/4n1P1/kPPN1nP1/5P2/8/2KR1B1R b - -; Rxb4; 100; P",
		"2r2rk1/5pp1/pp5p/q2p4/P3n3/1Q3NP1/1P2PP1P/2RR2K1 b - -; Rxc1; 0; R - R",
		"5rk1/5pp1/2r4p/5b2/2R5/6Q1/R1P1qPP1/5NK1 b - -; Bxc2; -100; P - B + R - Q + R",
		"1r3r1k/p4pp1/2p1p2p/qpQP3P/2P5/3R4/PP3PP1/1K1R4 b - -; Qxa2; -800; P - Q",
		"1r5k/p4pp1/2p1p2p/qpQP3P/2P2P2/1P1R4/P4rP1/1K1R4 b - -; Qxa2; 100; P",
		"r2q1rk1/1b2bppp/p2p1n2/1ppNp3/3nP3/P2P1N1P/BPP2PP1/R1BQR1K1 w - -; Nxe7; 0; B - N",
		"rnbqrbn1/pp3ppp/3p4/2p2k2/4p3/3B1K2/PPP2PPP/RNB1Q1NR w - -; Bxe4; 100; P",
		"rnb1k2r/p3p1pp/1p3p1b/7n/1N2N3/3P1PB1/PPP1P1PP/R2QKB1R w KQkq -; Nd6; -200; -N + P",
		"r1b1k2r/p4npp/1pp2p1b/7n/1N2N3/3P1PB1/PPP1P1PP/R2QKB1R w KQkq -; Nd6; 0; -N + N",
		"2r1k2r/pb4pp/5p1b/2KB3n/4N3/2NP1PB1/PPP1P1PP/R2Q3R w k -; Bc6; -300; -B",
		"2r1k2r/pb4pp/5p1b/2KB3n/1N2N3/3P1PB1/PPP1P1PP/R2Q3R w k -; Bc6; 0; -B + B",
		"2r1k3/pbr3pp/5p1b/2KB3n/1N2N3/3P1PB1/PPP1P1PP/R2Q3R w - -; Bc6; -300; -B + B - N",
		"5k2/p2P2pp/8/1pb5/1Nn1P1n1/6Q1/PPP4P/R3K1NR w KQ -; d8=Q; 800; (Q - P)",
		"r4k2/p2P2pp/8/1pb5/1Nn1P1n1/6Q1/PPP4P/R3K1NR w KQ -; d8=Q; -100; (Q - P) - Q",
		"5k2/p2P2pp/1b6/1p6/1Nn1P1n1/8/PPP4P/R2QK1NR w KQ -; d8=Q; 200; (Q - P) - Q + B",
		"4kbnr/p1P1pppp/b7/4q3/7n/8/PP1PPPPP/RNBQKBNR w KQk -; c8=Q; -100; (Q - P) - Q",
		"4kbnr/p1P1pppp/b7/4q3/7n/8/PPQPPPPP/RNB1KBNR w KQk -; c8=Q; 200; (Q - P) - Q + B",
		"4kbnr/p1P1pppp/b7/4q3/7n/8/PPQPPPPP/RNB1KBNR w KQk -; c8=Q; 200; (Q - P)",
		"4kbnr/p1P4p/b1q5/5pP1/4n3/5Q2/PP1PPP1P/RNB1KBNR w KQk f6; gxf6; 0; P - P",
		"4kbnr/p1P4p/b1q5/5pP1/4n3/5Q2/PP1PPP1P/RNB1KBNR w KQk f6; gxf6;	0; P - P",
		"4kbnr/p1P4p/b1q5/5pP1/4n2Q/8/PP1PPP1P/RNB1KBNR w KQk f6; gxf6; 0; P - P",
		"1n2kb1r/p1P4p/2qb4/5pP1/4n2Q/8/PP1PPP1P/RNB1KBNR w KQk -; cxb8=Q; 200; N + (Q - P) - Q",
		"rnbqk2r/pp3ppp/2p1pn2/3p4/3P4/N1P1BN2/PPB1PPPb/R2Q1RK1 w kq -; Kxh2; 300; B",
		"3N4/2K5/2n5/1k6/8/8/8/8 b - -; Nxd8; 0; N - N",
		"3N4/2P5/2n5/1k6/8/8/8/4K3 b - -; Nxd8; -800; N - (N + Q - P) ",
		"3n3r/2P5/8/1k6/8/8/3Q4/4K3 w - -; Qxd8; 300; N",
		"3n3r/2P5/8/1k6/8/8/3Q4/4K3 w - -; cxd8=Q; 700; (N + Q - P) - Q + R",
		"r2n3r/2P1P3/4N3/1k6/8/8/8/4K3 w - -; Nxd8; 300; N",
		"8/8/8/1k6/6b1/4N3/2p3K1/3n4 w - -; Nxd1; 0; N - N",
		"8/8/1k6/8/8/2N1N3/2p1p1K1/3n4 w - -; Nxd1; -800; N - (N + Q - P)",
		"8/8/1k6/8/8/2N1N3/4p1K1/3n4 w - -; Ncxd1; 100; N - (N + Q - P) + Q ",
		"r1bqk1nr/pppp1ppp/2n5/1B2p3/1b2P3/5N2/PPPP1PPP/RNBQK2R w KQkq -; O-O; 0; castling",
	};
	
	public static void testSEESuite()
	{
		boolean allPassed = true;
		for (int i = 0; i < SEEPositions.length; i++)
		{
			String position = SEEPositions[i];
			
			if (!testSEEPosition(position))
			{
				//System.out.println("SEE Test Failed on FEN: " + fen);
				allPassed = false;
			}
			//else System.out.println("SEE Test Succeeded on FEN: " + fen);
		}
		
		if (allPassed)
		{
			System.out.println("SEE Test Succeeded on all Positions");
		}
		else
		{
			System.out.println("SEE Test Failed!");
		}
	}
	
	public static boolean testSEEPosition(String position)
	{
		String [] fields = position.split(";");
		String fen = fields[0].trim();
		String algebraicMove = fields[1].trim();
		int expectedScore = Integer.parseInt(fields[2].trim());
		//String comment = fields[3].trim();
		
		Board board = new Board(fen);
		short [] moves = new short[MoveGenerator.MAXIMUM_LEGAL_MOVES];
		//MoveGenerator moveGen = new MoveGenerator(board, moves);
		MoveOrderer moveOrderer = new MoveOrderer(board, moves);
		
		boolean success = true;
		short move = Utils.getMoveFromAlgebraicNotation(board, algebraicMove);
		int score = testSEEMove(board, moveOrderer, move);
		
		if (score != expectedScore)
		{
			System.out.println("SEE Error at FEN(1):\t\t" + fen + " Move: " + algebraicMove + " Score Evaluated: " + score + " Expected Score: " + expectedScore);
			success = false;
		}
		
		board.flipBoard();
		move = flipMove(move);
		score = testSEEMove(board, moveOrderer, move);
		
		if (score != expectedScore)
		{
			System.out.println("SEE Error at FEN(2):\t\t" + fen + " Move: " + algebraicMove + " Score Evaluated: " + score + " Expected Score: " + expectedScore);
			success = false;
		}
		
		if (success) System.out.println("SEE Test Succeeded on FEN:\t" + fen + " Move: " + algebraicMove + " Score: " + expectedScore);
		return success;
	}
	
	/*
	public static void testSEESuite(String [] positions)
	{
		boolean allPassed = true;
		for (int i = 0; i < positions.length; i++)
		{
			String fen = positions[i].split(";")[0].trim();
			
			if (!testSEESymmetry(fen))
			{
				System.out.println("SEE Test Failed on FEN: " + fen);
				allPassed = false;
			}
			else System.out.println("SEE Test Succeeded on FEN: " + fen);
		}
		
		if (allPassed)
		{
			System.out.println("SEE Test Succeeded on all FENs");
		}
		else
		{
			System.out.println("SEE Test Failed!");
		}
	}
	
	public static boolean testSEESymmetry(String fen)
	{
		Board board = new Board(fen);
		short [] moves = new short[MoveGenerator.MAXIMUM_LEGAL_MOVES];
		MoveGenerator moveGen = new MoveGenerator(board, moves);
		MoveOrderer moveOrderer = new MoveOrderer(board, moves);
		
		// cannot use captures only because it includes f.e. queen captures without a capture
		int numMoves = moveGen.generateMoves(MoveGenerator.ALL_MOVES, 0);
		boolean passed = true;
		for (int i = 0; i < numMoves; i++)
		{
			short move = moves[i];
			// skip because non capture
			if (board.squares[MoveHelper.getTargetIndex(move)] == PieceHelper.NONE) continue;
			
			boolean success = testSEEMove(board, moveOrderer, move);
			if (!success) System.out.println("SEE test failed at FEN: " + fen + " with move: " + MoveHelper.toString(move));
			//else System.out.println("SEE test succeeded at FEN: " + fen + " with move: " + MoveHelper.toString(move));
			passed &= success;
		}
		
		return passed;
	}
	*/
	
	public static int testSEEMove(Board board, MoveOrderer orderer, short move)
	{
		//int start = MoveHelper.getStartIndex(move);
		//int target = MoveHelper.getTargetIndex(move);
		//byte piece = board.squares[start];
		//byte captured = board.squares[target];
		
		float gamePhase = orderer.getGamePhase(board);
		int mgSEE = orderer.SEE(board, move, gamePhase);
		//int mgSEE = orderer.SEE(board, move, gamePhase);
		//int mgSEE = orderer.SEE(board, move, gamePhase);
		//int mgSEE = orderer.SEE(board, start, target, piece, captured, 1);
		//int egSEE = orderer.SEE(board, start, target, piece, captured, 0);
		//int realSEE = orderer.SEE(board, start, target, piece, captured, gamePhase);
		
		//if (mgSEE != egSEE || mgSEE != realSEE) System.out.println("SEE scores not identical!");
		
		return mgSEE;
	}
	
	/*
	public static boolean checkResults(float gamePhase, int mgSEE, int egSEE, int realSEE, float flippedPhase, int flippedMgSEE, int flippedEgSEE, int flippedRealSEE)
	{
		boolean success = true;
		if (gamePhase != flippedPhase)
		{
			System.out.println("Game phases not equal: " + gamePhase + " " + flippedPhase);
			success = false;
		}
		
		success &= assertEqual(mgSEE, flippedMgSEE, "MG Phase SEE scores not equal");
		success &= assertEqual(egSEE, flippedEgSEE, "EG Phase SEE scores not equal");
		success &= assertEqual(realSEE, flippedRealSEE, "Regular Game Phase (" + gamePhase + ") SEE scores not equal");
		
		return success;
	}
	*/
	
	public static boolean assertEqual(int score, int otherScore, String error)
	{
		if (score != otherScore)
		{
			System.out.println(error + ": " + score + " " + otherScore);
			return false;
		}
		return true;
	}
	
	public static short flipMove(short move)
	{
		return (short) ((move ^ 56) ^ (56 << 6));
	}
}
