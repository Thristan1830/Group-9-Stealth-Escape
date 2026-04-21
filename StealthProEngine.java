import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.List;

public class StealthProEngine extends JFrame {
    public StealthProEngine() {
        setTitle("SHADOW PROTOCOL: FIXED SENSORS");
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

class GameEngine extends JPanel implements ActionListener, KeyListener {
    private enum State { MENU, PLAYING, GAME_OVER, WIN }
    private State currentState = State.MENU;
    private Timer gameTimer;
    private Point2D.Double player = new Point2D.Double(120, 120);
    private List<Detector> detectors = new ArrayList<>();
    private Point2D.Double distraction = null;
    private int distractionTimer = 0;
    private boolean[] keys = new boolean[256];
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
        setBackground(new Color(5, 5, 12));
        setFocusable(true);
        addKeyListener(this);
        
        // FIXED POSITIONS: Shifted coordinates to center of floor tiles
        // Logic: (Tile Column * TILE_SIZE) + (TILE_SIZE / 2)
        detectors.add(new Detector(4*TILE_SIZE+40, 1*TILE_SIZE+40, 0.04));  
        detectors.add(new Detector(1*TILE_SIZE+40, 7*TILE_SIZE+40, -0.05)); 
        detectors.add(new Detector(13*TILE_SIZE+40, 5*TILE_SIZE+40, 0.03)); 
        detectors.add(new Detector(18*TILE_SIZE+40, 1*TILE_SIZE+40, 0.06)); 
        detectors.add(new Detector(9*TILE_SIZE+40, 9*TILE_SIZE+40, -0.04)); 
        
        gameTimer = new Timer(16, this);
        gameTimer.start();
    }

    public void resetGame() {
        player = new Point2D.Double(120, 120);
        distraction = null;
        distractionTimer = 0;
        currentState = State.PLAYING;
        for(int i = 0; i < keys.length; i++) keys[i] = false;
    }

    private void update() {
        if (currentState != State.PLAYING) return;
        
        double speed = 5.0;
        double dx = 0, dy = 0;
        if (keys[KeyEvent.VK_W]) dy -= speed;
        if (keys[KeyEvent.VK_S]) dy += speed;
        if (keys[KeyEvent.VK_A]) dx -= speed;
        if (keys[KeyEvent.VK_D]) dx += speed;

        if (!isColliding(player.x + dx, player.y)) player.x += dx;
        if (!isColliding(player.x, player.y + dy)) player.y += dy;

        if (distraction != null) {
            distractionTimer--;
            if (distractionTimer <= 0) distraction = null;
        }

        pulse += 0.05f;

        for (Detector d : detectors) {
            d.update(distraction, MAP, TILE_SIZE);
            if (d.lastVisionShape != null && d.lastVisionShape.contains(player)) {
                currentState = State.GAME_OVER;
            }
        }

        int r = (int)(player.y/TILE_SIZE);
        int c = (int)(player.x/TILE_SIZE);
        if (r >= 0 && r < MAP.length && c >= 0 && c < MAP[0].length && MAP[r][c] == 2) currentState = State.WIN;
    }

    private boolean isColliding(double x, double y) {
        int r = (int)(y / TILE_SIZE);
        int c = (int)(x / TILE_SIZE);
        return r < 0 || r >= MAP.length || c < 0 || c >= MAP[0].length || MAP[r][c] == 1;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        int camX = (int)(getWidth()/2 - player.x);
        int camY = (int)(getHeight()/2 - player.y);
        g2.translate(camX, camY);
        if (currentState == State.PLAYING) drawWorld(g2);
        g2.translate(-camX, -camY);
        if (currentState != State.PLAYING) drawOverlay(g2);
    }

    private void drawWorld(Graphics2D g2) {
        for (int r = 0; r < MAP.length; r++) {
            for (int c = 0; c < MAP[r].length; c++) {
                if (MAP[r][c] == 1) {
                    g2.setColor(new Color(20, 25, 40));
                    g2.fillRect(c*TILE_SIZE, r*TILE_SIZE, TILE_SIZE, TILE_SIZE);
                } else if (MAP[r][c] == 2) {
                    g2.setColor(new Color(0, 255, 100, 150));
                    g2.fillRect(c*TILE_SIZE, r*TILE_SIZE, TILE_SIZE, TILE_SIZE);
                }
            }
        }

        if (distraction != null) {
            float s = (float)(20 + Math.sin(pulse * 10) * 10);
            g2.setColor(Color.YELLOW);
            g2.drawOval((int)distraction.x - (int)s/2, (int)distraction.y - (int)s/2, (int)s, (int)s);
        }

        for (Detector d : detectors) {
            if (d.lastVisionShape != null) {
                int alpha = (int)(130 + Math.sin(pulse) * 30); 
                g2.setPaint(new RadialGradientPaint(new Point2D.Double(d.x, d.y), 450,
                    new float[]{0f, 1f}, new Color[]{new Color(255, 50, 50, alpha), new Color(255, 0, 0, 0)}));
                g2.fill(d.lastVisionShape);
            }
            g2.setColor(Color.RED);
            g2.fillOval((int)d.x-10, (int)d.y-10, 20, 20);
        }

        float glow = (float)(15 + Math.sin(pulse * 2) * 5);
        g2.setColor(new Color(0, 255, 255, 100));
        g2.fillOval((int)player.x-(int)glow, (int)player.y-(int)glow, (int)glow*2, (int)glow*2);
        g2.setColor(Color.CYAN);
        g2.fillOval((int)player.x-10, (int)player.y-10, 20, 20);
    }

    private void drawOverlay(Graphics2D g2) {
        g2.setColor(new Color(0,0,0,220));
        g2.fillRect(0,0,getWidth(),getHeight());
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Monospaced", Font.BOLD, 40));
        String msg = currentState == State.MENU ? "REBOOTING_SYSTEM" : (currentState == State.WIN ? "MISSION_SUCCESS" : "DETECTION_FAILURE");
        g2.drawString(msg, getWidth()/2 - 200, getHeight()/2);
    }

    @Override public void actionPerformed(ActionEvent e) { update(); repaint(); }
    @Override public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() < 256) keys[e.getKeyCode()] = true;
        if (e.getKeyCode() == KeyEvent.VK_SPACE && currentState == State.PLAYING && distraction == null) {
            distraction = new Point2D.Double(player.x, player.y);
            distractionTimer = 180;
        }
        if (e.getKeyCode() == KeyEvent.VK_ENTER && currentState != State.PLAYING) resetGame();
    }
    @Override public void keyReleased(KeyEvent e) { if (e.getKeyCode() < 256) keys[e.getKeyCode()] = false; }
    @Override public void keyTyped(KeyEvent e) {}
}

class Detector {
    double x, y, angle = 0, rotSpeed;
    Path2D lastVisionShape;

    public Detector(double x, double y, double rotSpeed) {
        this.x = x; this.y = y; this.rotSpeed = rotSpeed;
    }

    public void update(Point2D.Double distraction, int[][] map, int tileSize) {
        if (distraction != null && Point2D.distance(x, y, distraction.x, distraction.y) < 450) {
            double targetAngle = Math.atan2(distraction.y - y, distraction.x - x);
            double diff = targetAngle - angle;
            while (diff <= -Math.PI) diff += Math.PI * 2;
            while (diff > Math.PI) diff -= Math.PI * 2;
            angle += diff * 0.05;
        } else {
            angle += rotSpeed;
        }
        lastVisionShape = calculateRaycastFOV(map, tileSize);
    }

    private Path2D calculateRaycastFOV(int[][] map, int tileSize) {
        Path2D path = new Path2D.Double();
        path.moveTo(x, y);
        double fov = Math.toRadians(60);
        int range = 450; 

        for (double i = -fov/2; i <= fov/2; i += 0.05) {
            double rayAngle = angle + i;
            double rayX = x, rayY = y;
            // IMPROVED COLLISION: Starts the ray slightly away from the origin
            // to prevent "self-collision" with the tile the detector is standing on
            for (int step = 2; step < range; step += 5) {
                rayX = x + Math.cos(rayAngle) * step;
                rayY = y + Math.sin(rayAngle) * step;
                int r = (int)(rayY / tileSize);
                int c = (int)(rayX / tileSize);
                if (r >= 0 && r < map.length && c >= 0 && c < map[0].length && map[r][c] == 1) break;
            }
            path.lineTo(rayX, rayY);
        }
        path.closePath();
        return path;
    }
}