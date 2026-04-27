import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * SHADOW PROTOCOL: ELITE EDITION
 * Complete Source Code
 */
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
    
    // Difficulty Settings
    private double enemyDamageMult = 1.0;
    private double enemyRotSpeedMult = 1.0;
    private double enemyFovMult = 1.0;
    private Timer gameTimer;
    private Random rnd = new Random();
    private Point2D.Double player = new Point2D.Double(120, 120);
    private double playerAngle = 0;
    private int playerHP = 100;
    private double screenShake = 0;
    private boolean gameInProgress = false;

    // Camera Smoothing
    private double camX = 0, camY = 0;

    // Combat Systems
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
    private boolean[] keys = new boolean[256]; 
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
            case 1: enemyDamageMult = 0.5; enemyRotSpeedMult = 0.6; enemyFovMult = 0.7; magsReserved = 8; break;
            case 2: enemyDamageMult = 1.0; enemyRotSpeedMult = 1.0; enemyFovMult = 1.0; magsReserved = 5; break;
            case 3: enemyDamageMult = 1.8; enemyRotSpeedMult = 1.6; enemyFovMult = 1.3; magsReserved = 3; break;
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
        playerHP = 100; ammoInMag = MAX_MAG; reloadTimer = 0;
        bullets.clear(); particles.clear();
        initLevel();
        gameInProgress = true;
        currentState = State.PLAYING;
    }

    private void update() {
        if (currentState != State.PLAYING) return;
        if (screenShake > 0) screenShake *= 0.9;

        // Player Movement
        double speed = keys[KeyEvent.VK_SHIFT] ? 2.5 : 4.5;
        double moveX = 0, moveY = 0;
        if (keys[KeyEvent.VK_W]) moveY -= 1;
        if (keys[KeyEvent.VK_S]) moveY += 1;
        if (keys[KeyEvent.VK_A]) moveX -= 1;
        if (keys[KeyEvent.VK_D]) moveX += 1;

        if (moveX != 0 && moveY != 0) {
            double length = Math.sqrt(moveX * moveX + moveY * moveY);
            moveX = (moveX / length) * speed;
            moveY = (moveY / length) * speed;
        } else { moveX *= speed; moveY *= speed; }

        if (!isColliding(player.x + moveX, player.y, 14)) player.x += moveX;
        if (!isColliding(player.x, player.y + moveY, 14)) player.y += moveY;

        // Camera Lerp
        double targetCamX = getWidth() / 2.0 - player.x;
        double targetCamY = getHeight() / 2.0 - player.y;
        camX += (targetCamX - camX) * 0.15;
        camY += (targetCamY - camY) * 0.15;

        playerAngle = Math.atan2((mousePos.y - camY) - player.y, (mousePos.x - camX) - player.x);
        pulse += 0.07f;
        if (muzzleFlashTimer > 0) muzzleFlashTimer--;

        // Reload Logic
        if (keys[KeyEvent.VK_R] && ammoInMag < MAX_MAG && magsReserved > 0 && reloadTimer == 0) reloadTimer = RELOAD_DURATION;
        if (reloadTimer > 0) {
            reloadTimer--;
            if (reloadTimer == 0) { ammoInMag = MAX_MAG; magsReserved--; }
        }

        // Enemy Update
        for (Detector d : detectors) {
            d.update(player, MAP, TILE_SIZE);
            if (d.canShoot()) {
                bullets.add(new Bullet(d.x, d.y, d.angle, false));
                screenShake = 4;
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
                        bIter.remove(); break;
                    }
                }
            } else if (Point2D.distance(b.x, b.y, player.x, player.y) < 18) {
                playerHP -= (int)(10 * enemyDamageMult);
                screenShake = 10;
                bIter.remove();
            }
        }
        detectors.removeIf(d -> d.hp <= 0);
    }

    private void spawnExplosion(double x, double y, Color c) {
        for(int i=0; i<15; i++) particles.add(new Particle(x, y, c));
    }

    private boolean isColliding(double x, double y, int r) {
        int row = (int)(y / TILE_SIZE), col = (int)(x / TILE_SIZE);
        return row < 0 || row >= MAP.length || col < 0 || col >= MAP[0].length || MAP[row][col] == 1;
    }

    private void checkWinCondition() {
        if (MAP[(int)(player.y/TILE_SIZE)][(int)(player.x/TILE_SIZE)] == 2) {
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
        if (screenShake > 1) g2.translate((rnd.nextDouble()-0.5)*screenShake, (rnd.nextDouble()-0.5)*screenShake);

        g2.translate((int)camX, (int)camY);
        if (gameInProgress || currentState == State.PAUSED) drawWorld(g2);
        g2.setTransform(oldAt);

        drawPostProcessing(g2);
        drawHUD(g2);
        if (currentState != State.PLAYING) drawOverlay(g2);
    }

    private void drawWorld(Graphics2D g2) {
        for (int r = 0; r < MAP.length; r++) {
            for (int c = 0; c < MAP[r].length; c++) {
                if (MAP[r][c] == 1) {
                    g2.setColor(new Color(20, 25, 45));
                    g2.fillRect(c * TILE_SIZE, r * TILE_SIZE, TILE_SIZE, TILE_SIZE);
                    g2.setColor(new Color(50, 70, 110));
                    g2.drawRect(c * TILE_SIZE, r * TILE_SIZE, TILE_SIZE, TILE_SIZE);
                } else if (MAP[r][c] == 2) {
                    float glow = (float)(Math.abs(Math.sin(pulse)) * 0.5);
                    g2.setColor(new Color(0, 255, 150, (int)(40 + glow * 120)));
                    g2.fillRect(c * TILE_SIZE, r * TILE_SIZE, TILE_SIZE, TILE_SIZE);
                }
            }
        }

        for (Particle p : particles) p.draw(g2);
        for (Detector d : detectors) {
            if (d.lastVisionShape != null) {
                g2.setColor(d.playerSpotted ? new Color(255, 0, 0, 35) : new Color(0, 180, 255, 15));
                g2.fill(d.lastVisionShape);
            }
            
            // LASER SIGHT
            if (d.playerSpotted) {
                g2.setStroke(new BasicStroke(1.5f));
                int laserAlpha = (int)(150 + Math.sin(pulse * 15) * 100);
                g2.setColor(new Color(255, 0, 0, Math.max(0, Math.min(255, laserAlpha))));
                g2.drawLine((int)d.x, (int)d.y, (int)player.x, (int)player.y);
            }

            g2.setColor(d.playerSpotted ? Color.RED : new Color(0, 130, 255));
            g2.fillOval((int)d.x - 15, (int)d.y - 15, 30, 30);
            g2.setColor(Color.WHITE);
            g2.drawOval((int)d.x - 15, (int)d.y - 15, 30, 30);
        }

        for (Bullet b : bullets) {
            g2.setColor(b.isPlayerBullet ? Color.CYAN : Color.RED);
            g2.fillOval((int)b.x - 3, (int)b.y - 3, 6, 6);
        }

        // Draw Player
        g2.translate(player.x, player.y);
        if (reloadTimer > 0) {
            g2.setColor(new Color(0, 255, 255, 150));
            g2.setStroke(new BasicStroke(3));
            int arc = (int)(360 * ((double)reloadTimer / RELOAD_DURATION));
            g2.drawArc(-25, -25, 50, 50, 90, arc);
        }

        g2.rotate(playerAngle);
        g2.setColor(new Color(0, 255, 200));
        g2.fillOval(-15, -15, 30, 30);
        g2.setColor(Color.WHITE);
        g2.drawOval(-15, -15, 30, 30);
        g2.setColor(Color.BLACK);
        g2.fillRect(12, fireFromRight ? 6 : -12, 16, 6);
        if (muzzleFlashTimer > 0) {
            g2.setColor(new Color(255, 255, 180));
            g2.fillOval(28, fireFromRight ? 4 : -14, 14, 14);
        }
        g2.rotate(-playerAngle);
        g2.translate(-player.x, -player.y);
    }

    private void drawPostProcessing(Graphics2D g2) {
        // Vignette
        float[] dist = {0.0f, 1.0f};
        Color[] colors = {new Color(0,0,0,0), new Color(0,0,0,180)};
        RadialGradientPaint p = new RadialGradientPaint(getWidth()/2f, getHeight()/2f, getWidth()/1.2f, dist, colors);
        g2.setPaint(p);
        g2.fillRect(0,0,getWidth(),getHeight());

        // Scanlines
        g2.setColor(new Color(255, 255, 255, 15));
        for(int i=0; i<getHeight(); i+=4) g2.drawLine(0, i, getWidth(), i);
    }

    private void drawHUD(Graphics2D g2) {
        if (!gameInProgress && currentState != State.PAUSED) return;
        
        g2.setFont(new Font("Monospaced", Font.BOLD, 12));
        
        // Health / Integrity Bar
        int hbY = getHeight() - 90;
        g2.setColor(new Color(0, 20, 40, 200));
        g2.fillRoundRect(20, hbY, 260, 70, 10, 10);
        g2.setColor(Color.CYAN);
        g2.drawRoundRect(20, hbY, 260, 70, 10, 10);

        g2.drawString("SYS_INTEGRITY", 35, hbY + 20);
        g2.setColor(Color.DARK_GRAY);
        g2.fillRect(35, hbY + 30, 200, 12);
        g2.setColor(playerHP > 30 ? new Color(0, 255, 150) : Color.RED);
        g2.fillRect(35, hbY + 30, playerHP * 2, 12);

        // Ammo Display
        int amX = getWidth() - 200;
        g2.setColor(new Color(0, 20, 40, 200));
        g2.fillRoundRect(amX, hbY, 180, 70, 10, 10);
        g2.setColor(Color.CYAN);
        g2.drawRoundRect(amX, hbY, 180, 70, 10, 10);
        
        g2.drawString("ORDNANCE", amX + 15, hbY + 20);
        g2.setFont(new Font("Monospaced", Font.BOLD, 22));
        g2.drawString(ammoInMag + " / " + (magsReserved * MAX_MAG), amX + 15, hbY + 50);
    }

    private void drawOverlay(Graphics2D g2) {
        g2.setColor(new Color(0, 5, 15, 230));
        g2.fillRect(0, 0, getWidth(), getHeight());
        
        g2.setColor(Color.CYAN);
        g2.setFont(new Font("Monospaced", Font.BOLD, 50));
        String head = "SHADOW PROTOCOL";
        if (currentState == State.PAUSED) head = "PAUSED";
        if (currentState == State.DIFFICULTY_SELECT) head = "SELECT DIFFICULTY";
        if (currentState == State.GAME_OVER) head = "UNIT_LOST";
        if (currentState == State.WIN) head = "MISSION_COMPLETE";
        
        g2.drawString(head, getWidth()/2 - g2.getFontMetrics().stringWidth(head)/2, 200);

        g2.setFont(new Font("Monospaced", Font.PLAIN, 18));
        g2.setColor(Color.WHITE);
        String sub = "[ENTER] TO INITIALIZE";
        if (currentState == State.DIFFICULTY_SELECT) sub = "[1] EASY [2] MEDIUM [3] HARD";
        if (currentState == State.PAUSED) sub = "[P] RESUME OPS";
        g2.drawString(sub, getWidth()/2 - g2.getFontMetrics().stringWidth(sub)/2, 350);
    }

    @Override public void actionPerformed(ActionEvent e) { update(); repaint(); }
    @Override public void keyPressed(KeyEvent e) { 
        int code = e.getKeyCode();
        if (code < keys.length) keys[code] = true; 
        if (code == KeyEvent.VK_ENTER) {
            if (currentState != State.PLAYING) currentState = State.DIFFICULTY_SELECT;
        }
        if (currentState == State.DIFFICULTY_SELECT) {
            if (code == KeyEvent.VK_1) applyDifficulty(1);
            if (code == KeyEvent.VK_2) applyDifficulty(2);
            if (code == KeyEvent.VK_3) applyDifficulty(3);
        }
        if (code == KeyEvent.VK_P) {
            if (currentState == State.PLAYING) currentState = State.PAUSED;
            else if (currentState == State.PAUSED) currentState = State.PLAYING;
        }
    }
    @Override public void keyReleased(KeyEvent e) { if (e.getKeyCode() < keys.length) keys[e.getKeyCode()] = false; }
    @Override public void keyTyped(KeyEvent e) {}
    @Override public void mousePressed(MouseEvent e) {
        if (currentState == State.PLAYING && ammoInMag > 0 && reloadTimer <= 0) {
            bullets.add(new Bullet(player.x, player.y, playerAngle, true));
            ammoInMag--; muzzleFlashTimer = 3; fireFromRight = !fireFromRight; screenShake = 6;
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
    double x, y, dx, dy; int life = 30; Color color;
    public Particle(double x, double y, Color c) {
        this.x = x; this.y = y; this.color = c;
        this.dx = (Math.random()-0.5)*8; this.dy = (Math.random()-0.5)*8;
    }
    public boolean update() { x+=dx; y+=dy; life--; return life > 0; }
    public void draw(Graphics2D g) {
        g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(0, life*8)));
        g.fillRect((int)x, (int)y, 3, 3);
    }
}

class Bullet {
    double x, y, angle, speed = 20.0; boolean isPlayerBullet;
    public Bullet(double x, double y, double a, boolean p) { this.x=x; this.y=y; this.angle=a; this.isPlayerBullet=p; }
    public void move() { x += Math.cos(angle)*speed; y += Math.sin(angle)*speed; }
}

class Detector {
    double x, y, angle, rotSpeed, fovMult; int hp = 100, fireRate = 0;
    boolean playerSpotted = false; Path2D lastVisionShape;
    public Detector(double x, double y, double rs, double fovM) { this.x=x; this.y=y; this.rotSpeed=rs; this.fovMult = fovM; }

    public void update(Point2D.Double player, int[][] map, int tileSize) {
        if (lastVisionShape != null && lastVisionShape.contains(player)) {
            playerSpotted = true;
            double target = Math.atan2(player.y - y, player.x - x);
            double diff = target - angle;
            while (diff < -Math.PI) diff += Math.PI * 2;
            while (diff > Math.PI) diff -= Math.PI * 2;
            angle += diff * 0.12; 
            if (fireRate > 0) fireRate--;
        } else {
            playerSpotted = false; angle += rotSpeed; fireRate = 12; 
        }
        lastVisionShape = calculateRaycast(map, tileSize);
    }
    public boolean canShoot() { if (playerSpotted && fireRate <= 0) { fireRate = 45; return true; } return false; }
    private Path2D calculateRaycast(int[][] map, int tileSize) {
        Path2D path = new Path2D.Double(); path.moveTo(x, y);
        double fov = Math.toRadians(55 * fovMult);
        for (double i = -fov/2; i <= fov/2; i += 0.05) {
            double rA = angle + i; double rX = x, rY = y;
            for (int d = 0; d < 480; d += 10) {
                rX = x + Math.cos(rA) * d; rY = y + Math.sin(rA) * d;
                int row = (int)(rY/tileSize), col = (int)(rX/tileSize);
                if (row >= 0 && row < map.length && col >= 0 && col < map[0].length && map[row][col] == 1) break;
            }
            path.lineTo(rX, rY);
        }
        path.closePath(); return path;
    }
}