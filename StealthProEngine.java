import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class StealthProEngine extends JFrame {
    public StealthProEngine() {
        setTitle("SHADOW PROTOCOL: ELITE EDITION");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);

        GameEngine engine = new GameEngine();
        add(engine);
        pack();

        setLocationRelativeTo(null);
        setVisible(true);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(StealthProEngine::new);
    }
}

class GameEngine extends JPanel implements ActionListener, KeyListener, MouseListener, MouseMotionListener {
    private enum State { MENU, DIFFICULTY_SELECT, PLAYING, PAUSED, GAME_OVER, WIN }
    private State currentState = State.MENU;

    private double enemyDamageMult = 1.0;
    private double enemyRotSpeedMult = 1.0;
    private double enemyFovMult = 1.0;
    private String selectedDiffName = "MEDIUM";

    private Timer gameTimer;
    private Random rnd = new Random();
    private Point2D.Double player = new Point2D.Double(120, 120);
    private double playerAngle = 0;
    private int playerHP = 100;
    private double screenShake = 0;
    private boolean gameInProgress = false;

    private final int MAX_MAG = 12;
    private int ammoInMag = 12;
    private int magsReserved = 5;
    private int reloadTimer = 0;
    private final int RELOAD_DURATION = 70;
    private boolean fireFromRight = true;
    private int muzzleFlashTimer = 0;

    private List<Detector> detectors = new ArrayList<>();
    private List<Bullet> bullets = new ArrayList<>();
    private List<Particle> particles = new ArrayList<>();
    private boolean[] keys = new boolean[256]; // Reduced size for performance
    private Point mousePos = new Point(0, 0);
    private float pulse = 0f;
    private final int TILE_SIZE = 80;

    private final int[][] MAP = {
        {1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1},
        {1,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,1},
        {1,0,1,1,1,0,1,0,1,1,1,1,1,1,1,1,1,1,0,1},
        {1,0,0,0,1,0,0,0,1,0,0,0,0,0,0,0,0,1,0,1},
        {1,1,1,0,1,1,1,1,1,0,1,1,1,1,1,0,1,1,0,1},
        {1,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,1},
        {1,0,1,1,1,1,1,1,1,0,1,0,1,1,1,1,1,1,0,1},
        {1,0,1,0,0,0,0,0,1,0,0,0,1,0,0,0,0,0,0,1},
        {1,0,1,0,1,1,1,0,1,1,1,0,1,0,1,1,1,1,0,1},
        {1,0,0,0,1,0,0,0,0,0,0,0,0,0,1,0,0,0,0,1},
        {1,1,1,1,1,0,1,1,1,1,1,1,1,0,1,0,1,1,1,1},
        {1,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,1,0,0,1},
        {1,0,1,1,1,1,1,1,1,1,1,0,1,1,1,1,1,0,1,1},
        {1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,1},
        {1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1}
    };

    public GameEngine() {
        setPreferredSize(new Dimension(1000, 700));
        setBackground(new Color(2, 4, 8));
        setFocusable(true);
        addKeyListener(this);
        addMouseListener(this);
        addMouseMotionListener(this);
        gameTimer = new Timer(16, this);
        gameTimer.start();
    }

    private void applyDifficulty(int mode) {
        switch(mode) {
            case 1: enemyDamageMult = 0.5; enemyRotSpeedMult = 0.6; enemyFovMult = 0.7; magsReserved = 8; selectedDiffName = "EASY"; break;
            case 2: enemyDamageMult = 1.0; enemyRotSpeedMult = 1.0; enemyFovMult = 1.0; magsReserved = 5; selectedDiffName = "MEDIUM"; break;
            case 3: enemyDamageMult = 1.8; enemyRotSpeedMult = 1.6; enemyFovMult = 1.3; magsReserved = 3; selectedDiffName = "HARD"; break;
        }
        startNewGame();
    }

    private void initLevel() {
        detectors.clear();
        detectors.add(new Detector(4*TILE_SIZE+40, 1*TILE_SIZE+40, 0.04 * enemyRotSpeedMult, enemyFovMult));
        detectors.add(new Detector(13*TILE_SIZE+40, 5*TILE_SIZE+40, 0.05 * enemyRotSpeedMult, enemyFovMult));
        detectors.add(new Detector(18*TILE_SIZE+40, 1*TILE_SIZE+40, 0.08 * enemyRotSpeedMult, enemyFovMult));
        detectors.add(new Detector(9*TILE_SIZE+40, 9*TILE_SIZE+40, -0.04 * enemyRotSpeedMult, enemyFovMult));
    }

    public void startNewGame() {
        player = new Point2D.Double(120, 120);
        playerHP = 100;
        ammoInMag = MAX_MAG;
        reloadTimer = 0;
        bullets.clear();
        particles.clear();
        initLevel();
        gameInProgress = true;
        currentState = State.PLAYING;
    }

    private void update() {
        if (currentState != State.PLAYING) return;
        if (screenShake > 0) screenShake *= 0.9;

        // Corrected Movement Logic
        double moveSpeed = keys[KeyEvent.VK_SHIFT] ? 2.5 : 4.5;
        double mx = 0, my = 0;
        if (keys[KeyEvent.VK_W]) my -= 1;
        if (keys[KeyEvent.VK_S]) my += 1;
        if (keys[KeyEvent.VK_A]) mx -= 1;
        if (keys[KeyEvent.VK_D]) mx += 1;

        if (mx != 0 || my != 0) {
            double length = Math.sqrt(mx * mx + my * my);
            double dx = (mx / length) * moveSpeed;
            double dy = (my / length) * moveSpeed;
            if (!isColliding(player.x + dx, player.y, 14)) player.x += dx;
            if (!isColliding(player.x, player.y + dy, 14)) player.y += dy;
        }

        int camX = (int)(getWidth() / 2 - player.x);
        int camY = (int)(getHeight() / 2 - player.y);
        playerAngle = Math.atan2((mousePos.y - camY) - player.y, (mousePos.x - camX) - player.x);

        pulse += 0.07f;
        if (muzzleFlashTimer > 0) muzzleFlashTimer--;

        if (keys[KeyEvent.VK_R] && ammoInMag < MAX_MAG && magsReserved > 0 && reloadTimer == 0) {
            reloadTimer = RELOAD_DURATION;
        }
        if (reloadTimer > 0) {
            reloadTimer--;
            if (reloadTimer == 0) { ammoInMag = MAX_MAG; magsReserved--; }
        }

        for (Detector d : detectors) {
            d.update(player, MAP, TILE_SIZE);
            if (d.canShoot()) {
                bullets.add(new Bullet(d.x, d.y, d.angle, false));
                screenShake = 3;
            }
        }

        updateBullets();
        particles.removeIf(p -> !p.update());
        if (playerHP <= 0) { currentState = State.GAME_OVER; gameInProgress = false; }
        checkWinCondition();
    }

    private void updateBullets() {
        Iterator<Bullet> bIter = bullets.iterator();
        while (bIter.hasNext()) {
            Bullet b = bIter.next();
            b.move();
            if (isColliding(b.x, b.y, 3)) { bIter.remove(); continue; }
            
            if (b.isPlayerBullet) {
                for (Detector d : detectors) {
                    if (Point2D.distance(b.x, b.y, d.x, d.y) < 22) {
                        d.hp -= 35;
                        spawnExplosion(d.x, d.y, Color.ORANGE);
                        bIter.remove();
                        break;
                    }
                }
            } else if (Point2D.distance(b.x, b.y, player.x, player.y) < 18) {
                playerHP -= (int)(10 * enemyDamageMult);
                screenShake = 8;
                bIter.remove();
            }
        }
        detectors.removeIf(d -> d.hp <= 0);
    }

    private void spawnExplosion(double x, double y, Color c) {
        for(int i=0; i<12; i++) particles.add(new Particle(x, y, c));
    }

    private boolean isColliding(double x, double y, int r) {
        int row = (int)(y / TILE_SIZE), col = (int)(x / TILE_SIZE);
        if (row < 0 || row >= MAP.length || col < 0 || col >= MAP[0].length) return true;
        return MAP[row][col] == 1;
    }

    private void checkWinCondition() {
        int row = (int)(player.y/TILE_SIZE);
        int col = (int)(player.x/TILE_SIZE);
        if (row >= 0 && row < MAP.length && col >= 0 && col < MAP[0].length && MAP[row][col] == 2) {
            currentState = State.WIN;
            gameInProgress = false;
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        AffineTransform oldAt = g2.getTransform();
        if (screenShake > 1) {
            g2.translate((rnd.nextDouble()-0.5)*screenShake, (rnd.nextDouble()-0.5)*screenShake);
        }

        int camX = (int)(getWidth() / 2 - player.x);
        int camY = (int)(getHeight() / 2 - player.y);

        g2.translate(camX, camY);
        if (gameInProgress || currentState == State.PAUSED) drawWorld(g2);
        g2.setTransform(oldAt);

        drawHUD(g2);
        if (currentState != State.PLAYING) drawOverlay(g2);
    }

    private void drawWorld(Graphics2D g2) {
        for (int r = 0; r < MAP.length; r++) {
            for (int c = 0; c < MAP[r].length; c++) {
                if (MAP[r][c] == 1) {
                    g2.setColor(new Color(25, 30, 50));
                    g2.fillRect(c * TILE_SIZE, r * TILE_SIZE, TILE_SIZE, TILE_SIZE);
                    g2.setColor(new Color(40, 50, 80));
                    g2.drawRect(c * TILE_SIZE, r * TILE_SIZE, TILE_SIZE, TILE_SIZE);
                } else if (MAP[r][c] == 2) {
                    float glow = (float)(Math.abs(Math.sin(pulse)) * 0.5);
                    g2.setColor(new Color(0, 255, 150, (int)(30 + glow * 100)));
                    g2.fillRect(c * TILE_SIZE, r * TILE_SIZE, TILE_SIZE, TILE_SIZE);
                }
            }
        }

        for (Particle p : particles) p.draw(g2);
        for (Detector d : detectors) {
            if (d.lastVisionShape != null) {
                g2.setColor(d.playerSpotted ? new Color(255, 0, 0, 40) : new Color(0, 200, 255, 15));
                g2.fill(d.lastVisionShape);
            }
            g2.setColor(d.playerSpotted ? Color.RED : new Color(0, 150, 255));
            g2.fillOval((int)d.x - 15, (int)d.y - 15, 30, 30);
        }

        for (Bullet b : bullets) {
            g2.setColor(b.isPlayerBullet ? Color.CYAN : Color.WHITE);
            g2.fillOval((int)b.x - 3, (int)b.y - 3, 6, 6);
        }

        g2.translate(player.x, player.y);
        if (reloadTimer > 0) {
            g2.setColor(new Color(255, 255, 255, 100));
            g2.setStroke(new BasicStroke(3));
            int arc = (int)(360 * ((double)reloadTimer / RELOAD_DURATION));
            g2.drawArc(-25, -25, 50, 50, 90, arc);
        }

        g2.rotate(playerAngle);
        g2.setColor(new Color(0, 255, 200));
        g2.fillOval(-15, -15, 30, 30);
        g2.setColor(Color.BLACK);
        g2.fillRect(12, fireFromRight ? 6 : -12, 16, 6);
        if (muzzleFlashTimer > 0) {
            g2.setColor(new Color(255, 255, 200));
            g2.fillOval(28, fireFromRight ? 4 : -14, 12, 12);
        }
        g2.rotate(-playerAngle);
        g2.translate(-player.x, -player.y);
    }

    private void drawHUD(Graphics2D g2) {
        if (!gameInProgress && currentState != State.PAUSED) return;
        
        g2.setColor(new Color(10, 10, 20, 200));
        g2.fillRoundRect(20, 20, 180, 40, 10, 10);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Monospaced", Font.BOLD, 14));
        g2.drawString("MODE: " + selectedDiffName, 35, 45);

        g2.setColor(new Color(10, 10, 20, 200));
        g2.fillRoundRect(20, getHeight()-90, 250, 70, 10, 10);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("SansSerif", Font.BOLD, 12));
        g2.drawString("INTEGRITY", 35, getHeight()-70);
        g2.setColor(Color.DARK_GRAY);
        g2.fillRect(35, getHeight()-60, 200, 12);
        g2.setColor(playerHP > 30 ? new Color(0, 255, 150) : Color.RED);
        g2.fillRect(35, getHeight()-60, playerHP * 2, 12);

        g2.setColor(new Color(10, 10, 20, 200));
        g2.fillRoundRect(getWidth()-180, getHeight()-90, 160, 70, 10, 10);
        g2.setColor(ammoInMag < 4 ? Color.RED : Color.CYAN);
        g2.setFont(new Font("Monospaced", Font.BOLD, 22));
        g2.drawString(ammoInMag + " / " + (magsReserved * MAX_MAG), getWidth()-160, getHeight()-50);
        g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
        g2.setColor(Color.GRAY);
        g2.drawString("RESERVE: " + magsReserved, getWidth()-160, getHeight()-70);
    }

    private void drawOverlay(Graphics2D g2) {
        g2.setColor(new Color(0, 0, 0, 225));
        g2.fillRect(0, 0, getWidth(), getHeight());
        
        g2.setColor(Color.CYAN);
        g2.setFont(new Font("Monospaced", Font.BOLD, 50));
        String head = "SHADOW PROTOCOL";
        if (currentState == State.PAUSED) head = "PAUSED";
        if (currentState == State.DIFFICULTY_SELECT) head = "SELECT DIFFICULTY";
        if (currentState == State.GAME_OVER) head = "M.I.A.";
        if (currentState == State.WIN) head = "EXTRACTED";
        
        g2.drawString(head, getWidth()/2 - g2.getFontMetrics().stringWidth(head)/2, 180);

        g2.setFont(new Font("Monospaced", Font.PLAIN, 22));
        int startY = 320;
        
        if (currentState == State.MENU) {
            drawMenuOption(g2, "[ENTER] INITIALIZE MISSION", startY);
        } else if (currentState == State.DIFFICULTY_SELECT) {
            drawMenuOption(g2, "[1] EASY - LOW SECURITY", startY);
            drawMenuOption(g2, "[2] MEDIUM - STANDARD OPS", startY + 50);
            drawMenuOption(g2, "[3] HARD - ELITE DEFENSE", startY + 100);
        } else if (currentState == State.PAUSED) {
            drawMenuOption(g2, "[P] RESUME OPS", startY);
            drawMenuOption(g2, "[ENTER] RE-DEPLOY (RESET)", startY + 50);
        } else {
            drawMenuOption(g2, "[ENTER] NEW MISSION", startY);
        }
    }

    private void drawMenuOption(Graphics2D g2, String text, int y) {
        g2.setColor(Color.WHITE);
        g2.drawString(text, getWidth()/2 - g2.getFontMetrics().stringWidth(text)/2, y);
    }

    @Override public void actionPerformed(ActionEvent e) { update(); repaint(); }
    @Override public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() < keys.length) keys[e.getKeyCode()] = true;
        if (e.getKeyCode() == KeyEvent.VK_ENTER) {
            if (currentState == State.MENU || currentState == State.GAME_OVER || currentState == State.WIN || currentState == State.PAUSED) {
                currentState = State.DIFFICULTY_SELECT;
            }
        }
        if (currentState == State.DIFFICULTY_SELECT) {
            if (e.getKeyCode() == KeyEvent.VK_1) applyDifficulty(1);
            if (e.getKeyCode() == KeyEvent.VK_2) applyDifficulty(2);
            if (e.getKeyCode() == KeyEvent.VK_3) applyDifficulty(3);
        }
        if (e.getKeyCode() == KeyEvent.VK_P) {
            if (currentState == State.PLAYING) currentState = State.PAUSED;
            else if (currentState == State.PAUSED && gameInProgress) currentState = State.PLAYING;
        }
    }
    @Override public void keyReleased(KeyEvent e) { if (e.getKeyCode() < keys.length) keys[e.getKeyCode()] = false; }
    @Override public void keyTyped(KeyEvent e) {}
    @Override public void mousePressed(MouseEvent e) {
        if (currentState == State.PLAYING && ammoInMag > 0 && reloadTimer <= 0) {
            bullets.add(new Bullet(player.x, player.y, playerAngle, true));
            ammoInMag--; muzzleFlashTimer = 3; fireFromRight = !fireFromRight; screenShake = 5;
        }
    }
    @Override public void mouseMoved(MouseEvent e) { mousePos = e.getPoint(); }
    @Override public void mouseDragged(MouseEvent e) { mousePos = e.getPoint(); }
    @Override public void mouseClicked(MouseEvent e) {}
    @Override public void mouseReleased(MouseEvent e) {}
    @Override public void mouseEntered(MouseEvent e) {}
    @Override public void mouseExited(MouseEvent e) {}
}

class Particle {
    double x, y, dx, dy;
    int life = 30;
    Color color;
    public Particle(double x, double y, Color c) {
        this.x = x; this.y = y; this.color = c;
        this.dx = (Math.random()-0.5)*6; this.dy = (Math.random()-0.5)*6;
    }
    public boolean update() { x+=dx; y+=dy; life--; return life > 0; }
    public void draw(Graphics2D g) {
        g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(0, life*8)));
        g.fillRect((int)x, (int)y, 3, 3);
    }
}

class Bullet {
    double x, y, angle, speed = 18.0;
    boolean isPlayerBullet;
    public Bullet(double x, double y, double a, boolean p) { this.x=x; this.y=y; this.angle=a; this.isPlayerBullet=p; }
    public void move() { x += Math.cos(angle)*speed; y += Math.sin(angle)*speed; }
}

class Detector {
    double x, y, angle, rotSpeed, fovMult;
    int hp = 100, fireRate = 0;
    boolean playerSpotted = false;
    Path2D lastVisionShape;

    public Detector(double x, double y, double rs, double fovM) {
        this.x=x; this.y=y; this.rotSpeed=rs; this.fovMult = fovM;
    }

    public void update(Point2D.Double player, int[][] map, int tileSize) {
        if (lastVisionShape != null && lastVisionShape.contains(player)) {
            playerSpotted = true;
            // Smooth look-at logic
            double targetAngle = Math.atan2(player.y - y, player.x - x);
            angle += (targetAngle - angle) * 0.1; 
            if (fireRate > 0) fireRate--;
        } else {
            playerSpotted = false;
            angle += rotSpeed;
            fireRate = 10;
        }
        lastVisionShape = calculateRaycast(map, tileSize);
    }

    public boolean canShoot() {
        if (playerSpotted && fireRate <= 0) { fireRate = 45; return true; }
        return false;
    }

    private Path2D calculateRaycast(int[][] map, int tileSize) {
        Path2D path = new Path2D.Double();
        path.moveTo(x, y);
        double fov = Math.toRadians(55 * fovMult);
        for (double i = -fov/2; i <= fov/2; i += 0.05) {
            double rA = angle + i;
            double rX = x, rY = y;
            for (int d = 0; d < 450; d += 10) {
                rX = x + Math.cos(rA) * d;
                rY = y + Math.sin(rA) * d;
                int row = (int)(rY/tileSize), col = (int)(rX/tileSize);
                if (row >= 0 && row < map.length && col >= 0 && col < map[0].length && map[row][col] == 1) break;
            }
            path.lineTo(rX, rY);
        }
        path.closePath();
        return path;
    }
}