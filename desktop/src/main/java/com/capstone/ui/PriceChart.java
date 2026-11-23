package com.capstone.ui;

import com.capstone.model.PricePoint;
import com.capstone.model.Stock;
import com.capstone.prediction.BacktestResult;
import com.capstone.prediction.Backtester;
import com.capstone.prediction.Forecaster;
import com.capstone.prediction.Forecasters;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Line chart of a stock's daily closes (or any dated series, e.g. portfolio value) for a chosen period, with the date and value
 * under the mouse, and optionally the next FORECAST_DAYS of the most accurate model's
 * forecast with its 80% range. Drawn with Java2D using the current look-and-feel colours.
 */
class PriceChart extends JComponent {

    enum Period {
        ONE_MONTH("1M", 1), THREE_MONTHS("3M", 3), SIX_MONTHS("6M", 6), ONE_YEAR("1Y", 12), ALL("All", 0);
        final String label; final int months;
        Period(String label, int months) { this.label = label; this.months = months; }
    }

    static final int FORECAST_DAYS = 10;
    private static final int LEFT = 64, RIGHT = 16, TOP = 34, BOTTOM = 28;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MMM d");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMM yy");

    private String title = "", subtitle = "";
    private List<PricePoint> series;          // null until a stock or series is set
    private List<Double> forecastCloses;      // null when forecasts don't apply (e.g. portfolio value)
    private String emptyMessage = "Select a stock to see its chart.";
    private Period period = Period.THREE_MONTHS;
    private boolean showForecast;

    private List<PricePoint> visible = List.of();
    private List<double[]> forecast = List.of();      // {point, low, high} per day ahead
    private String forecastModel = "";
    private int hover = -1;

    PriceChart() {
        setPreferredSize(new Dimension(600, 260));
        setOpaque(true);
        MouseAdapter mouse = new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e) { setHover(indexAt(e.getX())); }
            @Override public void mouseExited(MouseEvent e) { setHover(-1); }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    void setStock(Stock stock) {
        title = stock == null ? "" : stock.getSymbol();
        subtitle = stock == null ? "" : stock.getName();
        series = stock == null ? null : stock.getHistory();
        forecastCloses = stock == null ? null : stock.getPriceHistory();
        recompute();
    }

    /** Charts any dated series (no forecast option); shows emptyMessage when it's empty. */
    void setSeries(String title, List<PricePoint> points, String emptyMessage) {
        this.title = title;
        this.subtitle = "";
        this.series = points;
        this.forecastCloses = null;
        this.emptyMessage = emptyMessage;
        recompute();
    }
    void setPeriod(Period period) { this.period = period; recompute(); }
    void setShowForecast(boolean show) { this.showForecast = show; recompute(); }

    private void setHover(int i) { if (i != hover) { hover = i; repaint(); } }

    private void recompute() {
        hover = -1;
        visible = series == null ? List.of() : slice(series, period);
        forecast = List.of();
        forecastModel = "";
        if (forecastCloses != null && showForecast) {
            List<Double> closes = forecastCloses;
            List<BacktestResult> results = Backtester.compareAll(closes, FORECAST_DAYS);
            BacktestResult best = results.stream().filter(r -> r.samples() > 0)
                .min(Comparator.comparingDouble(BacktestResult::mae)).orElse(null);
            if (best != null) {
                Forecaster f = Forecasters.ALL.stream().filter(x -> x.name().equals(best.forecaster())).findFirst().orElseThrow();
                List<Double> points = f.forecast(closes, FORECAST_DAYS);
                List<double[]> out = new ArrayList<>();
                for (int d = 1; d <= FORECAST_DAYS; d++) {
                    double[] range = best.range(d, points.get(d - 1));
                    out.add(new double[]{points.get(d - 1), range[0], range[1]});
                }
                forecast = out;
                forecastModel = best.forecaster();
            }
        }
        repaint();
    }

    /** The closes on or after {@code months} before the latest date (all of them for 0). */
    static List<PricePoint> slice(List<PricePoint> history, Period period) {
        if (history.isEmpty() || period.months == 0) return history;
        LocalDate from = history.get(history.size() - 1).date().minusMonths(period.months);
        int i = 0;
        while (i < history.size() - 1 && history.get(i).date().isBefore(from)) i++;
        return history.subList(i, history.size());
    }

    /** Round-number axis ticks covering [min, max]: e.g. 312.4–357.9 → 310, 320 … 360. */
    static double[] niceTicks(double min, double max, int targetCount) {
        if (max <= min) { max = min + 1; min = min - 1; }
        double step = niceStep((max - min) / Math.max(1, targetCount - 1));
        double start = Math.floor(min / step) * step;
        double end = Math.ceil(max / step) * step;
        int n = (int) Math.round((end - start) / step) + 1;
        double[] ticks = new double[n];
        for (int i = 0; i < n; i++) ticks[i] = start + i * step;
        return ticks;
    }

    private static double niceStep(double rough) {
        double mag = Math.pow(10, Math.floor(Math.log10(rough)));
        double f = rough / mag;
        double nice = f <= 1 ? 1 : f <= 2 ? 2 : f <= 2.5 ? 2.5 : f <= 5 ? 5 : 10;
        return nice * mag;
    }

    // ----------------------------------------------------------------
    // Layout helpers
    // ----------------------------------------------------------------

    private int slots() { return visible.size() + forecast.size(); }

    private double xOf(int i) {
        int w = getWidth() - LEFT - RIGHT;
        return LEFT + (slots() <= 1 ? w / 2.0 : (double) i * w / (slots() - 1));
    }

    private int indexAt(int x) {
        if (visible.isEmpty()) return -1;
        int w = getWidth() - LEFT - RIGHT;
        int i = (int) Math.round((x - LEFT) * (slots() - 1) / (double) Math.max(1, w));
        return i < 0 || i >= visible.size() ? -1 : i;
    }

    // ----------------------------------------------------------------
    // Painting
    // ----------------------------------------------------------------

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Color bg = UIManager.getColor("Table.background"), fg = UIManager.getColor("Label.foreground");
        Color grid = UIManager.getColor("Separator.foreground");
        if (bg == null) bg = Color.WHITE;
        if (fg == null) fg = Color.DARK_GRAY;
        if (grid == null) grid = Color.LIGHT_GRAY;
        g.setColor(bg);
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setFont(getFont() != null ? getFont().deriveFont(11f) : new Font("SansSerif", Font.PLAIN, 11));

        if (visible.isEmpty()) {
            g.setColor(fg);
            g.drawString(series == null || series.isEmpty() ? emptyMessage : "No data for this period.", LEFT, getHeight() / 2);
            g.dispose();
            return;
        }

        double first = visible.get(0).close(), last = visible.get(visible.size() - 1).close();
        Color line = last >= first ? Ui.GAIN : Ui.LOSS;

        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        for (PricePoint p : visible) { lo = Math.min(lo, p.close()); hi = Math.max(hi, p.close()); }
        for (double[] f : forecast) { lo = Math.min(lo, f[1]); hi = Math.max(hi, f[2]); }
        double[] ticks = niceTicks(lo, hi, 5);
        double yMin = ticks[0], yMax = ticks[ticks.length - 1];
        int plotH = getHeight() - TOP - BOTTOM;
        java.util.function.DoubleUnaryOperator yOf = v -> TOP + (yMax - v) / (yMax - yMin) * plotH;

        // Grid and price axis
        FontMetrics fm = g.getFontMetrics();
        for (double t : ticks) {
            int y = (int) Math.round(yOf.applyAsDouble(t));
            g.setColor(grid);
            g.drawLine(LEFT, y, getWidth() - RIGHT, y);
            g.setColor(fg);
            String label = String.format("$%,.0f", t);
            if (yMax - yMin < 10) label = String.format("$%,.2f", t);
            g.drawString(label, LEFT - 8 - fm.stringWidth(label), y + fm.getAscent() / 2 - 1);
        }

        // Date axis: about six evenly spaced labels
        boolean longRange = visible.get(visible.size() - 1).date().minusMonths(7).isAfter(visible.get(0).date());
        int labels = Math.min(6, visible.size());
        for (int k = 0; k < labels; k++) {
            int i = labels == 1 ? 0 : k * (visible.size() - 1) / (labels - 1);
            String label = (longRange ? MONTH : DAY).format(visible.get(i).date());
            int x = (int) xOf(i) - fm.stringWidth(label) / 2;
            x = Math.max(LEFT - 10, Math.min(x, getWidth() - RIGHT - fm.stringWidth(label)));
            g.setColor(fg);
            g.drawString(label, x, getHeight() - BOTTOM + fm.getAscent() + 6);
        }

        // Forecast range, then forecast line
        if (!forecast.isEmpty()) {
            int start = visible.size() - 1;
            Path2D band = new Path2D.Double();
            band.moveTo(xOf(start), yOf.applyAsDouble(last));
            for (int d = 0; d < forecast.size(); d++) band.lineTo(xOf(start + 1 + d), yOf.applyAsDouble(forecast.get(d)[2]));
            for (int d = forecast.size() - 1; d >= 0; d--) band.lineTo(xOf(start + 1 + d), yOf.applyAsDouble(forecast.get(d)[1]));
            band.closePath();
            g.setColor(new Color(90, 120, 200, 50));
            g.fill(band);
            Path2D mid = new Path2D.Double();
            mid.moveTo(xOf(start), yOf.applyAsDouble(last));
            for (int d = 0; d < forecast.size(); d++) mid.lineTo(xOf(start + 1 + d), yOf.applyAsDouble(forecast.get(d)[0]));
            g.setColor(new Color(90, 120, 200));
            g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[]{5f, 4f}, 0f));
            g.draw(mid);
        }

        // Area and price line
        Path2D path = new Path2D.Double();
        for (int i = 0; i < visible.size(); i++) {
            double x = xOf(i), y = yOf.applyAsDouble(visible.get(i).close());
            if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
        }
        Path2D area = new Path2D.Double(path);
        area.lineTo(xOf(visible.size() - 1), TOP + plotH);
        area.lineTo(xOf(0), TOP + plotH);
        area.closePath();
        g.setColor(new Color(line.getRed(), line.getGreen(), line.getBlue(), 28));
        g.fill(area);
        g.setColor(line);
        g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(path);

        // Title: symbol, latest close, change over the period
        g.setColor(fg);
        g.setFont(g.getFont().deriveFont(Font.BOLD, 12f));
        String heading = String.format("%s  %s   $%,.2f", title, subtitle, last).replace("     ", "   ");
        g.drawString(heading, LEFT, 18);
        int tx = LEFT + g.getFontMetrics().stringWidth(heading) + 10;
        g.setColor(line);
        g.drawString(String.format("%+.2f%% over %s", (last - first) / first * 100, period == Period.ALL ? "all data" : period.label), tx, 18);
        if (!forecastModel.isEmpty()) {
            g.setFont(g.getFont().deriveFont(Font.PLAIN, 11f));
            g.setColor(new Color(90, 120, 200));
            String note = "Forecast: " + forecastModel + ", 80% range";
            g.drawString(note, getWidth() - RIGHT - g.getFontMetrics().stringWidth(note), 18);
        }

        // Hover marker
        if (hover >= 0) {
            PricePoint p = visible.get(hover);
            int x = (int) Math.round(xOf(hover)), y = (int) Math.round(yOf.applyAsDouble(p.close()));
            g.setStroke(new BasicStroke(1f));
            g.setColor(grid.darker());
            g.drawLine(x, TOP, x, TOP + plotH);
            g.setColor(line);
            g.fillOval(x - 4, y - 4, 8, 8);
            g.setFont(g.getFont().deriveFont(Font.PLAIN, 11f));
            String text = String.format("%s  $%,.2f", p.date(), p.close());
            FontMetrics m = g.getFontMetrics();
            int bw = m.stringWidth(text) + 12, bh = m.getHeight() + 6;
            int bx = Math.min(x + 10, getWidth() - RIGHT - bw), by = TOP + 4;
            g.setColor(bg);
            g.fillRoundRect(bx, by, bw, bh, 6, 6);
            g.setColor(grid.darker());
            g.drawRoundRect(bx, by, bw, bh, 6, 6);
            g.setColor(fg);
            g.drawString(text, bx + 6, by + m.getAscent() + 3);
        }
        g.dispose();
    }
}
