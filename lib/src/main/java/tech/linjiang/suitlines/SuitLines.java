/*
 * Copyright 2017 linjiang.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package tech.linjiang.suitlines;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.EdgeEffect;
import android.widget.Scroller;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * https://github.com/whataa
 */
public class SuitLines extends View {

    public static final String TAG = SuitLines.class.getSimpleName();

    public SuitLines(Context context) {
        this(context, null);
    }

    public SuitLines(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SuitLines(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initOptionalState(context, attrs);

        basePadding = Util.dip2px(basePadding);
        maxVelocity = ViewConfiguration.get(context).getScaledMaximumFlingVelocity();
        clickSlop = ViewConfiguration.get(context).getScaledEdgeSlop();
        scroller = new Scroller(context);
        edgeEffectLeft = new EdgeEffect(context);
        edgeEffectRight = new EdgeEffect(context);
        setEdgeEffectColor(edgeEffectColor);

        basePaint.setColor(defaultLineColor[0]);
        basePaint.setStyle(Paint.Style.STROKE);
        basePaint.setStrokeWidth(4);
        coverLinePaint.setStyle(Paint.Style.STROKE);
        coverLinePaint.setStrokeWidth(Util.dip2px(5));
        setLineStyle(SOLID);
        xyPaint.setTextSize(Util.size2sp(defaultXySize, getContext()));
        xyPaint.setColor(defaultXyColor);
        hintPaint.setTextSize(Util.size2sp(12, getContext()));
        hintPaint.setColor(hintColor);
        hintPaint.setStyle(Paint.Style.STROKE);
        hintPaint.setStrokeWidth(2);
        hintPaint.setTextAlign(Paint.Align.CENTER);
    }

    private void initOptionalState(Context ctx, AttributeSet attrs) {
        TypedArray ta = ctx.obtainStyledAttributes(attrs, R.styleable.suitlines);
        defaultXySize = ta.getFloat(R.styleable.suitlines_xySize, defaultXySize);
        defaultXyColor = ta.getColor(R.styleable.suitlines_xyColor, defaultXyColor);
        lineType = ta.getInt(R.styleable.suitlines_lineType, CURVE);
        lineStyle = ta.getInt(R.styleable.suitlines_lineStyle, SOLID);
        needEdgeEffect = ta.getBoolean(R.styleable.suitlines_needEdgeEffect, needEdgeEffect);
        edgeEffectColor = ta.getColor(R.styleable.suitlines_colorEdgeEffect, edgeEffectColor);
        needShowHint = ta.getBoolean(R.styleable.suitlines_needClickHint, needShowHint);
        hintColor = ta.getColor(R.styleable.suitlines_colorHint, hintColor);
        maxOfVisible = ta.getInt(R.styleable.suitlines_maxOfVisible, maxOfVisible);
        countOfY = ta.getInt(R.styleable.suitlines_countOfY, countOfY);
        ta.recycle();
    }


    // 创建自己的Handler，与ViewRootImpl的Handler隔离，方便detach时remove。
    private Handler handler = new Handler(Looper.getMainLooper());
    // 遍历线上点的动画插值器
    private TimeInterpolator linearInterpolator = new LinearInterpolator();
    // 每个数据点的动画插值
    private TimeInterpolator pointInterpolator = new OvershootInterpolator(3);
    private RectF linesArea, xArea, yArea, hintArea;
    /**
     * 默认画笔
     */
    private Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    /**
     * x，y轴对应的画笔
     */
    private Paint xyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    /**
     * 点击提示的画笔
     */
    private Paint hintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    /**
     * 默认画笔的颜色，索引0位置为画笔颜色，整个数组为shader颜色
     */
    private int[] defaultLineColor = {Color.RED, Color.YELLOW, Color.WHITE};
    private int hintColor = Color.RED;
    /**
     * xy轴文字颜色和大小
     */
    private int defaultXyColor = Color.GRAY;
    private float defaultXySize = 8;
    /**
     * 每根画笔对应一条线
     */
    private List<Paint> paints = new ArrayList<>();
    private List<Path> paths = new ArrayList<>();
    private Path tmpPath = new Path();
    /**
     * fill形态下时，边缘线画笔
     */
    Paint coverLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    /**
     * 约定：如果需要实现多组数据，那么每组数据的长度必须相同！
     * 多组数据的数据池；
     * Key：一组数据的唯一标识,注意：要求连续且从0开始
     * value：一组数据
     */
    private Map<Integer, List<Unit>> datas = new HashMap<>();

    /**
     * 所有数据集的动画
     */
    private List<ValueAnimator> animators = new ArrayList<>();
    /**
     * line的点击效果
     */
    private ValueAnimator clickHintAnimator;
    /**
     * 当前正在动画的那组数据
     */
    private int curAnimLine;
    /**
     * 整体动画的起始时间
     */
    private long startTimeOfAnim;
    /**
     * 是否正在整体动画中
     */
    private boolean isAniming;
    /**
     * 两个点之间的动画启动间隔，大于0时仅当总数据点<可见点数时有效
     */
    private long intervalOfAnimCost = 100;
    /**
     * 可见区域中，将一组数据遍历完总共花费的最大时间
     */
    private long maxOfAnimCost = 1000;
    /**
     * 一组数据在可见区域中的最大可见点数，至少>=2
     */
    private int maxOfVisible = 7;
    /**
     * 文本之间/图表之间的间距
     */
    private int basePadding = 4;
    /**
     * y轴刻度数，至少>=1
     */
    private int countOfY = 5;

    /**
     * y轴的缓存，提高移动效率
     */
    private Bitmap yAreaBuffer;
    /**
     * y轴的辅助刻度线
     */
    private Bitmap yGridBuffer;

    /**
     * y轴的最小和大刻度值，保留一位小数
     */
    private float[] minAndMaxOfY = new float[2];

    /**
     * 根据可见点数计算出的两点之间的距离
     */
    private float realBetween;
    /**
     * 手指/fling的上次位置
     */
    private float lastX;
    /**
     * 滚动当前偏移量
     */
    private float offset;
    /**
     * 滚动上一次的偏移量
     */
    private float lastOffset;
    /**
     * 滚动偏移量的边界
     */
    private float maxOffset;
    /**
     * fling最大速度
     */
    private int maxVelocity;
    // 点击y的误差
    private int clickSlop;
    /**
     * 判断左/右方向，当在边缘就不触发fling，以优化性能
     */
    private float orientationX;
    private VelocityTracker velocityTracker;
    private Scroller scroller;

    private EdgeEffect edgeEffectLeft, edgeEffectRight;
    // 对于fling，仅吸收到达边缘时的速度
    private boolean hasAbsorbLeft, hasAbsorbRight;
    /**
     * 是否需要边缘反馈效果
     */
    private boolean needEdgeEffect = true;
    private int edgeEffectColor = Color.GRAY;
    /**
     * fill形态下，是否绘制边缘线
     * 若开启该特性，闭合路径的操作将延迟到绘制时
     */
    private boolean needCoverLine;
    /**
     * 点击是否弹出额外信息
     */
    private boolean needShowHint = true;
    /**
     * 实际的点击位置，0为x索引，1为某条line
     */
    private int[] clickIndexs;
    private float firstX, firstY;
    /**
     * 控制是否强制重新生成path，当改变lineType/paint时需要
     */
    private boolean forceToDraw;
    /**
     * 是否显示y轴的辅助刻度线
     */
    private boolean showYGrid = false;

    /**
     * lines在当前可见区域的边缘点
     */
    private int[] suitEdge;
    /**
     * y为0时的坐标值
     */
    private float zeroAxisValue;

    // 曲线、线段
    public static final int CURVE = 0;
    public static final int SEGMENT = 1;
    private int lineType = CURVE;
    public static final int SOLID = 0;
    public static final int DASHED = 1;
    private int lineStyle = SOLID;

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        calcAreas();
        basePaint.setShader(buildPaintColor(defaultLineColor));
        if (!datas.isEmpty()) {
            calcUnitXY();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (datas.isEmpty() || isAniming) {
            recycleVelocityTracker();
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                firstX = lastX = event.getX();
                firstY = event.getY();
                scroller.abortAnimation();
                initOrResetVelocityTracker();
                velocityTracker.addMovement(event);
                super.onTouchEvent(event);
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                lastX = event.getX(0);
                break;
            case MotionEvent.ACTION_MOVE:
                orientationX = event.getX() - lastX;
                onScroll(orientationX);
                lastX = event.getX();
                velocityTracker.addMovement(event);
                if (needEdgeEffect && datas.get(0).size() > maxOfVisible) {
                    if (isArriveAtLeftEdge()) {
                        edgeEffectLeft.onPull(Math.abs(orientationX) / linesArea.height());
                    } else if (isArriveAtRightEdge()) {
                        edgeEffectRight.onPull(Math.abs(orientationX) / linesArea.height());
                    }
                }
                break;
            case MotionEvent.ACTION_POINTER_UP: // 计算出正确的追踪手指
                int minID = event.getPointerId(0);
                for (int i = 0; i < event.getPointerCount(); i++) {
                    if (event.getPointerId(i) <= minID) {
                        minID = event.getPointerId(i);
                    }
                }
                if (event.getPointerId(event.getActionIndex()) == minID) {
                    minID = event.getPointerId(event.getActionIndex() + 1);
                }
                lastX = event.getX(event.findPointerIndex(minID));
                break;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                if (needShowHint && event.getAction() == MotionEvent.ACTION_UP) {
                    boolean canCallTap = Math.abs(event.getX() - firstX) < 2
                            && Math.abs(event.getY() - firstY) < 2;
                    if (canCallTap) {
                        onTap(event.getX(), event.getY());
                    }
                }
                velocityTracker.addMovement(event);
                velocityTracker.computeCurrentVelocity(1000, maxVelocity);
                int initialVelocity = (int) velocityTracker.getXVelocity();
                velocityTracker.clear();
                if (!isArriveAtLeftEdge() && !isArriveAtRightEdge()) {
                    scroller.fling((int) event.getX(), (int) event.getY(), initialVelocity / 2,
                            0, Integer.MIN_VALUE, Integer.MAX_VALUE, 0, 0);
                    invalidate();
                } else {
                    edgeEffectLeft.onRelease();
                    edgeEffectRight.onRelease();
                }
                lastX = event.getX();
                break;
        }

        return super.onTouchEvent(event);
    }


    @Override
    public void computeScroll() {
        if (scroller.computeScrollOffset()) {
            onScroll(scroller.getCurrX() - lastX);
            lastX = scroller.getCurrX();
            if (needEdgeEffect) {
                if (!hasAbsorbLeft && isArriveAtLeftEdge()) {
                    hasAbsorbLeft = true;
                    edgeEffectLeft.onAbsorb((int) scroller.getCurrVelocity());
                } else if (!hasAbsorbRight && isArriveAtRightEdge()) {
                    hasAbsorbRight = true;
                    edgeEffectRight.onAbsorb((int) scroller.getCurrVelocity());
                }
            }
            postInvalidate();
        } else {
            hasAbsorbLeft = false;
            hasAbsorbRight = false;
        }
    }


    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);
        if (datas.isEmpty()) return;
        if (!needEdgeEffect) return;
        if (!edgeEffectLeft.isFinished()) {
            canvas.save();
            canvas.rotate(-90);
            canvas.translate(-linesArea.bottom, linesArea.left);
            edgeEffectLeft.setSize((int) linesArea.height(), (int) linesArea.height());
            if (edgeEffectLeft.draw(canvas)) {
                postInvalidate();
            }
            canvas.restore();
        }

        if (!edgeEffectRight.isFinished()) {
            canvas.save();
            canvas.rotate(90);
            canvas.translate(linesArea.top, -linesArea.right);
            edgeEffectRight.setSize((int) linesArea.height(), (int) linesArea.height());
            if (edgeEffectRight.draw(canvas)) {
                postInvalidate();
            }
            canvas.restore();
        }
    }


    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (datas.isEmpty()) return;
        // lines
        canvas.save();
        canvas.clipRect(linesArea.left, linesArea.top, linesArea.right, linesArea.bottom+xArea.height());
        canvas.translate(offset, 0);
        // 当滑动到边缘 或 上次与本次结果相同 或 不需要计算边缘点 的时候就不再计算，直接draw已有的path
        if (!paths.isEmpty() && !forceToDraw && !isAniming && (lastOffset == offset || noNeedCalcEdge(offset))) {
            drawExsitDirectly(canvas);
            // hint
            if (clickIndexs != null) {
                drawClickHint(canvas);
            }
        } else {
            // 因为手指或fling计算出的offset不是连续按1px递增/减的，即无法准确地确定当前suitEdge和linesArea之间的相对位置
            // 所以不适合直接加减suitEdge来划定数据区间
            suitEdge = findSuitEdgeInVisual2();
            drawLines(canvas, suitEdge[0], suitEdge[1]);
        }
        // x 蓝色会稍增加
        drawX(canvas, suitEdge[0], suitEdge[1]);
        if (lastOffset != offset) {
            clickIndexs = null;
        }
        lastOffset = offset;
        forceToDraw = false;
        canvas.restore();
        // y
        drawY(canvas);
    }

    /**
     * 边缘点在可见区域两侧时不需要重新计算<br>
     * 但是手指滑动越快，该分支的有效效果越差
     * @param offset
     * @return
     */
    private boolean noNeedCalcEdge(float offset) {
        return suitEdge != null
                && datas.get(0).get(suitEdge[0]).getXY().x <= linesArea.left - offset
                && datas.get(0).get(suitEdge[1]).getXY().x >= linesArea.right - offset;
    }

    /**
     * 滑动方法，同时检测边缘条件
     *
     * @param deltaX
     */
    private void onScroll(float deltaX) {
        offset += deltaX;
        offset = offset > 0 ? 0 : (Math.abs(offset) > maxOffset) ? -maxOffset : offset;
        invalidate();
    }


    private void onTap(float upX, float upY) {
        upX -= offset;
        RectF bak = new RectF(linesArea);
        bak.offset(-offset,0);
        if (datas.isEmpty() || !bak.contains(upX, upY)) {
            return;
        }
        float index = (upX - linesArea.left) / realBetween;
        int realIndex = -1;
        if ((index - (int) index) > 0.6f) {
            realIndex = (int) index + 1;
        } else if ((index - (int) index) < 0.4f) {
            realIndex = (int) index;
        }
        if (realIndex != -1) {
            int mostMatchY = -1;
            for (int i = 0; i < datas.size(); i++) {
                float cur = Math.abs(datas.get(i).get(realIndex).getXY().y - upY);
                if (cur <= clickSlop) {
                    if (mostMatchY != -1) {
                        if (Math.abs(datas.get(mostMatchY).get(realIndex).getXY().y - upY) > cur) {
                            mostMatchY = i;
                        }
                    } else {
                        mostMatchY = i;
                    }
                }
            }
            if (mostMatchY != -1) {
                if (clickHintAnimator != null && clickHintAnimator.isRunning()) {
                    clickHintAnimator.removeAllUpdateListeners();
                    clickHintAnimator.cancel();
                    hintPaint.setAlpha(100);
                    clickIndexs = null;
                    invalidate();
                }
                clickIndexs = new int[]{realIndex, mostMatchY};
                clickHintAnimator = ValueAnimator.ofInt(100, 30);
                clickHintAnimator.setDuration(800);
                clickHintAnimator.setInterpolator(linearInterpolator);
                clickHintAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator animation) {
                        int cur = (Integer) animation.getAnimatedValue();
                        if (cur <= 30) {
                            hintPaint.setAlpha(100);
                            clickIndexs = null;
                        } else {
                            hintPaint.setAlpha(cur);
                        }
                        postInvalidate();
                    }
                });
                clickHintAnimator.start();
            }
        }
    }

    private void initOrResetVelocityTracker() {
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain();
        } else {
            velocityTracker.clear();
        }
    }

    private void recycleVelocityTracker() {
        if (velocityTracker != null) {
            velocityTracker.recycle();
            velocityTracker = null;
        }
    }

    /**
     * 是否滑动到了左边缘，注意，并非指可视区域的边缘，下同
     *
     * @return
     */
    private boolean isArriveAtLeftEdge() {
        return offset == 0 && orientationX > 0;
    }

    /**
     * 是否滑动到了右边缘
     *
     * @return
     */
    private boolean isArriveAtRightEdge() {
        return Math.abs(offset) == Math.abs(maxOffset) && orientationX < 0;
    }

    /**
     * 找到当前可见区间内合适的两个边缘点，注意如果边缘点不在可见区间的边缘，则需要包含下一个不可见的点
     *
     * @return
     */
    private int[] findSuitEdgeInVisual() {
        int startIndex = 0, endIndex = datas.get(0).size() - 1;
        if (offset == 0) {// 不可滑动或当前位于最左边
            startIndex = 0;
            endIndex = Math.min(datas.get(0).size() - 1, maxOfVisible - 1);
        } else if (Math.abs(offset) == maxOffset) {// 可滑动且当前位于最右边
            endIndex = datas.get(0).size() - 1;
            startIndex = endIndex - maxOfVisible + 1;
        } else {
            float startX = linesArea.left - offset;
            float endX = linesArea.right - offset;
            if (datas.get(0).size() > maxOfVisible) {
                // 找到指定区间的第一个被发现的点
                int suitKey = 0;
                int low = 0;
                int high = datas.get(0).size() - 1;
                List<Unit> i = datas.get(0);
                while (low <= high) {
                    int mid = (low + high) >>> 1;
                    Unit midVal = i.get(mid);
                    if (midVal.getXY().x < startX) {
                        low = mid + 1;
                    } else if (midVal.getXY().x > endX) {
                        high = mid - 1;
                    } else {
                        suitKey = mid;
                        break;
                    }
                }
                int bakKey = suitKey;
                // 先左边
                while (suitKey >= 0) {
                    startIndex = suitKey;
                    if (datas.get(0).get(suitKey).getXY().x <= startX) {
                        break;
                    }
                    suitKey--;
                }
                suitKey = bakKey;
                // 再右边
                while (suitKey < datas.get(0).size()) {
                    endIndex = suitKey;
                    if (datas.get(0).get(suitKey).getXY().x >= endX) {
                        break;
                    }
                    suitKey++;
                }
            }
        }
        return new int[]{startIndex, endIndex};
    }

    /**
     * 1. ax+b >= y
     * 2. a(x+1)+b <= y
     * 得到： (int)x = (y-b) / a
     * 由于 y = b - offset
     * 所以：(int)x = |offset| / a
     * @return
     */
    private int[] findSuitEdgeInVisual2() {
        int startIndex, endIndex;
        if (offset == 0) {// 不可滑动或当前位于最左边
            startIndex = 0;
            endIndex = Math.min(datas.get(0).size() - 1, maxOfVisible - 1);
        } else if (Math.abs(offset) == maxOffset) {// 可滑动且当前位于最右边
            endIndex = datas.get(0).size() - 1;
            startIndex = endIndex - maxOfVisible + 1;
        } else {
            startIndex = (int) (Math.abs(offset) / realBetween);
            endIndex = startIndex + maxOfVisible;
        }
        return new int[]{startIndex, endIndex};
    }

    /**
     * 开始连接每条线的各个点<br>
     * 最耗费性能的地方：canvas.drawPath
     * @param canvas
     * @param startIndex
     * @param endIndex
     */
    private void drawLines(Canvas canvas, int startIndex, int endIndex) {
        for (int i = 0; i < paths.size(); i++) {
            paths.get(i).reset();
        }
        for (int i = startIndex; i <= endIndex; i++) {
            for (int j = 0; j < datas.size(); j++) {
                Unit current = datas.get(j).get(i);
                float curY = zeroAxisValue - (zeroAxisValue - current.getXY().y) * current.getPercent();
                if (i == startIndex) {
                    paths.get(j).moveTo(current.getXY().x, curY);
                    continue;
                }
                if (lineType == SEGMENT) {
                    paths.get(j).lineTo(current.getXY().x, curY);
                } else if (lineType == CURVE) {
                    // 到这里肯定不是起始点，所以可以减1
                    Unit previous = datas.get(j).get(i - 1);
                    // 两个锚点的坐标x为中点的x，y分别是两个连接点的y
                    paths.get(j).cubicTo((previous.getXY().x + current.getXY().x) / 2,
                            zeroAxisValue - (zeroAxisValue - previous.getXY().y) * previous.getPercent(),
                            (previous.getXY().x + current.getXY().x) / 2, curY,
                            current.getXY().x, curY);
                }
                if (!needCoverLine && isLineFill() && i == endIndex) {
                    paths.get(j).lineTo(current.getXY().x, linesArea.bottom);
                    paths.get(j).lineTo(datas.get(j).get(startIndex).getXY().x, linesArea.bottom);
                    paths.get(j).close();
                }
            }
        }
        drawExsitDirectly(canvas);
    }

    /**
     * 直接draw现成的
     * @param canvas
     */
    private void drawExsitDirectly(Canvas canvas) {
        // TODO 需要优化
        for (int j = 0; j < datas.size(); j++) {
            if (!isLineFill() || !needCoverLine) {
                canvas.drawPath(paths.get(j), paints.get(j));
            } else {
                if (needCoverLine) {
                    coverLinePaint.setColor(Util.tryGetStartColorOfLinearGradient((LinearGradient) paints.get(j).getShader()));
                    canvas.save();
                    canvas.clipRect(linesArea.left - offset, linesArea.top, linesArea.right - offset, linesArea.bottom);
                    // 由于paint的stroke是双边，所以下一个draw不会覆盖当前已经的draw
                    canvas.drawPath(paths.get(j), coverLinePaint);
                    canvas.restore();
                    tmpPath.set(paths.get(j));
                    tmpPath.lineTo(datas.get(j).get(suitEdge[1]).getXY().x, linesArea.bottom);
                    tmpPath.lineTo(datas.get(j).get(suitEdge[0]).getXY().x, linesArea.bottom);
                    tmpPath.close();
                    canvas.drawPath(tmpPath, paints.get(j));
                    tmpPath.reset();
                }
            }
        }
        // TODO 画点
    }

    private float calcReferenceLengthOf(int j) {
        return linesArea.height() * 2 - datas.get(j).get(suitEdge[0]).getXY().y
                - datas.get(j).get(suitEdge[1]).getXY().y
                + datas.get(j).get(suitEdge[1]).getXY().x - datas.get(j).get(suitEdge[0]).getXY().x;
    }

    /**
     * 画提示文本和辅助线
     * @param canvas
     */
    private void drawClickHint(Canvas canvas) {
        Unit cur = datas.get(clickIndexs[1]).get(clickIndexs[0]);
        canvas.drawLine(datas.get(clickIndexs[1]).get(suitEdge[0]).getXY().x,cur.getXY().y,
                datas.get(clickIndexs[1]).get(suitEdge[1]).getXY().x,cur.getXY().y, hintPaint);
        canvas.drawLine(cur.getXY().x,linesArea.bottom,
                cur.getXY().x,linesArea.top, hintPaint);
        RectF bak = new RectF(hintArea);
        bak.offset(-offset, 0);
        hintPaint.setAlpha(100);
        hintPaint.setStyle(Paint.Style.FILL);
        canvas.drawRect(bak, hintPaint);
        hintPaint.setColor(Color.WHITE);
        if (!TextUtils.isEmpty(cur.getExtX())) {
            canvas.drawText("x : " + cur.getExtX(), bak.centerX(), bak.centerY() - 12, hintPaint);
        }
        canvas.drawText("y : " + cur.getValue(), bak.centerX(),
                bak.centerY() + 12 + Util.getTextHeight(hintPaint), hintPaint);
        hintPaint.setColor(hintColor);
    }

    /**
     * 画x轴,默认取第一条线的值
     * @param canvas
     * @param startIndex
     * @param endIndex
     */
    private void drawX(Canvas canvas, int startIndex, int endIndex) {
        canvas.drawLine(datas.get(0).get(startIndex).getXY().x, xArea.top,
                datas.get(0).get(endIndex).getXY().x, xArea.top, xyPaint);
        for (int i = startIndex; i <= endIndex; i++) {
            String extX = datas.get(0).get(i).getExtX();
            if (TextUtils.isEmpty(extX)) {
                continue;
            }
            if (i == startIndex && startIndex == 0) {
                xyPaint.setTextAlign(Paint.Align.LEFT);
            } else if (i == endIndex && endIndex == datas.get(0).size()-1) {
                xyPaint.setTextAlign(Paint.Align.RIGHT);
            } else {
                xyPaint.setTextAlign(Paint.Align.CENTER);
            }
            canvas.drawText(extX, datas.get(0).get(i).getXY().x, Util.calcTextSuitBaseY(xArea, xyPaint), xyPaint);
            canvas.drawLine(datas.get(0).get(i).getXY().x, xArea.top,
                    datas.get(0).get(i).getXY().x, xArea.top+basePadding, xyPaint);
        }
    }


    private void drawY(Canvas canvas) {
        if (yAreaBuffer == null) {
            // 可以在这里自定义y轴的绘制以及逻辑，例如线的类型、颜色、大小等
            yAreaBuffer = Bitmap.createBitmap((int)yArea.width(), (int)yArea.height(), Bitmap.Config.ARGB_8888);
            Rect yRect = new Rect(0, 0, yAreaBuffer.getWidth(), yAreaBuffer.getHeight());
            Canvas yCanvas = new Canvas(yAreaBuffer);
            yCanvas.drawLine(yRect.right, yRect.bottom, yRect.right, yRect.top, xyPaint);
            for (int i = 0; i < countOfY; i++) {
                xyPaint.setTextAlign(Paint.Align.RIGHT);
                float extY;
                float y, yAxis;
                if (i == 0) {
                    extY = minAndMaxOfY[0];
                    y = yAxis = yRect.bottom;
                } else if (i == countOfY - 1) {
                    extY = minAndMaxOfY[1];
                    y = yRect.top + Util.getTextHeight(xyPaint) + 3;
                    yAxis = yRect.top;
                } else {
                    extY = minAndMaxOfY[0] + (minAndMaxOfY[1] - minAndMaxOfY[0]) / (countOfY - 1) * i;
                    y = yAxis = yRect.bottom - yRect.height() / (countOfY - 1) * i + Util.getTextHeight(xyPaint)/2;
                }
                yCanvas.drawText(new DecimalFormat("##.#").format(extY), yRect.right - basePadding, y, xyPaint);
                yCanvas.drawLine(yRect.right - basePadding, yAxis, yRect.right, yAxis, xyPaint);
            }
            if (minAndMaxOfY[0] != 0 && minAndMaxOfY[1] != 0) {
                float y = zeroAxisValue - yArea.top;
                yCanvas.drawText("0", yRect.right - basePadding, y, xyPaint);
                yCanvas.drawLine(yRect.right - basePadding, y, yRect.right, y, xyPaint);
            }
        }
        canvas.drawBitmap(yAreaBuffer,yArea.left,yArea.top,null);

        if (yGridBuffer == null) {
            // 可以在这里自定义刻度辅助线的绘制，例如线的类型、颜色、大小等
            yGridBuffer = Bitmap.createBitmap((int)linesArea.width(), (int)linesArea.height(), Bitmap.Config.ARGB_8888);
            Rect yRect = new Rect(0, 0, yGridBuffer.getWidth(), yGridBuffer.getHeight());
            Canvas yCanvas = new Canvas(yGridBuffer);
            for (int i = 0; i < countOfY; i++) {
                float yAxis;
                if (i == 0) {
                    yAxis = yRect.bottom;
                } else if (i == countOfY - 1) {
                    yAxis = yRect.top;
                } else {
                    yAxis = yRect.bottom - yRect.height() / (countOfY - 1) * i + Util.getTextHeight(xyPaint)/2;
                }
                yCanvas.drawLine(0, yAxis, yCanvas.getWidth(), yAxis, xyPaint);
            }
            if (minAndMaxOfY[0] != 0 && minAndMaxOfY[1] != 0) {
                float y = zeroAxisValue - yArea.top;
                yCanvas.drawLine(0, y, yCanvas.getWidth(), y, xyPaint);
            }
        }
        if (showYGrid) {
            canvas.drawBitmap(yGridBuffer,linesArea.left,linesArea.top,null);
        }
    }

    /**
     *
     * @param color 不能为null
     * @return
     */
    private LinearGradient buildPaintColor(int[] color) {
        int[] bakColor = color;
        if (color != null && color.length < 2) {
            bakColor = new int[2];
            bakColor[0] = color[0];
            bakColor[1] = color[0];
        }
        return new LinearGradient(linesArea.left, linesArea.top,
                linesArea.left, linesArea.bottom, bakColor, null, Shader.TileMode.CLAMP);
    }

    /**
     * 基于orgPaint的clone
     *
     * @return
     */
    private Paint buildNewPaint() {
        Paint paint = new Paint();
        paint.set(basePaint);
        return paint;
    }


    private void feedInternal(Map<Integer, List<Unit>> entry, List<Paint> entryPaints, boolean needAnim) {
        cancelAllAnims();
        reset(); // 该方法调用了datas.clear();
        if (entry.isEmpty()) {
            invalidate();
            return;
        }
        if (entry.size() != entryPaints.size()) {
            throw new IllegalArgumentException("线的数量应该和画笔数量对应");
        } else {
            paints.clear();
            paints.addAll(entryPaints);
        }
        if (entry.size() != paths.size()) {
            paths.clear();
            for (int i = 0; i < entry.size(); i++) {
                paths.add(new Path());
            }
        }
        datas.putAll(entry);
        calcMaxUnit(datas);
        calcAreas();
        calcUnitXY();
        if (needAnim) {
            showWithAnims();
        } else {
            forceToDraw = true;
            invalidate();
        }
    }

    /**
     * 得到maxValueOfY
     * @param datas
     */
    private void calcMaxUnit(Map<Integer, List<Unit>> datas) {
        // 先“扁平”
        List<Unit> allUnits = new ArrayList<>();
        for (List<Unit> line : datas.values()) {
            allUnits.addAll(line);
        }
        // 再拷贝，防止引用问题
        List<Unit> bakUnits = new ArrayList<>();
        for (int i = 0; i < allUnits.size(); i++) {
            bakUnits.add(allUnits.get(i).clone());
        }
        // 最后排序，得到最大值
        Collections.sort(bakUnits);
        Unit maxUnit = bakUnits.get(bakUnits.size() - 1);
        Unit minUnit = bakUnits.get(0);
        minAndMaxOfY[0] = Util.getCeil5(Math.min(minUnit.getValue(), 0));
        minAndMaxOfY[1] = Util.getCeil5(Math.max(maxUnit.getValue(), 0));
    }

    /**
     * 重新计算三个区域的大小
     */
    private void calcAreas() {
        float textWidth = Math.max(xyPaint.measureText(String.valueOf(minAndMaxOfY[0])),
                xyPaint.measureText(String.valueOf(minAndMaxOfY[1])));
        float maxWidth = Math.max(xyPaint.measureText("00"), textWidth);
        RectF validArea = new RectF(getPaddingLeft() + basePadding, getPaddingTop() + basePadding,
                getMeasuredWidth() - getPaddingRight() - basePadding, getMeasuredHeight() - getPaddingBottom());
        yArea = new RectF(validArea.left, validArea.top,
                validArea.left + maxWidth + basePadding,
                validArea.bottom - Util.getTextHeight(xyPaint) - basePadding * 2);
        xArea = new RectF(yArea.right, yArea.bottom, validArea.right, validArea.bottom);
        linesArea = new RectF(yArea.right+1, yArea.top, xArea.right, yArea.bottom);
        hintArea = new RectF(linesArea.right-linesArea.right/4,linesArea.top,
                linesArea.right,linesArea.top + linesArea.height()/4);
    }

    /**
     * 计算所有点的坐标
     * <br>同时得到了realBetween，maxOffset
     */
    private void calcUnitXY() {
        float absValueOfY = Math.abs(minAndMaxOfY[1] - minAndMaxOfY[0]);
        int realNum = Math.min(datas.get(0).size(), maxOfVisible);
        realBetween = linesArea.width() / (realNum - 1);
        // 防止line的stroke部分在lineArea外被clip
        float padding = paints.get(0).getStrokeWidth() / 2;
        for (int i = 0; i < datas.get(0).size(); i++) {
            for (int j = 0; j < datas.size(); j++) {
                float curValue = datas.get(j).get(i).getValue();
                float scale = new BigDecimal("1").subtract(
                        (new BigDecimal(Float.toString(curValue))
                                .subtract(new BigDecimal(Float.toString(minAndMaxOfY[0]))))
                                .divide(new BigDecimal(Float.toString(absValueOfY)), 2,BigDecimal.ROUND_DOWN)
                ).floatValue();
                Log.d(TAG, "calcUnitXY: scale="+scale);

                datas.get(j).get(i).setXY(new PointF(linesArea.left + realBetween * i,
                        linesArea.top + linesArea.height() * scale + (scale == 0 ? padding : (scale == 1 ? -padding : 0))));
                if (i == datas.get(0).size() - 1) {
                    maxOffset = Math.abs(datas.get(j).get(i).getXY().x) - linesArea.width() - linesArea.left;
                }
            }
        }
        zeroAxisValue = linesArea.top + linesArea.height() * minAndMaxOfY[1] / (minAndMaxOfY[1] - minAndMaxOfY[0]);
    }

    /**
     * 取消所有正在执行的动画，若存在的话;
     * 在 重新填充数据 / dettach-view 时调用
     */
    private void cancelAllAnims() {
        // 不使用ViewRootImpl的getHandler()，否则影响其事件分发
        handler.removeCallbacksAndMessages(null);
        scroller.abortAnimation();
        if (clickHintAnimator != null && clickHintAnimator.isRunning()) {
            clickHintAnimator.removeAllUpdateListeners();
            clickHintAnimator.cancel();
            hintPaint.setAlpha(100);
            clickHintAnimator = null;
        }
        if (!animators.isEmpty()) {
            for (int i = 0; i < animators.size(); i++) {
                animators.get(i).removeAllUpdateListeners();
                if (animators.get(i).isRunning()) {
                    animators.get(i).cancel();
                }
            }
            animators.clear();
        }
        if (!datas.isEmpty()) {
            for (List<Unit> line : datas.values()) {
                for (int i = 0; i < line.size(); i++) {
                    line.get(i).cancelToEndAnim();
                }
            }
        }
        for (int i = 0; i < paths.size(); i++) {
            paths.get(i).reset();
        }
        invalidate();
    }

    // 每个1/x启动下一条line的动画
    private int percentOfStartNextLineAnim = 3;
    /**
     * 约定每间隔一组数据遍历总时间的一半就启动下一组数据的遍历
     *
     * @return 遍历时间+最后一组数据的等待时间+最后一个点的动画时间+缓冲时间
     */
    private long calcTotalCost() {
        if (datas.isEmpty() || datas.get(0).isEmpty()) return 0;
        long oneLineCost = calcVisibleLineCost();
        return oneLineCost + oneLineCost / percentOfStartNextLineAnim * (datas.size() - 1) + Unit.DURATION + 16;
    }

    /**
     * 一条线遍历完的时间，
     *
     * @return
     */
    private long calcVisibleLineCost() {
        if (intervalOfAnimCost > 0) {
            if (maxOfVisible < datas.get(0).size()) {
                return maxOfAnimCost;
            }
            long oneLineCost = intervalOfAnimCost * (datas.get(0).size() - 1);
            oneLineCost = Math.min(maxOfAnimCost, oneLineCost);
            return oneLineCost;
        } else {
            return 0;
        }
    }

    private void showWithAnims() {
        if (datas.isEmpty()) return;
        curAnimLine = 0;
        startTimeOfAnim = System.currentTimeMillis();
        int[] suitEdge = findSuitEdgeInVisual();

        // 重置所有可见点的percent
        for (int i = suitEdge[0]; i <= suitEdge[1]; i++) {
            for (List<Unit> item : datas.values()) {
                item.get(i).setPercent(0);
            }
        }
        startLinesAnimOrderly(suitEdge[0], suitEdge[1]);
        autoInvalidate();
    }

    /**
     * 开启自动刷新
     */
    private void autoInvalidate() {
        isAniming = true;
        invalidate();
        if (System.currentTimeMillis() - startTimeOfAnim > calcTotalCost()) {
            isAniming = false;
            return;
        }
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                autoInvalidate();
            }
        }, 16);
    }

    /**
     * 间隔指定时间依次启动每条线
     */
    private void startLinesAnimOrderly(final int startIndex, final int endIndex) {
        startLineAnim(startIndex, endIndex);
        if (curAnimLine >= datas.size() - 1) return;
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                curAnimLine++;
                startLinesAnimOrderly(startIndex, endIndex);
            }
        }, calcVisibleLineCost() / percentOfStartNextLineAnim);
    }

    /**
     * 依次启动指定label的线的每个可见点的动画；
     *
     * @param startIndex
     * @param endIndex
     */
    private void startLineAnim(final int startIndex, final int endIndex) {
        final List<Unit> line = datas.get(curAnimLine);
        long duration = calcVisibleLineCost();
        if (duration > 0) {
            ValueAnimator animator = ValueAnimator.ofInt(startIndex, endIndex);
            animator.setDuration(duration);
            animator.setInterpolator(linearInterpolator);
            animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    for (int i = startIndex; i <= (Integer) animation.getAnimatedValue(); i++) {
                        line.get(i).startAnim(pointInterpolator);
                    }
                }
            });
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    for (int i = startIndex; i <= endIndex; i++) {
                        line.get(i).startAnim(pointInterpolator);
                    }
                }
            });
            animator.start();
            animators.add(animator);
        } else {
            for (int i = startIndex; i <= endIndex; i++) {
                line.get(i).startAnim(pointInterpolator);
            }
        }
    }

    /**
     * 重置相关状态
     */
    private void reset() {
        invalidateYBuffer();
        offset = 0;
        realBetween = 0;
        suitEdge = null;
        clickIndexs = null;
        datas.clear();
    }

    private void invalidateYBuffer() {
        if (yAreaBuffer != null) {
            yAreaBuffer.recycle();
            yAreaBuffer = null;
        }
        if (yGridBuffer != null) {
            yGridBuffer.recycle();
            yGridBuffer = null;
        }
    }

//    @Override
//    protected void onDetachedFromWindow() {
//        super.onDetachedFromWindow();
//        cancelAllAnims();
//        reset();
//    }

    ///APIs/////////////////////////////////////////////////////////////////////////////////////////

    /**
     * 在fill形态时，是否在图表上边缘绘制line
     * @param enable    true表示需要，反正不需要
     */
    public void setCoverLine(boolean enable) {
        needCoverLine = enable;
        forceToDraw = true;
        postInvalidate();
    }

    /**
     * 在fill形态时，指定在图表上边缘绘制line的宽度，该方法会开启needCoverLine特性
     * @param withdp    宽度
     */
    public void setCoverLine(float withdp) {
        needCoverLine = true;
        coverLinePaint.setStrokeWidth(Util.dip2px(withdp) * 2);
        forceToDraw = true;
        postInvalidate();
    }

    /**
     * 设置默认一条line时的颜色
     * @param colors    默认为defaultLineColor
     */
    public void setDefaultOneLineColor(int...colors) {
        if (colors == null || colors.length < 1) return;
        defaultLineColor = colors;
        basePaint.setColor(colors[0]);
        if (linesArea != null) {// 区域还未初始化
            basePaint.setShader(buildPaintColor(colors));
        }
        if (!datas.isEmpty() && datas.size() == 1) {
            paints.get(0).set(basePaint);
            postInvalidate();
        }
    }

    /**
     * 设置提示辅助线、文字颜色
     * @param hintColor
     */
    public void setHintColor(int hintColor) {
        needShowHint = true;
        this.hintColor = hintColor;
        hintPaint.setColor(hintColor);
        if (!datas.isEmpty()) {
            if (clickIndexs != null) {
                postInvalidate();
            }
        }
    }

    /**
     * 设置xy轴文字的颜色
     * @param color 默认为Color.GRAY
     */
    public void setXyColor(int color) {
        defaultXyColor = color;
        xyPaint.setColor(defaultXyColor);
        if (!datas.isEmpty()) {
            invalidateYBuffer();
            forceToDraw = true;
            postInvalidate();
        }
    }

    /**
     * 设置xy轴文字大小
     * @param sp
     */
    public void setXySize(float sp) {
        defaultXySize = sp;
        xyPaint.setTextSize(Util.size2sp(defaultXySize, getContext()));
        if (!datas.isEmpty()) {
            invalidateYBuffer();
            calcAreas();
            calcUnitXY();
            offset = 0;// fix bug.
            forceToDraw = true;
            postInvalidate();
        }
    }


    /**
     * 设置line的SEGMENT时的大小
     * @param lineSize
     */
    public void setLineSize(float lineSize) {
        basePaint.setStyle(Paint.Style.STROKE);
        basePaint.setStrokeWidth(lineSize);
        // 同时更新当前已存在的paint
        for (int i = 0; i < paints.size(); i++) {
            forceToDraw = true;
            paints.get(i).setStyle(basePaint.getStyle());
            paints.get(i).setStrokeWidth(lineSize);
        }
        postInvalidate();
    }

    /**
     * 指定line类型：CURVE / SEGMENT
     * @param lineType  默认CURVE
     */
    public void setLineType(int lineType) {
        this.lineType = lineType;
        forceToDraw = true;
        postInvalidate();
    }

    public int getLineType() {
        return lineType;
    }

    /**
     * 设置line的形态：是否填充
     * @param isFill    默认为false
     */
    public void setLineForm(boolean isFill) {
        if (isFill) {
            basePaint.setStyle(Paint.Style.FILL);
        } else {
            basePaint.setStyle(Paint.Style.STROKE);
        }
        if (!datas.isEmpty()) {
            // 同时更新当前已存在的paint
            for (int i = 0; i < paints.size(); i++) {
                forceToDraw = true;
                paints.get(i).setStyle(basePaint.getStyle());
            }
            postInvalidate();
        }
    }

    public boolean isLineFill() {
        return basePaint.getStyle() == Paint.Style.FILL;
    }

    public void setLineStyle(int style) {
        lineStyle = style;
        basePaint.setPathEffect(lineStyle == DASHED ? new DashPathEffect(new float[]{Util.dip2px(3),Util.dip2px(6)},0) : null);
        if (!datas.isEmpty()) {
            // 同时更新当前已存在的paint
            for (int i = 0; i < paints.size(); i++) {
                forceToDraw = true;
                paints.get(i).setPathEffect(basePaint.getPathEffect());
            }
            postInvalidate();
        }
    }

    public boolean isLineDashed() {
        return basePaint.getPathEffect() != null;
    }

    /**
     * 关闭边缘效果，默认开启
     */
    public void disableEdgeEffect() {
        needEdgeEffect = false;
        postInvalidate();
    }

    /**
     * 关闭点击提示信息，默认开启
     */
    public void disableClickHint() {
        needShowHint = false;
    }

    /**
     * 指定边缘效果的颜色
     * @param color 默认为Color.GRAY
     */
    public void setEdgeEffectColor(int color) {
        needEdgeEffect = true;
        edgeEffectColor = color;
        Util.trySetColorForEdgeEffect(edgeEffectLeft, edgeEffectColor);
        Util.trySetColorForEdgeEffect(edgeEffectRight, edgeEffectColor);
        postInvalidate();
    }

    public void setShowYGrid(boolean showYGrid) {
        this.showYGrid = showYGrid;
        postInvalidate();
    }


    /**
     * 本方式仅支持一条线，若需要支持多条线，请采用Builder方式
     *
     * @param line
     */
    public void feedWithAnim(List<Unit> line) {
        if (line == null || line.isEmpty()) return;
        final Map<Integer, List<Unit>> entry = new HashMap<>();
        entry.put(0, line);
        handler.post(new Runnable() {
            @Override
            public void run() {
                feedInternal(entry, Arrays.asList(buildNewPaint()), true);
            }
        });
    }

    /**
     * 本方式仅支持一条线，若需要支持多条线，请采用Builder方式
     *
     * @param line
     */
    public void feed(List<Unit> line) {
        if (line == null || line.isEmpty()) return;
        final Map<Integer, List<Unit>> entry = new HashMap<>();
        entry.put(0, line);
        handler.post(new Runnable() {
            @Override
            public void run() {
                feedInternal(entry, Arrays.asList(buildNewPaint()), false);
            }
        });
    }

    public void anim() {
        if (datas.isEmpty()) return;
        handler.post(new Runnable() {
            @Override
            public void run() {
                cancelAllAnims();
                showWithAnims();
            }
        });
    }

    public void postAction(Runnable runnable) {
        handler.post(runnable);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    // 多条线的情况应该采用该构建方式
    public static class LineBuilder {
        private int curIndex;
        private Map<Integer, List<Unit>> datas;
        private Map<Integer, int[]> colors;


        public LineBuilder() {
            datas = new HashMap<>();
            colors = new HashMap<>();
        }

        /**
         * 该方式是用于构建多条line，单条line可使用lineGraph#feed
         * @param data  单条line的数据集合
         * @param color 指定当前line的颜色。默认取数组的第一个颜色；另外如果开启了填充，则整个数组颜色作为填充色的渐变。
         * @return
         */
        public LineBuilder add(List<Unit> data, int... color) {
            if (data == null || data.isEmpty() || color == null || color.length <= 0) {
                throw new IllegalArgumentException("无效参数data或color");
            }
            int bakIndex = curIndex;
            datas.put(bakIndex, data);
            colors.put(bakIndex, color);
            curIndex++;
            return this;
        }

        /**
         * 调用该方法开始填充数据，该方法需要保证SuitLines已经初始化
         * @param suitLines 需要被填充的图表
         * @param needAnim  是否需要动画
         */
        public void build(final SuitLines suitLines, final boolean needAnim) {
            final List<Paint> tmpPaints = new ArrayList<>();
            for (int i = 0; i < colors.size(); i++) {
                Paint paint = suitLines.buildNewPaint();
                paint.setColor(colors.get(0)[0]);
                paint.setShader(suitLines.buildPaintColor(colors.get(i)));
                tmpPaints.add(i, paint);
            }
            suitLines.postAction(new Runnable() {
                @Override
                public void run() {
                    suitLines.feedInternal(datas, tmpPaints, needAnim);
                }
            });
        }
    }
}
