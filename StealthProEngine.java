import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.RadialGradientPaint;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class StealthProEngine extends JFrame {
    public StealthProEngine() {
        setTitle("SHADOW PROTOCOL: ELITE EDITION");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);
        setLayout(new BorderLayout());

        GameEngine engine = new GameEngine();

        JPanel uiPanel = new JPanel();
        uiPanel.setBackground(new Color(10, 10, 25));
        JButton restartBtn = new JButton("RESTART MISSION");
        restartBtn.setFocusable(false);
        restartBtn.addActionListener(e -> engine.resetGame());

        uiPanel.add(restartBtn);
        add(uiPanel, BorderLayout.NORTH);
        add(engine, BorderLayout.CENTER);

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

    private Point2D.Double player = new Point2D.Double(120, 120);
    private double playerAngle = 0;
    private int playerHP = 100;

    private final int MAX_MAG = 12;
    private int ammoInMag = 12;
    private int magsReserved = 3; // FIX 1: increased reserves so player isn't immediately dry
    private int reloadTimer = 0;
    private final int RELOAD_DURATION = 90;

    private boolean fireFromRight = true;
    private int muzzleFlashTimer = 0;

    private List<Detector> detectors = new ArrayList<>();
    private List<Bullet> bullets = new ArrayList<>();
    private boolean[] keys = new boolean[65536]; // FIX 2: was 256, caused ArrayIndexOutOfBoundsException on special keys
    private Point mousePos = new Point(0, 0);
    private float pulse = 0f;
    private final int TILE_SIZE = 80;
    private final int PLAYER_RADIUS = 12; // FIX 3: named constant for collision radius

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
        {1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,1}, // exit at [13][17]
        {1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1}
    };

    public GameEngine() {
        setPreferredSize(new Dimension(1000, 700));
        setBackground(new Color(5, 5, 12));
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
        detectors.add(new Detector(4*TILE_SIZE+40, 1*TILE_SIZE+40, 0.08));
        detectors.add(new Detector(1*TILE_SIZE+40, 7*TILE_SIZE+40, -0.09));
        detectors.add(new Detector(13*TILE_SIZE+40, 5*TILE_SIZE+40, 0.07));
        detectors.add(new Detector(18*TILE_SIZE+40, 1*TILE_SIZE+40, 0.12));
        detectors.add(new Detector(9*TILE_SIZE+40, 9*TILE_SIZE+40, -0.08));
    }

    public void resetGame() {
        player = new Point2D.Double(120, 120);
        playerHP = 100;
        ammoInMag = MAX_MAG;
        magsReserved = 3;
        reloadTimer = 0;
        bullets.clear();
        initLevel();
        currentState = State.PLAYING;
        for (int i = 0; i < keys.length; i++) keys[i] = false;
        this.requestFocusInWindow();
    }

    private void update() {
        if (currentState != State.PLAYING) return;

        // Reload timer countdown
        if (reloadTimer > 0) {
            reloadTimer--;
            if (reloadTimer == 0) {
                ammoInMag = MAX_MAG;
                magsReserved--;
            }
        }

        // FIX 4: auto-reload when mag is empty and reserves available
        if (ammoInMag == 0 && magsReserved > 0 && reloadTimer <= 0) {
            reloadTimer = RELOAD_DURATION;
        }

        double speed = 4.0;
        double dx = 0, dy = 0;
        if (keys[KeyEvent.VK_W]) dy -= speed;
        if (keys[KeyEvent.VK_S]) dy += speed;
        if (keys[KeyEvent.VK_A]) dx -= speed;
        if (keys[KeyEvent.VK_D]) dx += speed;

        // FIX 5: radius-based collision instead of single-point
        if (!isCollidingWithRadius(player.x + dx, player.y)) player.x += dx;
        if (!isCollidingWithRadius(player.x, player.y + dy)) player.y += dy;

        int camX = (int)(getWidth() / 2 - player.x);
        int camY = (int)(getHeight() / 2 - player.y);
        playerAngle = Math.atan2((mousePos.y - camY) - player.y, (mousePos.x - camX) - player.x);

        if (muzzleFlashTimer > 0) muzzleFlashTimer--;
        pulse += 0.05f;

        for (Detector d : detectors) {
            d.update(player, MAP, TILE_SIZE);
            if (d.canShoot()) bullets.add(new Bullet(d.x, d.y, d.angle, false));
        }

        // FIX 6: bullets now have a max lifetime to prevent off-screen accumulation
        Iterator<Bullet> bIter = bullets.iterator();
        while (bIter.hasNext()) {
            Bullet b = bIter.next();
            b.move();
            if (b.isExpired() || isColliding(b.x, b.y)) { bIter.remove(); continue; }
            if (b.isPlayerBullet) {
                for (Detector d : detectors) {
                    if (Point2D.distance(b.x, b.y, d.x, d.y) < 20) {
                        d.hp -= 25;
                        bIter.remove();
                        break;
                    }
                }
            } else if (Point2D.distance(b.x, b.y, player.x, player.y) < 15) {
                playerHP -= 10;
                bIter.remove();
            }
        }

        detectors.removeIf(d -> d.hp <= 0);
        if (playerHP <= 0) currentState = State.GAME_OVER;
        checkWinCondition();
    }

    private void checkWinCondition() {
        // FIX 7: check center of player tile correctly (row = y, col = x)
        int r = (int)(player.y / TILE_SIZE);
        int c = (int)(player.x / TILE_SIZE);
        if (r >= 0 && r < MAP.length && c >= 0 && c < MAP[0].length && MAP[r][c] == 2)
            currentState = State.WIN;
    }

    // Original single-point collision (used for bullets)
    private boolean isColliding(double x, double y) {
        int r = (int)(y / TILE_SIZE);
        int c = (int)(x / TILE_SIZE);
        return r < 0 || r >= MAP.length || c < 0 || c >= MAP[0].length || MAP[r][c] == 1;
    }

    // FIX 5: radius-based collision for the player body
    private boolean isCollidingWithRadius(double x, double y) {
        double r = PLAYER_RADIUS * 0.8; // slightly smaller than visual radius for comfort
        return isColliding(x - r, y - r) || isColliding(x + r, y - r)
            || isColliding(x - r, y + r) || isColliding(x + r, y + r);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); // FIX 8: smoother rendering
        int camX = (int)(getWidth() / 2 - player.x);
        int camY = (int)(getHeight() / 2 - player.y);

        g2.translate(camX, camY);
        if (currentState != State.MENU) drawWorld(g2);
        g2.translate(-camX, -camY);

        if (currentState == State.PLAYING || currentState == State.PAUSED) drawHUD(g2);
        if (currentState != State.PLAYING) drawOverlay(g2);
    }

    private void drawWorld(Graphics2D g2) {
        for (int r = 0; r < MAP.length; r++) {
            for (int c = 0; c < MAP[r].length; c++) {
                if (MAP[r][c] == 1) {
                    g2.setColor(new Color(20, 25, 40));
                    g2.fillRect(c * TILE_SIZE, r * TILE_SIZE, TILE_SIZE, TILE_SIZE);
                } else if (MAP[r][c] == 2) {
                    // FIX 9: pulsing exit tile so it's clearly visible
                    int alpha = (int)(120 + Math.sin(pulse) * 80);
                    g2.setColor(new Color(0, 255, 100, Math.min(255, alpha)));
                    g2.fillRect(c * TILE_SIZE, r * TILE_SIZE, TILE_SIZE, TILE_SIZE);
                    g2.setColor(Color.WHITE);
                    g2.setFont(new Font("Monospaced", Font.BOLD, 12));
                    g2.drawString("EXIT", c * TILE_SIZE + 24, r * TILE_SIZE + 44);
                }
            }
        }

        for (Bullet b : bullets) {
            g2.setColor(b.isPlayerBullet ? Color.CYAN : Color.YELLOW);
            g2.fillOval((int)b.x - 3, (int)b.y - 3, 6, 6);
        }

        for (Detector d : detectors) {
            if (d.lastVisionShape != null) {
                int alpha = (int)(80 + Math.sin(pulse) * 20);
                g2.setPaint(new RadialGradientPaint(new Point2D.Double(d.x, d.y), 450,
                    new float[]{0f, 1f},
                    new Color[]{new Color(255, 50, 50, alpha), new Color(255, 0, 0, 0)}));
                g2.fill(d.lastVisionShape);
            }
            g2.setColor(d.playerSpotted ? Color.ORANGE : Color.RED); // FIX 10: visual alert when spotted
            g2.fillOval((int)d.x - 10, (int)d.y - 10, 20, 20);
            drawHPBar(g2, (int)d.x - 20, (int)d.y - 25, d.hp, Color.RED);
        }

        // Draw player
        g2.translate(player.x, player.y);
        g2.rotate(playerAngle);
        g2.setColor(Color.CYAN);
        g2.fillOval(-12, -12, 24, 24);
        g2.setColor(Color.WHITE);
        g2.fillRect(8, 5, 14, 4);
        g2.fillRect(8, -9, 14, 4);
        if (muzzleFlashTimer > 0) {
            g2.setColor(Color.YELLOW);
            int flashY = fireFromRight ? -9 : 5;
            g2.fillOval(20, flashY - 2, 8, 8);
        }
        g2.rotate(-playerAngle);
        g2.translate(-player.x, -player.y);
        drawHPBar(g2, (int)player.x - 20, (int)player.y - 25, playerHP, Color.CYAN);
    }

    private void drawHPBar(Graphics2D g2, int x, int y, int hp, Color color) {
        g2.setColor(Color.DARK_GRAY);
        g2.fillRect(x, y, 40, 4);
        g2.setColor(color);
        g2.fillRect(x, y, (int)(40 * (hp / 100.0)), 4);
    }

    private void drawHUD(Graphics2D g2) {
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Monospaced", Font.BOLD, 20));
        // FIX 11: show "EMPTY" clearly when out of ammo and no reserves
        String ammoDisplay = (ammoInMag == 0 && magsReserved == 0) ? "AMMO: EMPTY" : "AMMO: " + ammoInMag + " / " + magsReserved;
        g2.drawString(ammoDisplay, 20, getHeight() - 20);
        g2.drawString("[P] PAUSE  [R] RELOAD", getWidth() - 230, getHeight() - 20);

        if (reloadTimer > 0) {
            g2.setColor(Color.GRAY);
            g2.fillRect(20, getHeight() - 60, 100, 10);
            g2.setColor(Color.CYAN);
            int width = (int)(100 * (1.0 - (double)reloadTimer / RELOAD_DURATION));
            g2.fillRect(20, getHeight() - 60, width, 10);
            g2.setColor(Color.WHITE);
            g2.drawString("RELOADING...", 130, getHeight() - 50);
        }
    }

    private void drawOverlay(Graphics2D g2) {
        g2.setColor(new Color(0, 0, 0, 200));
        g2.fillRect(0, 0, getWidth(), getHeight());

        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Monospaced", Font.BOLD, 50));

        String title = "";
        String subtitle = "";

        switch (currentState) {
            case MENU:
                title = "LEVEL 1";
                subtitle = "PRESS [ENTER] TO COMMENCE MISSION";
                break;
            case PAUSED:
                title = "SYSTEM_PAUSED";
                subtitle = "PRESS [P] TO RESUME";
                break;
            case WIN:
                title = "MISSION SUCCESS";
                subtitle = "EXTRACTION COMPLETE - PRESS [ENTER] TO REPLAY";
                break;
            case GAME_OVER:
                title = "CRITICAL FAILURE";
                subtitle = "UNIT TERMINATED - PRESS [ENTER] TO REBOOT";
                break;
            default: break;
        }

        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(title, (getWidth() - fm.stringWidth(title)) / 2, getHeight() / 2);
        g2.setFont(new Font("Monospaced", Font.PLAIN, 18));
        fm = g2.getFontMetrics();
        g2.drawString(subtitle, (getWidth() - fm.stringWidth(subtitle)) / 2, getHeight() / 2 + 50);
    }

    @Override public void actionPerformed(ActionEvent e) { update(); repaint(); }

    @Override public void keyPressed(KeyEvent e) {
        int code = e.getKeyCode();
        if (code >= 0 && code < keys.length) keys[code] = true;

        if (code == KeyEvent.VK_P) {
            if (currentState == State.PLAYING) currentState = State.PAUSED;
            else if (currentState == State.PAUSED) currentState = State.PLAYING;
        }

        if (code == KeyEvent.VK_R && currentState == State.PLAYING) {
            if (magsReserved > 0 && ammoInMag < MAX_MAG && reloadTimer <= 0)
                reloadTimer = RELOAD_DURATION;
        }

        if (code == KeyEvent.VK_ENTER && currentState != State.PLAYING && currentState != State.PAUSED) {
            resetGame();
        }
    }

    @Override public void keyReleased(KeyEvent e) {
        int code = e.getKeyCode();
        if (code >= 0 && code < keys.length) keys[code] = false;
    }
    @Override public void keyTyped(KeyEvent e) {}

    @Override public void mousePressed(MouseEvent e) {
        if (currentState == State.PLAYING && ammoInMag > 0 && reloadTimer <= 0) {
            double offset = fireFromRight ? 7 : -7;
            double spawnX = player.x + Math.cos(playerAngle) * 20 - Math.sin(playerAngle) * offset;
            double spawnY = player.y + Math.sin(playerAngle) * 20 + Math.cos(playerAngle) * offset;
            bullets.add(new Bullet(spawnX, spawnY, playerAngle, true));
            fireFromRight = !fireFromRight;
            muzzleFlashTimer = 3;
            ammoInMag--;
        }
    }
    @Override public void mouseMoved(MouseEvent e) { mousePos = e.getPoint(); }
    @Override public void mouseDragged(MouseEvent e) { mousePos = e.getPoint(); }
    @Override public void mouseClicked(MouseEvent e) {}
    @Override public void mouseReleased(MouseEvent e) {}
    @Override public void mouseEntered(MouseEvent e) {}
    @Override public void mouseExited(MouseEvent e) {}
}

class Bullet {
    double x, y, angle;
    boolean isPlayerBullet;
    double speed = 12.0;
    private int life = 0;
    private static final int MAX_LIFE = 120; // FIX 6: bullets expire after ~2 seconds

    public Bullet(double x, double y, double angle, boolean player) {
        this.x = x; this.y = y; this.angle = angle; this.isPlayerBullet = player;
    }
    public void move() { x += Math.cos(angle) * speed; y += Math.sin(angle) * speed; life++; }
    public boolean isExpired() { return life >= MAX_LIFE; }
}

class Detector {
    double x, y, angle = 0, rotSpeed;
    int hp = 100, fireRate = 0;
    Path2D lastVisionShape;
    boolean playerSpotted = false;

    public Detector(double x, double y, double rotSpeed) { this.x = x; this.y = y; this.rotSpeed = rotSpeed; }

    public void update(Point2D.Double player, int[][] map, int tileSize) {
        if (lastVisionShape != null && lastVisionShape.contains(player)) {
            playerSpotted = true;
            double targetAngle = Math.atan2(player.y - y, player.x - x);
            double diff = targetAngle - angle;
            while (diff <= -Math.PI) diff += Math.PI * 2;
            while (diff > Math.PI) diff -= Math.PI * 2;
            angle += diff * 0.3;
        } else {
            playerSpotted = false;
            angle += rotSpeed;
        }
        if (fireRate > 0) fireRate--;
        lastVisionShape = calculateRaycastFOV(map, tileSize);
    }

    public boolean canShoot() {
        if (playerSpotted && fireRate <= 0) { fireRate = 20; return true; }
        return false;
    }

    private Path2D calculateRaycastFOV(int[][] map, int tileSize) {
        Path2D path = new Path2D.Double();
        path.moveTo(x, y);
        double fov = Math.toRadians(60);
        int range = 450;
        for (double i = -fov / 2; i <= fov / 2; i += 0.05) {
            double rayAngle = angle + i;
            double rayX = x, rayY = y;
            for (int step = 5; step < range; step += 5) {
                rayX = x + Math.cos(rayAngle) * step;
                rayY = y + Math.sin(rayAngle) * step;
                int r = (int)(rayY / tileSize), c = (int)(rayX / tileSize);
                if (r >= 0 && r < map.length && c >= 0 && c < map[0].length && map[r][c] == 1) break;
            }
            path.lineTo(rayX, rayY);
        }
        path.closePath();
        return path;
    }
}