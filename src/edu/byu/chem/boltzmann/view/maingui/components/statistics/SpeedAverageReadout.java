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

import edu.byu.chem.boltzmann.model.statistics.InstantaneousSpeed;
import edu.byu.chem.boltzmann.utils.Units;
import java.awt.Component;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Insets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.JTextField;

/** Shows predicted and measured speed averages beneath the speed histogram. */
public final class SpeedAverageReadout extends JPanel {
    private final JLabel averageLabel = new JLabel("Average:");
    private final JLabel predictedLabel = new JLabel("Predicted:");
    private final JTextField predictedValue = createValueField(new Color(255, 153, 0));
    private final JTextField averageValue = createValueField(new Color(0, 170, 0));
    private final DecimalFormat numberFormat =
            new DecimalFormat("0.0", DecimalFormatSymbols.getInstance(Locale.US));

    public SpeedAverageReadout() {
        setLayout(new GridLayout(1, 2, 8, 0));
        setAlignmentX(LEFT_ALIGNMENT);
        add(createColumn(averageLabel, averageValue));
        add(createColumn(predictedLabel, predictedValue));
    }

    public void update(InstantaneousSpeed statistic, boolean cumulative, boolean width) {
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
        return Double.isNaN(value) || Double.isInfinite(value)
                ? "--" : numberFormat.format(value) + " m/s";
    }

    private static JTextField createValueField(Color outline) {
        JTextField field = new JTextField(10);
        field.setEditable(false);
        field.setHorizontalAlignment(JTextField.RIGHT);
        field.setBorder(BorderFactory.createLineBorder(outline, 2));
        field.setMargin(new Insets(3, 4, 3, 4));
        Dimension preferredSize = field.getPreferredSize();
        int height = Math.max(30, preferredSize.height + 8);
        Dimension readoutSize = new Dimension(preferredSize.width, height);
        field.setPreferredSize(readoutSize);
        field.setMinimumSize(readoutSize);
        return field;
    }

    private static JPanel createColumn(JLabel label, JTextField value) {
        JPanel column = new JPanel();
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));

        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setAlignmentX(Component.CENTER_ALIGNMENT);
        label.setMaximumSize(new Dimension(Integer.MAX_VALUE,
                label.getPreferredSize().height));
        value.setAlignmentX(Component.CENTER_ALIGNMENT);
        value.setMaximumSize(new Dimension(Integer.MAX_VALUE,
                value.getPreferredSize().height));

        column.add(label);
        column.add(Box.createVerticalStrut(3));
        column.add(value);
        return column;
    }
}
