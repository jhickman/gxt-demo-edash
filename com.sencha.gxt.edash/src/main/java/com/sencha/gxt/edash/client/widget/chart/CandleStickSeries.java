/**
 * Sencha GXT 1.0.0-SNAPSHOT - Sencha for GWT
 * Copyright (c) 2006-2018, Sencha Inc.
 *
 * licensing@sencha.com
 * http://www.sencha.com/products/gxt/license/
 *
 * ================================================================================
 * Commercial License
 * ================================================================================
 * This version of Sencha GXT is licensed commercially and is the appropriate
 * option for the vast majority of use cases.
 *
 * Please see the Sencha GXT Licensing page at:
 * http://www.sencha.com/products/gxt/license/
 *
 * For clarification or additional options, please contact:
 * licensing@sencha.com
 * ================================================================================
 *
 *
 *
 *
 *
 *
 *
 *
 * ================================================================================
 * Disclaimer
 * ================================================================================
 * THIS SOFTWARE IS DISTRIBUTED "AS-IS" WITHOUT ANY WARRANTIES, CONDITIONS AND
 * REPRESENTATIONS WHETHER EXPRESS OR IMPLIED, INCLUDING WITHOUT LIMITATION THE
 * IMPLIED WARRANTIES AND CONDITIONS OF MERCHANTABILITY, MERCHANTABLE QUALITY,
 * FITNESS FOR A PARTICULAR PURPOSE, DURABILITY, NON-INFRINGEMENT, PERFORMANCE AND
 * THOSE ARISING BY STATUTE OR FROM CUSTOM OR USAGE OF TRADE OR COURSE OF DEALING.
 * ================================================================================
 */
package com.sencha.gxt.edash.client.widget.chart;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.sencha.gxt.chart.client.chart.Chart.Position;
import com.sencha.gxt.chart.client.chart.Legend;
import com.sencha.gxt.chart.client.chart.axis.Axis;
import com.sencha.gxt.chart.client.chart.axis.NumericAxis;
import com.sencha.gxt.chart.client.chart.series.BarHighlighter;
import com.sencha.gxt.chart.client.chart.series.MultipleColorSeries;
import com.sencha.gxt.chart.client.chart.series.SeriesRenderer;
import com.sencha.gxt.chart.client.draw.Color;
import com.sencha.gxt.chart.client.draw.DrawFx;
import com.sencha.gxt.chart.client.draw.RGB;
import com.sencha.gxt.chart.client.draw.path.PathSprite;
import com.sencha.gxt.chart.client.draw.sprite.RectangleSprite;
import com.sencha.gxt.chart.client.draw.sprite.Sprite;
import com.sencha.gxt.chart.client.draw.sprite.SpriteList;
import com.sencha.gxt.core.client.ValueProvider;
import com.sencha.gxt.core.client.util.PrecisePoint;
import com.sencha.gxt.core.client.util.PreciseRectangle;
import com.sencha.gxt.data.shared.ListStore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 */
public class CandleStickSeries<M> extends MultipleColorSeries<M> {

  private boolean column = true;
  private boolean stacked = false;

  // The gutter space between single bars, as a percentage of the bar width
  private double gutter = 38.2;

  // The gutter space between groups of bars, as a percentage of the bar width
  private double groupGutter = 38.2;

  // Padding between the left/right axes and the bars
  protected int xPadding = 10;

  // Padding between the top/bottom axes and the bars
  protected int yPadding = 0;

  private Axis<M, ?> axis;
  private double minY = 0;
  private double maxY = 0;
  private double groupBarWidth = 0;
  private int groupBarsLength = 0;
  private int storeSize = 0;
  private double scale = 0;
  private double zero = 0;
  private Set<Integer> exclude = new HashSet<Integer>();
  // the bar attributes
  private Map<Integer, RectangleSprite> rects = new HashMap<Integer, RectangleSprite>();
  private Map<Integer, Double> totalPositiveDimensions = new HashMap<Integer, Double>();
  private Map<Integer, Double> totalNegativeDimensions = new HashMap<Integer, Double>();
  protected List<ValueProvider<? super M, ? extends Number>> yFields = new ArrayList<ValueProvider<? super M, ? extends Number>>();
  private Position yAxisPosition;
  private Position xAxisPosition;

  /**
   * Creates a bar {@link com.sencha.gxt.chart.client.chart.series.Series}.
   */
  public CandleStickSeries() {
    // setup shadow attributes
    Sprite config = new PathSprite();
    config.setStrokeWidth(6);
    config.setStrokeOpacity(0.05);
    config.setStroke(new RGB(200, 200, 200));
    config.setTranslation(1.2, 1.2);
    config.setFill(Color.NONE);
    config.setZIndex(9);
    shadowAttributes.add(config);
    config = new PathSprite();
    config.setStrokeWidth(4);
    config.setStrokeOpacity(0.1);
    config.setStroke(new RGB(150, 150, 150));
    config.setTranslation(0.9, 0.9);
    config.setFill(Color.NONE);
    config.setZIndex(9);
    shadowAttributes.add(config);
    config = new PathSprite();
    config.setStrokeWidth(2);
    config.setStrokeOpacity(0.15);
    config.setStroke(new RGB(100, 100, 100));
    config.setTranslation(0.6, 0.6);
    config.setFill(Color.NONE);
    config.setZIndex(9);
    shadowAttributes.add(config);

    // initialize shadow groups
    if (shadowGroups.size() == 0) {
      for (int i = 0; i < shadowAttributes.size(); i++) {
        shadowGroups.add(new SpriteList<Sprite>());
      }
    }

    setHighlighter(new BarHighlighter());
  }

  /**
   * Adds a data field for the y axis of the series.
   *
   * @param index  the index to have the yField inserted
   * @param yField the value provider for the data on the y axis
   */
  public void addYField(int index, ValueProvider<? super M, ? extends Number> yField) {
    this.yFields.add(index, yField);
  }

  /**
   * Adds a data field for the y axis of the series.
   *
   * @param yField the value provider for the data on the y axis
   */
  public void addYField(ValueProvider<? super M, ? extends Number> yField) {
    this.yFields.add(yField);
  }

  @Override
  public void drawSeries() {
    ListStore<M> store = chart.getCurrentStore();

    if (store == null || store.size() == 0) {
      this.clear();
      return;
    }

    if (store.size() != storeSize) {
      rects.clear();
    }
    storeSize = store.size();

    calculatePaths();

    // Create new or reuse sprites and animate/display
    for (int i = 0; i < rects.size(); i++) {
      if (rects.get(i) == null) {
        continue;
      }
      if (chart.hasShadows()) {
        renderShadows(i);
      }

      final RectangleSprite bar;
      RectangleSprite rect = rects.get(i);
      if (i < sprites.size()) {
        bar = (RectangleSprite) sprites.get(i);
        bar.setHidden(false);
      } else {
        // Create a new bar if needed (no height)
        bar = new RectangleSprite();
        if (column) {
          bar.setX(rect.getX());
          bar.setY(rect.getY() + rect.getHeight());
          bar.setWidth(rect.getWidth());
          bar.setHeight(0);
        } else {
          bar.setX(rect.getX());
          bar.setY(rect.getY());
          bar.setWidth(0);
          bar.setHeight(rect.getHeight());
        }
        sprites.add(bar);
        chart.addSprite(bar);
      }
      if (chart.isAnimated() && chart.isResizing()) {
        if (column) {
          bar.setX(rect.getX());
          bar.setY(rect.getY() + rect.getHeight());
          bar.setWidth(rect.getWidth());
          bar.setHeight(0);
        } else {
          bar.setX(rect.getX());
          bar.setY(rect.getY());
          bar.setWidth(0);
          bar.setHeight(rect.getHeight());
        }
      }
      bar.setFill(rect.getFill());
      if (stroke != null) {
        bar.setStroke(stroke);
      }
      if (!Double.isNaN(strokeWidth)) {
        bar.setStrokeWidth(strokeWidth);
      }
      if (chart.isAnimated() && !Double.isNaN(bar.getX())) {
        DrawFx.createRectangleAnimator(bar, rect.toRectangle()).run(chart.getAnimationDuration(),
            chart.getAnimationEasing());
      } else {
        bar.setX(rect.getX());
        bar.setY(rect.getY());
        bar.setWidth(rect.getWidth());
        bar.setHeight(rect.getHeight());
        bar.redraw();
      }
      if (renderer != null) {
        renderer.spriteRenderer(bar, i, store);
      }
    }

    for (int j = rects.size(); j < sprites.size(); j++) {
      Sprite unusedSprite = sprites.get(j);
      unusedSprite.setHidden(true);
      unusedSprite.redraw();
    }
    for (int j = rects.size(); j < labels.size(); j++) {
      Sprite unusedSprite = labels.get(j);
      unusedSprite.setHidden(true);
      unusedSprite.redraw();
    }

    if (!chart.hasShadows()) {
      hideShadows();
    } else {
      for (int k = 0; k < shadowGroups.size(); k++) {
        SpriteList<Sprite> shadows = shadowGroups.get(k);
        for (int j = rects.size(); j < shadows.size(); j++) {
          Sprite unusedSprite = shadows.get(j);
          unusedSprite.setHidden(true);
          unusedSprite.redraw();
        }
      }
    }
    drawLabels();
  }

  /**
   * Calculates the girth of bars in the series.
   *
   * @return the girth of bars in the series
   */
  public double getBarGirth() {
    int ln = chart.getCurrentStore().size();
    double gutter = this.gutter / 100;
    double chartGirth = column ? chart.getBBox().getWidth() : chart.getBBox().getHeight();
    double numerator = (column ? xPadding : yPadding) * 2;
    double denominator = (ln * (gutter + 1) - gutter);
    return (chartGirth - numerator) / denominator;
  }

  /**
   * Returns the fields that have been hidden from the series using
   * {@link #hide(int)}.
   *
   * @return the fields that have been hidden from the series
   */
  public Set<Integer> getExcluded() {
    if (yFields.size() > 1) {
      return exclude;
    } else {
      return null;
    }
  }

  /**
   * Returns the gutter between group bars.
   *
   * @return the gutter between group bars
   */
  public double getGroupGutter() {
    return groupGutter;
  }

  /**
   * Returns the gutter between bars.
   *
   * @return the gutter between bars
   */
  public double getGutter() {
    return gutter;
  }

  @Override
  public double[] getGutters() {
    double gutter = Math.ceil((column ? xPadding : yPadding) + getBarGirth() / 2);
    double[] gutters = {0, 0};
    gutters[column ? 0 : 1] = gutter;
    return gutters;
  }

  @Override
  public ArrayList<String> getLegendTitles() {
    ArrayList<String> titles = new ArrayList<String>();
    for (int j = 0; j < getYFields().size(); j++) {
      if (legendTitles.size() > j) {
        titles.add(legendTitles.get(j));
      } else {
        titles.add(getValueProviderName(getYField(j), j + 1));
      }
    }
    return titles;
  }

  /**
   * Returns the x axis position of the series.
   *
   * @return the x axis position of the series
   */
  public Position getXAxisPosition() {
    return xAxisPosition;
  }

  /**
   * Returns the y axis position of the series.
   *
   * @return the y axis position of the series
   */
  public Position getYAxisPosition() {
    return yAxisPosition;
  }

  /**
   * Returns the value provider for the y-axis of the series at the given index.
   *
   * @param index the index of the value provider
   * @return the value provider for the y-axis of the series at the given index
   */
  public ValueProvider<? super M, ? extends Number> getYField(int index) {
    return yFields.get(index);
  }

  /**
   * Returns the list of value providers for the y-axis of the series.
   *
   * @return the list of value providers for the y-axis of the series
   */
  public List<ValueProvider<? super M, ? extends Number>> getYFields() {
    return yFields;
  }

  @Override
  public void hide(int yFieldIndex) {
    if (yFields.size() > 1) {
      for (int i = 0; i < sprites.size() / yFields.size(); i++) {
        sprites.get(yFieldIndex + i * yFields.size()).setHidden(true);
        sprites.get(yFieldIndex + i * yFields.size()).redraw();
        if (labels.size() == sprites.size()) {
          labels.get(yFieldIndex + i * yFields.size()).setHidden(true);
          labels.get(yFieldIndex + i * yFields.size()).redraw();
        }
        if (chart.hasShadows()) {
          for (int j = 0; j < shadowGroups.size(); j++) {
            SpriteList<Sprite> shadows = shadowGroups.get(j);
            if (shadows.size() > yFieldIndex + i * yFields.size()) {
              shadows.get(yFieldIndex + i * yFields.size()).setHidden(true);
              shadows.get(yFieldIndex + i * yFields.size()).redraw();
            }
          }
        }
      }
      exclude.add(yFieldIndex);
      chart.redrawChartForced();
    } else {
      toggle(true);
      exclude.add(0);
    }
    // update axes
    chart.getAxis(yAxisPosition);
  }

  @Override
  public void highlight(int yFieldIndex) {
    RectangleSprite bar = (RectangleSprite) sprites.get(yFieldIndex);
    highlighter.highlight(bar);
  }

  @Override
  public void highlightAll(int index) {
    if (yFields.size() > 1) {
      for (int i = 0; i < sprites.size() / yFields.size(); i++) {
        highlighter.highlight(sprites.get(index + i * yFields.size()));
      }
    } else {
      for (int i = 0; i < sprites.size(); i++) {
        highlighter.highlight(sprites.get(i));
      }
    }
  }


  /**
   * Removes a data field for the y axis of the series.
   *
   * @param index the index to have the yField inserted
   * @return the removed field
   */
  public ValueProvider<? super M, ? extends Number> removeYField(int index) {
    return this.yFields.remove(index);
  }

  /**
   * Removes a data field for the y axis of the series.
   *
   * @param yField the value provider for the data on the y axis
   * @return whether or not the field was successfully removed
   */
  public boolean removeYField(ValueProvider<? super M, ? extends Number> yField) {
    return this.yFields.remove(yField);
  }

  /**
   * Sets the gutter between group bars.
   *
   * @param groupGutter
   */
  public void setGroupGutter(double groupGutter) {
    this.groupGutter = groupGutter;
  }

  /**
   * Sets the gutter between bars.
   *
   * @param gutter the gutter between bars
   */
  public void setGutter(double gutter) {
    this.gutter = gutter;
  }

  /**
   * Sets the series title used in the legend.
   *
   * @param title the series title used in the legend
   */
  public void setLegendTitle(String title) {
    legendTitles.clear();
    legendTitles.add(title);

    if (chart != null) {
      Legend<M> legend = chart.getLegend();
      if (legend != null) {
        legend.create();
        legend.updatePosition();
      }
    }
  }

  /**
   * Sets the list of labels used by the legend.
   *
   * @param legendTitles the list of labels
   */
  public void setLegendTitles(List<String> legendTitles) {
    this.legendTitles = legendTitles;

    if (chart != null) {
      Legend<M> legend = chart.getLegend();
      if (legend != null) {
        legend.create();
        legend.updatePosition();
      }
    }
  }

  /**
   * Sets whether or not the series is stacked.
   *
   * @param stacked whether or not the series is stacked
   */
  public void setStacked(boolean stacked) {
    this.stacked = stacked;
  }

  /**
   * Sets the position of the x axis on the chart to be used by the series.
   *
   * @param xAxisPosition the position of the x axis on the chart to be used by
   *                      the series
   */
  public void setXAxisPosition(Position xAxisPosition) {
    this.xAxisPosition = xAxisPosition;
  }

  /**
   * Sets the position of the y axis on the chart to be used by the series.
   *
   * @param yAxisPosition the position of the y axis on the chart to be used by
   *                      the series
   */
  public void setYAxisPosition(Position yAxisPosition) {
    this.yAxisPosition = yAxisPosition;
  }

  @Override
  public void show(int yFieldIndex) {
    if (yFields.size() > 1) {
      for (int i = 0; i < sprites.size() / yFields.size(); i++) {
        sprites.get(yFieldIndex + i * yFields.size()).setHidden(false);
        sprites.get(yFieldIndex + i * yFields.size()).redraw();
        if (labels.size() == sprites.size()) {
          labels.get(yFieldIndex + i * yFields.size()).setHidden(false);
          labels.get(yFieldIndex + i * yFields.size()).redraw();
        }
        if (chart.hasShadows()) {
          for (int j = 0; j < shadowGroups.size(); j++) {
            SpriteList<Sprite> shadows = shadowGroups.get(j);
            if (shadows.size() == sprites.size()) {
              shadows.get(yFieldIndex + i * yFields.size()).setHidden(false);
              shadows.get(yFieldIndex + i * yFields.size()).redraw();
            }
          }
        }
      }
      exclude.remove(yFieldIndex);
      calculateBBox(false);
      chart.redrawChartForced();
    } else {
      toggle(false);
      exclude.remove(0);
    }
  }

  @Override
  public void unHighlight(int yFieldIndex) {
    RectangleSprite bar = (RectangleSprite) sprites.get(yFieldIndex);
    highlighter.unHighlight(bar);
  }

  @Override
  public void unHighlightAll(int index) {
    if (yFields.size() > 1) {
      for (int i = 0; i < sprites.size() / yFields.size(); i++) {
        highlighter.unHighlight(sprites.get(index + i * yFields.size()));
      }
    } else {
      for (int i = 0; i < sprites.size(); i++) {
        highlighter.unHighlight(sprites.get(i));
      }
    }
  }

  @Override
  public boolean visibleInLegend(int index) {
    if (yFields.size() > 1) {
      if (exclude.contains(index)) {
        return false;
      }
      return true;
    } else {
      if (sprites.size() == 0) {
        return true;
      } else {
        return !sprites.get(0).isHidden();
      }
    }
  }

  @Override
  protected int getIndex(PrecisePoint point) {
    for (int i = 0; i < sprites.size(); i++) {
      if (((RectangleSprite) sprites.get(i)).toRectangle().contains(point) && !sprites.get(i).isHidden()) {
        return i;
      }
    }
    return -1;
  }

  @Override
  protected int getStoreIndex(int index) {
    return (int) Math.floor(index / yFields.size());
  }

  @Override
  protected ValueProvider<? super M, ? extends Number> getValueProvider(int index) {
    ValueProvider<? super M, ? extends Number> value = null;
    int storeIndex = getStoreIndex(index);
    int yFieldIndex = index - (yFields.size() * storeIndex);
    if (yFields.size() > 1) {
      value = yFields.get(yFieldIndex);

    } else if (yFields.size() == 1) {
      value = yFields.get(yFieldIndex);
    }
    return value;
  }

  /**
   * Calculates the bounds of the series.
   */
  private void calculateBounds() {
    ListStore<M> store = chart.getCurrentStore();
    double barWidth = getBarGirth();
    double groupGutter = this.groupGutter / 100;
    groupBarsLength = yFields.size();
    PreciseRectangle chartBBox = chart.getBBox();

    calculateBBox(false);

    // skip excluded series
    groupBarsLength -= exclude.size();
    axis = chart.getAxis(yAxisPosition);
    if (axis != null) {
      minY = axis.getFrom();
      maxY = axis.getTo();
    } else if (yFields.size() > 0) {
      NumericAxis<M> numAxis = new NumericAxis<M>();
      numAxis.setChart(chart);
      for (int i = 0; i < yFields.size(); i++) {
        numAxis.addField(yFields.get(i));
      }
      numAxis.calcEnds();
      minY = numAxis.getFrom();
      maxY = numAxis.getTo();
    }
    axis = chart.getAxis(xAxisPosition);
    if (axis != null) {
      minY = axis.getFrom();
      maxY = axis.getTo();
    }


    scale = (column ? chartBBox.getHeight() - yPadding * 2 : chartBBox.getWidth() - xPadding * 2)
        / (Math.abs(maxY - minY));
    groupBarWidth = barWidth;//barWidth / ((stacked ? 1 : groupBarsLength) * (groupGutter + 1) - groupGutter);
    zero = column ? chartBBox.getY() + chartBBox.getHeight() - yPadding : chartBBox.getX() + xPadding;

    List<Double> totalPositive = new ArrayList<Double>();
    List<Double> totalNegative = new ArrayList<Double>();
    if (stacked) {
      for (int i = 0; i < store.size(); i++) {
        M model = store.get(i);
        totalPositive.add(0.0);
        totalNegative.add(0.0);
        for (int j = 0; j < yFields.size(); j++) {
          if (exclude.contains(j)) {
            continue;
          }
          double value = yFields.get(j).getValue(model).doubleValue();
          if (value > 0) {
            totalPositive.set(i, totalPositive.get(i) + value);
          } else {
            totalNegative.set(i, totalNegative.get(i) + Math.abs(value));
          }
        }
      }
      if (maxY > 0) {
        totalPositive.add(maxY);
      } else {
        totalNegative.add(Math.abs(maxY));
      }
      if (minY > 0) {
        totalPositive.add(minY);
      } else {
        totalNegative.add(Math.abs(minY));
      }
      double minus = 0;
      double plus = 0;
      for (int i = 0; i < totalNegative.size(); i++) {
        minus = Math.max(minus, totalNegative.get(i));
      }
      for (int i = 0; i < totalPositive.size(); i++) {
        plus = Math.max(plus, totalPositive.get(i));
      }
      scale = (column ? bbox.getHeight() - yPadding * 2 : bbox.getWidth() - xPadding * 2) / (plus + minus);
      zero = zero + minus * scale * (column ? -1 : 1);
    } else if (minY * maxY < 0) { // mix of positive and negative
      if (column) {
        zero -= -minY * scale;
      } else {
        zero += -minY * scale;
      }
    } else if (minY < 0) { // all negative

      if (column) {
        zero = chartBBox.getY();
      } else {
        zero = chartBBox.getX() + chartBBox.getWidth();
      }
    }

  }

  /**
   * Build an array of paths for the chart.
   */
  private void calculatePaths() {
    ListStore<M> store = chart.getCurrentStore();

    double gutter = this.gutter / 100;

    calculateBounds();

    for (int i = 0; i < store.size(); i++) {
      RectangleSprite lowHighRect = new RectangleSprite();
      RectangleSprite openCloseRect = new RectangleSprite();

      rects.put(i * 2, lowHighRect);
      rects.put(i * 2 + 1, openCloseRect);


      double open = getYField(0).getValue(store.get(i)).doubleValue();
      double close = getYField(1).getValue(store.get(i)).doubleValue();
      double low = getYField(2).getValue(store.get(i)).doubleValue();
      double high = getYField(3).getValue(store.get(i)).doubleValue();

      low = Math.min(low, Math.min(open, close));
      high = Math.max(high, Math.max(open, close));

      double x = bbox.getX() + xPadding + i * getBarGirth() * (1 + gutter) + (groupBarWidth / 2);

      {
        double mainHeight = (high - low) * scale;

        lowHighRect.setFill(colors.get(close > open ? 1 : 0));

        lowHighRect.setWidth(1);//Math.max(groupBarWidth, 0));
        lowHighRect.setX(x);

        double mainY = bbox.getY() + scale * (maxY - high);

        lowHighRect.setY(mainY);
        lowHighRect.setHeight(mainHeight);
      }


      {
        double height = scale * Math.abs(close - open);
        double y = bbox.getY() + scale * (maxY - Math.max(open, close));
        openCloseRect.setX(x - 4);
        openCloseRect.setY(y);
        openCloseRect.setWidth(9);
        openCloseRect.setHeight(height);

        openCloseRect.setFill(colors.get(close > open ? 1 : 0));
      }
    }
  }

  /**
   * Draws the labels on the series.
   */
  private void drawLabels() {
    if (labelConfig != null) {
      LabelPosition labelPosition = labelConfig.getLabelPosition();
      for (int j = 0; j < rects.size(); j++) {
        if (rects.get(j) == null) {
          continue;
        }
        final Sprite sprite;
        RectangleSprite bar = rects.get(j);
        if (labels.get(j) != null) {
          sprite = labels.get(j);
          sprite.setHidden(false);
        } else {
          sprite = labelConfig.getSpriteConfig().copy();
          if (column) {
            sprite.setTranslation(bar.getX(), bar.getY() + bar.getHeight());
          } else {
            sprite.setTranslation(bar.getX(), bar.getY());
          }
          labels.put(j, sprite);
          chart.addSprite(sprite);
        }
        if (chart.isAnimated() && chart.isResizing()) {
          if (column) {
            sprite.setTranslation(bar.getX(), bar.getY() + bar.getHeight());
          } else {
            sprite.setTranslation(bar.getX(), bar.getY());
          }
        }
        setLabelText(sprite, j);
        sprite.redraw();
        PreciseRectangle box = sprite.getBBox();
        double x = 0;
        double y = 0;
        if (column) {
          x = bar.getX() + bar.getWidth() / 2.0;
          if (labelPosition == LabelPosition.START) {
            y = bar.getY() + bar.getHeight() - box.getHeight();
          } else if (labelPosition == LabelPosition.END) {
            if (bar.getHeight() > box.getHeight()) {
              y = bar.getY();
            } else {
              y = bar.getY() - box.getHeight();
            }
          } else if (labelPosition == LabelPosition.OUTSIDE) {
            y = bar.getY() - box.getHeight();
          }
        } else {
          y = bar.getY() + bar.getHeight() / 2.0 - box.getHeight() / 2.0;
          if (labelPosition == LabelPosition.START) {
            x = bar.getX();
          } else if (labelPosition == LabelPosition.END) {
            if (bar.getWidth() > box.getWidth()) {
              x = bar.getX() + bar.getWidth() - box.getWidth();
            } else {
              x = bar.getX() + bar.getWidth();
            }
          } else if (labelPosition == LabelPosition.OUTSIDE) {
            x = bar.getX() + bar.getWidth();
          }
        }
        if (labelConfig.isLabelContrast()) {
          final Sprite back = sprites.get(j);
          if (chart.isAnimated()) {
            Scheduler.get().scheduleDeferred(new ScheduledCommand() {
              @Override
              public void execute() {
                setLabelContrast(sprite, labelConfig, back);
              }
            });
          } else {
            setLabelContrast(sprite, labelConfig, back);
          }
        }
        if (chart.isAnimated() && sprite.getTranslation() != null) {
          DrawFx.createTranslationAnimator(sprite, x, y).run(chart.getAnimationDuration(), chart.getAnimationEasing());
        } else {
          sprite.setTranslation(x, y);
        }
        SeriesRenderer<M> labelRenderer = labelConfig.getSpriteRenderer();
        if (labelRenderer != null) {
          labelRenderer.spriteRenderer(sprite, j, chart.getCurrentStore());
        }
        sprite.redraw();
      }
    }
  }

  /**
   * Renders the shadows for the bar at the given index.
   *
   * @param index the index of the shadows
   */
  private void renderShadows(int index) {
    if (groupBarsLength == 0) {
      return;
    }
    // create shadows
    for (int shindex = 0; shindex < shadowGroups.size(); shindex++) {
      Sprite shadowBarAttr = shadowAttributes.get(shindex);
      SpriteList<Sprite> shadows = shadowGroups.get(shindex);
      final RectangleSprite shadowSprite;
      RectangleSprite rect = rects.get(index);
      if (index < shadows.size()) {
        shadowSprite = (RectangleSprite) shadows.get(index);
        shadowSprite.setHidden(false);
      } else {
        shadowSprite = new RectangleSprite();
        if (column) {
          shadowSprite.setX(rect.getX());
          shadowSprite.setY(rect.getY() + rect.getHeight());
          shadowSprite.setWidth(rect.getWidth());
          shadowSprite.setHeight(0);
        } else {
          shadowSprite.setX(rect.getX());
          shadowSprite.setY(rect.getY());
          shadowSprite.setWidth(0);
          shadowSprite.setHeight(rect.getHeight());
        }
        shadowSprite.update(shadowBarAttr);
        shadows.add(shadowSprite);
        chart.addSprite(shadowSprite);
      }
      if (chart.isAnimated() && chart.isResizing()) {
        if (column) {
          shadowSprite.setX(rect.getX());
          shadowSprite.setY(rect.getY() + rect.getHeight());
          shadowSprite.setWidth(rect.getWidth());
          shadowSprite.setHeight(0);
        } else {
          shadowSprite.setX(rect.getX());
          shadowSprite.setY(rect.getY());
          shadowSprite.setWidth(0);
          shadowSprite.setHeight(rect.getHeight());
        }
      }
      if (chart.isAnimated() && !Double.isNaN(shadowSprite.getHeight()) && !Double.isNaN(shadowSprite.getWidth())) {
        DrawFx.createRectangleAnimator(shadowSprite, rect.toRectangle()).run(chart.getAnimationDuration(),
            chart.getAnimationEasing());
      } else {
        shadowSprite.setX(rect.getX());
        shadowSprite.setY(rect.getY());
        shadowSprite.setWidth(rect.getWidth());
        shadowSprite.setHeight(rect.getHeight());
        shadowSprite.redraw();
      }
      if (shadowRenderer != null) {
        shadowRenderer.spriteRenderer(shadowSprite, index, chart.getCurrentStore());
      }
    }
    shadowed = true;
  }

  /**
   * Toggles all the sprites in the series to be hidden or shown.
   *
   * @param hide if true hides
   */
  private void toggle(boolean hide) {
    calculateBBox(false);
    if (sprites.size() > 0) {
      for (int i = 0; i < sprites.size(); i++) {
        sprites.get(i).setHidden(hide);
        sprites.get(i).redraw();
      }
    }
    if (labelConfig != null) {
      for (Sprite s : labels.values()) {
        s.setHidden(hide);
        s.redraw();
      }
    }
    for (int i = 0; i < shadowGroups.size(); i++) {
      SpriteList<Sprite> shadows = shadowGroups.get(i);
      for (int j = 0; j < shadows.size(); j++) {
        shadows.get(j).setHidden(hide);
        shadows.get(j).redraw();
      }
    }
  }
}
