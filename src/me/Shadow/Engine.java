package me.Shadow;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;

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
	long transpositionTime, positionEvaluationTime, checkRepetitionTime;
	long boardInfoTime;

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

		boardGlobal.loadFEN(originalFEN);
		
		positiveInfinity = 32767;
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
		// testSuite();
		
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
			return new PositionEvaluation(0, depth, false, false, null);
		}
		
		if (isDuplicatePosition(board) || board.boardInfo.getHalfMoves() >= 100)
		{
			return new PositionEvaluation(0, depth, false, false, null);
		}
		
		long tempTime = System.currentTimeMillis();
		PositionEvaluation transposEval = transpositions.get(board.boardInfo.getZobristHash());
		transpositionTime += System.currentTimeMillis() - tempTime;
		if (transposEval != null && transposEval.getEvalDepth() >= depth)
		{
			numTranspositions++;
			
			if (transposEval.isExact()) return transposEval;
			if (transposEval.isLowerBound() && transposEval.getEvaluation() >= beta) return transposEval;
			if (transposEval.isUpperBound() && transposEval.getEvaluation() <= alpha) return transposEval;
			
			// this should only be updated on a lower bound
			if (transposEval.isLowerBound()) alpha = Math.max(alpha, transposEval.getEvaluation());
		}
		
		tempTime = System.currentTimeMillis();
		if (!board.hasLegalMoves(board.boardInfo.isWhiteToMove()))
		{
			moveLegalityCheckTime += System.currentTimeMillis() - tempTime;
			if (board.boardInfo.getCheckPiece() != null)
				return new PositionEvaluation(negativeInfinity, depth, false, false, null);
			else
				return new PositionEvaluation(0, depth, false, false, null);
		}
		moveLegalityCheckTime += System.currentTimeMillis() - tempTime;
		
		if (depth == 0)
		{
			return new PositionEvaluation(searchCaptures(alpha, beta, board), 0, false, false, null);
		}
		
		if (node.children.size() == 0)
		{
			tempTime = System.currentTimeMillis();
			ArrayList<Move> newMoves = board.generateAllPseudoLegalMoves(board.boardInfo.isWhiteToMove(), false);
			moveGenTime += System.currentTimeMillis() - tempTime;
			
			tempTime = System.currentTimeMillis();
			guessMoveEvals(board, newMoves);
			
			node.setChildren(newMoves);
			moveEvalTime += System.currentTimeMillis() - tempTime;
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
			
			tempTime = System.currentTimeMillis();
			BoardInfo boardInfoOld = new BoardInfo(board.boardInfo);
			ArrayList<PinnedPiece> pins = (ArrayList<PinnedPiece>) board.pinnedPieces.clone();
			boardInfoTime += System.currentTimeMillis() - tempTime;
			
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
				// not actually getting any partial search results
				return new PositionEvaluation(0, depth, false, false, null);
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
				failedHigh = true;
				break;
			}
		}
				
		tempTime = System.currentTimeMillis();
		node.sortChildren(nodeComparator);
		moveEvalTime += System.currentTimeMillis() - tempTime;
		
		Move moveBest = node.children.get(0).data;
		transposEval = new PositionEvaluation(moveBest.getEvalGuess(), depth, failedHigh, (moveBest.getEvalGuess() < alpha), moveBest);
		tempTime = System.currentTimeMillis();
		transpositions.put(board.boardInfo.getZobristHash(), transposEval);
		transpositionTime += System.currentTimeMillis() - tempTime;
		return transposEval;
	}
	
	public int searchCaptures(int alpha, int beta, Board board)
	{
		if (searchCancelled) return 0;
		
		// captures arent forced so check eval before capturing something
		int evaluation = staticEvaluation(board);
		
		if (evaluation >= beta)
		{
			return evaluation;
		}
		alpha = Math.max(alpha, evaluation);
		
		long tempTime = System.currentTimeMillis();
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
			
			tempTime = System.currentTimeMillis();
			BoardInfo boardInfoOld = new BoardInfo(board.boardInfo);
			ArrayList<PinnedPiece> pins = (ArrayList<PinnedPiece>) board.pinnedPieces.clone();
			boardInfoTime += System.currentTimeMillis() - tempTime;
			
			tempTime = System.currentTimeMillis();
			Piece captured = board.movePiece(move);
			movePieceTime += System.currentTimeMillis() - tempTime;
			
			evaluation = -(searchCaptures(-beta, -alpha, board));
			
			tempTime = System.currentTimeMillis();
			board.moveBack(move, captured, boardInfoOld, pins);
			moveBackTime += System.currentTimeMillis() - tempTime;

			if (evaluation >= beta)
			{
				// move was too good so opponent will avoid this branch
				return evaluation;
			}
			alpha = Math.max(alpha, evaluation);
		}
		
		return alpha;
	}

	public void guessMoveEvals(Board board, ArrayList<Move> moves)
	{
		boolean endgame = board.boardInfo.getWhiteMaterial() + board.boardInfo.getBlackMaterial() < 4000;
		moves.forEach(move -> guessMoveEval(board, move, endgame));
		moves.sort(Comparator.comparing(Move::getEvalGuess).reversed());
	}
	
	public void guessMoveEval(Board board, Move move, boolean endgame)
	{		
		Piece piece = board.squares.get(move.getStartIndex()).getPiece();
		int evalGuess = 0;
		
		evalGuess -= piece.getPieceSquareValue(move.getStartIndex(), endgame);
		evalGuess -= piece.getPieceSquareValue(move.getTargetIndex(), endgame);
		
		if (board.squares.get(move.getTargetIndex()).hasPiece())
		{
			if (piece.getPieceType() == Piece.KING)
			{
				evalGuess += (100.0 * board.squares.get(move.getTargetIndex()).getPiece().getValue()) / 100; // TODO: this is an arbitary value
			}
			else
				evalGuess += (100.0 * board.squares.get(move.getTargetIndex()).getPiece().getValue()) / piece.getValue();
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

	public int staticEvaluation(Board board)
	{
		long tempTime = System.currentTimeMillis();
		numPositions++;
		double evaluation = 0;
		evaluation = board.boardInfo.getWhiteMaterial() + board.boardInfo.getWhiteSquareBonus();
		evaluation -= (board.boardInfo.getBlackMaterial() + board.boardInfo.getBlackSquareBonus());
		
		evaluation += forceKingToCorner(board.whiteKing, board.blackKing, 1 - (board.boardInfo.getBlackMaterial() / 2000));
		evaluation -= forceKingToCorner(board.blackKing, board.whiteKing, 1 - (board.boardInfo.getWhiteMaterial() / 2000));
		
		evaluation *= board.boardInfo.isWhiteToMove() ? 1 : -1;
		
		positionEvaluationTime += System.currentTimeMillis() - tempTime;
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
		transpositionTime = positionEvaluationTime = boardInfoTime = 0;
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
		
		int depth = 0;
		while (!searchCancelled)
		{
			depth++;
			PositionEvaluation result = search(depth, negativeInfinity, positiveInfinity, boardCopy, gameTree);
			
			if (result.getMove() != null)
			{
				posEval = result;
				
				if (posEval.getEvaluation() >= (positiveInfinity - depth))
				{
					System.out.println("Stopping search early because forced checkmate for engine found");
					break;
				}
				else if (posEval.getEvaluation() <= (negativeInfinity + depth))
				{
					System.out.println("Stopping search early because forced checkmate against engine found");
					break;
				}
			}
			
			// TODO: eventually try and stop early if checkmate found while considering repetition draws
		}
		
		engineSearching = false;
		
		System.out.print(boardGlobal.boardInfo.getMoveNum() + ". ");
		if(!engineIsWhite) System.out.print("...");
		System.out.println(posEval.getMove().toString());
		System.out.println("Evaluation time: " + (System.currentTimeMillis() - startTime) + "\t\t\tEvaluation for Engine: " + posEval.getEvaluation() + "\t\tEvaluation Depth: " + posEval.getEvalDepth());
		System.out.println("Number of positions: " + numPositions + "\t\tNumber of Transpositions: " + numTranspositions + "\t\tStored Transpositions: " + transpositions.size());
		System.out.println("Move Generation Time: " + moveGenTime + "\t\tMove Eval Time: " + moveEvalTime + "\t\t\tMove Legality Time: " + moveLegalityCheckTime);
		System.out.println("Move Piece Time: " + movePieceTime + "\t\t\tMove Back Time: " + moveBackTime + "\t\t\tEvaluation Time: " + positionEvaluationTime);
		System.out.println("Transposition Lookup Time: " + transpositionTime + "\t\t\tBoard Info Time: " + boardInfoTime + "\t\tCheck Repetition Time: " + checkRepetitionTime + "\n");
		long sum = moveGenTime + moveEvalTime + moveLegalityCheckTime + movePieceTime + moveBackTime + positionEvaluationTime + transpositionTime + boardInfoTime + checkRepetitionTime;
		System.out.println("Tracked Time: " + sum);
		
		boardGlobal.movePiece(posEval.getMove());
		
		Node<Move> node = gameTree.findChild(posEval.getMove());
		if (node != null) gameTree = gameTree.findChild(posEval.getMove());
		else gameTree = new Node<Move>(null);
		
		return posEval;
	}
	
	public boolean isDuplicatePosition(Board board)
	{
		long temp = System.currentTimeMillis();
		long zobristHash = board.boardInfo.getZobristHash();
		ArrayList<Long> positions = board.boardInfo.getPositionList();
		checkRepetitionTime += System.currentTimeMillis() - temp;
		return (positions.indexOf(zobristHash) != (positions.size()-1));
	}
	
	public boolean isThreeFoldRepetition(Board board)
	{
		long zobristHash = board.boardInfo.getZobristHash();
		ArrayList<Long> positions = board.boardInfo.getPositionList();
		int duplicateCount = 0;
		for (long position : positions)
		{
			if (position == zobristHash) duplicateCount++;
		}
		
		return duplicateCount >= 3;
	}
	
	public boolean isGameOver(Board board)
	{
		if(isThreeFoldRepetition(board))
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
		ArrayList<Move> moves = board.generateAllLegalMoves(board.boardInfo.isWhiteToMove(), false);
		
		if (depth == 1) return moves.size();
		
		for (Move move : moves)
		{
			BoardInfo boardInfoOld = new BoardInfo(board.boardInfo);
			ArrayList<PinnedPiece> pins = (ArrayList<PinnedPiece>) board.pinnedPieces.clone();
			Piece captured = board.movePiece(move);
			int add = countMoves(depth - 1, originalDepth, board, divide);
			
			if (depth == originalDepth && divide) System.out.println(move + " " + add);
			
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
		//LinkedList<Node<T>> children;
		T data;
		
		public Node(T dataIn)
		{
			data = dataIn;
			children = new ArrayList<Node<T>>();
			//children = new LinkedList<Node<T>>();
		}
		
		public Node(T dataIn, ArrayList<T> childrenIn)
		{
			data = dataIn;
			children = new ArrayList<Node<T>>(childrenIn.size());
			//children = new LinkedList<Node<T>>();
			setChildren(childrenIn);
		}
		
		public void setChildren(ArrayList<T> childrenIn)
		{
			children = new ArrayList<Node<T>>(childrenIn.size());
			//children = new LinkedList<Node<T>>();
			childrenIn.forEach(child -> children.add(new Node<T>(child)));
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