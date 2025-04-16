package me.Shadow.Engine;

public class MoveSearcher
{
	static final int MAX_DEPTH = 64;

	static final int positiveInfinity = 0x3FFF;
	static final int negativeInfinity = -positiveInfinity;

	short bestMove;

	boolean searchCancelled;

	private Board board;
	private MoveGenerator moveGen;
	private MoveOrderer moveOrderer;
	private TranspositionTable transpositionTable;
	private short[] moves;

	//int nodes = 0;
	//int qMoves = 0;
	//int captures = 0;

	public MoveSearcher(Board board)
	{
		this.board = board;
		moves = new short[1024];
		moveGen = new MoveGenerator(board, moves);
		moveOrderer = new MoveOrderer(board, moves);
		transpositionTable = new TranspositionTable();
	}

	public short startSearch()
	{
		searchCancelled = false;

		// clearSearchStats();
		bestMove = MoveHelper.NULL_MOVE;
		moveOrderer.clearHistoryHeuristic();
		moveOrderer.clearKillers();
		int depth = 0;
		int eval = 0;

		do
		{
			//nodes = qMoves = captures = 0;
			//nodes++;
			depth++;
			
			eval = rootSearch(depth, negativeInfinity, positiveInfinity);

			if (eval >= (positiveInfinity - depth))
			{
				// System.out.println("Forced mate for engine found");
				break;
			}
			else if (eval <= (negativeInfinity + depth))
			{
				// System.out.println("Forced mate against engine found");
				break;
			}
		}
		while (!searchCancelled && depth <= 6);

		/*
		System.out.println("Evaluation: " + eval);
		System.out.println("Depth searched: " + (depth - 1));
		System.out.println("Nodes visited: " + nodes);
		System.out.println("Captures visited: " + captures);
		System.out.println("qMoves visited: " + qMoves);
		System.out.println("Average num captures by node: " + ((float) (captures) /
		nodes)); System.out.println("Average num qmoves by node: " + ((float)
		(qMoves) / nodes));
		*/
		
		return bestMove;
	}

	public int rootSearch(final int depth, int alpha, int beta)
	{
		int numMoves = moveGen.generateMoves(MoveGenerator.ALL_MOVES, 0);

		// checkmate/stalemate
		if (numMoves == 0)
			return moveGen.inCheck() ? negativeInfinity : 0;

		moveOrderer.guessMoveEvals(bestMove, false, 0, 0, numMoves);

		int bound = TranspositionTable.UPPER_BOUND;
		long zobristHash = board.zobristHash;
		long pawnsHash = board.pawnsHash;
		short[] boardInfoOld = board.packBoardInfo();
		for (int i = 0; i < numMoves; i++)
		{
			moveOrderer.singleSelectionSort(i, numMoves);

			final short move = moves[i];

			int evaluation = searchMove(move, alpha, beta, depth, 0, i, zobristHash, pawnsHash, boardInfoOld, numMoves);

			if (searchCancelled)
			{
				if (bestMove == MoveHelper.NULL_MOVE)
					bestMove = move;
				return evaluation;
			}

			if (evaluation > alpha)
			{
				bound = TranspositionTable.EXACT_BOUND;
				alpha = evaluation;

				bestMove = move;
			}

			if (evaluation >= beta)
			{
				// move was too good so opponent will avoid this branch
				transpositionTable.storeEvaluation(board.zobristHash, beta, depth, TranspositionTable.LOWER_BOUND,
						move);
				return beta;
			}
		}

		transpositionTable.storeEvaluation(board.zobristHash, alpha, depth, bound, bestMove);

		return alpha;
	}

	public int search(final int depth, final int plyFromRoot, int alpha, int beta, int moveIndex)
	{
		if (searchCancelled)
			return 0;

		if (isDuplicatePosition() || board.halfMoves >= 100)
		{
			return 0;
		}

		long zobristHash = board.zobristHash;
		short bestMoveInPosition = MoveHelper.NULL_MOVE;
		short[] transposition = transpositionTable.getEntry(zobristHash);

		if (transposition != null)
		{
			bestMoveInPosition = transposition[TranspositionTable.MOVE_INDEX];
			if (transposition[TranspositionTable.DEPTH_INDEX] >= depth)
			{
				
				short bound = transposition[TranspositionTable.BOUND_INDEX];
				short eval = transposition[TranspositionTable.EVAL_INDEX];
				if (bound == TranspositionTable.EXACT_BOUND)
				{
					return transposition[TranspositionTable.EVAL_INDEX];
				}
				else if (bound == TranspositionTable.LOWER_BOUND)
				{
					if (eval >= beta)
					{
						return beta;
					}
					else
						alpha = Math.max(alpha, eval);
				}
				else if (bound == TranspositionTable.UPPER_BOUND)
				{
					if (eval <= alpha)
					{
						return alpha;
					}
					else
						beta = Math.max(beta, eval);
				}
			}
		}

		if (depth == 0)
			return searchCaptures(alpha, beta, moveIndex);
		
		//if (depth > 2) nodes++;

		int bound = TranspositionTable.UPPER_BOUND;
		boolean searchedFirst = false;
		long pawnsHash = board.pawnsHash;
		short[] boardInfoOld = board.packBoardInfo();

		if (bestMoveInPosition != MoveHelper.NULL_MOVE)
		{
			searchedFirst = true;

			int evaluation = searchMove(bestMoveInPosition, alpha, beta, depth, plyFromRoot, 0, zobristHash, pawnsHash,
					boardInfoOld, moveIndex);

			if (searchCancelled)
				return evaluation;

			if (evaluation > alpha)
			{
				bound = TranspositionTable.EXACT_BOUND;
				alpha = evaluation;
			}

			if (evaluation >= beta)
			{
				// move was too good so opponent will avoid this branch
				transpositionTable.storeEvaluation(zobristHash, beta, depth, TranspositionTable.LOWER_BOUND,
						bestMoveInPosition);

				return beta;
			}
		}

		int numMoves = moveGen.generateMoves(MoveGenerator.ALL_MOVES, moveIndex);

		// checkmate/stalemate
		if (numMoves == 0)
			return moveGen.inCheck() ? negativeInfinity : 0;

		// ensure the potentially first searched move gets boosted to top with highest
		// score
		moveOrderer.guessMoveEvals(bestMoveInPosition, false, plyFromRoot, moveIndex, numMoves);
		// push the first searched move to the top (guaranteed to be highest score)
		if (searchedFirst)
			moveOrderer.singleSelectionSort(moveIndex, moveIndex + numMoves);

		// ignore first move if we already searched it
		for (int i = moveIndex + (searchedFirst ? 1 : 0); i < (moveIndex + numMoves); i++)
		{
			moveOrderer.singleSelectionSort(i, moveIndex + numMoves);

			final short move = moves[i];

			int evaluation = searchMove(move, alpha, beta, depth, plyFromRoot, i - moveIndex, zobristHash, pawnsHash,
					boardInfoOld, moveIndex + numMoves);

			if (searchCancelled)
				return evaluation;

			if (evaluation > alpha)
			{
				bound = TranspositionTable.EXACT_BOUND;
				alpha = evaluation;
				bestMoveInPosition = move;
			}

			if (evaluation >= beta)
			{
				// move was too good so opponent will avoid this branch
				transpositionTable.storeEvaluation(zobristHash, beta, depth, TranspositionTable.LOWER_BOUND, move);
				return beta;
			}
		}

		transpositionTable.storeEvaluation(zobristHash, alpha, depth, bound, bestMoveInPosition);

		return alpha;
	}

	public int searchMove(short move, int alpha, int beta, int depth, int plyFromRoot, int moveNum, long zobristHash,
			long pawnsHash, short[] boardInfoOld, int moveIndex)
	{
		final byte captured = board.movePiece(move);

		//if (depth > 2) qMoves++;
		
		int searchDepth = calculateSearchDepth(move, captured, depth, moveNum);

		int evaluation = -(search(searchDepth, plyFromRoot + 1, -beta, -alpha, moveIndex));

		board.moveBack(move, captured, zobristHash, pawnsHash, boardInfoOld);

		if (evaluation > (positiveInfinity - depth) || evaluation < (negativeInfinity + depth))
			evaluation += ((evaluation > 0) ? -1 : 1);

		if (searchCancelled)
			return 0;

		if (evaluation >= beta)
		{
			if (captured == PieceHelper.NONE) // ignore captures for killer moves
			{
				if (plyFromRoot < MoveOrderer.maxKillerDepth)
				{
					moveOrderer.addKiller(move, plyFromRoot);
				}

				// keep start/target square and add color to move for index
				int index = (move & 0xFFF) | (board.colorToMove << 12);
				moveOrderer.historyHeuristic[index] += depth * depth;
			}
		}

		return evaluation;
	}

	public int calculateSearchDepth(short move, int captured, int depth, int moveNum)
	{
		depth--;
		if (board.inCheck())
			depth++;

		// give bonus to a pawn moving to one square from promotion
		// maybe change this just to a promotion move itself
		
		int target = MoveHelper.getTargetIndex(move);
		if (((board.squares[target] & PieceHelper.TYPE_MASK) == PieceHelper.PAWN)
				&& (Utils.getSquareRank(target) == 2 || Utils.getSquareRank(target) == 7))
		{
			return depth + 1;
		}
		

		// search reduction, ensure depth doesn't go negative
		return Math.max(((moveNum >= 2 && captured == PieceHelper.NONE && depth >= 3) ? (depth - 1) : depth), 0);
	}

	public int searchCaptures(int alpha, int beta, int moveIndex)
	{
		if (searchCancelled)
			return 0;

		long zobristHash = board.zobristHash;
		short bestMoveInPosition = MoveHelper.NULL_MOVE;
		short[] transposition = transpositionTable.getEntry(zobristHash);

		if (transposition != null)
		{
			// transposition depth is always greater than 0
			bestMoveInPosition = transposition[TranspositionTable.MOVE_INDEX];
			short bound = transposition[TranspositionTable.BOUND_INDEX];
			short eval = transposition[TranspositionTable.EVAL_INDEX];
			if (bound == TranspositionTable.EXACT_BOUND)
			{
				return transposition[TranspositionTable.EVAL_INDEX];
			}
			else if (bound == TranspositionTable.LOWER_BOUND)
			{
				if (eval >= beta)
					return beta;
				else
					alpha = Math.max(alpha, eval);
			}
			else if (bound == TranspositionTable.UPPER_BOUND)
			{
				if (eval <= alpha)
					return alpha;
				else
					beta = Math.max(beta, eval);
			}
		}

		// captures arent forced so check eval before capturing something
		int evaluation = staticEvaluation();

		if (evaluation >= beta)
		{
			return evaluation;
		}
		alpha = Math.max(alpha, evaluation);

		int numMoves = moveGen.generateMoves(MoveGenerator.CAPTURES_ONLY, moveIndex);

		moveOrderer.guessMoveEvals(bestMoveInPosition, true, 0, moveIndex, numMoves);

		long pawnsHash = board.pawnsHash;
		final short[] boardInfoOld = board.packBoardInfo();

		for (int i = moveIndex; i < (moveIndex + numMoves); i++)
		{
			moveOrderer.singleSelectionSort(i, (moveIndex + numMoves));

			final short move = moves[i];

			final byte captured = board.movePiece(move);
			evaluation = -(searchCaptures(-beta, -alpha, moveIndex + numMoves));
			board.moveBack(move, captured, zobristHash, pawnsHash, boardInfoOld);

			if (searchCancelled)
				return 0;

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
		long zobristHash = board.repetitionHistory[board.repetitionIndex];
		for (int i = board.repetitionIndex - 2; i >= 0; i -= 2)
		{
			if (board.repetitionHistory[i] == zobristHash)
			{
				return true;
			}
		}
		return false;
	}

	public int staticEvaluation()
	{
		float evaluation = 0;

		float gamePhase = getGamePhase();
		evaluation = evaluateMaterial(gamePhase);

		final int wkIndex = Bitboards.getLSB(board.bitBoards.pieceBoards[PieceHelper.WHITE_KING]);
		final int bkIndex = Bitboards.getLSB(board.bitBoards.pieceBoards[PieceHelper.BLACK_KING]);

		evaluation += forceKingToCorner(wkIndex, bkIndex, 1 - (gamePhase * (24 / 5.0f)));
		evaluation -= forceKingToCorner(bkIndex, wkIndex, 1 - (gamePhase * (24 / 5.0f)));

		evaluation += evaluatePawns();

		evaluation *= board.colorToMove == PieceHelper.WHITE ? 1 : -1;

		return (int) evaluation;
	}

	public float evaluateMaterial(float gamePhase)
	{
		int mgScore = board.material[PieceHelper.WHITE * 2] - board.material[PieceHelper.BLACK * 2];
		int egScore = board.material[PieceHelper.WHITE * 2 + 1] - board.material[PieceHelper.BLACK * 2 + 1];
		return mgScore * gamePhase + egScore * (1 - gamePhase);
	}

	public float getGamePhase()
	{
		int mgPhase = 0;
		mgPhase += Long
				.bitCount(board.bitBoards.pieceBoards[PieceHelper.WHITE_QUEEN]
						| board.bitBoards.pieceBoards[PieceHelper.BLACK_QUEEN])
				* PieceHelper.getGamePhaseValue(PieceHelper.QUEEN);

		mgPhase += Long
				.bitCount(board.bitBoards.pieceBoards[PieceHelper.WHITE_ROOK]
						| board.bitBoards.pieceBoards[PieceHelper.BLACK_ROOK])
				* PieceHelper.getGamePhaseValue(PieceHelper.QUEEN);

		mgPhase += Long
				.bitCount(board.bitBoards.pieceBoards[PieceHelper.WHITE_BISHOP]
						| board.bitBoards.pieceBoards[PieceHelper.BLACK_BISHOP])
				* PieceHelper.getGamePhaseValue(PieceHelper.QUEEN);

		if (mgPhase > 24)
			mgPhase = 24;
		return mgPhase / 24.0f;
	}

	public int evaluatePawns()
	{
		long pawnsHash = board.pawnsHash;
		int pawnsEval = transpositionTable.getPawnsEval(pawnsHash);
		if (pawnsEval != TranspositionTable.LOOKUP_FAILED)
		{
			return pawnsEval;
		}
		else
		{
			int newEval = evaluatePawns(PieceHelper.WHITE, PieceHelper.BLACK)
					- evaluatePawns(PieceHelper.BLACK, PieceHelper.WHITE);
			transpositionTable.storePawnsEvaluation(pawnsHash, newEval);
			return newEval;
		}
	}

	public int evaluatePawns(int friendlyColor, int enemyColor)
	{
		final int[] passedPawnsBonus = { 0, 10, 15, 25, 40, 60, 100, 0 };
		boolean whitePieces = friendlyColor == PieceHelper.WHITE;
		int evaluation = 0;

		long pawnsBitboard = board.bitBoards.pieceBoards[friendlyColor | PieceHelper.PAWN];
		long friendlyPawns = pawnsBitboard;
		long enemyPawns = board.bitBoards.pieceBoards[enemyColor | PieceHelper.PAWN];

		while (pawnsBitboard != 0)
		{
			int pawnIndex = Bitboards.getLSB(pawnsBitboard);
			pawnsBitboard = Bitboards.toggleBit(pawnsBitboard, pawnIndex);

			int rank = pawnIndex & 56;
			long ranksAheadMask = -1; // All 1 bits
			ranksAheadMask = whitePieces ? (ranksAheadMask << (rank + 8)) : (ranksAheadMask >>> (rank ^ 56 + 8));

			int file = pawnIndex & 0x7;
			long fileMask = MoveGenerator.A_FILE << (file); // mask last 3 bits, aka mod 8
			long tripleFileMask = fileMask;
			if (file > 0)
				tripleFileMask |= (tripleFileMask >>> 1); // left file
			if (file < 7)
				tripleFileMask |= (tripleFileMask << 1); // right file

			long passedPawnMask = ranksAheadMask & tripleFileMask & enemyPawns;
			if (passedPawnMask == 0)
				evaluation += passedPawnsBonus[whitePieces ? (pawnIndex / 8) : (7 - (pawnIndex / 8))]; // getting the
																										// rank

			long friendlyPawnsAhead = (ranksAheadMask & fileMask & friendlyPawns);
			evaluation -= 10 * (Long.bitCount(friendlyPawnsAhead) * Long.bitCount(friendlyPawnsAhead));

			/*
			 * // next/prev rank are swapped for white/black pawns long nextRankMask =
			 * MoveGenerator.FIRST_RANK << ((pawnIndex & ~7) + (whitePieces ? 8 : -8)); long
			 * prevRankMask = MoveGenerator.FIRST_RANK << ((pawnIndex & ~7) - (whitePieces ?
			 * 8 : -8)); long friendlyPawnsSupported = tripleFileMask & ~fileMask &
			 * nextRankMask & friendlyPawns; long friendlyPawnsSupporting = tripleFileMask &
			 * ~fileMask & prevRankMask & friendlyPawns;
			 * 
			 * evaluation += 5 * Long.bitCount(friendlyPawnsSupported) *
			 * Long.bitCount(friendlyPawnsSupported) + 5; evaluation += 5 *
			 * Long.bitCount(friendlyPawnsSupporting) *
			 * Long.bitCount(friendlyPawnsSupporting) + 5;
			 */
		}

		return evaluation;
	}

	public float forceKingToCorner(final int friendlyKingIndex, final int enemyKingIndex, final float endgameWeight)
	{
		if (endgameWeight < 0)
			return 0;

		float evaluation = (PrecomputedData.distToCenter[enemyKingIndex] * 15)
				+ (PrecomputedData.orthoSquaresDist[(friendlyKingIndex << 6) | enemyKingIndex] * 8);

		return evaluation * endgameWeight;
	}

	public boolean makeMove(short move)
	{
		if (move != MoveHelper.NULL_MOVE)
		{
			board.movePiece(move);
			board.repetitionIndex = board.halfMoves;
			return true;
		}
		return false;
	}

	public void stopSearch()
	{
		//searchCancelled = true;
	}

	public void newPosition()
	{
		transpositionTable.clearTable();
	}
}
