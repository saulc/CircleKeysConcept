/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.basicmultitouch;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;

import com.example.android.basicmultitouch.Pools.SimplePool;

/**
 * View that shows touch events and their history. This view demonstrates the
 * use of {@link #onTouchEvent(android.view.MotionEvent)} and {@link android.view.MotionEvent}s to keep
 * track of touch pointers across events.
 */
public class TouchDisplayView extends View {

    private void log(String s){ Log.d(getClass().getSimpleName(), s); }

    // Hold data for active touch pointer IDs
    private SparseArray<TouchHistory> mTouches;

    // Is there an active touch?
    private boolean mHasTouch = false;

    /**
     * Holds data related to a touch pointer, including its current position,
     * pressure and historical positions. Objects are allocated through an
     * object pool using {@link #obtain()} and {@link #recycle()} to reuse
     * existing objects.
     */
    static final class TouchHistory {

        // number of historical points to store
        public static final int HISTORY_COUNT = 20;

        public float x;
        public float y;
        public float pressure = 0f;
        public String label = null;

        // current position in history array
        public int historyIndex = 0;
        public int historyCount = 0;

        // arrray of pointer position history
        public PointF[] history = new PointF[HISTORY_COUNT];

        private static final int MAX_POOL_SIZE = 10;
        private static final SimplePool<TouchHistory> sPool =
                new SimplePool<TouchHistory>(MAX_POOL_SIZE);

        public static TouchHistory obtain(float x, float y, float pressure) {
            TouchHistory data = sPool.acquire();
            if (data == null) {
                data = new TouchHistory();
            }

            data.setTouch(x, y, pressure);

            return data;
        }

        public TouchHistory() {

            // initialise history array
            for (int i = 0; i < HISTORY_COUNT; i++) {
                history[i] = new PointF();
            }
        }

        public void setTouch(float x, float y, float pressure) {
            this.x = x;
            this.y = y;
            this.pressure = pressure;
        }

        public void recycle() {
            this.historyIndex = 0;
            this.historyCount = 0;
            sPool.release(this);
        }

        /**
         * Add a point to its history. Overwrites oldest point if the maximum
         * number of historical points is already stored.
         *
         * @param point
         */
        public void addHistory(float x, float y) {
            PointF p = history[historyIndex];
            p.x = x;
            p.y = y;

            historyIndex = (historyIndex + 1) % history.length;

            if (historyCount < HISTORY_COUNT) {
                historyCount++;
            }
        }

    }

    public TouchDisplayView(Context context, AttributeSet attrs) {
        super(context, attrs);

        // SparseArray for touch events, indexed by touch id
        mTouches = new SparseArray<TouchHistory>(10);
        log("Touch view created.");
        initialisePaint();
        iniStuff();
    }

    // BEGIN_INCLUDE(onTouchEvent)
    @Override
    public boolean onTouchEvent(MotionEvent event) {

        final int action = event.getAction();

        /*
         * Switch on the action. The action is extracted from the event by
         * applying the MotionEvent.ACTION_MASK. Alternatively a call to
         * event.getActionMasked() would yield in the action as well.
         */
        switch (action & MotionEvent.ACTION_MASK) {

            case MotionEvent.ACTION_DOWN: {
                // first pressed gesture has started

                /*
                 * Only one touch event is stored in the MotionEvent. Extract
                 * the pointer identifier of this touch from the first index
                 * within the MotionEvent object.
                 */
                int id = event.getPointerId(0);

                TouchHistory data = TouchHistory.obtain(event.getX(0), event.getY(0),
                        event.getPressure(0));
                data.label = "id: " + 0;

                /*
                 * Store the data under its pointer identifier. The pointer
                 * number stays consistent for the duration of a gesture,
                 * accounting for other pointers going up or down.
                 */
                mTouches.put(id, data);

                mHasTouch = true;

                break;
            }

            case MotionEvent.ACTION_POINTER_DOWN: {
                /*
                 * A non-primary pointer has gone down, after an event for the
                 * primary pointer (ACTION_DOWN) has already been received.
                 */

                /*
                 * The MotionEvent object contains multiple pointers. Need to
                 * extract the index at which the data for this particular event
                 * is stored.
                 */
                int index = event.getActionIndex();
                int id = event.getPointerId(index);

                TouchHistory data = TouchHistory.obtain(event.getX(index), event.getY(index),
                        event.getPressure(index));
                data.label = "id: " + id;

                /*
                 * Store the data under its pointer identifier. The index of
                 * this pointer can change over multiple events, but this
                 * pointer is always identified by the same identifier for this
                 * active gesture.
                 */
                mTouches.put(id, data);

                break;
            }

            case MotionEvent.ACTION_UP: {
                /*
                 * Final pointer has gone up and has ended the last pressed
                 * gesture.
                 */

                /*
                 * Extract the pointer identifier for the only event stored in
                 * the MotionEvent object and remove it from the list of active
                 * touches.
                 */
                int id = event.getPointerId(0);
                TouchHistory data = mTouches.get(id);
                mTouches.remove(id);
                data.recycle();

                mHasTouch = false;

                break;
            }

            case MotionEvent.ACTION_POINTER_UP: {
                /*
                 * A non-primary pointer has gone up and other pointers are
                 * still active.
                 */

                /*
                 * The MotionEvent object contains multiple pointers. Need to
                 * extract the index at which the data for this particular event
                 * is stored.
                 */
                int index = event.getActionIndex();
                int id = event.getPointerId(index);

                TouchHistory data = mTouches.get(id);
                mTouches.remove(id);
                data.recycle();

                break;
            }

            case MotionEvent.ACTION_MOVE: {
                /*
                 * A change event happened during a pressed gesture. (Between
                 * ACTION_DOWN and ACTION_UP or ACTION_POINTER_DOWN and
                 * ACTION_POINTER_UP)
                 */

                /*
                 * Loop through all active pointers contained within this event.
                 * Data for each pointer is stored in a MotionEvent at an index
                 * (starting from 0 up to the number of active pointers). This
                 * loop goes through each of these active pointers, extracts its
                 * data (position and pressure) and updates its stored data. A
                 * pointer is identified by its pointer number which stays
                 * constant across touch events as long as it remains active.
                 * This identifier is used to keep track of a pointer across
                 * events.
                 */
                for (int index = 0; index < event.getPointerCount(); index++) {
                    // get pointer id for data stored at this index
                    int id = event.getPointerId(index);

                    // get the data stored externally about this pointer.
                    TouchHistory data = mTouches.get(id);

                    // add previous position to history and add new values
                    data.addHistory(data.x, data.y);
                    data.setTouch(event.getX(index), event.getY(index),
                            event.getPressure(index));

                }

                break;
            }
        }

        // trigger redraw on UI thread
        this.postInvalidate();

        return true;
    }

    // END_INCLUDE(onTouchEvent)

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Canvas background color depends on whether there is an active touch
        if (mHasTouch) {
            canvas.drawColor(BACKGROUND_ACTIVE);
        } else {
            // draw inactive border
            canvas.drawRect(mBorderWidth, mBorderWidth, getWidth() - mBorderWidth, getHeight()
                    - mBorderWidth, mBorderPaint);
        }

        // loop through all active touches and draw them
        for (int i = 0; i < mTouches.size(); i++) {

            // get the pointer id and associated data for this index
            int id = mTouches.keyAt(i);
            TouchHistory data = mTouches.valueAt(i);

            // draw the data and its history to the canvas
            drawCircle(canvas, id, data);
        }
    }

    /*
     * Below are only helper methods and variables required for drawing.
     */

    // radius of active touch circle in dp
    private static final float CIRCLE_RADIUS_DP = 75f;
    // radius of historical circle in dp
    private static final float CIRCLE_HISTORICAL_RADIUS_DP = 7f;

    // calculated radiuses in px
    private float mCircleRadius;
    private float mCircleHistoricalRadius;

    private Paint mCirclePaint = new Paint();
    private Paint mTextPaint = new Paint();

    private static final int BACKGROUND_ACTIVE = Color.WHITE;

    // inactive border
    private static final float INACTIVE_BORDER_DP = 15f;
    private static final int INACTIVE_BORDER_COLOR = 0xFFffd060;
    private Paint mBorderPaint = new Paint();
    private float mBorderWidth;

    public final int[] COLORS = {
            0xFF33B5E5, 0xFFAA66CC, 0xFF99CC00, 0xFFFFBB33, 0xFFFF4444,
            0xFF0099CC, 0xFF9933CC, 0xFF669900, 0xFFFF8800, 0xFFCC0000
    };

    /**
     * Sets up the required {@link android.graphics.Paint} objects for the screen density of this
     * device.
     */
    private void initialisePaint() {

        // Calculate radiuses in px from dp based on screen density
        float density = getResources().getDisplayMetrics().density;
        mCircleRadius = CIRCLE_RADIUS_DP * density;
        mCircleHistoricalRadius = CIRCLE_HISTORICAL_RADIUS_DP * density;

        // Setup text paint for circle label
        mTextPaint.setTextSize(27f);
        mTextPaint.setColor(Color.BLACK);

        // Setup paint for inactive border
        mBorderWidth = INACTIVE_BORDER_DP * density;
        mBorderPaint.setStrokeWidth(mBorderWidth);
        mBorderPaint.setColor(INACTIVE_BORDER_COLOR);
        mBorderPaint.setStyle(Paint.Style.STROKE);

    }

    /**
     * Draws the data encapsulated by a {@link TouchDisplayView.TouchHistory} object to a canvas.
     * A large circle indicates the current position held by the
     * {@link TouchDisplayView.TouchHistory} object, while a smaller circle is drawn for each
     * entry in its history. The size of the large circle is scaled depending on
     * its pressure, clamped to a maximum of <code>1.0</code>.
     *
     * @param canvas
     * @param id
     * @param data
     */

    private Bitmap cir;
    private final float r1 = 70f; //first ring radius
    private final float r2 = 120f;  //outter ring radius

    private final float o1 = 70f;
    private final float o2 = 56f;

    private void iniStuff(){
        cir = BitmapFactory.decodeResource(getResources(), R.drawable.circlecontrol2);

    }

    private String getKey(int i){
        switch(i){
            case 0 : return "A";
            case 1 : return "S";
            case 2 : return "D";
            case 3 : return "F";
            case 4 : return "G";
            case 5 : return "_";
            //second ring
            case 6 : return "Q";
            case 7 : return "W";
            case 8 : return "E";
            case 9 : return "R";
            case 10 : return "T";
            case 11 : return "Z";
            case 12 : return "X";
            case 13 : return "C";
            case 14 : return "V";
            case 15 : return "B";
            case 16 : return "SH";
            case 17 : return "TB";

            //right hand
            case 18 : return "H";
            case 19 : return "J";
            case 20 : return "K";
            case 21 : return "L";
            case 22 : return ":";
            case 23 : return "'";
            //second ring
            case 24 : return "Y";
            case 25 : return "U";
            case 26 : return "I";
            case 27 : return "O";
            case 28 : return "P";
            case 29 : return "{";
            case 30 : return "}";
            case 31 : return "N";
            case 32 : return "M";
            case 33 : return ",";
            case 34 : return ".";
            case 35 : return "/";
        }
        return "";
    }

    private void drawKeys(Canvas canvas, TouchHistory data){
        log("Drawing Left Keys");
        //draw keys in rings.
        float x = data.x;
        float y = data.y;
        Path path = new Path();
        path.addCircle(x, y, r1, Path.Direction.CW);
        mTextPaint.setTextAlign(Paint.Align.LEFT);
        for(int i =0; i<5; i++){
            canvas.drawTextOnPath(getKey(i), path, o1*i , 0, mTextPaint );
           // canvas.drawText(getKey(i), x + (float)(r1*Math.cos(i*60.) ), y + (float) (r1*Math.sin(i*60.)) , mTextPaint);
        }

        //second ring
        path = new Path();
        path.addArc(new RectF(x-cir.getWidth()/3, y-cir.getHeight()/3, x+cir.getWidth()/3, y+cir.getHeight()/3 ), 0, 360);
     //   path.addArc(new RectF(0, 0, x, y), 180, 360);
        mTextPaint.setTextAlign(Paint.Align.LEFT);
        for(int i =6; i<18; i++){
            canvas.drawTextOnPath(getKey(i), path, o2*(i-6) + 11f , -11f, mTextPaint );
            // canvas.drawText(getKey(i), x + (float)(r1*Math.cos(i*60.) ), y + (float) (r1*Math.sin(i*60.)) , mTextPaint);
        }


    }

    private void drawRightKeys(Canvas canvas, TouchHistory data){
        log("Drawing right Keys");
        //draw keys in rings.
        float x = data.x;
        float y = data.y;
        Path path = new Path();
        path.addCircle(x, y, r1, Path.Direction.CW);
        mTextPaint.setTextAlign(Paint.Align.LEFT);
        for(int i =18; i<24; i++){
            canvas.drawTextOnPath(getKey(i), path, o1*(i-18) , 0, mTextPaint );
            // canvas.drawText(getKey(i), x + (float)(r1*Math.cos(i*60.) ), y + (float) (r1*Math.sin(i*60.)) , mTextPaint);
        }

        //second ring
        path = new Path();
        path.addArc(new RectF(x-cir.getWidth()/3, y-cir.getHeight()/3, x+cir.getWidth()/3, y+cir.getHeight()/3 ), 0, 360);
        //   path.addArc(new RectF(0, 0, x, y), 180, 360);
        mTextPaint.setTextAlign(Paint.Align.LEFT);
        for(int i =24; i<36; i++){
            canvas.drawTextOnPath(getKey(i), path, o2*(i-24) + 11f , -11f, mTextPaint );
            // canvas.drawText(getKey(i), x + (float)(r1*Math.cos(i*60.) ), y + (float) (r1*Math.sin(i*60.)) , mTextPaint);
        }


    }

    protected void drawCircle(Canvas canvas, int id, TouchHistory data) {
        // select the color based on the id
        int color = COLORS[id % COLORS.length];
        mCirclePaint.setColor(color);
        mCirclePaint.setAlpha(100);
        /*
         * Draw the circle, size scaled to its pressure. Pressure is clamped to
         * 1.0 max to ensure proper drawing. (Reported pressure values can
         * exceed 1.0, depending on the calibration of the touch screen).
         */
        float pressure = Math.min(data.pressure, 1f);
        float radius = pressure * mCircleRadius;

        //canvas.drawCircle(data.x, (data.y) - (radius / 2f), radius,  mCirclePaint);
        radius = cir.getWidth()/2f - 10f ;

        canvas.drawCircle(data.x, (data.y ), radius,  mCirclePaint);
        canvas.drawBitmap(cir, data.x-cir.getWidth()/2f, (data.y) - (cir.getHeight()/2f),  mCirclePaint);

        if(id == 0) drawKeys(canvas, data);
        else drawRightKeys(canvas, data);
        // draw all historical points with a lower alpha value
        mCirclePaint.setAlpha(125);
        for (int j = 0; j < data.history.length && j < data.historyCount; j++) {
            PointF p = data.history[j];
            canvas.drawCircle(p.x, p.y, mCircleHistoricalRadius, mCirclePaint);
        }

        // draw its label next to the main circle
      //  canvas.drawText(data.label, data.x + radius, data.y  - radius, mTextPaint);
    }

}
