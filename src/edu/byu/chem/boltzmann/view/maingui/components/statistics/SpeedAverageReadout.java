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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 */
package edu.byu.chem.boltzmann.view.maingui.components.statistics;

import edu.byu.chem.boltzmann.model.statistics.interfaces.StatisticWithDistribution;
import edu.byu.chem.boltzmann.utils.Units;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

/** Shows predicted and measured speed averages beneath the speed histogram. */
public final class SpeedAverageReadout extends JPanel {
    private final JLabel averageLabel = new JLabel("Average:");
    private final JTextField predictedValue = createValueField(new Color(255, 153, 0));
    private final JTextField averageValue = createValueField(new Color(0, 170, 0));
    private final DecimalFormat numberFormat =
            new DecimalFormat("0.0", DecimalFormatSymbols.getInstance(Locale.US));

    public SpeedAverageReadout() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setAlignmentX(LEFT_ALIGNMENT);
        add(createRow("Predicted:", predictedValue));
        add(createRow(averageLabel, averageValue));
    }

    public void update(StatisticWithDistribution<Units.Velocity> statistic,
            boolean cumulative, boolean width) {
        Units.Velocity unit = Units.Velocity.METER_PER_SECOND;
        double predicted;
        double measured;
        if (width) {
            predicted = statistic.getDistributionWidthPrediction(unit);
            measured = cumulative
                    ? statistic.getCumulativeDistributionWidth(unit)
                    : statistic.getCurrentDistributionWidth(unit);
            averageLabel.setText("Width:");
        } else {
            predicted = statistic.getPredictionForAverage(unit);
            measured = cumulative
                    ? statistic.getCumulativeAverage(unit)
                    : statistic.getCurrentAverage(unit);
            averageLabel.setText("Average:");
        }

        predictedValue.setText(format(predicted));
        averageValue.setText(format(measured));
    }

    private String format(double value) {
        return Double.isNaN(value) || Double.isInfinite(value) ? "--" : numberFormat.format(value) + " m/s";
    }

    private static JTextField createValueField(Color outline) {
        JTextField field = new JTextField(9);
        field.setEditable(false);
        field.setHorizontalAlignment(JTextField.RIGHT);
        field.setBorder(BorderFactory.createLineBorder(outline, 2));
        return field;
    }

    private static JPanel createRow(String labelText, JTextField value) {
        return createRow(new JLabel(labelText), value);
    }

    private static JPanel createRow(JLabel label, JTextField value) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 3));
        row.setAlignmentX(LEFT_ALIGNMENT);
        label.setPreferredSize(new Dimension(84, label.getPreferredSize().height));
        row.add(label);
        row.add(value);
        return row;
    }
}
