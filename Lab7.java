
import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

// =====================================================
// ЛАБОРАТОРНА РОБОТА №7. ВАРІАНТ 2
// ЗАВДАННЯ 1. РУХ БДЖІЛ І ТРУТНІВ
// =====================================================

class Settings {
    static final int WIDTH = 900;
    static final int HEIGHT = 600;
    static final int RADIUS = 10;
    static final double V = 100.0;
    static final int N = 2;
    static final int OBJECTS_PER_TYPE = 5;
    static final int FRAME_DELAY = 16;
}

// Дані одного об'єкта симуляції
class MovingObject {
    final double startX;
    final double startY;

    double x;
    double y;
    double angle;
    boolean returning;
    long lastDirectionChange;

    final boolean bee;
    final Color color;

    MovingObject(double x, double y, boolean bee,
                 Color color, Random random) {
        this.x = x;
        this.y = y;
        this.startX = x;
        this.startY = y;
        this.bee = bee;
        this.color = color;
        this.angle = random.nextDouble() * Math.PI * 2.0;
        this.lastDirectionChange = System.nanoTime();
    }
}

// Спільна колекція об'єктів
class SimulationData {
    static final List<MovingObject> objects = new ArrayList<>();

    static {
        Random random = new Random();

        for (int i = 0; i < Settings.OBJECTS_PER_TYPE; i++) {
            double y = 70 + i * 90;

            objects.add(new MovingObject(
                    100, y, true,
                    new Color(245, 170, 35), random
            ));

            objects.add(new MovingObject(
                    400 + random.nextInt(250),
                    70 + i * 90,
                    false,
                    new Color(55, 135, 230),
                    random
            ));
        }
    }
}

// =====================================================
// ЗАВДАННЯ 1. АЛГОРИТМИ РУХУ
// =====================================================

// Бджоли-робітники рухаються до кута (0,0),
// а потім повертаються до початкової точки.
class WorkerBeeAI extends BaseAI {

    WorkerBeeAI(List<MovingObject> objects) {
        super("Потік бджіл-робітників", objects, true);
    }

    @Override
    protected void moveObject(
            MovingObject object,
            double deltaSeconds) {

        double targetX = Settings.RADIUS;
        double targetY = Settings.RADIUS;

        double destinationX = object.returning
                ? object.startX : targetX;

        double destinationY = object.returning
                ? object.startY : targetY;

        double dx = destinationX - object.x;
        double dy = destinationY - object.y;

        double distance = Math.sqrt(dx * dx + dy * dy);
        double step = Settings.V * deltaSeconds;

        if (distance <= step || distance < 0.001) {
            object.x = destinationX;
            object.y = destinationY;
            object.returning = !object.returning;
        } else {
            object.x += dx / distance * step;
            object.y += dy / distance * step;
        }
    }
}

// Трутні рухаються хаотично,
// змінюючи напрямок кожні N секунд.
class DroneAI extends BaseAI {

    private final Random random = new Random();

    DroneAI(List<MovingObject> objects) {
        super("Потік трутнів", objects, false);
    }

    @Override
    protected void moveObject(
            MovingObject object,
            double deltaSeconds) {

        long now = System.nanoTime();

        if (now - object.lastDirectionChange
                >= Settings.N * 1_000_000_000L) {

            object.angle = random.nextDouble()
                    * Math.PI * 2.0;

            object.lastDirectionChange = now;
        }

        object.x += Math.cos(object.angle)
                * Settings.V * deltaSeconds;

        object.y += Math.sin(object.angle)
                * Settings.V * deltaSeconds;

        // Відбиття від лівої та правої меж
        if (object.x < Settings.RADIUS) {
            object.x = Settings.RADIUS;
            object.angle = Math.PI - object.angle;

        } else if (object.x > Settings.WIDTH - Settings.RADIUS) {
            object.x = Settings.WIDTH - Settings.RADIUS;
            object.angle = Math.PI - object.angle;
        }

        // Відбиття від верхньої та нижньої меж
        if (object.y < Settings.RADIUS) {
            object.y = Settings.RADIUS;
            object.angle = -object.angle;

        } else if (object.y > Settings.HEIGHT - Settings.RADIUS) {
            object.y = Settings.HEIGHT - Settings.RADIUS;
            object.angle = -object.angle;
        }
    }
}

// =====================================================
// ЗАВДАННЯ 2. БАГАТОПОТОКОВА РЕАЛІЗАЦІЯ
// НА ОСНОВІ ЛАБОРАТОРНОЇ РОБОТИ №3
// =====================================================

// Абстрактний базовий клас потоків
abstract class BaseAI extends Thread {

    protected final List<MovingObject> objects;

    private final Object pauseLock = new Object();
    private final boolean beeType;

    private volatile boolean running = true;
    private boolean paused = false;

    BaseAI(String name,
           List<MovingObject> objects,
           boolean beeType) {

        super(name);
        this.objects = objects;
        this.beeType = beeType;
    }

    // Призупинення потоку
    public void pauseThread() {
        synchronized (pauseLock) {
            paused = true;
        }
    }

    // Відновлення роботи потоку
    public void resumeThread() {
        synchronized (pauseLock) {
            paused = false;
            pauseLock.notifyAll();
        }
    }

    // Завершення потоку
    public void stopThread() {
        running = false;
        resumeThread();
        interrupt();
    }

    // Перевірка прапорця паузи
    private void checkPause() throws InterruptedException {
        synchronized (pauseLock) {
            while (paused && running) {
                pauseLock.wait();
            }
        }
    }

    @Override
    public void run() {

        long previousTime = System.nanoTime();

        while (running) {
            try {
                checkPause();

                if (!running) {
                    break;
                }

                long currentTime = System.nanoTime();

                double deltaSeconds =
                        (currentTime - previousTime)
                                / 1_000_000_000.0;

                previousTime = currentTime;

                // Обмеження кроку після паузи
                deltaSeconds = Math.min(deltaSeconds, 0.05);

                // Синхронізований доступ до колекції
                synchronized (objects) {
                    for (MovingObject object : objects) {

                        if (object.bee == beeType) {
                            moveObject(object, deltaSeconds);
                        }
                    }
                }

                Thread.sleep(Settings.FRAME_DELAY);

            } catch (InterruptedException e) {
                if (!running) {
                    break;
                }
            }
        }
    }

    protected abstract void moveObject(
            MovingObject object,
            double deltaSeconds);
}

// =====================================================
// ГРАФІЧНЕ ВІДОБРАЖЕННЯ ОБ'ЄКТІВ
// =====================================================

class SimulationPanel extends JPanel {

    SimulationPanel() {
        setPreferredSize(
                new Dimension(Settings.WIDTH, Settings.HEIGHT)
        );

        setBackground(Color.WHITE);
    }

    @Override
    protected void paintComponent(Graphics graphics) {

        super.paintComponent(graphics);

        Graphics2D g2 = (Graphics2D) graphics.create();

        g2.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON
        );

        // Позначення кута області
        g2.setColor(Color.LIGHT_GRAY);
        g2.drawRect(0, 0, 35, 35);
        g2.drawString("(0,0)", 40, 18);

        // Синхронізоване малювання об'єктів
        synchronized (SimulationData.objects) {

            for (MovingObject object : SimulationData.objects) {

                g2.setColor(object.color);

                int diameter = Settings.RADIUS * 2;

                g2.fillOval(
                        (int) Math.round(object.x) - Settings.RADIUS,
                        (int) Math.round(object.y) - Settings.RADIUS,
                        diameter,
                        diameter
                );

                g2.setColor(Color.BLACK);

                g2.drawOval(
                        (int) Math.round(object.x) - Settings.RADIUS,
                        (int) Math.round(object.y) - Settings.RADIUS,
                        diameter,
                        diameter
                );
            }
        }

        g2.setColor(new Color(190, 125, 20));
        g2.drawString("Бджоли-робітники", 15, 20);

        g2.setColor(new Color(35, 100, 190));
        g2.drawString("Трутні", 15, 40);

        g2.dispose();
    }
}

// =====================================================
// КЛАСИ З ЛАБОРАТОРНОЇ РОБОТИ №3
// ЗАВДАННЯ 2: BODY, PARALLELEPIPED, BALL
// =====================================================

abstract class Body {

    public abstract double surfaceArea();

    public abstract double volume();

    public abstract void Show();
}

// Паралелепіпед
class Parallelepiped extends Body {

    private final double a;
    private final double b;
    private final double c;

    public Parallelepiped(double a, double b, double c) {
        this.a = a;
        this.b = b;
        this.c = c;
    }

    @Override
    public double surfaceArea() {
        return 2 * (a * b + a * c + b * c);
    }

    @Override
    public double volume() {
        return a * b * c;
    }

    @Override
    public void Show() {

        System.out.println("Паралелепіпед:");
        System.out.println("a = " + a);
        System.out.println("b = " + b);
        System.out.println("c = " + c);

        System.out.println(
                "Площа поверхні = " + surfaceArea()
        );

        System.out.println("Об'єм = " + volume());
    }

    @Override
    public String toString() {
        return "Parallelepiped{a=" + a
                + ", b=" + b
                + ", c=" + c + '}';
    }

    @Override
    public boolean equals(Object obj) {

        if (this == obj) {
            return true;
        }

        if (!(obj instanceof Parallelepiped)) {
            return false;
        }

        Parallelepiped other = (Parallelepiped) obj;

        return Double.compare(a, other.a) == 0
                && Double.compare(b, other.b) == 0
                && Double.compare(c, other.c) == 0;
    }
}

// Куля
class Ball extends Body {

    private final double radius;

    public Ball(double radius) {
        this.radius = radius;
    }

    @Override
    public double surfaceArea() {
        return 4 * Math.PI * radius * radius;
    }

    @Override
    public double volume() {
        return (4.0 / 3.0)
                * Math.PI
                * radius * radius * radius;
    }

    @Override
    public void Show() {

        System.out.println("Куля:");
        System.out.println("Радіус = " + radius);

        System.out.println(
                "Площа поверхні = " + surfaceArea()
        );

        System.out.println("Об'єм = " + volume());
    }

    @Override
    public String toString() {
        return "Ball{radius=" + radius + '}';
    }

    @Override
    public boolean equals(Object obj) {

        if (this == obj) {
            return true;
        }

        if (!(obj instanceof Ball)) {
            return false;
        }

        Ball other = (Ball) obj;

        return Double.compare(radius, other.radius) == 0;
    }
}

// =====================================================
// ГОЛОВНИЙ КЛАС ПРОГРАМИ
// =====================================================

public class Lab7 {

    // Створення списку пріоритетів
    private static JComboBox<String> createPriorityBox(
            BaseAI thread) {

        String[] priorities = {
                "MIN_PRIORITY",
                "NORM_PRIORITY",
                "MAX_PRIORITY"
        };

        JComboBox<String> comboBox =
                new JComboBox<>(priorities);

        comboBox.setSelectedIndex(1);

        comboBox.addActionListener(event -> {

            int selected = comboBox.getSelectedIndex();

            if (selected == 0) {
                thread.setPriority(Thread.MIN_PRIORITY);

            } else if (selected == 1) {
                thread.setPriority(Thread.NORM_PRIORITY);

            } else {
                thread.setPriority(Thread.MAX_PRIORITY);
            }
        });

        return comboBox;
    }

    // Панель керування окремим видом об'єктів
    private static JPanel createControlPanel(
            String title,
            BaseAI thread) {

        JPanel panel = new JPanel(
                new FlowLayout(FlowLayout.LEFT)
        );

        panel.setBorder(
                BorderFactory.createTitledBorder(title)
        );

        JButton pauseButton = new JButton("Пауза");
        JButton resumeButton = new JButton("Продовжити");

        JComboBox<String> priorityBox =
                createPriorityBox(thread);

        pauseButton.addActionListener(
                event -> thread.pauseThread()
        );

        resumeButton.addActionListener(
                event -> thread.resumeThread()
        );

        panel.add(pauseButton);
        panel.add(resumeButton);
        panel.add(new JLabel("Пріоритет:"));
        panel.add(priorityBox);

        return panel;
    }

    // Демонстрація класів із лабораторної №3
    private static void showLab3Task2() {

        System.out.println(
                "===== Частина з лабораторної роботи №3 ====="
        );

        Body parallelepiped =
                new Parallelepiped(2, 3, 4);

        Body ball = new Ball(5);

        Body[] bodies = {
                parallelepiped,
                ball
        };

        for (Body body : bodies) {
            body.Show();
            System.out.println();
        }

        System.out.println("Перевірка toString():");
        System.out.println(parallelepiped);
        System.out.println(ball);

        System.out.println("Перевірка equals():");

        Parallelepiped p1 =
                new Parallelepiped(2, 3, 4);

        Parallelepiped p2 =
                new Parallelepiped(2, 3, 4);

        System.out.println(
                "p1.equals(p2) = " + p1.equals(p2)
        );

        System.out.println();
    }

    // Створення графічного інтерфейсу
    private static void createAndShowGUI() {

        JFrame frame = new JFrame(
                "Лабораторна робота №7 — Варіант 2"
        );

        frame.setDefaultCloseOperation(
                JFrame.DISPOSE_ON_CLOSE
        );

        SimulationPanel simulationPanel =
                new SimulationPanel();

        WorkerBeeAI beeThread =
                new WorkerBeeAI(SimulationData.objects);

        DroneAI droneThread =
                new DroneAI(SimulationData.objects);

        JPanel controls = new JPanel(
                new GridLayout(2, 1)
        );

        controls.add(
                createControlPanel(
                        "Керування бджолами-робітниками",
                        beeThread
                )
        );

        controls.add(
                createControlPanel(
                        "Керування трутнями",
                        droneThread
                )
        );

        frame.setLayout(new BorderLayout());

        frame.add(
                simulationPanel,
                BorderLayout.CENTER
        );

        frame.add(
                controls,
                BorderLayout.SOUTH
        );

        frame.pack();
        frame.setLocationRelativeTo(null);

        // Таймер Swing працює в EDT
        // і лише оновлює графічне відображення
        Timer repaintTimer = new Timer(
                Settings.FRAME_DELAY,
                event -> simulationPanel.repaint()
        );

        repaintTimer.start();

        // Запуск двох окремих потоків
        beeThread.start();
        droneThread.start();

        frame.addWindowListener(new WindowAdapter() {

            @Override
            public void windowClosed(WindowEvent event) {

                repaintTimer.stop();

                beeThread.stopThread();
                droneThread.stopThread();
            }
        });

        frame.setVisible(true);
    }

    public static void main(String[] args) {

        showLab3Task2();

        SwingUtilities.invokeLater(
                Lab7::createAndShowGUI
        );
    }
}
