/*
 * Boltzmann 3D, a kinetic theory demonstrator
 * Copyright (C) 2013 Dr. Randall B. Shirts
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package edu.byu.chem.boltzmann.view.maingui.components.statistics;

import edu.byu.chem.boltzmann.controller.ErrorHandler;
import edu.byu.chem.boltzmann.model.statistics.InstantaneousSpeed;
import edu.byu.chem.boltzmann.model.statistics.interfaces.Range;
import edu.byu.chem.boltzmann.model.statistics.interfaces.Statistic;
import edu.byu.chem.boltzmann.model.statistics.interfaces.SingleAverageStatistic;
import edu.byu.chem.boltzmann.model.statistics.interfaces.StatisticWithDistribution;
import edu.byu.chem.boltzmann.model.statistics.plots.HistogramBins;
import edu.byu.chem.boltzmann.model.statistics.plots.History;
import edu.byu.chem.boltzmann.model.statistics.plots.PlotUtils;
import edu.byu.chem.boltzmann.model.statistics.plots.PlotUtils.PlotType;
import edu.byu.chem.boltzmann.utils.Units.Unit;
import javax.swing.*;
import java.awt.*;
import java.text.NumberFormat;
import javax.swing.border.*;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

//Legacy comments:
// rendering and could be greatly sped up if rewritten in OpenGL as well.  (I
// have done this in the Lennard-Jones simulator and plan on one day porting that
// to Java to improve the performance.

/** Class StatPanel - Shows the statistics display.  This uses standard Java 2D rendering. */
public class StatPanel extends JPanel {
	
    /** Left and right margin (in pixels) */
    public static final int edge = 22;

    /** Top margin (in pixels) */
    public static final int topEdge = 15;

    /** Bottom margin (in pixels) */
    public static final int bottomBuffer = 15;

    private PlotType currentType = PlotType.HISTOGRAM;
    private Statistic currentStatistic = null;
    private boolean cumulativeAverage = false; //Plotting instantaneous / cumulative data
    private Range frozenHistogramRange;
    
    //used by redrawPredictionCurve() and reused to scale lines in drawHistogramAvgAndWidthLines()
    private int[] predCurveYValues;

    /** Constructor - initializes background, font, etc. */
    public StatPanel() {
        setBackground (Color.WHITE);
        setBorder (BorderFactory.createBevelBorder (BevelBorder.LOWERED));
        setPreferredSize (new Dimension(200,190));
        setFont (new Font ("Arial", Font.PLAIN, 10));

        
    }
        
    @Override
    public void paintComponent (Graphics g) {
        //Draw the background.
        super.paintComponent(g);

        if (currentStatistic != null && currentType != null) {
            switch (currentType) {
                case HISTORY:
                    if (!(currentStatistic instanceof SingleAverageStatistic)) {
                        throw new IllegalArgumentException("History plots cannot be drawn for " + 
                                currentStatistic.getClass());
                    }
                    drawHistory(g, (SingleAverageStatistic) currentStatistic);
                    break;
                case HISTOGRAM:
                    if (!(currentStatistic instanceof StatisticWithDistribution)) {
                        throw new IllegalArgumentException("Histogram plots cannot be drawn for " + 
                                currentStatistic.getClass());
                    }
                    drawHistogram(g, (StatisticWithDistribution) currentStatistic);
                    break;
                default:
                    throw new IllegalArgumentException("Plot type " + currentType + " is not supported");                    
            }            
        }
            
    }

    BufferedImage currentPredictionLine = null;

    public void setPlotInfo(Statistic statistic, PlotType plotType, boolean cumulativeAverage) {
        currentStatistic = statistic;
        currentType = plotType;
        this.cumulativeAverage = cumulativeAverage;
    }

    private final Map<SingleAverageStatistic, History> historiesOfCurrentAverages = new HashMap<SingleAverageStatistic, History>();
    private final Map<SingleAverageStatistic, History> historiesOfCumulativeAverages = new HashMap<SingleAverageStatistic, History>();
    
    private void drawHistory(Graphics g, SingleAverageStatistic statistic) {
        History history;
        if (cumulativeAverage)
            history = historiesOfCumulativeAverages.get(statistic);
        else
            history = historiesOfCurrentAverages.get(statistic);
        if(history != null)//happens if the number of enabled statistics is changed while the plot type is history
            history.drawHistory(g, this);
    }

    @SuppressWarnings("unchecked")
    public void updateHistories() {
        for (SingleAverageStatistic statistic : historiesOfCurrentAverages.keySet()) {
            historiesOfCurrentAverages.get(statistic).addPoint(
                    statistic.getCurrentAverage(statistic.getDefaultDisplayUnit()));
            historiesOfCumulativeAverages.get(statistic).addPoint(
                    statistic.getCumulativeAverage(statistic.getDefaultDisplayUnit()));
        }
    }

    @SuppressWarnings("unchecked")
    public void setStatisticsWithHistories(Set<SingleAverageStatistic> statisticsWithHistories) {
        historiesOfCurrentAverages.clear();
        historiesOfCumulativeAverages.clear();
        
        for (SingleAverageStatistic statistic : statisticsWithHistories) {
            History historyOfCurrentAverages = new History();
            History historyOfCumulativeAverages = new History();
            
            Unit statisticUnit = statistic.getDefaultDisplayUnit();
            double averagePrediction = statistic.getPredictionForAverage(statisticUnit);
            final double distanceToMax;
            if (statistic instanceof StatisticWithDistribution) {
                StatisticWithDistribution statisticWithDist = (StatisticWithDistribution) statistic;
                double widthPrediction = statisticWithDist.getDistributionWidthPrediction(statisticUnit);
                if(Double.isNaN(widthPrediction))
                    widthPrediction = 0;
                distanceToMax = Math.max(widthPrediction, averagePrediction);
                predictionCurves.put(statisticWithDist, null);
                predictionCurveRanges.remove(statisticWithDist);
            } else {
                distanceToMax = 0.5 * averagePrediction;
            }
            
            Range plotRange = new Range(averagePrediction, distanceToMax, true);
            historyOfCurrentAverages.setRange(plotRange);
            historyOfCumulativeAverages.setRange(plotRange);
            
            historiesOfCurrentAverages.put(statistic, historyOfCurrentAverages);
            historiesOfCumulativeAverages.put(statistic, historyOfCumulativeAverages);
        }
    }

    private final Map<StatisticWithDistribution, BufferedImage> predictionCurves = new HashMap<StatisticWithDistribution, BufferedImage>();
    private final Map<StatisticWithDistribution, Range> predictionCurveRanges =
            new HashMap<StatisticWithDistribution, Range>();
    
    @SuppressWarnings("unchecked")
    private void drawHistogram(Graphics g, StatisticWithDistribution statisticWithDistribution) {
        Unit statisticUnit = statisticWithDistribution.getDefaultDisplayUnit();        
        Range distributionRange = statisticWithDistribution.getDistributionRange(statisticUnit);        
        Dimension size = getSize();
        Range requestedRange = frozenHistogramRange == null ? distributionRange : frozenHistogramRange;
        double majorTickInterval = calculateNiceTickInterval(
                requestedRange.getDistanceFromMinToMax(), size.width - (2 * StatPanel.edge));
        Range displayedRange = expandToNiceTickBounds(requestedRange, majorTickInterval);

        HistogramBins bins;
        if (cumulativeAverage) {
            bins = statisticWithDistribution.getHistogramBinsForCumulativeDistribution();
        } else {
            bins = statisticWithDistribution.getHistogramBinsForCurrentDistribution();
        }
        bins.drawHistogramBars(size, g, displayedRange);
        
        drawHistogramLines(size, g, displayedRange, majorTickInterval);

        overlayPredictionCurve(g, statisticWithDistribution, displayedRange);
        
        drawHistogramAvgAndWidthLines(g, statisticWithDistribution, displayedRange, statisticUnit);
        if (statisticWithDistribution instanceof InstantaneousSpeed) {
            drawVelocityRmsLines(g, statisticWithDistribution, displayedRange, statisticUnit);
            drawVelocityMarkerLegend(g);
        }
    }

    private void redrawPredictionCurve(StatisticWithDistribution statisticWithDistribution,
            Range displayedRange) {
        int width = getSize().width - (2 * StatPanel.edge);
        int bottomY = getSize().height - StatPanel.bottomBuffer - StatPanel.edge;
        int height = bottomY - StatPanel.topEdge;
        int curveHeight = (int) Math.round(height * 0.8);
        
        BufferedImage predictionCurve = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics g = predictionCurve.createGraphics();

        Unit statisticUnit = statisticWithDistribution.getDefaultDisplayUnit();
        Range sourceRange = statisticWithDistribution.getDistributionRange(statisticUnit);
        double[] unscaledPoints = statisticWithDistribution.getPredictionCurve(width);
        if (!rangesMatch(sourceRange, displayedRange)) {
            unscaledPoints = resampleCurve(unscaledPoints, sourceRange, displayedRange);
        }
        predCurveYValues = PlotUtils.scalePoints(unscaledPoints, curveHeight);

        PlotUtils.reversePoints(predCurveYValues, height);

        int[] xValues = new int[ predCurveYValues.length ];
        for (int i = 0; i < xValues.length; i++) {
            xValues[ i ] = i;
        }

        g.setColor(Color.BLACK);
        g.drawPolyline(xValues, predCurveYValues, xValues.length);
        
        predictionCurves.put(statisticWithDistribution, predictionCurve);
        predictionCurveRanges.put(statisticWithDistribution, displayedRange);
    }

    private static double[] resampleCurve(double[] sourcePoints, Range sourceRange,
            Range displayedRange) {
        double[] displayedPoints = new double[sourcePoints.length];
        double sourceSpan = sourceRange.getDistanceFromMinToMax();
        double displayedSpan = displayedRange.getDistanceFromMinToMax();
        if (sourcePoints.length == 0 || !(sourceSpan > 0.0) || !(displayedSpan > 0.0)) {
            return displayedPoints;
        }

        for (int index = 0; index < displayedPoints.length; index++) {
            double value = displayedRange.min
                    + displayedSpan * (index + 0.5) / displayedPoints.length;
            if (value < sourceRange.min || value > sourceRange.max) {
                continue;
            }

            double sourcePosition = (value - sourceRange.min) / sourceSpan
                    * sourcePoints.length - 0.5;
            sourcePosition = Math.max(0.0, Math.min(sourcePoints.length - 1.0, sourcePosition));
            int lowerIndex = (int) Math.floor(sourcePosition);
            int upperIndex = Math.min(sourcePoints.length - 1, lowerIndex + 1);
            double fraction = sourcePosition - lowerIndex;
            displayedPoints[index] = sourcePoints[lowerIndex] * (1.0 - fraction)
                    + sourcePoints[upperIndex] * fraction;
        }

        return displayedPoints;
    }

    private static boolean rangesMatch(Range first, Range second) {
        return first != null && second != null
                && Double.compare(first.min, second.min) == 0
                && Double.compare(first.max, second.max) == 0;
    }

    @SuppressWarnings("unchecked")
    private void drawHistogramAvgAndWidthLines(Graphics g, StatisticWithDistribution statisticWithDistribution, Range range, Unit defaultUnit) {
        double predictedAverage = statisticWithDistribution.getPredictionForAverage(defaultUnit);
        double actualAverage = cumulativeAverage ? statisticWithDistribution.getCumulativeAverage(defaultUnit)
                    : statisticWithDistribution.getCurrentAverage(defaultUnit);
        double predictedWidth = statisticWithDistribution.getDistributionWidthPrediction(defaultUnit);
        double actualWidth = cumulativeAverage ? statisticWithDistribution.getCumulativeDistributionWidth(defaultUnit)
                    : statisticWithDistribution.getCurrentDistributionWidth(defaultUnit);
        
        double rangeToPixelScaleFactor = predCurveYValues.length / range.getDistanceFromMinToMax();
        int bottomY = getSize().height - StatPanel.bottomBuffer - StatPanel.edge;
        
        drawHistogramAvgOrWidthLine(g, range, predictedAverage, actualAverage, rangeToPixelScaleFactor, bottomY, 3, true);
        drawHistogramAvgOrWidthLine(g, range, predictedAverage - predictedWidth, actualAverage - actualWidth, rangeToPixelScaleFactor, bottomY, 1, true);
        drawHistogramAvgOrWidthLine(g, range, predictedAverage + predictedWidth, actualAverage + actualWidth, rangeToPixelScaleFactor, bottomY, 1, true);
    }
    
    private void drawHistogramAvgOrWidthLine(Graphics g, Range range, double predictedValue, double actualValue, double rangeToPixelScaleFactor, int bottomY, int lineWidth, boolean extendToTop){
        if(predictedValue >= range.min && predictedValue <= range.max){
            int predictedXValue = (int)Math.round((predictedValue - range.min) * rangeToPixelScaleFactor);
            int actualXValue = (int)Math.round((actualValue - range.min) * rangeToPixelScaleFactor);
            if(actualXValue == predictedXValue)
                actualXValue++;
            predictedXValue = Math.max(0, Math.min(predCurveYValues.length - 1, predictedXValue));
            int predictedYValue = extendToTop
                    ? StatPanel.topEdge : StatPanel.topEdge + predCurveYValues[predictedXValue];
                
            g.setColor(Color.ORANGE);
            drawVerticalMarker(g, predictedXValue + StatPanel.edge, predictedYValue, bottomY, lineWidth);
            
            g.setColor(new Color(0, 210, 0));//special green
            drawVerticalMarker(g, actualXValue + StatPanel.edge, predictedYValue, bottomY, lineWidth);//same height as prediction
        }
    }

    private static void drawVerticalMarker(Graphics g, int centerX, int topY, int bottomY, int lineWidth) {
        g.fillRect(centerX - (lineWidth / 2), topY, lineWidth, Math.max(1, bottomY - topY));
    }

    private void drawVelocityRmsLines(Graphics g, StatisticWithDistribution statistic, Range range, Unit unit) {
        double predictedAverage = statistic.getPredictionForAverage(unit);
        double predictedWidth = statistic.getDistributionWidthPrediction(unit);
        double actualAverage = cumulativeAverage
                ? statistic.getCumulativeAverage(unit) : statistic.getCurrentAverage(unit);
        double actualWidth = cumulativeAverage
                ? statistic.getCumulativeDistributionWidth(unit) : statistic.getCurrentDistributionWidth(unit);

        double predictedRms = rmsFromMeanAndWidth(predictedAverage, predictedWidth);
        double actualRms = rmsFromMeanAndWidth(actualAverage, actualWidth);
        double scale = predCurveYValues.length / range.getDistanceFromMinToMax();
        int bottomY = getSize().height - StatPanel.bottomBuffer - StatPanel.edge;

        drawVelocityMarker(g, range, predictedRms, scale, bottomY, new Color(35, 105, 220));
        drawVelocityMarker(g, range, actualRms, scale, bottomY, new Color(170, 45, 190));
    }

    private static double rmsFromMeanAndWidth(double mean, double width) {
        if (!Double.isFinite(mean) || !Double.isFinite(width)) {
            return Double.NaN;
        }
        return Math.sqrt(mean * mean + width * width);
    }

    private void drawVelocityMarker(Graphics g, Range range, double value,
            double rangeToPixelScaleFactor, int bottomY, Color color) {
        if (!Double.isFinite(value) || value < range.min || value > range.max) {
            return;
        }

        int x = (int) Math.round((value - range.min) * rangeToPixelScaleFactor);
        x = Math.max(0, Math.min(predCurveYValues.length - 1, x));
        int topY = StatPanel.topEdge;
        g.setColor(color);
        drawVerticalMarker(g, x + StatPanel.edge, topY, bottomY, 3);
    }

    private void drawVelocityMarkerLegend(Graphics g) {
        int y = 10;
        drawLegendPair(g, StatPanel.edge + 2, y, Color.ORANGE,
                new Color(0, 210, 0), "avg");
        drawLegendPair(g, StatPanel.edge + 62, y, new Color(35, 105, 220),
                new Color(170, 45, 190), "RMS");
    }

    private static void drawLegendPair(Graphics g, int x, int y,
            Color predictedColor, Color actualColor, String label) {
        g.setColor(predictedColor);
        g.fillRect(x, y - 1, 5, 3);
        g.setColor(actualColor);
        g.fillRect(x + 5, y - 1, 5, 3);
        g.setColor(Color.BLACK);
        g.drawString(label, x + 12, y + 3);
    }
    
    private static double calculateNiceTickInterval(double range, int axisWidth) {
        if (!(range > 0.0) || !Double.isFinite(range)) {
            return 1.0;
        }

        int targetIntervals = Math.max(2, Math.min(8, axisWidth / 85));
        double rawInterval = range / targetIntervals;
        double magnitude = Math.pow(10.0, Math.floor(Math.log10(rawInterval)));
        double normalizedInterval = rawInterval / magnitude;
        double niceMultiplier;
        if (normalizedInterval <= 1.0) {
            niceMultiplier = 1.0;
        } else if (normalizedInterval <= 2.0) {
            niceMultiplier = 2.0;
        } else if (normalizedInterval <= 5.0) {
            niceMultiplier = 5.0;
        } else {
            niceMultiplier = 10.0;
        }
        return niceMultiplier * magnitude;
    }

    private static Range expandToNiceTickBounds(Range range, double tickInterval) {
        double span = range.getDistanceFromMinToMax();
        if (!(span > 0.0) || !Double.isFinite(span)
                || !(tickInterval > 0.0) || !Double.isFinite(tickInterval)) {
            return range;
        }

        double minUnits = range.min / tickInterval;
        double maxUnits = range.max / tickInterval;
        double minTolerance = 4.0 * Math.ulp(minUnits);
        double maxTolerance = 4.0 * Math.ulp(maxUnits);
        double min = Math.floor(minUnits + minTolerance) * tickInterval;
        double max = Math.ceil(maxUnits - maxTolerance) * tickInterval;
        if (!(max > min)) {
            return range;
        }
        return new Range(min, max);
    }

    private void drawHistogramLines(Dimension panelSize, Graphics g, Range distributionRange,
            double majorTickInterval) {
        g.setColor(Color.BLACK);
        FontMetrics font = g.getFontMetrics();

        //Draw the axis.
        g.drawLine(StatPanel.edge, panelSize.height-StatPanel.edge-StatPanel.bottomBuffer, panelSize.width-StatPanel.edge, panelSize.height-StatPanel.edge-StatPanel.bottomBuffer);
        g.drawLine(StatPanel.edge, StatPanel.topEdge, StatPanel.edge, panelSize.height-StatPanel.edge-StatPanel.bottomBuffer);
        double span = distributionRange.getDistanceFromMinToMax();
        int width = panelSize.width - (2 * StatPanel.edge);
        if (!(span > 0.0) || !(majorTickInterval > 0.0) || width <= 0) {
            return;
        }

        int numberOfIntervals = (int) Math.round(span / majorTickInterval);
        int axisY = panelSize.height - StatPanel.edge - StatPanel.bottomBuffer;
        int minorTickLength = 3;
        int majorTickLength = 6;
        int subdivisions = 5;

        // Add four short ticks between each pair of evenly spaced, labeled major ticks.
        for (int interval = 0; interval < numberOfIntervals; interval++) {
            double startValue = distributionRange.min + interval * majorTickInterval;
            for (int subdivision = 1; subdivision < subdivisions; subdivision++) {
                double value = startValue + majorTickInterval * subdivision / subdivisions;
                int x = histogramTickX(value, distributionRange, width);
                g.drawLine(x, axisY, x, axisY + minorTickLength);
            }
        }

        for (int interval = 0; interval <= numberOfIntervals; interval++) {
            double value = distributionRange.min + interval * majorTickInterval;
            int x = histogramTickX(value, distributionRange, width);
            g.drawLine(x, axisY, x, axisY + majorTickLength);

            String label = formatHistogramTick(value, majorTickInterval);
            int labelWidth = font.stringWidth(label);
            int labelX = Math.max(0, Math.min(panelSize.width - labelWidth,
                    x - labelWidth / 2));
            g.drawString(label, labelX, axisY + 15);
        }
    }

    private static int histogramTickX(double value, Range range, int axisWidth) {
        double fraction = (value - range.min) / range.getDistanceFromMinToMax();
        return StatPanel.edge + (int) Math.round(fraction * axisWidth);
    }

    private static String formatHistogramTick(double value, double tickInterval) {
        int maximumFractionDigits = Math.max(0, Math.min(10,
                (int) Math.ceil(-Math.log10(tickInterval) - 1.0e-10)));
        NumberFormat format = NumberFormat.getNumberInstance();
        format.setGroupingUsed(false);
        format.setMaximumFractionDigits(maximumFractionDigits);

        if (Math.abs(value) >= 1.0e6 || (Math.abs(value) > 0.0 && Math.abs(value) < 1.0e-3)) {
            return ErrorHandler.format(value, 4);
        }
        return format.format(value);
    }

    private void overlayPredictionCurve(Graphics g, StatisticWithDistribution statisticWithDistribution,
            Range displayedRange) {
        if (statisticWithDistribution.hasNewPredictionCurve()
                || !rangesMatch(predictionCurveRanges.get(statisticWithDistribution), displayedRange)) {
            redrawPredictionCurve(statisticWithDistribution, displayedRange);
        }

        int topY = StatPanel.topEdge;
        int leftX = StatPanel.edge;

        BufferedImage predictionCurve = predictionCurves.get(statisticWithDistribution);
        if (predictionCurve != null) {
            g.drawImage(predictionCurve, leftX, topY, null);
        }
    }

    public void setFrozenHistogramRange(Range range) {
        frozenHistogramRange = range;
    }

    public void clearPlot() {
        currentStatistic = null;
        currentType = null; 
    }
}
