package me.Shadow;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

public class MoveSearcher
{
	Move bestMove;
	Move bestMoveCurrentIteration;
	int bestEval;
	int bestEvalCurrentIteration;
	boolean oneMoveSearched;
	int maxDepthReached;
	
	boolean searchCancelled;
	Board board;
	TranspositionTable transpositionTable;
	int timeLimit;
	
	int positiveInfinity = 1_000_000;
	int negativeInfinity = -positiveInfinity;
	
	long startTime;
	long moveGenTime, moveEvalTime, moveLegalityCheckTime;
	long movePieceTime, moveBackTime;
	long transpositionTime, positionEvaluationTime, checkRepetitionTime;
	long boardInfoTime;
	
	int numPositions;
	int numTranspositions;
	
	
	public MoveSearcher(Board board, int timeLimitMS, TranspositionTable tt)
	{
		this.board = board;
		timeLimit = timeLimitMS;
		transpositionTable = tt;
	}
	
	public Move startSearch()
	{
		Timer timer = new Timer();
		timer.schedule(new TimerTask()
		{
			@Override
			public void run()
			{
				searchCancelled = true;
			}
		}, timeLimit);
		
		
		bestMove = bestMoveCurrentIteration = Move.NULL_MOVE;
		bestEval = bestEvalCurrentIteration = negativeInfinity;
		startTime = System.currentTimeMillis();
		int depth = 0;
		int depthMax = 512;
		while (!searchCancelled && depth < depthMax)
		{
			depth++;
			search(depth, 0, negativeInfinity, positiveInfinity);
			
			if (oneMoveSearched)
			{
				bestMove = bestMoveCurrentIteration;
				bestEval = bestEvalCurrentIteration;
				
				if (bestEvalCurrentIteration >= (positiveInfinity - depth))
				{
					System.out.println("Forced mate for engine found");
					break;
				}
				else if (bestEvalCurrentIteration <= (negativeInfinity + depth))
				{
					System.out.println("Forced mate against engine found");
					break;
				}
			}
						
			bestMoveCurrentIteration = Move.NULL_MOVE;
			bestEvalCurrentIteration = negativeInfinity;
			oneMoveSearched = false;
		}
		maxDepthReached = depth;
				
		return bestMove;
	}
	
	public int search(int depth, int plyFromRoot, int alpha, int beta)
	{
		if (searchCancelled) return 0;
		
		if ((isDuplicatePosition() && plyFromRoot > 0) || board.boardInfo.getHalfMoves() >= 100)
		{
			return 0;
		}
		
		long tempTime = System.currentTimeMillis();
		int transposEval = transpositionTable.lookupEvaluation(board.boardInfo.getZobristHash(), depth, alpha, beta);
		transpositionTime += System.currentTimeMillis() - tempTime;
		if (transposEval != TranspositionTable.LOOKUP_FAILED)
		{
			numTranspositions++;
			
			if (plyFromRoot == 0)
			{
				Move move = transpositionTable.lookupMove(board.boardInfo.getZobristHash());
				
				long updatedHash = board.updateZobristHash(move);
				board.boardInfo.getPositionList().add(updatedHash);
				
				boolean isDuplicate = isDuplicatePosition();
				
				board.boardInfo.getPositionList().remove(board.boardInfo.getPositionList().size()-1);
				
				if (!isDuplicate)
				{
					bestMoveCurrentIteration = move;
					bestEvalCurrentIteration = transposEval;
					oneMoveSearched = true;
					return transposEval;
				}
			}
			else return transposEval;
		}
		
		tempTime = System.currentTimeMillis();
		boolean noLegalMoves = !board.hasLegalMoves(board.boardInfo.isWhiteToMove());
		moveLegalityCheckTime += System.currentTimeMillis() - tempTime;
		
		if (noLegalMoves)
		{
			if (board.boardInfo.getCheckPiece() != null)
				return negativeInfinity;
			else
				return 0;
		}
		
		if (depth == 0)
		{
			return searchCaptures(alpha, beta);
		}
		
		tempTime = System.currentTimeMillis();
		Move[] moves = board.generateAllPseudoLegalMoves(board.boardInfo.isWhiteToMove(), false);
		moveGenTime += System.currentTimeMillis() - tempTime;
		
		tempTime = System.currentTimeMillis();
		Move firstMove = (plyFromRoot == 0 ? bestMove : transpositionTable.lookupMove(board.boardInfo.getZobristHash()));
		MoveOrderer.guessMoveEvals(board, moves, firstMove);
		moveEvalTime += System.currentTimeMillis() - tempTime;
		
		int bound = TranspositionTable.UPPER_BOUND;
		Move bestMoveInPosition = Move.NULL_MOVE;
		for (Move move : moves)
		{
			tempTime = System.currentTimeMillis();
			boolean moveLegal = board.isMoveLegal(move);
			moveLegalityCheckTime += System.currentTimeMillis() - tempTime;
			
			if (!moveLegal)
			{
				continue;
			}
			
			tempTime = System.currentTimeMillis();
			BoardInfo boardInfoOld = new BoardInfo(board.boardInfo);
			ArrayList<PinnedPiece> pins = (ArrayList<PinnedPiece>) board.pinnedPieces.clone();
			boardInfoTime += System.currentTimeMillis() - tempTime;
			
			tempTime = System.currentTimeMillis();
			Piece captured = board.movePiece(move);
			movePieceTime += System.currentTimeMillis() - tempTime;
			
			int evaluation = -(search(depth - 1, plyFromRoot + 1, -beta, -alpha));
			
			tempTime = System.currentTimeMillis();
			board.moveBack(move, captured, boardInfoOld, pins);
			moveBackTime += System.currentTimeMillis() - tempTime;
			
			if (searchCancelled)
			{
				return 0;
			}
			
			if (evaluation > (positiveInfinity - depth) || evaluation < (negativeInfinity + depth))
			{
				evaluation += ((evaluation > 0) ? -1 : 1);
			}
			
			if (evaluation > alpha)
			{
				bound = TranspositionTable.EXACT_BOUND;
				alpha = evaluation;
				bestMoveInPosition = move;
				
				if (plyFromRoot == 0)
				{
					bestMoveCurrentIteration = move;
					bestEvalCurrentIteration = alpha;
					oneMoveSearched = true;
				}
			}
			
			if (evaluation >= beta)
			{
				// move was too good so opponent will avoid this branch
				bound = TranspositionTable.LOWER_BOUND;
				
				tempTime = System.currentTimeMillis();
				transpositionTable.storeEvaluation(board.boardInfo.getZobristHash(), beta, depth, bound, move);
				transpositionTime += System.currentTimeMillis() - tempTime;
				
				return beta;
			}
		}
		
		tempTime = System.currentTimeMillis();
		transpositionTable.storeEvaluation(board.boardInfo.getZobristHash(), alpha, depth, bound, bestMoveInPosition);
		transpositionTime += System.currentTimeMillis() - tempTime;
		
		return alpha;
	}
	
	public int searchCaptures(int alpha, int beta)
	{
		if (searchCancelled) return 0;
		
		// captures arent forced so check eval before capturing something
		int evaluation = staticEvaluation();
		
		if (evaluation >= beta)
		{
			return evaluation;
		}
		alpha = Math.max(alpha, evaluation);
		
		long tempTime = System.currentTimeMillis();
		Move[] captures = board.generateAllPseudoLegalMoves(board.boardInfo.isWhiteToMove(), true);
		moveGenTime += System.currentTimeMillis() - tempTime;
		
		tempTime = System.currentTimeMillis();
		MoveOrderer.guessMoveEvals(board, captures, Move.NULL_MOVE);
		moveEvalTime += System.currentTimeMillis() - tempTime;
				
		for (Move move : captures)
		{			
			tempTime = System.currentTimeMillis();
			boolean moveLegal = board.isMoveLegal(move);
			moveLegalityCheckTime += System.currentTimeMillis() - tempTime;
			
			if (!moveLegal)
			{
				continue;
			}
			
			tempTime = System.currentTimeMillis();
			BoardInfo boardInfoOld = new BoardInfo(board.boardInfo);
			ArrayList<PinnedPiece> pins = (ArrayList<PinnedPiece>) board.pinnedPieces.clone();
			boardInfoTime += System.currentTimeMillis() - tempTime;
			
			tempTime = System.currentTimeMillis();
			Piece captured = board.movePiece(move);
			movePieceTime += System.currentTimeMillis() - tempTime;
			
			evaluation = -(searchCaptures(-beta, -alpha));
			
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
	
	public boolean isDuplicatePosition()
	{
		long temp = System.currentTimeMillis();
		long zobristHash = board.boardInfo.getZobristHash();
		ArrayList<Long> positions = board.boardInfo.getPositionList();
		checkRepetitionTime += System.currentTimeMillis() - temp;
		return (positions.indexOf(zobristHash) != (positions.size()-1));
	}
	
	public int staticEvaluation()
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
	
	public void printSearchStats()
	{
		System.out.println("Evaluation time: " + (System.currentTimeMillis() - startTime) + "\t\t\tEvaluation for Engine: " + bestEval + "\t\tEvaluation Depth: " + maxDepthReached);
		System.out.println("Number of positions: " + numPositions + "\t\tNumber of Transpositions: " + numTranspositions + "\t\tStored Transpositions: " + transpositionTable.numStored);
		System.out.println("Move Generation Time: " + moveGenTime + "\t\tMove Eval Time: " + moveEvalTime + "\t\t\tMove Legality Time: " + moveLegalityCheckTime);
		System.out.println("Move Piece Time: " + movePieceTime + "\t\t\tMove Back Time: " + moveBackTime + "\t\t\tEvaluation Time: " + positionEvaluationTime);
		System.out.println("Transposition Lookup Time: " + transpositionTime + "\t\t\tBoard Info Time: " + boardInfoTime + "\t\tCheck Repetition Time: " + checkRepetitionTime + "\n");
		long sum = moveGenTime + moveEvalTime + moveLegalityCheckTime + movePieceTime + moveBackTime + positionEvaluationTime + transpositionTime + boardInfoTime + checkRepetitionTime;
		System.out.println("Tracked Time: " + sum);
	}
}
