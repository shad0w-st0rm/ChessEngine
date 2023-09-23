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
	MoveGenerator moveGen;
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
	
	
	public MoveSearcher(Board board, MoveGenerator moveGen, int timeLimitMS, TranspositionTable tt)
	{
		this.board = board;
		this.moveGen = moveGen;
		timeLimit = timeLimitMS;
		transpositionTable = tt;
	}
	
	public Move startSearch()
	{
		searchCancelled = false;
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
		oneMoveSearched = false;
		startTime = System.currentTimeMillis();
		int depth = maxDepthReached = 0;
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
		
		if (depth == 0)
		{
			return searchCaptures(alpha, beta);
		}
		
		tempTime = System.currentTimeMillis();
		Move[] moves = moveGen.generateMoves(false);
		moveGenTime += System.currentTimeMillis() - tempTime;
		
		if (moves.length == 0)
		{
			if (moveGen.inCheck)
				return negativeInfinity;
			else
				return 0;
		}
		
		tempTime = System.currentTimeMillis();
		Move firstMove = (plyFromRoot == 0 ? bestMove : transpositionTable.lookupMove(board.boardInfo.getZobristHash()));
		MoveOrderer.guessMoveEvals(board, moves, firstMove);
		moveEvalTime += System.currentTimeMillis() - tempTime;
		
		int bound = TranspositionTable.UPPER_BOUND;
		Move bestMoveInPosition = Move.NULL_MOVE;
		for (Move move : moves)
		{
			tempTime = System.currentTimeMillis();
			BoardInfo boardInfoOld = new BoardInfo(board.boardInfo);
			boardInfoTime += System.currentTimeMillis() - tempTime;
			
			tempTime = System.currentTimeMillis();
			int captured = board.movePiece(move);
			movePieceTime += System.currentTimeMillis() - tempTime;
			
			int evaluation = -(search(depth - 1, plyFromRoot + 1, -beta, -alpha));
			
			tempTime = System.currentTimeMillis();
			board.moveBack(move, captured, boardInfoOld);
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
		Move[] captures = moveGen.generateMoves(true);
		moveGenTime += System.currentTimeMillis() - tempTime;
		
		tempTime = System.currentTimeMillis();
		MoveOrderer.guessMoveEvals(board, captures, Move.NULL_MOVE);
		moveEvalTime += System.currentTimeMillis() - tempTime;
				
		for (Move move : captures)
		{
			tempTime = System.currentTimeMillis();
			BoardInfo boardInfoOld = new BoardInfo(board.boardInfo);
			boardInfoTime += System.currentTimeMillis() - tempTime;
			
			tempTime = System.currentTimeMillis();
			int captured = board.movePiece(move);
			movePieceTime += System.currentTimeMillis() - tempTime;
			
			evaluation = -(searchCaptures(-beta, -alpha));
			
			tempTime = System.currentTimeMillis();
			board.moveBack(move, captured, boardInfoOld);
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
		
		int whiteKingIndex = Bitboards.getLSB(board.bitBoards.pieceBoards[Piece.WHITE_KING]);
		int blackKingIndex = Bitboards.getLSB(board.bitBoards.pieceBoards[Piece.BLACK_KING]);
		evaluation += forceKingToCorner(whiteKingIndex, blackKingIndex, 1 - (board.boardInfo.getBlackMaterial() / 2000));
		evaluation -= forceKingToCorner(blackKingIndex, whiteKingIndex, 1 - (board.boardInfo.getWhiteMaterial() / 2000));
		
		evaluation *= board.boardInfo.isWhiteToMove() ? 1 : -1;
		
		positionEvaluationTime += System.currentTimeMillis() - tempTime;
		return (int)evaluation;
	}

	public int forceKingToCorner(int friendlyKingIndex, int enemyKingIndex, float endgameWeight)
	{
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
