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
package edu.byu.chem.boltzmann.model.statistics.plots;

import edu.byu.chem.boltzmann.model.statistics.interfaces.Range;
import edu.byu.chem.boltzmann.utils.data.ParticleType;
import edu.byu.chem.boltzmann.view.maingui.components.statistics.StatPanel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Created 5 May 2011
 * Keeps track of and draws bins for a histogram plot.
 * @author Derek Manwaring
 */
public class HistogramBins {

    public static final int DEFAULT_MAX_BIN_HEIGHT = 70;
    
    public static final int DEFAULT_NUM_OF_BINS = 30;
    public static final int DEFAULT_INST_BINS = 15; //For instantaneous histograms

    private Range distributionRange;
    
    private Map<Color, double[]> coloredBins;
    private double[] allBins;
    private double[] binLimits;

    //Types sorted by particle weight
    private final SortedSet<ParticleType> typesByWeight =
            new TreeSet<ParticleType>(PlotUtils.SORT_BY_WEIGHT);

    public HistogramBins(Range distributionRange,
            int numberOfBins,
            Set<ParticleType> typesPlotted) {
        this.distributionRange = distributionRange;
        coloredBins = new HashMap<Color, double[]>();

        for(ParticleType type: typesPlotted) {
            coloredBins.put(type.defaultColor, new double[numberOfBins]);
        }
        
        allBins = new double[numberOfBins];
        binLimits = new double[numberOfBins];

        double binWidth = distributionRange.getIncrement(numberOfBins);

        for (int i = 0; i < numberOfBins; i++) {
            binLimits[i] = (i + 1) * binWidth + distributionRange.min;
        }

        typesByWeight.addAll(typesPlotted);
    }

    public synchronized void drawHistogramBars(Dimension panelSize, Graphics graphics) {
        drawHistogramBars(panelSize, graphics, distributionRange);
    }

    public synchronized void setDistributionRange(Range distributionRange) {
        this.distributionRange = distributionRange;
        double binWidth = distributionRange.getIncrement(allBins.length);
        for (int i = 0; i < binLimits.length; i++) {
            binLimits[i] = (i + 1) * binWidth + distributionRange.min;
        }
        resetBins();
    }

    public synchronized void drawHistogramBars(Dimension panelSize, Graphics graphics,
            Range displayedRange) {
        double[] displayedAllBins = rebinBins(allBins, displayedRange);
        Map<Color, double[]> displayedColorBins = new HashMap<Color, double[]>();
        for (Map.Entry<Color, double[]> entry : coloredBins.entrySet()) {
            displayedColorBins.put(entry.getKey(), rebinBins(entry.getValue(), displayedRange));
        }

        int maxBinHeight = (int) (0.8 * (panelSize.height - StatPanel.edge
                - StatPanel.bottomBuffer - StatPanel.topEdge));
        double totalNormalize = calculateNormalizingFactor(maxBinHeight, displayedAllBins);

        //int width = size.width-(2*edge)-4;
        double wid = ((double)(panelSize.width - (2 * StatPanel.edge)) / displayedAllBins.length);
        int bottomY = panelSize.height - StatPanel.bottomBuffer - StatPanel.edge;
        int leftX = StatPanel.edge;


        double[] stackedCounts = new double[displayedAllBins.length];
        int[] stackedHeights = new int[displayedAllBins.length];
        for (ParticleType type: typesByWeight) {
            double[] currentBins = displayedColorBins.get(type.defaultColor);
            Color barColor = type.defaultColor;
            graphics.setColor(new Color(barColor.getRed(), barColor.getGreen(),
                    barColor.getBlue(), 191));

            for (int i = 0; i < currentBins.length; i++) {
                //Shading of histogram bars based on value could be included later
                // - Derek Manwaring 5 May 2011
    //			        	if (main.statName == main.arena.getRainbowMode())
    //			        	{
    //			        		float hue = MainOld.getHue(i, bins[0].length);
    //			        		g.setColor(Color.getHSBColor(hue, 1, 1));
    //							g.fillRect((int)(StatPanel.edge+((i/(double)bins[0].length)*width)+2),size.height-StatPanel.edge-StatPanel.bottomBuffer-bins[Const.RED][i]-bins[Const.BLUE][i]-2,wid-2,bins[Const.RED][i]+bins[Const.BLUE][i]);
    //			        	}
    //			        	else
                // Stack each type's contribution using the mixture's shared vertical scale.
                int newStackHeight = (int) Math.ceil(
                        totalNormalize * (stackedCounts[i] + currentBins[i]));
                int normalizedValue = newStackHeight - stackedHeights[i];
                int topEdge = bottomY - newStackHeight;
                int binLeftEdge = leftX + (int) Math.ceil(i * wid);
                int binRightEdge = leftX + (int) Math.ceil((i + 1) * wid);
//                edge+((i/(double)bins[0].length)*width)+2)
                graphics.fillRect(binLeftEdge + 1, topEdge,
                        binRightEdge - binLeftEdge - 1, normalizedValue);
                stackedCounts[i] += currentBins[i];
                stackedHeights[i] = newStackHeight;
            }
        }

        int leftBarEdge = leftX;
        graphics.setColor(Color.WHITE);
        for (int i = 0; i < displayedAllBins.length; i++) {
            int normalizedValue = (int) (totalNormalize * displayedAllBins[i]);
            int bottomEdge = bottomY;
            int topEdge = bottomEdge - normalizedValue;
            int nextEdge = leftX + (int)Math.ceil((i + 1) * wid);

            graphics.drawRect(leftBarEdge, topEdge, nextEdge - leftBarEdge, normalizedValue);
            leftBarEdge = nextEdge;
        }
    }

    private static double calculateNormalizingFactor(int maxBinHeight, double[] bins) {
        double maxBinValue = 0.0;
        for (double binValue : bins) {
            maxBinValue = Math.max(maxBinValue, binValue);
        }
        return maxBinValue > 0.0 ? maxBinHeight / maxBinValue : 0.0;
    }

    private double[] rebinBins(double[] sourceBins, Range targetRange) {
        if (targetRange.min == distributionRange.min
                && targetRange.max == distributionRange.max) {
            return sourceBins;
        }

        double[] targetBins = new double[sourceBins.length];
        double sourceWidth = distributionRange.getIncrement(sourceBins.length);
        double targetWidth = targetRange.getIncrement(targetBins.length);
        if (!(sourceWidth > 0.0) || !(targetWidth > 0.0)) {
            return targetBins;
        }

        for (int sourceIndex = 0; sourceIndex < sourceBins.length; sourceIndex++) {
            double sourceMin = distributionRange.min + sourceIndex * sourceWidth;
            double sourceMax = sourceMin + sourceWidth;
            for (int targetIndex = 0; targetIndex < targetBins.length; targetIndex++) {
                double targetMin = targetRange.min + targetIndex * targetWidth;
                double targetMax = targetMin + targetWidth;
                double overlap = Math.min(sourceMax, targetMax) - Math.max(sourceMin, targetMin);
                if (overlap > 0.0) {
                    targetBins[targetIndex] += sourceBins[sourceIndex] * overlap / sourceWidth;
                }
            }
        }

        return targetBins;
    }

    public synchronized void resetBins() {
        if (coloredBins != null) {
            for (double[] bins: coloredBins.values()) {
                Arrays.fill(bins, 0.0);
            }
            Arrays.fill(allBins, 0.0);
        }
    }

    public synchronized void addWeightedValue(double value, double weight, Color color) {
        double[] bins = coloredBins.get(color);

        if (value >= distributionRange.min) {
            for (int i = 0; i < bins.length; i++) {
                if (value < binLimits[i]) {
                    bins[i] += weight;
                    allBins[i] += weight;
                    return;
                }
            }
        }
    }
}
