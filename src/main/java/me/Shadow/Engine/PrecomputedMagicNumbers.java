package me.Shadow.Engine;

import java.util.Random;

public class PrecomputedMagicNumbers
{
	public static final long [] ROOK_MASK = new long[64];
	public static final long [] BISHOP_MASK = new long[64];
	
	public static final long [][] MAGIC_ROOK_MOVES = new long[64][];
	public static final long [] ROOK_MAGICS = {36030446824341504L, 18049585834508288L, 7638124761381879841L, 4647736840047038464L, 144150392044257792L, 72068623581053184L, 144116433616407560L, 4755801757469246724L, 72198366960035968L, 4036421672218136576L, -4584101393391861720L, 2350883404616106496L, 148759542371975298L, -6618602395269726132L, -8900238557244275140L, 4233946610567359212L, 1601211636830846976L, 635008921852977164L, -5763268317569605503L, 243335667390810114L, 11259275020337664L, 1267187486556672L, 6652762230671028261L, 953507479441113479L, -8997765305387171840L, 290825124708640L, 1706444202188800L, 17635137945920L, 82195093394030721L, 1367124000542818322L, -3325390437649211816L, 7515207940589228187L, -7614773056308770935L, 145487398074073089L, 163396361427881984L, 650913039517696L, 567358745741312L, 422564660777984L, -6942975917455023858L, -6096410146882180860L, -8989184029431332864L, 288091409612864L, 563500784156704L, 288793601656356881L, -7983094332507946976L, 126663791195848848L, 8560474583867326480L, -8458774395253030900L, 8436873236171874816L, -8506012305404722304L, -3082636001982541312L, -5394676831036452352L, 6748383595972277760L, 5700994746578136576L, 7939846097328749568L, 7417603962880972288L, -4062429378508259270L, 10837893657527522L, 3327878755694829571L, 7540996792276527173L, -9222809000464087006L, -4612248865281670062L, 5806459356350748684L, -4839117840408377066L};
	public static final int [] ROOK_SHIFTS = {52, 53, 53, 53, 53, 53, 53, 52, 53, 54, 54, 54, 54, 54, 54, 53, 53, 54, 54, 54, 54, 54, 54, 53, 53, 54, 54, 54, 54, 54, 54, 53, 53, 54, 54, 54, 54, 54, 54, 53, 53, 54, 54, 54, 54, 54, 54, 53, 53, 54, 54, 54, 54, 54, 54, 53, 52, 53, 53, 53, 53, 53, 53, 52};
	
	public static final long [][] MAGIC_BISHOP_MOVES = new long[64][];
	public static final long [] BISHOP_MAGICS = {1246321520696262655L, 2964807252734377828L, -5473993736587794196L, -4750118561034045445L, 8017547122931826693L, -4097989331536246539L, -4482866017747368576L, 1865446903162077047L, -9192578451771025414L, -519613031812067334L, 2060991351107020429L, -428484053063902008L, 4564763313375550634L, 8327613666734186273L, 2909078326010199926L, 557588397172293552L, -5026012465266479130L, -2116617174395852813L, -6184560938050124951L, 2231534663403436673L, 7920707193292401163L, -4642294523751456757L, 136234212513857462L, 4265471984730841077L, -8320303265151840237L, 3843738111193393502L, -758853237703179519L, 844699875409930L, 1154328949300412448L, -5077793182291295183L, 6050759777852138555L, 6141198240838907990L, -77229584079147100L, -4107370751822388212L, 7478728764757118388L, 22007412949520L, 450361070838677576L, 2560194713404440833L, -8613123687293620297L, -1645913910722065393L, 5413232013905346598L, -7633607183318867945L, 4205359864546963465L, 5045544374155661570L, 6459849916410840067L, 886141519273241088L, -4053304385237091324L, 2197709830768616964L, -2771985578258547642L, -5081188147547963507L, 61437694819987263L, -4561078173826541578L, -6552139250450425242L, -3520926116525768599L, 6340956441735673809L, -3693011389175682350L, 5211790114995001424L, 6846764439243966807L, 5499666308325177567L, 574430245069817891L, -6014670161983960573L, 8488853738152878562L, 2912421546914802930L, -4287571988613693108L};
	public static final int [] BISHOP_SHIFTS = {59, 60, 59, 59, 59, 59, 60, 59, 60, 60, 59, 59, 59, 59, 60, 60, 60, 60, 57, 57, 57, 57, 60, 60, 59, 59, 57, 55, 55, 57, 59, 59, 59, 59, 57, 55, 55, 57, 59, 59, 60, 60, 57, 57, 57, 57, 60, 60, 60, 60, 59, 59, 59, 59, 60, 60, 59, 60, 59, 59, 59, 59, 60, 59};

	
	public static Random rng = new Random();
	
	public static void precomputeMagics()
	{
		populateMagicArray();
		//searchMagicNumbers(250);
	}
	
	public static void searchMagicNumbers(int timeMS)
	{		
		while (true)
		{
			for (int i = 0; i < 64; i++)
			{
				boolean result = findNewMagicNumber(i, true, timeMS);
				if (result) printMagicsAndShifts(true);
				result = findNewMagicNumber(i, false, timeMS);
				if (result) printMagicsAndShifts(false);
			}
		}
	}
	
	public static void populateMagicArray()
	{
		for (int i = 0; i < 64; i++)
		{
			ROOK_MASK[i] = createMovementMask(i, true);
			MAGIC_ROOK_MOVES[i] = new long[1 << (64 - ROOK_SHIFTS[i])];
			long [] allBlockers = getAllPossibleBlockers(ROOK_MASK[i]);
			for (long blocker : allBlockers)
			{
				int index = (int) ((blocker * ROOK_MAGICS[i]) >>> ROOK_SHIFTS[i]);
				MAGIC_ROOK_MOVES[i][index] = getAllPossibleMoves(i, blocker, true);
			}
			
			BISHOP_MASK[i] = createMovementMask(i, false);
			MAGIC_BISHOP_MOVES[i] = new long[1 << (64 - BISHOP_SHIFTS[i])];
			allBlockers = getAllPossibleBlockers(BISHOP_MASK[i]);
			for (long blocker : allBlockers)
			{
				int index = (int) ((blocker * BISHOP_MAGICS[i]) >>> BISHOP_SHIFTS[i]);
				MAGIC_BISHOP_MOVES[i][index] = getAllPossibleMoves(i, blocker, false);
			}
		}
	}
	
	public static void printMagicsAndShifts(boolean ortho)
	{
		if (ortho)
		{
			for (int i = 0; i < ROOK_MAGICS.length; i++)
			{
				System.out.print(ROOK_MAGICS[i] + "L, ");
			}
			
			double tableSizeSum = 0;
			System.out.println("\n");
			for (int shift : ROOK_SHIFTS)
			{
				System.out.print(shift + ", ");
				tableSizeSum += ((1 << (64 - shift)) * 8) / 1024;
			}
			System.out.println("\n");
			System.out.print("Average Rook table size in kb: ");
			double averageSize = tableSizeSum / 64;
			System.out.println(averageSize + "\n");
		}
		else
		{
			for (int i = 0; i < BISHOP_MAGICS.length; i++)
			{
				System.out.print(BISHOP_MAGICS[i] + "L, ");
			}
			
			double tableSizeSum = 0;
			System.out.println("\n");
			for (int shift : BISHOP_SHIFTS)
			{
				System.out.print(shift + ", ");
				tableSizeSum += ((1 << (64 - shift)) * 8) / 1024;
			}
			System.out.println("\n");
			System.out.print("Average Bishop table size in kb: ");
			double averageSize = tableSizeSum / 64;
			System.out.println(averageSize + "\n");
		}
	}
	
	public static void printMagicsTableSize()
	{
		double tableSizeSum = 0;
		for (int shift : ROOK_SHIFTS)
		{
			tableSizeSum += ((1 << (64 - shift)) * 8) / 1024;
		}
		System.out.print("Average Rook table size: ");
		double averageSize = tableSizeSum / 64;
		System.out.println(averageSize + " kb");
		
		tableSizeSum = 0;
		for (int shift : BISHOP_SHIFTS)
		{
			tableSizeSum += ((1 << (64 - shift)) * 8) / 1024;
		}
		System.out.print("Average Bishop table size: ");
		averageSize = tableSizeSum / 64;
		System.out.println(averageSize + " kb\n");
	}
	
	public static boolean findNewMagicNumber(int square, boolean orthogonal, int timeLimitMS)
	{
		long [] blockerCombinations = getAllPossibleBlockers(orthogonal ? ROOK_MASK[square] : BISHOP_MASK[square]);
		long bestNumber = orthogonal ? ROOK_MAGICS[square] : BISHOP_MAGICS[square];
		int shiftAmount = (orthogonal ? ROOK_SHIFTS[square] : BISHOP_SHIFTS[square]) + 1;
		long endTime = System.currentTimeMillis() + timeLimitMS;
		
		while (System.currentTimeMillis() < endTime)
		{
			long [] magicArray = new long[1 << (64 - shiftAmount)];
			boolean failed = false;
			for (long blockers : blockerCombinations)
			{
				int arrayIndex = (int) ((blockers * bestNumber) >>> shiftAmount);
				if (magicArray[arrayIndex] != 0)
				{
					if (getSliderMoves(square, blockers, orthogonal) != magicArray[arrayIndex])
					{
						failed = true;
						break;
					}
				}
				else
				{
					magicArray[arrayIndex] = getSliderMoves(square, blockers, orthogonal);
				}
			}
			
			if (failed)
			{
				bestNumber = randomLong() & randomLong() & randomLong();
				while (Long.bitCount(bestNumber) < 6)
				{
					bestNumber = randomLong() & randomLong() & randomLong();
				}
			}
			else
			{
				if (orthogonal)
				{
					ROOK_MAGICS[square] = bestNumber;
					ROOK_SHIFTS[square] = shiftAmount;
				}
				else
				{
					BISHOP_MAGICS[square] = bestNumber;
					BISHOP_SHIFTS[square] = shiftAmount;
				}
				
				shiftAmount++;
				return true;
			}
		}
		return false;
	}
	
	public static long randomLong()
	{
		return (rng.nextLong() & 0xFFFFFFFFL) | ((rng.nextLong() & 0xFFFFFFFFL) << 32);
	}
	
	public static long getSliderMoves(int square, long blockers, boolean orthogonal)
	{
		return orthogonal ? getRookMoves(square, blockers) : getBishopMoves(square, blockers);
	}
	
	public static long getRookMoves(int square, long blockers)
	{
		return MAGIC_ROOK_MOVES[square][(int)(((blockers & ROOK_MASK[square]) * ROOK_MAGICS[square]) >>> ROOK_SHIFTS[square])];
	}
	
	public static long getBishopMoves(int square, long blockers)
	{
		return MAGIC_BISHOP_MOVES[square][(int)(((blockers & BISHOP_MASK[square]) * BISHOP_MAGICS[square]) >>> BISHOP_SHIFTS[square])];
	}
	
	/*
	public static HashMap<Integer, HashMap<Long, Long>> precomputeMagics(boolean orthogonal)
	{
		HashMap<Integer, HashMap<Long, Long>> squaresToBlockersMap = new HashMap<>(64);
		for (int square = 0; square < 64; square++)
		{
			HashMap<Long, Long> blockersToMovesMap = new HashMap<>();
			
			long mask = createMovementMask(square, orthogonal);
			
			long [] blockers = getAllPossibleBlockers(mask);
			for (long blockersMask : blockers)
			{
				long moves = getAllPossibleMoves(square, blockersMask, orthogonal);
				blockersToMovesMap.put(blockersMask, moves);				
			}
						
			squaresToBlockersMap.put(square, blockersToMovesMap);
		}
		return squaresToBlockersMap;
	}
	*/
	
	public static long getAllPossibleMoves(int square, long blockers, boolean orthogonal)
	{
		long moves = 0;
		int startIndex = orthogonal ? 0 : 4;
		for (int i = startIndex; i < startIndex + 4; i++)
		{
			int dir = PrecomputedData.directionOffsets[i];
			long sliderMoves = PrecomputedData.rayDirectionMask[square*8 + i];
			
			while (sliderMoves != 0)
			{
				int targetSquare = (dir > 0 ? Bitboards.getLSB(sliderMoves) : Bitboards.getMSB(sliderMoves));
				sliderMoves = Bitboards.toggleBit(sliderMoves, targetSquare);

				moves |= (1L << targetSquare);
				
				if ((blockers & (1L << targetSquare)) != 0)
				{
					break;
				}
			}
		}
		
		return moves;
	}
	
	public static long[] getAllPossibleBlockers(long mask)
	{
		int bitCount = Long.bitCount(mask);
		int [] onesArray = new int[bitCount];
		int onesFound = 0;
		while (mask != 0)
		{
			int lsb = Bitboards.getLSB(mask);
			mask = Bitboards.toggleBit(mask, lsb);
			onesArray[onesFound] = lsb;
			onesFound++;
		}
		
		int combinations = 1 << bitCount;
		long[] resultsArray = new long[combinations];
		for (int i = 0; i < combinations; i++)
		{
			long result = 0;
			int sourceBitPos = 0;
			for (int shiftAmount : onesArray)
			{
				long resultBitToSet = (i >>> sourceBitPos) & 1;
				result |= (resultBitToSet << shiftAmount);
				sourceBitPos++;
			}
			resultsArray[i] = result;
		}
		return resultsArray;
	}
	
	public static long createMovementMask(int square, boolean orthogonal)
	{
		long mask = 0;
		int startIndex = orthogonal ? 0 : 4;
		for (int i = startIndex; i < startIndex + 4; i++)
		{
			int n = PrecomputedData.numSquaresToEdge[square*8 + i];
			for (int j = 1; j < n; j++)
			{
				int newSquare = square + j*PrecomputedData.directionOffsets[i];
				mask |= (1L << newSquare);
			}
		}		
		return mask;
	}
}
