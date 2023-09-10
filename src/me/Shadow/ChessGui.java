package me.Shadow;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.util.ArrayList;

import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;

import me.Shadow.pieces.*;

public class ChessGui
{
	final JPanel gui = new JPanel(new BorderLayout(3, 3));
	JButton[][] chessBoardSquares = new JButton[8][8];
	Image[][] chessPieceImages = new Image[2][6];
	JPanel chessBoard;
	final JTextField message = new JTextField("Make a move!");
	public static final int KING = 0, QUEEN = 1, BISHOP = 2, KNIGHT = 3, ROOK = 4, PAWN = 5;
	public static final int[] STARTING_ROW =
	{ ROOK, KNIGHT, BISHOP, KING, QUEEN, BISHOP, KNIGHT, ROOK };
	public static final int BLACK = 0, WHITE = 1;
	Color lightBoard = new Color(239, 217, 183);
	Color darkBoard = new Color(180, 136, 101);
	Color lightRed = new Color(241, 58, 67);
	Color darkRed = new Color(235, 49, 60);
	Color lightYellow = new Color(246, 235, 122);
	Color darkYellow = new Color(217, 194, 83);

	Engine engine;

	int indexOld = -1;

	public void createGui(Engine engineIn)
	{
		Runnable r = new Runnable()
		{
			@Override
			public void run()
			{
				initializeGui();
				engine = engineIn;
				updatePieces();

				JFrame f = new JFrame("Chess Engine");
				f.add(getGui());
				// Ensures JVM closes after frame(s) closed and
				// all non-daemon threads are finished
				f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
				// See https://stackoverflow.com/a/7143398/418556 for demo.
				// f.setLocationByPlatform(true);
				f.setLocation(750, 10);

				// ensures the frame is the minimum size it needs to be
				// in order display the components within it
				f.pack();
				// ensures the minimum size is enforced.
				f.setMinimumSize(f.getSize());
				f.setVisible(true);
			}
		};
		// Swing GUIs should be created and updated on the EDT
		// http://docs.oracle.com/javase/tutorial/uiswing/concurrency
		SwingUtilities.invokeLater(r);
	}

	public final void initializeGui()
	{
		// create the images for the chess pieces
		createImages();

		// set up the main GUI
		gui.setBorder(new EmptyBorder(5, 5, 5, 5));
		JToolBar tools = new JToolBar();
		tools.setFloatable(false);
		gui.add(tools, BorderLayout.PAGE_START);

		JButton restore = new JButton("Restore");

		Action newGameAction = new AbstractAction("New")
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				engine.originalFEN = engine.boardGlobal.boardInfo.defaultFEN;
				restore.doClick();
			}
		};
		tools.add(newGameAction);
		JButton createFEN = new JButton("Create FEN");
		createFEN.setActionCommand("createFEN");
		createFEN.addActionListener(new ToolbarButtons());
		tools.add(createFEN);

		restore.setActionCommand("restore");
		restore.addActionListener(new ToolbarButtons());
		tools.add(restore);
		tools.addSeparator();

		tools.add(new JButton("Resign")); // TODO - add functionality!
		tools.addSeparator();
		tools.add(message);
		message.setEditable(false);

		gui.add(new JLabel("?"), BorderLayout.LINE_START);

		chessBoard = new JPanel(new GridLayout(0, 8))
		{

			/**
			 * Override the preferred size to return the largest it can, in a square shape. Must (must, must) be added to a GridBagLayout as the only component (it uses the parent as a guide to size) with no GridBagConstaint (so it is centered).
			 */
			@Override
			public final Dimension getPreferredSize()
			{
				Dimension d = super.getPreferredSize();
				Dimension prefSize = null;
				Component c = getParent();
				if (c == null)
					prefSize = new Dimension((int) d.getWidth(), (int) d.getHeight());
				else if (c != null && c.getWidth() > d.getWidth() && c.getHeight() > d.getHeight())
					prefSize = c.getSize();
				else
					prefSize = d;
				int w = (int) prefSize.getWidth();
				int h = (int) prefSize.getHeight();
				// the smaller of the two sizes
				int s = (w > h ? h : w);
				return new Dimension(s, s);
			}
		};
		chessBoard.setBorder(new CompoundBorder(new EmptyBorder(8, 8, 8, 8), new LineBorder(darkBoard)));
		// Set the BG to be ochre
		Color ochre = new Color(204, 119, 34);
		chessBoard.setBackground(ochre);
		JPanel boardConstrain = new JPanel(new GridBagLayout());
		boardConstrain.setBackground(ochre);
		boardConstrain.add(chessBoard);
		gui.add(boardConstrain);

		// create the chess board squares
		Insets buttonMargin = new Insets(0, 0, 0, 0);
		for (int ii = 0; ii < chessBoardSquares.length; ii++)
		{
			for (int jj = 0; jj < chessBoardSquares[ii].length; jj++)
			{
				JButton b = new JButton();
				b.setActionCommand("" + ((ii * 8) + jj));
				ButtonSquare listener = new ButtonSquare();
				b.addActionListener(listener);
				b.setMargin(buttonMargin);
				b.setBorder(null);
				// our chess pieces are 64x64 px in size, so we'll
				// 'fill this in' using a transparent icon..
				ImageIcon icon = new ImageIcon(new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB));
				b.setIcon(icon);
				if ((jj % 2 == 1 && ii % 2 == 1) || (jj % 2 == 0 && ii % 2 == 0))
					b.setBackground(lightBoard);
				else
					b.setBackground(darkBoard);
				chessBoardSquares[ii][jj] = b;
			}
		}

		/*
		 * fill the chess board
		 */
		// fill the black non-pawn piece row
		for (int ii = 0; ii < 8; ii++)
		{
			for (int jj = 0; jj < 8; jj++)
			{
				chessBoard.add(chessBoardSquares[ii][jj]);
			}
		}
	}

	public final JComponent getGui()
	{
		return gui;
	}

	private final void createImages()
	{
		try
		{
			BufferedImage bi = ImageIO.read(Engine.class.getResource("chesspieces.png"));
			for (int i = 0; i < 2; i++)
			{
				for (int j = 0; j < 4; j++)
				{
					chessPieceImages[1 - i][j] = bi.getSubimage(j * 106, i * 106, 106, 106);
				}
				for (int j = 0; j < 3; j++)
				{
					chessPieceImages[1 - i][j + 3] = bi.getSubimage(j * 106 + 3 * 107, i * 106, 106, 106);
				}
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
			System.exit(1);
		}
	}

	/** colorSet = 0 for default board colors, 1 for light/dark yellow, and 2 for light/dark red */
	public void setColor(int index, int colorSet)
	{
		Color light = lightBoard;
		Color dark = darkBoard;

		if (colorSet == 1)
		{
			light = lightYellow;
			dark = darkYellow;
		}
		else if (colorSet == 2)
		{
			light = lightRed;
			dark = darkRed;
		}
		int rowOld = index / 8;
		int colOld = index % 8;
		if ((rowOld + colOld) % 2 == 0)
			chessBoardSquares[rowOld][colOld].setBackground(light);
		else
			chessBoardSquares[rowOld][colOld].setBackground(dark);

	}

	public void updatePieces()
	{
		for (Square square : engine.boardGlobal.squares)
		{
			int squareIndex = square.getIndex();
			int squareRow = squareIndex / 8;
			int squareCol = squareIndex % 8;
			ImageIcon icon = new ImageIcon(new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB));
			if (square.hasPiece())
			{
				if (square.getPiece() instanceof Pawn)
					icon = new ImageIcon(chessPieceImages[square.getPiece().isWhite() ? WHITE : BLACK][PAWN]);
				else if (square.getPiece() instanceof Knight)
					icon = new ImageIcon(chessPieceImages[square.getPiece().isWhite() ? WHITE : BLACK][KNIGHT]);
				else if (square.getPiece() instanceof Rook)
					icon = new ImageIcon(chessPieceImages[square.getPiece().isWhite() ? WHITE : BLACK][ROOK]);
				else if (square.getPiece() instanceof Bishop)
					icon = new ImageIcon(chessPieceImages[square.getPiece().isWhite() ? WHITE : BLACK][BISHOP]);
				else if (square.getPiece() instanceof Queen)
					icon = new ImageIcon(chessPieceImages[square.getPiece().isWhite() ? WHITE : BLACK][QUEEN]);
				else if (square.getPiece() instanceof King)
					icon = new ImageIcon(chessPieceImages[square.getPiece().isWhite() ? WHITE : BLACK][KING]);
			}
			chessBoardSquares[squareRow][squareCol].setIcon(icon);
		}
	}

	class ButtonSquare implements ActionListener
	{
		public void actionPerformed(ActionEvent e)
		{
			int index = Integer.parseInt(e.getActionCommand());
			int row = index / 8;
			int col = index % 8;

			boolean moveMade = false;
			Move pieceMove = null;

			if (indexOld != -1)
			{
				setColor(indexOld, 0);
			}

			if (indexOld == index)
			{
				indexOld = -1;
				for (Move move : engine.movesOld)
				{
					setColor(move.getTargetIndex(), 0);
				}
				return;
			}

			if ((engine.boardGlobal.boardInfo.isWhiteToMove() != engine.engineIsWhite) && !engine.engineSearching && (chessBoardSquares[row][col].getBackground() == darkRed || chessBoardSquares[row][col].getBackground() == lightRed))
			{
				pieceMove = null;
				for (Move move2 : engine.movesOld)
				{
					if (move2.getTargetIndex() == index)
					{
						pieceMove = move2;
						break;
					}
				}

				System.out.print("\n" + engine.boardGlobal.boardInfo.getMoveNum() + ". ");
				if (engine.engineIsWhite)
					System.out.print("...");
				System.out.println(pieceMove.toString() + "\n");

				engine.boardGlobal.movePiece(pieceMove);
				moveMade = true;
				
				Engine.Node<Move> node = engine.gameTree.findChild(pieceMove);
				if (node != null) engine.gameTree = engine.gameTree.findChild(pieceMove);
				else engine.gameTree = engine.new Node<Move>(null);
			}

			for (Move move : engine.movesOld)
			{
				setColor(move.getTargetIndex(), 0);
			}

			if (!moveMade && !engine.engineSearching && engine.boardGlobal.squares.get(index).hasPiece() && engine.boardGlobal.squares.get(index).getPiece().isWhite() != engine.engineIsWhite)
			{
				ArrayList<Move> moves = engine.boardGlobal.generateLegalMoves(engine.boardGlobal.squares.get(index).getPiece());
				for (Move move : moves)
				{
					setColor(move.getTargetIndex(), 2);
				}
				engine.movesOld = moves;
			}

			setColor(index, 1);
			indexOld = index;

			if (moveMade)
			{
				setColor(index, 0);
				indexOld = -1;
				updatePieces();
				engine.playerMoveMade = true;
			}
		}
	}

	class ToolbarButtons implements ActionListener
	{
		public void actionPerformed(ActionEvent e)
		{
			String command = e.getActionCommand();
			if (command.equals("createFEN"))
			{
				String fen = engine.boardGlobal.createFEN(true);
				message.setText(fen);
			}

			else if (command.equals("restore"))
			{
				if (engine.engineSearching)
					message.setText("Please wait until the engine plays a move before restoring original position!");
				else
				{
					message.setText("Make a move!");
					for (Move move : engine.movesOld)
					{
						setColor(move.getTargetIndex(), 0);
					}

					if (indexOld != -1)
					{
						setColor(indexOld, 0);
					}

					if (engine.engineMoveOld != null)
					{
						setColor(engine.engineMoveOld.getTargetIndex(), 0);
						setColor(engine.engineMoveOld.getTargetIndex(), 0);
					}

					engine.boardGlobal.loadFEN(engine.originalFEN);
					engine.engineSearching = false;
					//engine.transpositionsList.clear();
					engine.transpositions.clear();
					engine.playerMoveMade = engine.engineIsWhite == engine.boardGlobal.boardInfo.isWhiteToMove();
					updatePieces();
				}
			}
		}
	}
}
