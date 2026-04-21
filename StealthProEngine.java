import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.List;
 
public class StealthProEngine extends JFrame {
    public StealthProEngine() {
        setTitle("SHADOW PROTOCOL: ARCHITECT");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);
        add(new GameEngine());
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
    private Point2D.Double player = new Point2D.Double(90, 90);
    private List<Detector> detectors = new ArrayList<>();
    private Point2D.Double distraction = null;
    private int distractionTimer = 0;
   
    private boolean[] keys = new boolean[256];
    private float pulse = 0f;
    private final int TILE_SIZE = 80; // Bigger tiles for better visibility
 
    // 1=Wall, 0=Path, 2=Goal
    private final int[][] MAP = {
        {1,1,1,1,1,1,1,1,1,1,1,1,1,1,1},
        {1,0,0,0,0,0,1,0,0,0,0,0,0,0,1},
        {1,0,1,1,1,0,1,0,1,1,1,1,1,0,1},
        {1,0,0,0,1,0,0,0,1,0,0,0,1,0,1},
        {1,1,1,0,1,1,1,1,1,0,1,0,1,0,1},
        {1,0,0,0,0,0,0,0,0,0,1,0,0,0,1},
        {1,0,1,1,1,1,1,1,1,1,1,1,1,0,1},
        {1,0,1,0,0,0,0,0,0,0,0,0,1,0,1},
        {1,0,1,0,1,1,1,0,1,1,1,0,1,0,1},
        {1,0,0,0,1,0,0,0,1,2,1,0,0,0,1},
        {1,1,1,1,1,1,1,1,1,1,1,1,1,1,1}
    };
 
    public GameEngine() {
        setPreferredSize(new Dimension(1000, 700));
        setBackground(new Color(5, 5, 12));
        setFocusable(true);
        addKeyListener(this);
       
        // Setup Detectors
        detectors.add(new Detector(440, 120, 0.03));
        detectors.add(new Detector(120, 520, -0.05));
        detectors.add(new Detector(840, 760, 0.04));
       
        gameTimer = new Timer(16, this);
        gameTimer.start();
    }
 
    private void update() {
        if (currentState != State.PLAYING) return;
 
        // Player Movement
        double speed = 4.0;
        double dx = 0, dy = 0;
        if (keys[KeyEvent.VK_W]) dy -= speed;
        if (keys[KeyEvent.VK_S]) dy += speed;
        if (keys[KeyEvent.VK_A]) dx -= speed;
        if (keys[KeyEvent.VK_D]) dx += speed;
 
        if (!isColliding(player.x + dx, player.y)) player.x += dx;
        if (!isColliding(player.x, player.y + dy)) player.y += dy;
 
        // Distraction Logic
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
 
        if (MAP[(int)(player.y/TILE_SIZE)][(int)(player.x/TILE_SIZE)] == 2) currentState = State.WIN;
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
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
 
        // CAMERA FOLLOW: Offset everything by player position
        int camX = (int)(getWidth()/2 - player.x);
        int camY = (int)(getHeight()/2 - player.y);
        g2.translate(camX, camY);
 
        if (currentState == State.PLAYING) drawWorld(g2);
       
        // Reset translation for UI
        g2.translate(-camX, -camY);
        if (currentState != State.PLAYING) drawOverlay(g2);
    }
 
    private void drawWorld(Graphics2D g2) {
        // Draw Maze
        for (int r = 0; r < MAP.length; r++) {
            for (int c = 0; c < MAP[r].length; c++) {
                if (MAP[r][c] == 1) {
                    g2.setColor(new Color(20, 25, 40));
                    g2.fillRect(c*TILE_SIZE, r*TILE_SIZE, TILE_SIZE, TILE_SIZE);
                    g2.setColor(new Color(40, 50, 80));
                    g2.drawRect(c*TILE_SIZE, r*TILE_SIZE, TILE_SIZE, TILE_SIZE);
                } else if (MAP[r][c] == 2) {
                    g2.setColor(new Color(0, 255, 100, 100));
                    g2.fillRect(c*TILE_SIZE, r*TILE_SIZE, TILE_SIZE, TILE_SIZE);
                }
            }
        }
 
        // Draw Distraction
        if (distraction != null) {
            g2.setColor(Color.YELLOW);
            float s = (float)Math.abs(Math.sin(pulse*5)*15);
            g2.draw(new Ellipse2D.Double(distraction.x - s/2, distraction.y - s/2, s, s));
        }
 
        // Draw Detectors & Vision
        for (Detector d : detectors) {
            if (d.lastVisionShape != null) {
                g2.setPaint(new RadialGradientPaint(new Point2D.Double(d.x, d.y), 300,
                    new float[]{0f, 1f}, new Color[]{new Color(255, 50, 50, 150), new Color(255, 0, 0, 0)}));
                g2.fill(d.lastVisionShape);
            }
            g2.setColor(Color.RED);
            g2.fillOval((int)d.x-8, (int)d.y-8, 16, 16);
        }
 
        // Draw Player
        g2.setColor(Color.CYAN);
        g2.fillOval((int)player.x-10, (int)player.y-10, 20, 20);
        g2.setStroke(new BasicStroke(2));
        g2.drawOval((int)player.x-15, (int)player.y-15, 30, 30);
    }
 
    private void drawOverlay(Graphics2D g2) {
        g2.setColor(new Color(0,0,0,200));
        g2.fillRect(0,0,1000,700);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Monospaced", Font.BOLD, 50));
        String msg = currentState == State.MENU ? "SHADOW_PROTOCOL" : (currentState == State.WIN ? "MISSION_COMPLETE" : "SYSTEM_HALTED");
        g2.drawString(msg, 250, 300);
        g2.setFont(new Font("SansSerif", Font.PLAIN, 20));
        g2.drawString("PRESS [ENTER] TO START | [SPACE] FOR DISTRACTION", 240, 360);
    }
 
    @Override public void actionPerformed(ActionEvent e) { update(); repaint(); }
    @Override public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() < 256) keys[e.getKeyCode()] = true;
        if (e.getKeyCode() == KeyEvent.VK_SPACE && currentState == State.PLAYING && distraction == null) {
            distraction = new Point2D.Double(player.x, player.y);
            distractionTimer = 180; // 3 seconds
        }
        if (e.getKeyCode() == KeyEvent.VK_ENTER && currentState != State.PLAYING) {
            player = new Point2D.Double(120, 120);
            currentState = State.PLAYING;
        }
    }
    @Override public void keyReleased(KeyEvent e) { if (e.getKeyCode() < 256) keys[e.getKeyCode()] = false; }
    @Override public void keyTyped(KeyEvent e) {}
}
 
class Detector {
    double x, y, angle = 0;
    double rotSpeed;
    Path2D lastVisionShape;
 
    public Detector(double x, double y, double rotSpeed) {
        this.x = x; this.y = y; this.rotSpeed = rotSpeed;
    }
 
    public void update(Point2D.Double distraction, int[][] map, int tileSize) {
        if (distraction != null && Point2D.distance(x, y, distraction.x, distraction.y) < 400) {
            // Point towards distraction
            double targetAngle = Math.atan2(distraction.y - y, distraction.x - x);
            angle += (targetAngle - angle) * 0.1;
        } else {
            angle += rotSpeed;
        }
        lastVisionShape = calculateRaycastFOV(map, tileSize);
    }
 
    private Path2D calculateRaycastFOV(int[][] map, int tileSize) {
        Path2D path = new Path2D.Double();
        path.moveTo(x, y);
        double fov = Math.toRadians(50);
        int range = 300;
 
        for (double i = -fov/2; i <= fov/2; i += 0.05) {
            double rayAngle = angle + i;
            double rayX = x;
            double rayY = y;
           
            // Raymarching: step along the ray until we hit a wall
            for (int step = 0; step < range; step += 5) {
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