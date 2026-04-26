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

import android.content.Context;
import android.graphics.Rect;
import android.text.Layout;
import android.text.Selection;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.URLSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.MotionEvent;
import android.widget.EditText;

import net.micode.notes.R;

import java.util.HashMap;
import java.util.Map;

/**
 * 自定义的 EditText，用于笔记编辑界面中的普通文本编辑或待办清单中的每一条目。
 * 主要功能：
 * 1. 在待办清单模式下支持通过回车键创建新条目、通过删除键删除空条目。
 * 2. 提供触摸定位文本光标的能力。
 * 3. 为文本中的超链接（电话、网址、邮箱）提供长按弹出菜单并跳转。
 */
public class NoteEditText extends EditText {
    private static final String TAG = "NoteEditText";
    private int mIndex;                      // 当前编辑条目在清单列表中的索引
    private int mSelectionStartBeforeDelete; // 删除光标前的位置，用于判断是否在行首删除

    // 支持的超链接协议
    private static final String SCHEME_TEL = "tel:" ;
    private static final String SCHEME_HTTP = "http:" ;
    private static final String SCHEME_EMAIL = "mailto:" ;

    // 协议对应的菜单项文本资源 ID 映射表
    private static final Map<String, Integer> sSchemaActionResMap = new HashMap<String, Integer>();
    static {
        sSchemaActionResMap.put(SCHEME_TEL, R.string.note_link_tel);
        sSchemaActionResMap.put(SCHEME_HTTP, R.string.note_link_web);
        sSchemaActionResMap.put(SCHEME_EMAIL, R.string.note_link_email);
    }

    /**
     * 文本变化监听接口，用于与 NoteEditActivity 交互
     */
    public interface OnTextViewChangeListener {
        /**
         * 当文本为空且按下删除键时，通知 Activity 删除当前 EditText
         * @param index 当前条目索引
         * @param text  当前文本内容
         */
        void onEditTextDelete(int index, String text);

        /**
         * 当按下回车键时，通知 Activity 在当前条目后方新增一个空白条目
         * @param index 新增条目的位置索引
         * @param text  当前光标之后的文本（将被移动到新条目中）
         */
        void onEditTextEnter(int index, String text);

        /**
         * 当文本内容变化时，通知 Activity 显示或隐藏条目的复选框（清单模式下）
         * @param index   条目索引
         * @param hasText 当前条目是否有文本内容
         */
        void onTextChange(int index, boolean hasText);
    }

    private OnTextViewChangeListener mOnTextViewChangeListener;

    // 构造方法
    public NoteEditText(Context context) {
        super(context, null);
        mIndex = 0;
    }

    /**
     * 设置当前条目在清单列表中的索引
     */
    public void setIndex(int index) {
        mIndex = index;
    }

    /**
     * 设置文本变化监听器
     */
    public void setOnTextViewChangeListener(OnTextViewChangeListener listener) {
        mOnTextViewChangeListener = listener;
    }

    public NoteEditText(Context context, AttributeSet attrs) {
        super(context, attrs, android.R.attr.editTextStyle);
    }

    public NoteEditText(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    /**
     * 重写触摸事件，实现点击文本任意位置时自动定位光标到手指点击处
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // 获取点击位置的坐标（考虑内边距和滚动偏移）
                int x = (int) event.getX();
                int y = (int) event.getY();
                x -= getTotalPaddingLeft();
                y -= getTotalPaddingTop();
                x += getScrollX();
                y += getScrollY();

                Layout layout = getLayout();
                int line = layout.getLineForVertical(y);
                int off = layout.getOffsetForHorizontal(line, x);
                Selection.setSelection(getText(), off); // 将光标设置到该位置
                break;
        }
        return super.onTouchEvent(event);
    }

    /**
     * 按键按下时的处理：仅记录删除键按下时的光标位置，以备后续判断
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_ENTER:
                // 如果设置了监听器，则返回 false 让 onKeyUp 处理回车事件（实现分行业务）
                if (mOnTextViewChangeListener != null) {
                    return false;
                }
                break;
            case KeyEvent.KEYCODE_DEL:
                // 记录删除前的光标起始位置，用于判断是否位于行首且无字符
                mSelectionStartBeforeDelete = getSelectionStart();
                break;
            default:
                break;
        }
        return super.onKeyDown(keyCode, event);
    }

    /**
     * 按键弹起时的处理：实现删除空行和回车换行创建新条目的逻辑
     */
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch(keyCode) {
            case KeyEvent.KEYCODE_DEL:
                if (mOnTextViewChangeListener != null) {
                    // 如果光标位于第一个字符之前（selectionStart == 0）且不是第一条（mIndex != 0）
                    // 则说明想要删除一个空条目，通知 Activity 删除当前 EditText
                    if (0 == mSelectionStartBeforeDelete && mIndex != 0) {
                        mOnTextViewChangeListener.onEditTextDelete(mIndex, getText().toString());
                        return true;
                    }
                } else {
                    Log.d(TAG, "OnTextViewChangeListener was not seted");
                }
                break;
            case KeyEvent.KEYCODE_ENTER:
                if (mOnTextViewChangeListener != null) {
                    // 获取光标之后的所有文本
                    int selectionStart = getSelectionStart();
                    String text = getText().subSequence(selectionStart, length()).toString();
                    // 将当前 EditText 的文本截断至光标位置
                    setText(getText().subSequence(0, selectionStart));
                    // 通知 Activity 在当前位置之后新增一个 EditText，并将剩余文本填入新条目
                    mOnTextViewChangeListener.onEditTextEnter(mIndex + 1, text);
                } else {
                    Log.d(TAG, "OnTextViewChangeListener was not seted");
                }
                break;
            default:
                break;
        }
        return super.onKeyUp(keyCode, event);
    }

    /**
     * 当焦点发生变化时，通知 Activity 当前条目是否有文本内容，用于决定是否显示复选框
     */
    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
        if (mOnTextViewChangeListener != null) {
            if (!focused && TextUtils.isEmpty(getText())) {
                mOnTextViewChangeListener.onTextChange(mIndex, false);
            } else {
                mOnTextViewChangeListener.onTextChange(mIndex, true);
            }
        }
        super.onFocusChanged(focused, direction, previouslyFocusedRect);
    }

    /**
     * 创建上下文菜单（长按弹出菜单）
     * 如果选中的文本片段包含且仅包含一个超链接（URLSpan），则根据链接协议（电话/网址/邮箱）显示对应的菜单项，
     * 用户点击后触发该链接的默认行为（拨号、打开网页、发送邮件等）。
     */
    @Override
    protected void onCreateContextMenu(ContextMenu menu) {
        if (getText() instanceof Spanned) {
            int selStart = getSelectionStart();
            int selEnd = getSelectionEnd();

            int min = Math.min(selStart, selEnd);
            int max = Math.max(selStart, selEnd);

            final URLSpan[] urls = ((Spanned) getText()).getSpans(min, max, URLSpan.class);
            if (urls.length == 1) {
                int defaultResId = 0;
                // 根据 URL 的协议查找对应的菜单文本资源 ID
                for(String schema: sSchemaActionResMap.keySet()) {
                    if(urls[0].getURL().indexOf(schema) >= 0) {
                        defaultResId = sSchemaActionResMap.get(schema);
                        break;
                    }
                }

                if (defaultResId == 0) {
                    defaultResId = R.string.note_link_other;
                }

                // 添加菜单项，点击时执行 URLSpan 的 onClick 方法（打开对应 Intent）
                menu.add(0, 0, 0, defaultResId).setOnMenuItemClickListener(
                        new OnMenuItemClickListener() {
                            public boolean onMenuItemClick(MenuItem item) {
                                urls[0].onClick(NoteEditText.this);
                                return true;
                            }
                        });
            }
        }
        super.onCreateContextMenu(menu);
    }
}