package me.Shadow;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import me.Shadow.pieces.Piece;

public class Engine
{
	ChessGui gui;
	Board boardGlobal;
	int numPositions;
	int numTranspositions;
	HashMap<Long, PositionEvaluation> transpositions;
	
	final NodeComparator<Move> nodeComparator = new NodeComparator<Move>(Comparator.comparing(Move::getEvalGuess).reversed());
	Node<Move> gameTree;

	boolean engineSearching;
	boolean playerMoveMade;
	boolean engineIsWhite;
	String originalFEN;
	boolean searchCancelled;
	
	int positiveInfinity;
	int negativeInfinity;
	
	long startTime;
	long moveGenTime, moveEvalTime, moveLegalityCheckTime;
	long movePieceTime, moveBackTime;
	long transpositionTime, positionEvaluationTime;

	Move engineMoveOld;
	ArrayList<Move> movesOld = new ArrayList<Move>();

	public Engine()
	{
		gui = new ChessGui();
		gui.createGui(this);
		boardGlobal = new Board();
		originalFEN = boardGlobal.boardInfo.getBoardFEN();
		// originalFEN = "8/3KP3/8/8/8/8/6k1/7q b - - 0 1"; //white king + pawn vs black king + queen
		// originalFEN = "3r4/3r4/3k4/8/8/3K4/8/8 w - - 0 1"; //white king vs black king + 2 rooks
		// originalFEN = "3r4/8/3k4/8/8/3K4/8/8 w - - 0 1"; //white king vs black king + rook
		// originalFEN = "8/7k/4p3/2p1P2p/2P1P2P/8/8/7K w - - 0 1"; // king and pawns vs king and pawns
		// originalFEN = "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - ";
		// originalFEN = "7k/8/8/8/8/8/8/7K w - - ";
		// originalFEN = "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1";
		// originalFEN = "r2q1rk1/pP1p2pp/Q4n2/bbp1p3/Np6/1B3NBn/pPPP1PPP/R3K2R b KQ - 0 1";

		boardGlobal.loadFEN(originalFEN);
		
		positiveInfinity = 1000000;
		negativeInfinity = -positiveInfinity;
		engineIsWhite = false;
		if (engineIsWhite == boardGlobal.boardInfo.isWhiteToMove())
			playerMoveMade = true;
		transpositions = new HashMap<Long, PositionEvaluation>();
		gameTree = new Node<Move>(null);
	}

	public static void main(String[] args)
	{
		Engine engine = new Engine();
		engine.runIt();
	}
	
	public void runIt()
	{
		testSuite();
		
		Timer timer = new Timer();
		timer.schedule(new TimerTask()
		{
			public void run()
			{
				if (playerMoveMade)
				{
					playerMoveMade = false;
					if (isGameOver(boardGlobal))
						timer.cancel();
					gui.message.setText("Engine is thinking");

					if (engineMoveOld != null)
					{
						gui.setColor(engineMoveOld.getTargetIndex(), 0);
						gui.setColor(engineMoveOld.getStartIndex(), 0);
					}

					boardGlobal.boardInfo.setBoardFEN(boardGlobal.createFEN(false));
					PositionEvaluation posEval = engineMove();
					
					

					gui.setColor(posEval.getMove().getStartIndex(), 1);
					gui.setColor(posEval.getMove().getTargetIndex(), 1);
					engineMoveOld = posEval.getMove();

					gui.updatePieces();
					gui.message.setText("Engine done searching!");
					if (isGameOver(boardGlobal))
						timer.cancel();
				}
			}
		}, 0, 250);
	}
	
	public PositionEvaluation search(int depth, int alpha, int beta, Board board, Node<Move> node)
	{
		if(searchCancelled)
		{
			return new PositionEvaluation(0, depth, false, null);
		}
		
		if (checkRepetition(board) || board.boardInfo.getHalfMoves() >= 100)
		{
			return new PositionEvaluation(0, depth, false, null);
		}
		
		long tempTime = System.currentTimeMillis();
		PositionEvaluation transposEval = transpositions.get(board.boardInfo.getZobristHash());
		transpositionTime += System.currentTimeMillis() - tempTime;
		if (transposEval != null && transposEval.getEvalDepth() >= depth)
		{
			numTranspositions++;
			// if the evaluation is a full search or if only lower bounded, if it beats beta, return that evaluation
			if (!transposEval.isLowerBound() || transposEval.getEvaluation() >= beta)
			{
				return transposEval;
			}
			// the evaluation was lower bounded and didn't beat beta but if it beats alpha, update alpha
			alpha = Math.max(alpha, transposEval.getEvaluation());
		}
		
		if (!board.hasLegalMoves(board.boardInfo.isWhiteToMove()))
		{
			if (board.boardInfo.getCheckPiece() != null)
				return new PositionEvaluation(negativeInfinity, depth, false, null);
			else
				return new PositionEvaluation(0, depth, false, null);
		}
		
		if (depth == 0)
		{
			PositionEvaluation posEval = searchCaptures(alpha, beta, board);
			return posEval;
		}
		
		if (node.children.size() == 0)
		{
			tempTime = System.currentTimeMillis();
			ArrayList<Move> newMoves = board.generateAllPseudoLegalMoves(board.boardInfo.isWhiteToMove(), false);
			moveGenTime += System.currentTimeMillis() - tempTime;
			
			tempTime = System.currentTimeMillis();
			guessMoveEvals(board, newMoves);
			moveEvalTime += System.currentTimeMillis() - tempTime;
			node.setChildren(newMoves);
		}
		
		boolean failedHigh = false;
		Iterator<Node<Move>> nodeIterator = node.children.iterator();
		
		while (nodeIterator.hasNext())
		{
			Node<Move> moveNode = nodeIterator.next();
			Move move = moveNode.data;
			
			tempTime = System.currentTimeMillis();
			boolean moveLegal = board.isMoveLegal(move);
			moveLegalityCheckTime += System.currentTimeMillis() - tempTime;
			
			if (!moveLegal)
			{
				nodeIterator.remove();
				continue;
			}
			
			BoardInfo boardInfoOld = new BoardInfo(board.boardInfo);
			ArrayList<PinnedPiece> pins = (ArrayList<PinnedPiece>) board.pinnedPieces.clone();
			
			tempTime = System.currentTimeMillis();
			Piece captured = board.movePiece(move);
			movePieceTime += System.currentTimeMillis() - tempTime;
			
			PositionEvaluation posEval = (search(depth - 1, -beta, -alpha, board, moveNode));
			int evaluation = -posEval.getEvaluation();
			
			tempTime = System.currentTimeMillis();
			board.moveBack(move, captured, boardInfoOld, pins);
			moveBackTime += System.currentTimeMillis() - tempTime;
			
			if (searchCancelled)
			{
				return new PositionEvaluation(0, depth, false, null);
			}
			
			if (evaluation > (positiveInfinity - depth) || evaluation < (negativeInfinity + depth))
			{
				evaluation += ((evaluation > 0) ? -1 : 1);
			}
			
			move.setEvalGuess(evaluation);
			alpha = Math.max(alpha, evaluation);
			
			if (evaluation >= beta)
			{
				// move was too good so opponent will avoid this branch
				// return beta
				failedHigh = true;
				break;
			}
		}
		
		node.sortChildren(nodeComparator);
		Move moveBest = node.children.get(0).data;
		transposEval = new PositionEvaluation(moveBest.getEvalGuess(), depth, failedHigh, moveBest);
		tempTime = System.currentTimeMillis();
		transpositions.put(board.boardInfo.getZobristHash(), transposEval);
		transpositionTime += System.currentTimeMillis() - tempTime;
		return transposEval;
	}
	
	public PositionEvaluation searchCaptures(int alpha, int beta, Board board)
	{
		long tempTime = System.currentTimeMillis();
		PositionEvaluation transposEval = transpositions.get(board.boardInfo.getZobristHash());
		transpositionTime += System.currentTimeMillis() - tempTime;
		if (transposEval != null)
		{
			numTranspositions++;
			if (!transposEval.isLowerBound() || transposEval.getEvaluation() >= beta)
			{
				return transposEval;
			}
			alpha = Math.max(alpha, transposEval.getEvaluation());
		}
		
		// captures arent forced so check eval before capturing something
		tempTime = System.currentTimeMillis();
		int evaluation = staticEvaluation(board);
		positionEvaluationTime += System.currentTimeMillis() - tempTime;
		if (evaluation >= beta)
		{
			transposEval = new PositionEvaluation(evaluation, 0, true, null);
			//transpositions.put(board.boardInfo.getZobristHash(), transposEval);
			return transposEval;
		}
		alpha = Math.max(alpha, evaluation);
		
		tempTime = System.currentTimeMillis();
		ArrayList<Move> captures = board.generateAllPseudoLegalMoves(board.boardInfo.isWhiteToMove(), true);
		moveGenTime += System.currentTimeMillis() - tempTime;
		
		tempTime = System.currentTimeMillis();
		guessMoveEvals(board, captures);
		moveEvalTime += System.currentTimeMillis() - tempTime;
		
		Iterator<Move> moveIterator = captures.iterator();
		
		while (moveIterator.hasNext())
		{
			Move move = moveIterator.next();
			
			tempTime = System.currentTimeMillis();
			boolean moveLegal = board.isMoveLegal(move);
			moveLegalityCheckTime += System.currentTimeMillis() - tempTime;
			
			if (!moveLegal)
			{
				moveIterator.remove();
				continue;
			}
			
			BoardInfo boardInfoOld = new BoardInfo(board.boardInfo);
			ArrayList<PinnedPiece> pins = (ArrayList<PinnedPiece>) board.pinnedPieces.clone();
			
			tempTime = System.currentTimeMillis();
			Piece captured = board.movePiece(move);
			movePieceTime += System.currentTimeMillis() - tempTime;
			
			evaluation = -(searchCaptures(-beta, -alpha, board).getEvaluation());
			
			tempTime = System.currentTimeMillis();
			board.moveBack(move, captured, boardInfoOld, pins);
			moveBackTime += System.currentTimeMillis() - tempTime;

			if (evaluation >= beta)
			{
				// move was too good so opponent will avoid this branch
				// return beta
				transposEval = new PositionEvaluation(evaluation, 0, true, null);
				//transpositions.put(board.boardInfo.getZobristHash(), transposEval);
				return transposEval;
			}
			alpha = Math.max(alpha, evaluation);
		}
		
		transposEval = new PositionEvaluation(alpha, 0, false, null);
		//transpositions.put(board.boardInfo.getZobristHash(), transposEval);
		return transposEval;
	}

	public void guessMoveEvals(Board board, ArrayList<Move> moves)
	{
		Piece piece = null;
		if (moves.size() > 0) piece = board.squares.get(moves.get(0).getStartIndex()).getPiece();
		boolean endgame = board.boardInfo.getWhiteMaterial() + board.boardInfo.getBlackMaterial() < 4000;
		
		for (Move move : moves)
		{
			int evalGuess = 0;
			if (piece.isWhite())
			{
				evalGuess -= (piece.getPieceSquareTable(endgame)[move.getStartIndex()]);
				evalGuess += (piece.getPieceSquareTable(endgame)[move.getTargetIndex()]);
			}
			else
			{
				evalGuess -= (piece.getPieceSquareTable(endgame)[((63 - move.getStartIndex()) / 8) * 8 + (move.getStartIndex() % 8)]);
				evalGuess += (piece.getPieceSquareTable(endgame)[((63 - move.getTargetIndex()) / 8) * 8 + (move.getTargetIndex() % 8)]);
			}
			if (board.squares.get(move.getTargetIndex()).hasPiece())
			{
				evalGuess += board.squares.get(move.getTargetIndex()).getPiece().getValue() - board.squares.get(move.getStartIndex()).getPiece().getValue();
			}
			if (move.getPromotedPiece() != 0)
			{
				if (move.getPromotedPiece() == 1) evalGuess += 900;
				else if (move.getPromotedPiece() == 2) evalGuess += 500;
				else if (move.getPromotedPiece() == 3) evalGuess += 330;
				else if (move.getPromotedPiece() == 4) evalGuess += 320;
			}
			move.setEvalGuess(evalGuess);
		}
		moves.sort(Comparator.comparing(Move::getEvalGuess).reversed());
	}

	public int staticEvaluation(Board board)
	{
		numPositions++;
		double evaluation = 0;
		evaluation = board.boardInfo.getWhiteMaterial() + board.boardInfo.getWhiteSquareBonus();
		evaluation -= (board.boardInfo.getBlackMaterial() + board.boardInfo.getBlackSquareBonus());
		
		evaluation += forceKingToCorner(board.pieces.get(28), board.pieces.get(4), 1 - (board.boardInfo.getBlackMaterial() / 2000));
		evaluation -= forceKingToCorner(board.pieces.get(4), board.pieces.get(28), 1 - (board.boardInfo.getWhiteMaterial() / 2000));
		
		evaluation *= board.boardInfo.isWhiteToMove() ? 1 : -1;
		
		return (int)evaluation;
	}

	public int forceKingToCorner(Piece friendlyKing, Piece enemyKing, float endgameWeight)
	{
		int friendlyKingIndex = friendlyKing.getSquare().getIndex();
		int enemyKingIndex = enemyKing.getSquare().getIndex();
		
		if (endgameWeight < 0)
			return 0;

		double evaluation = 0;

		int enemyRank = 8 - (enemyKingIndex / 8);
		int enemyFile = (enemyKingIndex % 8) + 1;
		int distToCenter = Math.max(4 - enemyRank, enemyRank - 5) + Math.max(4 - enemyFile, enemyFile - 5);
		evaluation += distToCenter;

		int friendlyRank = 8 - (friendlyKingIndex / 8);
		int friendlyFile = (friendlyKingIndex % 8) + 1;
		int distBetweenKings = Math.abs(friendlyRank - enemyRank) + Math.abs(friendlyFile - enemyFile);
		evaluation += (distBetweenKings);

		return (int) (evaluation * endgameWeight);
	}
	
	public PositionEvaluation engineMove()
	{
		engineSearching = true;
		searchCancelled = false;

		Board boardCopy = new Board(boardGlobal.boardInfo.getBoardFEN());
		
		numPositions = numTranspositions = 0;
		moveGenTime = movePieceTime = moveBackTime = moveLegalityCheckTime = moveEvalTime = 0;
		transpositionTime = positionEvaluationTime = 0;
		startTime = System.currentTimeMillis();
		
		PositionEvaluation posEval = null;
		int timeLimit = 5;
		
		Timer timer = new Timer();
		timer.schedule(new TimerTask()
		{
			@Override
			public void run()
			{
				searchCancelled = true;
			}
		}, timeLimit*1000);
		
		int depth = 1;
		PositionEvaluation temp = null;
		while (!searchCancelled)
		{
			posEval = temp; // set posEval to results of previous search
			temp = search(depth, negativeInfinity, positiveInfinity, boardCopy, gameTree);
			depth++;
			// eventually try and stop early if checkmate found while considering repetition draws
		}
		
		if (temp.getMove() != null)
		{
			posEval = temp; // utilize results of previous search
		}
		
		//updateTranspositionsList();
		
		engineSearching = false;
		
		System.out.print(boardGlobal.boardInfo.getMoveNum() + ". ");
		if(!engineIsWhite) System.out.print("...");
		System.out.println(posEval.getMove().toString());
		System.out.println("Evaluation time: " + (System.currentTimeMillis() - startTime) + "\t\t\tEvaluation for Engine: " + posEval.getEvaluation() + "\t\tEvaluation Depth: " + posEval.getEvalDepth());
		System.out.println("Number of positions: " + numPositions + "\t\tNumber of Transpositions: " + numTranspositions + "\t\tStored Transpositions: " + transpositions.size());
		System.out.println("Move Generation Time: " + moveGenTime + "\t\tMove Eval Time: " + moveEvalTime + "\t\t\tMove Legality Time: " + moveLegalityCheckTime);
		System.out.println("Move Piece Time: " + movePieceTime + "\t\t\tMove Back Time: " + moveBackTime + "\t\t\tEvaluation Time: " + positionEvaluationTime);
		System.out.println("Transposition Lookup Time: " + transpositionTime + "\n");
		
		boardGlobal.movePiece(posEval.getMove());
		
		Node<Move> node = gameTree.findChild(posEval.getMove());
		if (node != null) gameTree = gameTree.findChild(posEval.getMove());
		else gameTree = new Node<Move>(null);
		
		return posEval;
	}
	
	public boolean checkRepetition(Board board)
	{
		long zobristHash = board.boardInfo.getZobristHash();
		ArrayList<Long> posList = (ArrayList<Long>) board.boardInfo.getPositionList().clone();
		posList.remove(posList.size()-1);
		int index = posList.indexOf(zobristHash);
		if (index != -1)
		{
			posList.remove(zobristHash);
			index = posList.indexOf(zobristHash);
			if (index != -1)
			{
				return true;
			}
		}
		return false;
	}

	
	public boolean isGameOver(Board board)
	{
		if(checkRepetition(board))
		{
			gui.message.setText("Game Over! " + "Draw by Three Times Repetition");
			return true;
		}
		
		if(board.boardInfo.getHalfMoves() >= 100)
		{
			gui.message.setText("Game Over! " + "Draw by Fifty Move Rule!");
			return true;
		}
		
		if (!board.hasLegalMoves(board.boardInfo.isWhiteToMove()))
		{
			if (board.boardInfo.getCheckPiece() != null)
				gui.message.setText("Game Over! " + (board.boardInfo.isWhiteToMove() ? "Black" : "White") + " wins by checkmate!");
			else
				gui.message.setText("Game Over! " + "Draw by Stalemate!");
			return true;
		}
		
		return false;
	}
	
	public int countMoves(int depth, int originalDepth, Board board, boolean divide)
	{
		if (depth == 0)
			return 1;
		int num = 0;
		ArrayList<Move> moves = board.generateAllPseudoLegalMoves(board.boardInfo.isWhiteToMove(), false);
		
		Iterator<Move> moveIterator = moves.iterator();
		
		while (moveIterator.hasNext())
		{
			Move move = moveIterator.next();
			if (!board.isMoveLegal(move))
			{
				moveIterator.remove();
				continue;
			}
			else if (depth == 1)
			{
				num++;
				continue;
			}
			
			BoardInfo boardInfoOld = new BoardInfo(board.boardInfo);
			ArrayList<PinnedPiece> pins = (ArrayList<PinnedPiece>) board.pinnedPieces.clone();
			Piece captured = board.movePiece(move);
			int add = countMoves(depth - 1, originalDepth, board, divide);
			if (depth == originalDepth && divide) System.out.println(move + " " + add + " " + board.pinnedPieces.size());
			//if (depth == originalDepth-1 && divide) System.out.println("\t" + move + " " + add);
			num += add;
			board.moveBack(move, captured, boardInfoOld, pins);
		}		
		return num;
	}
	
	public void testSuite()
	{
		long time = System.currentTimeMillis();
		
		testPerft(5, new Board(), 4865609);
		testPerft(5, new Board("r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1"), 15833292);
		testPerft(4, new Board("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq -"), 4085603);
		testPerft(6, new Board("8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - "), 11030083);
		testPerft(4, new Board("rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8  "), 2103487);
		testPerft(4, new Board("r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10 "), 3894594);
		testPerft(5, new Board("n1n5/PPPk4/8/8/8/8/4Kppp/5N1N b - - 0 1"), 3605103);
				
		System.out.println("Time Taken: " + (System.currentTimeMillis()-time));
	}
	
	public void testPerft(int depth, Board board, int nodeCount)
	{
		int result = countMoves(depth, depth, board, false);
		System.out.println(nodeCount + " " + result);
		if (result != nodeCount)
		{
			System.out.println("Test Failed: " + Math.abs(result-nodeCount) + " nodes too " + (nodeCount > result ? "few" : "many"));
			result = countMoves(depth, depth, board, true);
			System.out.println(result);
		}
		System.out.println("\n");
	}
	
	class Node<T>
	{
		ArrayList<Node<T>> children;
		T data;
		
		public Node(T dataIn)
		{
			data = dataIn;
			children = new ArrayList<Node<T>>();
		}
		
		public Node(T dataIn, ArrayList<T> childrenIn)
		{
			data = dataIn;
			children = new ArrayList<Node<T>>(childrenIn.size());
			setChildren(childrenIn);
		}
		
		public void setChildren(ArrayList<T> childrenIn)
		{
			children = new ArrayList<Node<T>>(childrenIn.size());
			for (T child : childrenIn)
			{
				children.add(new Node<T>(child));
			}
		}
		
		public void sortChildren(NodeComparator<T> comparator)
		{
			children.sort(comparator);
		}
		
		public Node<T> findChild(T childData)
		{
			for (Node<T> node : children)
			{
				if (node.data.equals(childData)) return node;
			}
			return null;
		}
		
		public int size()
		{
			if (children.size() == 0) return 1;
			
			int size = 0;
			for (Node<T> node : children)
			{
				size += node.size();
			}
			return size;
		}
		
		public void printTree(int depth, int depthMax)
		{
			System.out.println(data);
			if (depth == depthMax) return;
			
			for (Node<T> node : children)
			{
				for (int i = 0; i < depth; i++) System.out.print("\t");
				node.printTree(depth+1, depthMax);
			}
		}
	}
	
	class NodeComparator<T> implements Comparator<Node<T>>
	{
		Comparator<T> wrappedComparator;
		
		public NodeComparator(Comparator<T> wrapped)
		{
			wrappedComparator = wrapped;
		}
		
		public int compare(Node<T> one, Node<T> two)
		{
			return wrappedComparator.compare(one.data, two.data);
		}
	}
}