import javax.swing.*;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.geom.Ellipse2D;
import java.util.Random;

public class Main {
  static void main() {
    SwingUtilities.invokeLater(() -> {
      JFrame royFrame = new JFrame("Scorched Earth by Roy");
      royFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
      royFrame.setResizable(false);
      royFrame.add(new GamePanel());
      royFrame.pack();
      royFrame.setLocationRelativeTo(null);
      royFrame.setVisible(true);
    });
  }
}

class GamePanel extends JPanel {
  private static final int WIDTH = 900;
  private static final int HEIGHT = 600;
  private static final double SECONDS_PER_FRAME = 0.016;
  private static final int EXPLOSION_RADIUS = 42;
  private static final int TANK_MOVE_STEP = 8;
  private static final int TANK_HALF_WIDTH = 25;
  private static final int GRAVITY = 600;
  private static final int CRATER_RADIUS = 36;
  private static final int CRATER_DEPTH = 27;
  private static final int EXPLOSION_DURATION = 18;
  private static final int TANK_HIT_RADIUS = 22;

  private final Tank[] tanks = new Tank[2];
  private final int[] terrain = new int[WIDTH + 1];
  private final Timer timer;
  private int currentPlayer;
  private Projectile projectile;
  private double explosionX;
  private double explosionY;
  private int explosionFrames;
  private String message = "Player 1's turn";

  GamePanel() {
    setPreferredSize(new Dimension(WIDTH, HEIGHT));
    setBackground(new Color(135, 195, 230));
    setFocusable(true);

    resetGame();
    setupControls();

    //framerate 60
    timer = new Timer(16, royEvent -> updateGame());
    timer.start();
  }

  private void resetGame() {
    generateTerrain();
    tanks[0] = new Tank("Player 1", 135, new Color(45, 105, 210), 1);
    tanks[1] = new Tank("Player 2", WIDTH - 135, new Color(205, 75, 55), -1);
    currentPlayer = 0;
    projectile = null;
    explosionFrames = 0;
    message = "Player 1's turn";
    repaint();
  }

  private void generateTerrain() {
    Random royRandom = new Random();
    double royOffset = royRandom.nextDouble() * Math.PI * 2;

    //uses 2 sin for hills
    for (int boy = 0; boy <= WIDTH; boy++) {
      double royHills = Math.sin(boy * 0.01 + royOffset) * 48
          + Math.sin(boy * 0.02 + royOffset * 0.7) * 20;
      terrain[boy] = (int) (HEIGHT * 0.72 + royHills + royRandom.nextInt(5));
    }
  }

  private void setupControls() {
    // controls
    bind("LEFT", "lowerAngle", () -> adjustAngle(-2));
    bind("RIGHT", "raiseAngle", () -> adjustAngle(2));
    bind("DOWN", "lowerPower", () -> adjustPower(-5));
    bind("UP", "raisePower", () -> adjustPower(5));
    bind("A", "moveLeft", () -> moveTank(-TANK_MOVE_STEP));
    bind("D", "moveRight", () -> moveTank(TANK_MOVE_STEP));
    bind("SPACE", "fire", this::fire);
    bind("R", "restart", this::resetGame);
  }

  private void bind(String key, String name, Runnable action) {
    getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key), name);
    getActionMap().put(name, new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent royEvent) {
        action.run();
      }
    });
  }

  private void adjustAngle(int amount) {
    // cant aim while bomb in sky
    if (projectile != null || explosionFrames != 0 || isGameOver()) {
      return;
    }
    Tank royTank = tanks[currentPlayer];
    royTank.angle = clamp(royTank.angle + amount, 10, 80);
    repaint();
  }

  private int clamp(int value, int min, int max) {
    return Math.max(min, Math.min(max, value));
  }

  private void adjustPower(int amount) {
    if (projectile == null && explosionFrames == 0 && !isGameOver()) {
      Tank royTank = tanks[currentPlayer];
      royTank.power = Math.max(20, Math.min(100, royTank.power + amount));
      repaint();
    }
  }

  private void moveTank(int amount) {
    if (projectile != null || explosionFrames > 0 || isGameOver()) {
      return;
    }

    Tank royTank = tanks[currentPlayer];
    Tank royOpponent = tanks[1 - currentPlayer];
    // keeps tank on screen and not inside each other
    double royNewX = Math.max(TANK_HALF_WIDTH,
        Math.min(WIDTH - TANK_HALF_WIDTH, royTank.x + amount));
    if (Math.abs(royNewX - royOpponent.x) < TANK_HALF_WIDTH * 2) {
      return;
    }

    royTank.x = royNewX;
    repaint();
  }

  private void fire() {
    if (projectile != null || explosionFrames > 0 || isGameOver()) {
      return;
    }

    Tank royTank = tanks[currentPlayer];
    double royRadians = Math.toRadians(royTank.angle);
    // more power more bomb speed
    double roySpeed = 250 + royTank.power * 4.5;
    double royMuzzleX = royTank.x + royTank.direction * Math.cos(royRadians) * 35;
    double royMuzzleY = tankY(royTank) - 22 - Math.sin(royRadians) * 35;
    projectile = new Projectile(
        royMuzzleX,
        royMuzzleY,
        royTank.direction * roySpeed * Math.cos(royRadians),
        -roySpeed * Math.sin(royRadians)
    );
    message = royTank.name + " fired!";
    repaint();
  }

  private void updateGame() {
    if (projectile != null) {
      // moves bomb up and uses gravity to bring it down
      projectile.x += projectile.velocityX * SECONDS_PER_FRAME;
      projectile.y += projectile.velocityY * SECONDS_PER_FRAME;
      projectile.velocityY += GRAVITY * SECONDS_PER_FRAME;

      if (hitsTank(projectile.x, projectile.y)) {
        explode(projectile.x, projectile.y, true);
      } else if (projectile.x < 0 || projectile.x > WIDTH || projectile.y > HEIGHT) {
        projectile = null;
        finishTurn();
      } else if (projectile.y >= terrainAt(projectile.x)) {
        explode(projectile.x, terrainAt(projectile.x), true);
      }
    } else if (explosionFrames > 0) {
      explosionFrames--;
      if (explosionFrames == 0) {
        finishTurn();
      }
    }
    repaint();
  }

  private boolean hitsTank(double x, double y) {
    for (Tank royTank : tanks) {
      double royDistanceX = x - royTank.x;
      double royDistanceY = y - (tankY(royTank) - 12);
      if (Math.hypot(royDistanceX, royDistanceY) < TANK_HIT_RADIUS) {
        return true;
      }
    }
    return false;
  }

  private void explode(double x, double y, boolean damage) {
    projectile = null;
    explosionX = x;
    explosionY = y;
    explosionFrames = EXPLOSION_DURATION;

    // makes a crater where it blew
    for (int royTerrainX = Math.max(0, (int) x - CRATER_RADIUS);
         royTerrainX <= Math.min(WIDTH, (int) x + CRATER_RADIUS);
         royTerrainX++) {
      double royDistance = Math.abs(royTerrainX - x);
      double royDepth = Math.sqrt(Math.max(0,
          1 - royDistance * royDistance / (CRATER_RADIUS * CRATER_RADIUS))) * CRATER_DEPTH;
      terrain[royTerrainX] = Math.min(HEIGHT - 25, terrain[royTerrainX] + (int) royDepth);
    }

    if (damage) {
      for (Tank royTank : tanks) {
        double royDistance = Math.hypot(x - royTank.x, y - (tankY(royTank) - 12));
        if (royDistance < EXPLOSION_RADIUS + 18) {
          int royDamage = (int) Math.round(55 * (1 - royDistance / (EXPLOSION_RADIUS + 18)));
          royTank.health = Math.max(0, royTank.health - royDamage);
        }
      }
    }
    repaint();
  }

  private void finishTurn() {
    if (isGameOver()) {
      Tank winner = tanks[0].health > 0 ? tanks[0] : tanks[1];
      message = winner.name + " wins! Press R to play again.";
    } else {
      // switches to the other player
      currentPlayer = 1 - currentPlayer;
      message = tanks[currentPlayer].name + "'s turn";
    }
  }

  private boolean isGameOver() {
    return tanks[0].health == 0 || tanks[1].health == 0;
  }

  private int terrainAt(double x) {
    // adds something between terrain to make it go smooth
    int royLeft = Math.max(0, Math.min(WIDTH, (int) x));
    int royRight = Math.min(WIDTH, royLeft + 1);
    double royFraction = Math.max(0, Math.min(1, x - royLeft));
    return (int) (terrain[royLeft] * (1 - royFraction) + terrain[royRight] * royFraction);
  }

  private int tankY(Tank tank) {
    return terrainAt(tank.x);
  }

  @Override
  protected void paintComponent(Graphics graphics) {
    super.paintComponent(graphics);
    Graphics2D royGraphics = (Graphics2D) graphics.create();
    royGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    // draws the scene
    drawTerrain(royGraphics);
    drawTank(royGraphics, tanks[0]);
    drawTank(royGraphics, tanks[1]);
    drawProjectile(royGraphics);
    drawHud(royGraphics);

    royGraphics.dispose();
  }

  private void drawTerrain(Graphics2D g) {
    Polygon royGround = new Polygon();
    royGround.addPoint(0, HEIGHT);
    for (int royX = 0; royX <= WIDTH; royX += 2) {
      royGround.addPoint(royX, terrain[royX]);
    }
    royGround.addPoint(WIDTH, HEIGHT);
    g.setColor(new Color(190, 145, 75));
    g.fillPolygon(royGround);
    g.setColor(new Color(110, 95, 55));
    g.setStroke(new BasicStroke(3));
    for (int royX = 0; royX < WIDTH; royX += 2) {
      g.drawLine(royX, terrain[royX], royX + 2, terrain[royX + 2]);
    }
  }

  private void drawTank(Graphics2D g, Tank tank) {
    int royGroundY = tankY(tank);
    int royX = (int) tank.x;
    int royBodyY = royGroundY - 25;
    boolean royActive = tanks[currentPlayer] == tank && !isGameOver();

    g.setColor(Color.DARK_GRAY);
    g.fillRoundRect(royX - 25, royGroundY - 12, 50, 14, 8, 8);
    g.setColor(tank.color);
    g.fillRoundRect(royX - 21, royBodyY, 42, 18, 8, 8);
    g.fillOval(royX - 10, royGroundY - 34, 20, 20);

    double royRadians = Math.toRadians(tank.angle);
    int royPivotX = royX;
    int royPivotY = royGroundY - 25;
    int royMuzzleX = (int) (royPivotX + tank.direction * Math.cos(royRadians) * 36);
    int royMuzzleY = (int) (royPivotY - Math.sin(royRadians) * 36);
    g.setColor(Color.BLACK);
    g.setStroke(new BasicStroke(6, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    g.drawLine(royPivotX, royPivotY, royMuzzleX, royMuzzleY);

    if (royActive) {
      g.setColor(Color.WHITE);
      g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
      g.drawString("AIM", royX - 14, royGroundY - 43);
    }
  }

  private void drawProjectile(Graphics2D g) {
    if (projectile != null) {
      g.setColor(Color.BLACK);
      g.fillOval((int) projectile.x - 5, (int) projectile.y - 5, 10, 10);
    }
    if (explosionFrames > 0) {
      int royDiameter = EXPLOSION_RADIUS * 2;
      g.setColor(new Color(255, 115, 20, 155));
      g.fill(new Ellipse2D.Double(explosionX - EXPLOSION_RADIUS,
          explosionY - EXPLOSION_RADIUS, royDiameter, royDiameter));
      g.setColor(new Color(255, 220, 70, 210));
      g.fill(new Ellipse2D.Double(explosionX - EXPLOSION_RADIUS / 2.0,
          explosionY - EXPLOSION_RADIUS / 2.0, EXPLOSION_RADIUS, EXPLOSION_RADIUS));
    }
  }

  private void drawHud(Graphics2D g) {
    g.setColor(new Color(0, 0, 0, 150));
    g.fillRoundRect(15, 15, WIDTH - 30, 94, 16, 16);
    g.setColor(Color.WHITE);
    g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
    g.drawString(message, 30, 41);
    g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
    g.drawString(tankStatus(tanks[0]), 30, 67);
    g.drawString(tankStatus(tanks[1]), WIDTH - 300, 67);
    g.drawString("Move: A/D    Aim: LEFT/RIGHT    Power: UP/DOWN    Fire: SPACE    Restart: R", 30, 93);
  }

  private String tankStatus(Tank tank) {
    String royTurnMarker = tanks[currentPlayer] == tank && !isGameOver() ? "  <" : "";
    return tank.name + "  HP " + tank.health + "  Angle " + tank.angle
        + "  Power " + tank.power + royTurnMarker;
  }
}

class Tank {
  // 1 is right -1 is left
  final String name;
  double x;
  final Color color;
  final int direction;
  int health = 100;
  int angle = 45;
  int power = 60;

  Tank(String name, double x, Color color, int direction) {
    this.name = name;
    this.x = x;
    this.color = color;
    this.direction = direction;
  }
}

class Projectile {
  // keeps velo during framerate
  double x;
  double y;
  double velocityX;
  double velocityY;

  Projectile(double x, double y, double velocityX, double velocityY) {
    this.x = x;
    this.y = y;
    this.velocityX = velocityX;
    this.velocityY = velocityY;
  }
}
