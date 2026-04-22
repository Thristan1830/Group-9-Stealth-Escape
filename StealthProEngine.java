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
    private enum State { MENU, PLAYING, PAUSED, GAME_OVER, WIN }
    private State currentState = State.MENU;
    private Timer gameTimer;
    private Random rnd = new Random();

    // Game Objects
    private Point2D.Double player = new Point2D.Double(120, 120);
    private double playerAngle = 0;
    private int playerHP = 100;
    private double screenShake = 0;

    // Combat
    private final int MAX_MAG = 12;
    private int ammoInMag = 12;
    private int magsReserved = 5;
    private int reloadTimer = 0;
    private final int RELOAD_DURATION = 80;
    private boolean fireFromRight = true;
    private int muzzleFlashTimer = 0;

    private List<Detector> detectors = new ArrayList<>();
    private List<Bullet> bullets = new ArrayList<>();
    private List<Particle> particles = new ArrayList<>();
    
    private boolean[] keys = new boolean[65536];
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
        setBackground(new Color(3, 5, 10));
        setFocusable(true);
        addKeyListener(this);
        addMouseListener(this);
        addMouseMotionListener(this);
        initLevel();
        gameTimer = new Timer(16, this);
        gameTimer.start();
    }

    private void initLevel() {
        detectors.clear();
        detectors.add(new Detector(4*TILE_SIZE+40, 1*TILE_SIZE+40, 0.04));
        detectors.add(new Detector(13*TILE_SIZE+40, 5*TILE_SIZE+40, 0.05));
        detectors.add(new Detector(18*TILE_SIZE+40, 1*TILE_SIZE+40, 0.08));
        detectors.add(new Detector(9*TILE_SIZE+40, 9*TILE_SIZE+40, -0.04));
    }

    public void resetGame() {
        player = new Point2D.Double(120, 120);
        playerHP = 100;
        ammoInMag = MAX_MAG;
        magsReserved = 5;
        reloadTimer = 0;
        bullets.clear();
        particles.clear();
        initLevel();
        currentState = State.PLAYING;
    }

    private void update() {
        if (currentState != State.PLAYING) return;

        if (screenShake > 0) screenShake *= 0.9;

        // Movement
        double speed = keys[KeyEvent.VK_SHIFT] ? 2.0 : 4.5; // Stealth walk
        double dx = 0, dy = 0;
        if (keys[KeyEvent.VK_W]) dy -= speed;
        if (keys[KeyEvent.VK_S]) dy += speed;
        if (keys[KeyEvent.VK_A]) dx -= speed;
        if (keys[KeyEvent.VK_D]) dx += speed;

        if (!isColliding(player.x + dx, player.y, 10)) player.x += dx;
        if (!isColliding(player.x, player.y + dy, 10)) player.y += dy;

        int camX = (int)(getWidth() / 2 - player.x);
        int camY = (int)(getHeight() / 2 - player.y);
        playerAngle = Math.atan2((mousePos.y - camY) - player.y, (mousePos.x - camX) - player.x);

        pulse += 0.07f;
        if (muzzleFlashTimer > 0) muzzleFlashTimer--;

        // Reload Logic
        if (reloadTimer > 0) {
            reloadTimer--;
            if (reloadTimer == 0) { ammoInMag = MAX_MAG; magsReserved--; }
        }

        // Entities update
        for (Detector d : detectors) {
            d.update(player, MAP, TILE_SIZE);
            if (d.canShoot()) {
                bullets.add(new Bullet(d.x, d.y, d.angle, false));
                screenShake = 3;
            }
        }

        updateBullets();
        particles.removeIf(p -> !p.update());

        if (playerHP <= 0) currentState = State.GAME_OVER;
        checkWinCondition();
    }

    private void updateBullets() {
        Iterator<Bullet> bIter = bullets.iterator();
        while (bIter.hasNext()) {
            Bullet b = bIter.next();
            b.move();
            if (isColliding(b.x, b.y, 2)) { bIter.remove(); continue; }
            
            if (b.isPlayerBullet) {
                for (Detector d : detectors) {
                    if (Point2D.distance(b.x, b.y, d.x, d.y) < 20) {
                        d.hp -= 35;
                        spawnExplosion(d.x, d.y, Color.ORANGE);
                        bIter.remove();
                        break;
                    }
                }
            } else if (Point2D.distance(b.x, b.y, player.x, player.y) < 15) {
                playerHP -= 10;
                screenShake = 10;
                bIter.remove();
            }
        }
        detectors.removeIf(d -> d.hp <= 0);
    }

    private void spawnExplosion(double x, double y, Color c) {
        for(int i=0; i<10; i++) particles.add(new Particle(x, y, c));
    }

    private boolean isColliding(double x, double y, int r) {
        int row = (int)(y / TILE_SIZE);
        int col = (int)(x / TILE_SIZE);
        return row < 0 || row >= MAP.length || col < 0 || col >= MAP[0].length || MAP[row][col] == 1;
    }

    private void checkWinCondition() {
        if (MAP[(int)(player.y/TILE_SIZE)][(int)(player.x/TILE_SIZE)] == 2) currentState = State.WIN;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Camera shake implementation
        AffineTransform oldAt = g2.getTransform();
        if (screenShake > 1) {
            g2.translate((rnd.nextDouble()-0.5)*screenShake, (rnd.nextDouble()-0.5)*screenShake);
        }

        int camX = (int)(getWidth() / 2 - player.x);
        int camY = (int)(getHeight() / 2 - player.y);

        g2.translate(camX, camY);
        if (currentState != State.MENU) drawWorld(g2);
        g2.setTransform(oldAt);

        drawHUD(g2);
        if (currentState != State.PLAYING) drawOverlay(g2);
        
        // Scanline effect
        g2.setColor(new Color(255, 255, 255, 15));
        for(int i=0; i<getHeight(); i+=4) g2.fillRect(0, i, getWidth(), 1);
    }

    private void drawWorld(Graphics2D g2) {
        // Draw Map
        for (int r = 0; r < MAP.length; r++) {
            for (int c = 0; c < MAP[r].length; c++) {
                if (MAP[r][c] == 1) {
                    g2.setColor(new Color(25, 30, 50));
                    g2.fillRect(c * TILE_SIZE, r * TILE_SIZE, TILE_SIZE, TILE_SIZE);
                    g2.setColor(new Color(40, 50, 80));
                    g2.drawRect(c * TILE_SIZE, r * TILE_SIZE, TILE_SIZE, TILE_SIZE);
                } else if (MAP[r][c] == 2) {
                    float glow = (float)(Math.abs(Math.sin(pulse)) * 0.5);
                    g2.setColor(new Color(0, 1.0f, 0.4f, 0.2f + glow));
                    g2.fillRect(c * TILE_SIZE, r * TILE_SIZE, TILE_SIZE, TILE_SIZE);
                }
            }
        }

        for (Particle p : particles) p.draw(g2);

        for (Detector d : detectors) {
            if (d.lastVisionShape != null) {
                Color visionCol = d.playerSpotted ? new Color(255, 0, 0, 40) : new Color(0, 200, 255, 20);
                g2.setColor(visionCol);
                g2.fill(d.lastVisionShape);
            }
            g2.setColor(d.playerSpotted ? Color.RED : new Color(0, 150, 255));
            g2.fillOval((int)d.x - 12, (int)d.y - 12, 24, 24);
        }

        for (Bullet b : bullets) {
            g2.setColor(b.isPlayerBullet ? Color.CYAN : Color.ORANGE);
            g2.fillOval((int)b.x - 3, (int)b.y - 3, 6, 6);
        }

        // Player
        g2.translate(player.x, player.y);
        g2.rotate(playerAngle);
        g2.setColor(new Color(0, 255, 200));
        g2.fillOval(-15, -15, 30, 30);
        g2.setColor(Color.BLACK);
        g2.fillRect(10, -12, 15, 6); // Weapon
        g2.fillRect(10, 6, 15, 6);
        if (muzzleFlashTimer > 0) {
            g2.setColor(Color.WHITE);
            g2.fillOval(25, fireFromRight ? 6 : -12, 10, 10);
        }
        g2.rotate(-playerAngle);
        g2.translate(-player.x, -player.y);
    }

    private void drawHUD(Graphics2D g2) {
        // Modern Health Bar (Bottom Left)
        g2.setColor(new Color(20, 20, 30, 200));
        g2.fillRoundRect(20, getHeight()-100, 250, 80, 15, 15);
        
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("SansSerif", Font.BOLD, 12));
        g2.drawString("OPERATIVE INTEGRITY", 35, getHeight()-80);
        
        g2.setColor(Color.DARK_GRAY);
        g2.fillRoundRect(35, getHeight()-70, 200, 12, 5, 5);
        g2.setColor(playerHP > 30 ? new Color(0, 255, 150) : Color.RED);
        g2.fillRoundRect(35, getHeight()-70, (int)(2 * playerHP), 12, 5, 5);

        // Ammo (Bottom Right)
        String ammoStr = ammoInMag + " / " + (magsReserved * MAX_MAG);
        g2.setFont(new Font("Monospaced", Font.BOLD, 28));
        g2.setColor(ammoInMag == 0 ? Color.RED : Color.CYAN);
        g2.drawString(ammoStr, getWidth()-180, getHeight()-40);
        g2.setFont(new Font("SansSerif", Font.PLAIN, 12));
        g2.setColor(Color.WHITE);
        g2.drawString("MAGAZINE STATUS", getWidth()-180, getHeight()-70);
        
        if (reloadTimer > 0) {
            g2.setColor(new Color(255, 255, 255, 100));
            g2.drawArc(getWidth()/2-25, getHeight()/2-25, 50, 50, 90, (int)(360 * ((double)reloadTimer/RELOAD_DURATION)));
        }
    }

    private void drawOverlay(Graphics2D g2) {
        g2.setColor(new Color(0, 0, 0, 220));
        g2.fillRect(0, 0, getWidth(), getHeight());
        g2.setColor(Color.CYAN);
        g2.setFont(new Font("Monospaced", Font.BOLD, 40));
        String txt = currentState.toString();
        g2.drawString(txt, getWidth()/2 - g2.getFontMetrics().stringWidth(txt)/2, getHeight()/2);
    }

    // Input Handling
    @Override public void actionPerformed(ActionEvent e) { update(); repaint(); }
    @Override public void keyPressed(KeyEvent e) { 
        if (e.getKeyCode() < keys.length) keys[e.getKeyCode()] = true; 
        if (e.getKeyCode() == KeyEvent.VK_ENTER && currentState != State.PLAYING) resetGame();
    }
    @Override public void keyReleased(KeyEvent e) { if (e.getKeyCode() < keys.length) keys[e.getKeyCode()] = false; }
    @Override public void keyTyped(KeyEvent e) {}
    @Override public void mousePressed(MouseEvent e) {
        if (currentState == State.PLAYING && ammoInMag > 0 && reloadTimer <= 0) {
            bullets.add(new Bullet(player.x, player.y, playerAngle, true));
            ammoInMag--;
            muzzleFlashTimer = 3;
            fireFromRight = !fireFromRight;
            screenShake = 4;
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
        this.dx = (Math.random()-0.5)*6;
        this.dy = (Math.random()-0.5)*6;
    }
    public boolean update() { x+=dx; y+=dy; life--; return life > 0; }
    public void draw(Graphics2D g) {
        g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), life*8));
        g.fillOval((int)x, (int)y, 4, 4);
    }
}

class Bullet {
    double x, y, angle, speed = 15.0;
    boolean isPlayerBullet;
    public Bullet(double x, double y, double a, boolean p) { this.x=x; this.y=y; this.angle=a; this.isPlayerBullet=p; }
    public void move() { x += Math.cos(angle)*speed; y += Math.sin(angle)*speed; }
}

class Detector {
    double x, y, angle, rotSpeed;
    int hp = 100, fireRate = 0;
    boolean playerSpotted = false;
    Path2D lastVisionShape;

    public Detector(double x, double y, double rs) { this.x=x; this.y=y; this.rotSpeed=rs; }

    public void update(Point2D.Double player, int[][] map, int tileSize) {
        if (lastVisionShape != null && lastVisionShape.contains(player)) {
            playerSpotted = true;
            angle = Math.atan2(player.y - y, player.x - x);
            if (fireRate > 0) fireRate--;
        } else {
            playerSpotted = false;
            angle += rotSpeed;
        }
        lastVisionShape = calculateRaycast(map, tileSize);
    }

    public boolean canShoot() {
        if (playerSpotted && fireRate <= 0) { fireRate = 30; return true; }
        return false;
    }

    private Path2D calculateRaycast(int[][] map, int tileSize) {
        Path2D path = new Path2D.Double();
        path.moveTo(x, y);
        double fov = Math.toRadians(50);
        for (double i = -fov/2; i <= fov/2; i += 0.05) {
            double rA = angle + i;
            double rX = x, rY = y;
            for (int d = 0; d < 400; d += 8) {
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