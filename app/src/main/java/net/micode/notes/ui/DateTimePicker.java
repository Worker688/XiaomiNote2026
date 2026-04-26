/*
 * Copyright (c) 2010-2011, The MiCode Open Source Community (www.micode.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.micode.notes.ui;

import java.text.DateFormatSymbols;
import java.util.Calendar;

import net.micode.notes.R;

import android.content.Context;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.NumberPicker;

/**
 * 日期时间选择器控件，继承自FrameLayout，支持24小时/12小时制切换，
 * 可选择日期、小时、分钟，并提供日期时间变更的回调监听
 */
public class DateTimePicker extends FrameLayout {

    // 默认启用状态
    private static final boolean DEFAULT_ENABLE_STATE = true;

    // 半天的小时数（12小时制）
    private static final int HOURS_IN_HALF_DAY = 12;
    // 一天的小时数（24小时制）
    private static final int HOURS_IN_ALL_DAY = 24;
    // 一周的天数
    private static final int DAYS_IN_ALL_WEEK = 7;
    // 日期选择器最小值
    private static final int DATE_SPINNER_MIN_VAL = 0;
    // 日期选择器最大值（一周天数-1）
    private static final int DATE_SPINNER_MAX_VAL = DAYS_IN_ALL_WEEK - 1;
    // 24小时制下小时选择器最小值
    private static final int HOUR_SPINNER_MIN_VAL_24_HOUR_VIEW = 0;
    // 24小时制下小时选择器最大值
    private static final int HOUR_SPINNER_MAX_VAL_24_HOUR_VIEW = 23;
    // 12小时制下小时选择器最小值
    private static final int HOUR_SPINNER_MIN_VAL_12_HOUR_VIEW = 1;
    // 12小时制下小时选择器最大值
    private static final int HOUR_SPINNER_MAX_VAL_12_HOUR_VIEW = 12;
    // 分钟选择器最小值
    private static final int MINUT_SPINNER_MIN_VAL = 0;
    // 分钟选择器最大值
    private static final int MINUT_SPINNER_MAX_VAL = 59;
    // 上午/下午选择器最小值
    private static final int AMPM_SPINNER_MIN_VAL = 0;
    // 上午/下午选择器最大值
    private static final int AMPM_SPINNER_MAX_VAL = 1;

    // 日期选择器（周维度）
    private final NumberPicker mDateSpinner;
    // 小时选择器
    private final NumberPicker mHourSpinner;
    // 分钟选择器
    private final NumberPicker mMinuteSpinner;
    // 上午/下午选择器（12小时制下显示）
    private final NumberPicker mAmPmSpinner;
    // 当前选中的日期时间
    private Calendar mDate;

    // 日期选择器显示的文本值（一周的日期文本）
    private String[] mDateDisplayValues = new String[DAYS_IN_ALL_WEEK];

    // 是否为上午（12小时制下使用）
    private boolean mIsAm;

    // 是否为24小时制显示
    private boolean mIs24HourView;

    // 控件是否启用
    private boolean mIsEnabled = DEFAULT_ENABLE_STATE;

    // 是否处于初始化状态（避免初始化时触发不必要的回调）
    private boolean mInitialising;

    // 日期时间变更监听器
    private OnDateTimeChangedListener mOnDateTimeChangedListener;

    /**
     * 日期选择器值变更监听器
     * 当日期选择器值变化时，更新选中日期并触发回调
     */
    private NumberPicker.OnValueChangeListener mOnDateChangedListener = new NumberPicker.OnValueChangeListener() {
        @Override
        public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
            // 根据新旧值的差值调整日期
            mDate.add(Calendar.DAY_OF_YEAR, newVal - oldVal);
            // 更新日期选择器显示
            updateDateControl();
            // 触发日期时间变更回调
            onDateTimeChanged();
        }
    };

    /**
     * 小时选择器值变更监听器
     * 处理12/24小时制下小时变更逻辑，包括跨天、上午/下午切换等场景
     */
    private NumberPicker.OnValueChangeListener mOnHourChangedListener = new NumberPicker.OnValueChangeListener() {
        @Override
        public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
            // 标记日期是否变更（跨天场景）
            boolean isDateChanged = false;
            Calendar cal = Calendar.getInstance();
            // 12小时制处理逻辑
            if (!mIs24HourView) {
                // 下午场景：11点切换到12点，日期+1
                if (!mIsAm && oldVal == HOURS_IN_HALF_DAY - 1 && newVal == HOURS_IN_HALF_DAY) {
                    cal.setTimeInMillis(mDate.getTimeInMillis());
                    cal.add(Calendar.DAY_OF_YEAR, 1);
                    isDateChanged = true;
                }
                // 上午场景：12点切换到11点，日期-1
                else if (mIsAm && oldVal == HOURS_IN_HALF_DAY && newVal == HOURS_IN_HALF_DAY - 1) {
                    cal.setTimeInMillis(mDate.getTimeInMillis());
                    cal.add(Calendar.DAY_OF_YEAR, -1);
                    isDateChanged = true;
                }
                // 11点<->12点切换时，切换上午/下午状态
                if (oldVal == HOURS_IN_HALF_DAY - 1 && newVal == HOURS_IN_HALF_DAY ||
                        oldVal == HOURS_IN_HALF_DAY && newVal == HOURS_IN_HALF_DAY - 1) {
                    mIsAm = !mIsAm;
                    // 更新上午/下午选择器显示
                    updateAmPmControl();
                }
            }
            // 24小时制处理逻辑
            else {
                // 23点切换到0点，日期+1
                if (oldVal == HOURS_IN_ALL_DAY - 1 && newVal == 0) {
                    cal.setTimeInMillis(mDate.getTimeInMillis());
                    cal.add(Calendar.DAY_OF_YEAR, 1);
                    isDateChanged = true;
                }
                // 0点切换到23点，日期-1
                else if (oldVal == 0 && newVal == HOURS_IN_ALL_DAY - 1) {
                    cal.setTimeInMillis(mDate.getTimeInMillis());
                    cal.add(Calendar.DAY_OF_YEAR, -1);
                    isDateChanged = true;
                }
            }
            // 计算新的小时值（转换为24小时制）
            int newHour = mHourSpinner.getValue() % HOURS_IN_HALF_DAY + (mIsAm ? 0 : HOURS_IN_HALF_DAY);
            mDate.set(Calendar.HOUR_OF_DAY, newHour);
            // 触发日期时间变更回调
            onDateTimeChanged();
            // 若日期变更，更新年/月/日
            if (isDateChanged) {
                setCurrentYear(cal.get(Calendar.YEAR));
                setCurrentMonth(cal.get(Calendar.MONTH));
                setCurrentDay(cal.get(Calendar.DAY_OF_MONTH));
            }
        }
    };

    /**
     * 分钟选择器值变更监听器
     * 处理分钟跨小时变更场景（如59分切0分则小时+1，0分切59分则小时-1）
     */
    private NumberPicker.OnValueChangeListener mOnMinuteChangedListener = new NumberPicker.OnValueChangeListener() {
        @Override
        public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
            int minValue = mMinuteSpinner.getMinValue();
            int maxValue = mMinuteSpinner.getMaxValue();
            // 小时偏移量（跨小时时调整）
            int offset = 0;
            // 59分切换到0分，小时+1
            if (oldVal == maxValue && newVal == minValue) {
                offset += 1;
            }
            // 0分切换到59分，小时-1
            else if (oldVal == minValue && newVal == maxValue) {
                offset -= 1;
            }
            // 存在小时偏移时调整日期时间
            if (offset != 0) {
                mDate.add(Calendar.HOUR_OF_DAY, offset);
                // 更新小时选择器显示
                mHourSpinner.setValue(getCurrentHour());
                // 更新日期选择器显示
                updateDateControl();
                // 重新判断上午/下午状态
                int newHour = getCurrentHourOfDay();
                if (newHour >= HOURS_IN_HALF_DAY) {
                    mIsAm = false;
                } else {
                    mIsAm = true;
                }
                // 更新上午/下午选择器显示
                updateAmPmControl();
            }
            // 设置新的分钟值
            mDate.set(Calendar.MINUTE, newVal);
            // 触发日期时间变更回调
            onDateTimeChanged();
        }
    };

    /**
     * 上午/下午选择器值变更监听器
     * 切换上午/下午时调整小时（±12小时）并更新显示
     */
    private NumberPicker.OnValueChangeListener mOnAmPmChangedListener = new NumberPicker.OnValueChangeListener() {
        @Override
        public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
            // 切换上午/下午状态
            mIsAm = !mIsAm;
            // 上午->下午：小时+12；下午->上午：小时-12
            if (mIsAm) {
                mDate.add(Calendar.HOUR_OF_DAY, -HOURS_IN_HALF_DAY);
            } else {
                mDate.add(Calendar.HOUR_OF_DAY, HOURS_IN_HALF_DAY);
            }
            // 更新上午/下午选择器显示
            updateAmPmControl();
            // 触发日期时间变更回调
            onDateTimeChanged();
        }
    };

    /**
     * 日期时间变更监听器接口
     * 当日期/时间发生变更时触发回调
     */
    public interface OnDateTimeChangedListener {
        /**
         * 日期时间变更回调方法
         * @param view 当前日期时间选择器控件
         * @param year 选中的年
         * @param month 选中的月
         * @param dayOfMonth 选中的日
         * @param hourOfDay 选中的小时（24小时制）
         * @param minute 选中的分钟
         */
        void onDateTimeChanged(DateTimePicker view, int year, int month,
                               int dayOfMonth, int hourOfDay, int minute);
    }

    /**
     * 构造方法：使用当前系统时间初始化，自动判断24小时制
     * @param context 上下文
     */
    public DateTimePicker(Context context) {
        this(context, System.currentTimeMillis());
    }

    /**
     * 构造方法：指定初始时间，自动判断24小时制
     * @param context 上下文
     * @param date 初始时间（毫秒值）
     */
    public DateTimePicker(Context context, long date) {
        this(context, date, DateFormat.is24HourFormat(context));
    }

    /**
     * 构造方法：指定初始时间和是否24小时制
     * @param context 上下文
     * @param date 初始时间（毫秒值）
     * @param is24HourView 是否为24小时制
     */
    public DateTimePicker(Context context, long date, boolean is24HourView) {
        super(context);
        // 初始化日历对象
        mDate = Calendar.getInstance();
        // 标记为初始化状态
        mInitialising = true;
        // 初始化上午/下午状态（根据当前小时判断）
        mIsAm = getCurrentHourOfDay() >= HOURS_IN_HALF_DAY;
        // 加载布局
        inflate(context, R.layout.datetime_picker, this);

        // 绑定控件
        mDateSpinner = (NumberPicker) findViewById(R.id.date);
        mDateSpinner.setMinValue(DATE_SPINNER_MIN_VAL);
        mDateSpinner.setMaxValue(DATE_SPINNER_MAX_VAL);
        mDateSpinner.setOnValueChangedListener(mOnDateChangedListener);

        mHourSpinner = (NumberPicker) findViewById(R.id.hour);
        mHourSpinner.setOnValueChangedListener(mOnHourChangedListener);

        mMinuteSpinner =  (NumberPicker) findViewById(R.id.minute);
        mMinuteSpinner.setMinValue(MINUT_SPINNER_MIN_VAL);
        mMinuteSpinner.setMaxValue(MINUT_SPINNER_MAX_VAL);
        // 设置长按更新间隔（快速滚动）
        mMinuteSpinner.setOnLongPressUpdateInterval(100);
        mMinuteSpinner.setOnValueChangedListener(mOnMinuteChangedListener);

        // 初始化上午/下午选择器文本
        String[] stringsForAmPm = new DateFormatSymbols().getAmPmStrings();
        mAmPmSpinner = (NumberPicker) findViewById(R.id.amPm);
        mAmPmSpinner.setMinValue(AMPM_SPINNER_MIN_VAL);
        mAmPmSpinner.setMaxValue(AMPM_SPINNER_MAX_VAL);
        mAmPmSpinner.setDisplayedValues(stringsForAmPm);
        mAmPmSpinner.setOnValueChangedListener(mOnAmPmChangedListener);

        // 更新控件到初始状态
        updateDateControl();
        updateHourControl();
        updateAmPmControl();

        // 设置24小时制/12小时制
        set24HourView(is24HourView);

        // 设置初始时间
        setCurrentDate(date);

        // 设置启用状态
        setEnabled(isEnabled());

        // 初始化完成
        mInitialising = false;
    }

    /**
     * 设置控件启用状态
     * @param enabled 是否启用
     */
    @Override
    public void setEnabled(boolean enabled) {
        // 状态未变化则直接返回
        if (mIsEnabled == enabled) {
            return;
        }
        super.setEnabled(enabled);
        // 同步设置所有子选择器的启用状态
        mDateSpinner.setEnabled(enabled);
        mMinuteSpinner.setEnabled(enabled);
        mHourSpinner.setEnabled(enabled);
        mAmPmSpinner.setEnabled(enabled);
        // 更新启用状态标记
        mIsEnabled = enabled;
    }

    /**
     * 获取控件启用状态
     * @return 是否启用
     */
    @Override
    public boolean isEnabled() {
        return mIsEnabled;
    }

    /**
     * 获取当前选中的日期时间（毫秒值）
     * @return 日期时间毫秒值
     */
    public long getCurrentDateInTimeMillis() {
        return mDate.getTimeInMillis();
    }

    /**
     * 设置当前选中的日期时间（毫秒值）
     * @param date 日期时间毫秒值
     */
    public void setCurrentDate(long date) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(date);
        // 解析年/月/日/时/分并设置
        setCurrentDate(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH),
                cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE));
    }

    /**
     * 设置当前选中的日期时间（分字段设置）
     * @param year 年
     * @param month 月
     * @param dayOfMonth 日
     * @param hourOfDay 小时（24小时制）
     * @param minute 分钟
     */
    public void setCurrentDate(int year, int month,
                               int dayOfMonth, int hourOfDay, int minute) {
        setCurrentYear(year);
        setCurrentMonth(month);
        setCurrentDay(dayOfMonth);
        setCurrentHour(hourOfDay);
        setCurrentMinute(minute);
    }

    /**
     * 获取当前选中的年
     * @return 年
     */
    public int getCurrentYear() {
        return mDate.get(Calendar.YEAR);
    }

    /**
     * 设置当前选中的年
     * @param year 年
     */
    public void setCurrentYear(int year) {
        // 非初始化状态且值未变化则返回
        if (!mInitialising && year == getCurrentYear()) {
            return;
        }
        mDate.set(Calendar.YEAR, year);
        // 更新日期选择器显示
        updateDateControl();
        // 触发日期时间变更回调
        onDateTimeChanged();
    }

    /**
     * 获取当前选中的月
     * @return 月（Calendar.MONTH格式，0-11）
     */
    public int getCurrentMonth() {
        return mDate.get(Calendar.MONTH);
    }

    /**
     * 设置当前选中的月
     * @param month 月（Calendar.MONTH格式，0-11）
     */
    public void setCurrentMonth(int month) {
        // 非初始化状态且值未变化则返回
        if (!mInitialising && month == getCurrentMonth()) {
            return;
        }
        mDate.set(Calendar.MONTH, month);
        // 更新日期选择器显示
        updateDateControl();
        // 触发日期时间变更回调
        onDateTimeChanged();
    }

    /**
     * 获取当前选中的日
     * @return 日
     */
    public int getCurrentDay() {
        return mDate.get(Calendar.DAY_OF_MONTH);
    }

    /**
     * 设置当前选中的日
     * @param dayOfMonth 日
     */
    public void setCurrentDay(int dayOfMonth) {
        // 非初始化状态且值未变化则返回
        if (!mInitialising && dayOfMonth == getCurrentDay()) {
            return;
        }
        mDate.set(Calendar.DAY_OF_MONTH, dayOfMonth);
        // 更新日期选择器显示
        updateDateControl();
        // 触发日期时间变更回调
        onDateTimeChanged();
    }

    /**
     * 获取当前选中的小时（24小时制，0-23）
     * @return 小时（24小时制）
     */
    public int getCurrentHourOfDay() {
        return mDate.get(Calendar.HOUR_OF_DAY);
    }

    /**
     * 获取当前选中的小时（适配显示格式：24小时制直接返回，12小时制转换为1-12）
     * @return 小时（适配显示格式）
     */
    private int getCurrentHour() {
        if (mIs24HourView){
            return getCurrentHourOfDay();
        } else {
            int hour = getCurrentHourOfDay();
            // 12小时制下：大于12则减12，0点转换为12点
            if (hour > HOURS_IN_HALF_DAY) {
                return hour - HOURS_IN_HALF_DAY;
            } else {
                return hour == 0 ? HOURS_IN_HALF_DAY : hour;
            }
        }
    }

    /**
     * 设置当前选中的小时（24小时制，0-23）
     * @param hourOfDay 小时（24小时制）
     */
    public void setCurrentHour(int hourOfDay) {
        // 非初始化状态且值未变化则返回
        if (!mInitialising && hourOfDay == getCurrentHourOfDay()) {
            return;
        }
        // 设置小时（24小时制）
        mDate.set(Calendar.HOUR_OF_DAY, hourOfDay);
        // 12小时制下处理上午/下午状态
        if (!mIs24HourView) {
            if (hourOfDay >= HOURS_IN_HALF_DAY) {
                mIsAm = false;
                // 大于12则转换为12小时制显示值（如13->1）
                if (hourOfDay > HOURS_IN_HALF_DAY) {
                    hourOfDay -= HOURS_IN_HALF_DAY;
                }
            } else {
                mIsAm = true;
                // 0点转换为12点显示
                if (hourOfDay == 0) {
                    hourOfDay = HOURS_IN_HALF_DAY;
                }
            }
            // 更新上午/下午选择器显示
            updateAmPmControl();
        }
        // 设置小时选择器显示值
        mHourSpinner.setValue(hourOfDay);
        // 触发日期时间变更回调
        onDateTimeChanged();
    }

    /**
     * 获取当前选中的分钟
     * @return 分钟
     */
    public int getCurrentMinute() {
        return mDate.get(Calendar.MINUTE);
    }

    /**
     * 设置当前选中的分钟
     * @param minute 分钟
     */
    public void setCurrentMinute(int minute) {
        // 非初始化状态且值未变化则返回
        if (!mInitialising && minute == getCurrentMinute()) {
            return;
        }
        // 设置分钟选择器显示值
        mMinuteSpinner.setValue(minute);
        // 设置日历对象的分钟值
        mDate.set(Calendar.MINUTE, minute);
        // 触发日期时间变更回调
        onDateTimeChanged();
    }

    /**
     * 判断是否为24小时制显示
     * @return true：24小时制，false：12小时制
     */
    public boolean is24HourView () {
        return mIs24HourView;
    }

    /**
     * 设置是否为24小时制显示
     * @param is24HourView true：24小时制，false：12小时制
     */
    public void set24HourView(boolean is24HourView) {
        // 状态未变化则返回
        if (mIs24HourView == is24HourView) {
            return;
        }
        // 更新24小时制标记
        mIs24HourView = is24HourView;
        // 24小时制隐藏上午/下午选择器，12小时制显示
        mAmPmSpinner.setVisibility(is24HourView ? View.GONE : View.VISIBLE);
        // 保存当前小时值
        int hour = getCurrentHourOfDay();
        // 更新小时选择器的取值范围
        updateHourControl();
        // 重新设置小时（适配新的显示格式）
        setCurrentHour(hour);
        // 更新上午/下午选择器显示
        updateAmPmControl();
    }

    /**
     * 更新日期选择器的显示内容
     * 生成以当前日期为中心的一周日期文本（MM.dd 星期X）
     */
    private void updateDateControl() {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(mDate.getTimeInMillis());
        // 计算一周日期的起始点（当前日期往前推 3天+1天）
        cal.add(Calendar.DAY_OF_YEAR, -DAYS_IN_ALL_WEEK / 2 - 1);
        // 清空原有显示值
        mDateSpinner.setDisplayedValues(null);
        // 生成一周的日期显示文本
        for (int i = 0; i < DAYS_IN_ALL_WEEK; ++i) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
            // 格式：月.日 星期（如 08.01 星期一）
            mDateDisplayValues[i] = (String) DateFormat.format("MM.dd EEEE", cal);
        }
        // 设置日期选择器的显示文本
        mDateSpinner.setDisplayedValues(mDateDisplayValues);
        // 默认选中中间项（当前日期）
        mDateSpinner.setValue(DAYS_IN_ALL_WEEK / 2);
        // 刷新控件
        mDateSpinner.invalidate();
    }

    /**
     * 更新上午/下午选择器的显示状态
     * 24小时制隐藏，12小时制根据mIsAm设置选中值
     */
    private void updateAmPmControl() {
        if (mIs24HourView) {
            mAmPmSpinner.setVisibility(View.GONE);
        } else {
            // 根据mIsAm设置选中项（AM:0，PM:1）
            int index = mIsAm ? Calendar.AM : Calendar.PM;
            mAmPmSpinner.setValue(index);
            mAmPmSpinner.setVisibility(View.VISIBLE);
        }
    }

    /**
     * 更新小时选择器的取值范围
     * 24小时制：0-23；12小时制：1-12
     */
    private void updateHourControl() {
        if (mIs24HourView) {
            mHourSpinner.setMinValue(HOUR_SPINNER_MIN_VAL_24_HOUR_VIEW);
            mHourSpinner.setMaxValue(HOUR_SPINNER_MAX_VAL_24_HOUR_VIEW);
        } else {
            mHourSpinner.setMinValue(HOUR_SPINNER_MIN_VAL_12_HOUR_VIEW);
            mHourSpinner.setMaxValue(HOUR_SPINNER_MAX_VAL_12_HOUR_VIEW);
        }
    }

    /**
     * 设置日期时间变更监听器
     * @param callback 监听器实例（null则取消监听）
     */
    public void setOnDateTimeChangedListener(OnDateTimeChangedListener callback) {
        mOnDateTimeChangedListener = callback;
    }

    /**
     * 触发日期时间变更回调
     * 若监听器不为空，则调用其onDateTimeChanged方法
     */
    private void onDateTimeChanged() {
        if (mOnDateTimeChangedListener != null) {
            mOnDateTimeChangedListener.onDateTimeChanged(this, getCurrentYear(),
                    getCurrentMonth(), getCurrentDay(), getCurrentHourOfDay(), getCurrentMinute());
        }
    }
}