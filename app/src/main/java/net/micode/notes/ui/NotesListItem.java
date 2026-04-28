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
import android.text.format.DateUtils;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.tool.DataUtils;
import net.micode.notes.tool.ResourceParser.NoteItemBgResources;

/**
 * 笔记列表的自定义项视图，继承自 LinearLayout。
 * 用于展示单条笔记或文件夹的 UI，包括标题、时间、提醒图标、复选框等。
 * 根据数据类型（普通笔记、文件夹、通话记录文件夹）以及是否多选模式，
 * 动态改变显示内容和样式。
 */
public class NotesListItem extends LinearLayout {
    private ImageView mAlert;      // 提醒图标（闹钟或通话记录图标）
    private TextView mTitle;       // 标题/内容摘要
    private TextView mTime;        // 最后修改时间（相对时间）
    private TextView mCallName;    // 通话记录的联系人姓名（仅通话记录子项使用）
    private NoteItemData mItemData; // 当前项的数据对象
    private CheckBox mCheckBox;    // 多选模式下的复选框

    /**
     * 构造函数：加载布局文件，初始化各子视图
     * @param context 上下文
     */
    public NotesListItem(Context context) {
        super(context);
        inflate(context, R.layout.note_item, this);
        mAlert = (ImageView) findViewById(R.id.iv_alert_icon);
        mTitle = (TextView) findViewById(R.id.tv_title);
        mTime = (TextView) findViewById(R.id.tv_time);
        mCallName = (TextView) findViewById(R.id.tv_name);
        mCheckBox = (CheckBox) findViewById(android.R.id.checkbox);
    }

    /**
     * 将数据绑定到视图上，根据多选模式、数据类型等设置显示内容和样式
     * @param context    上下文
     * @param data       当前项的数据（NoteItemData）
     * @param choiceMode 是否处于多选模式
     * @param checked    多选模式下该项是否被选中
     */
    public void bind(Context context, NoteItemData data, boolean choiceMode, boolean checked) {
        // 多选模式且当前项为普通笔记时，显示复选框并设置选中状态；否则隐藏复选框
        if (choiceMode && data.getType() == Notes.TYPE_NOTE) {
            mCheckBox.setVisibility(View.VISIBLE);
            mCheckBox.setChecked(checked);
        } else {
            mCheckBox.setVisibility(View.GONE);
        }

        mItemData = data;

        // ========== 分类型设置 UI ==========
        // 情况1：通话记录文件夹（特殊系统文件夹）
        if (data.getId() == Notes.ID_CALL_RECORD_FOLDER) {
            mCallName.setVisibility(View.GONE);
            mAlert.setVisibility(View.VISIBLE);
            mTitle.setTextAppearance(context, R.style.TextAppearancePrimaryItem);
            mTitle.setText(context.getString(R.string.call_record_folder_name)
                    + context.getString(R.string.format_folder_files_count, data.getNotesCount()));
            mAlert.setImageResource(R.drawable.call_record);
        }
        // 情况2：位于通话记录文件夹下的子项（通话记录详情）
        else if (data.getParentId() == Notes.ID_CALL_RECORD_FOLDER) {
            mCallName.setVisibility(View.VISIBLE);
            mCallName.setText(data.getCallName());
            mTitle.setTextAppearance(context,R.style.TextAppearanceSecondaryItem);
            mTitle.setText(DataUtils.getFormattedSnippet(data.getSnippet()));
            if (data.hasAlert()) {
                mAlert.setImageResource(R.drawable.clock);
                mAlert.setVisibility(View.VISIBLE);
            } else {
                mAlert.setVisibility(View.GONE);
            }
        }
        // 情况3：普通文件夹或普通笔记
        else {
            mCallName.setVisibility(View.GONE);
            mTitle.setTextAppearance(context, R.style.TextAppearancePrimaryItem);

            if (data.getType() == Notes.TYPE_FOLDER) {
                // 文件夹：显示文件夹名称 + 内部笔记数量
                mTitle.setText(data.getSnippet()
                        + context.getString(R.string.format_folder_files_count,
                        data.getNotesCount()));
                mAlert.setVisibility(View.GONE);
            } else {
                // 普通笔记：显示格式化后的摘要，如果有提醒则显示闹钟图标
                mTitle.setText(DataUtils.getFormattedSnippet(data.getSnippet()));
                if (data.hasAlert()) {
                    mAlert.setImageResource(R.drawable.clock);
                    mAlert.setVisibility(View.VISIBLE);
                } else {
                    mAlert.setVisibility(View.GONE);
                }
            }
        }

        // 设置相对时间（如“3分钟前”）
        mTime.setText(DateUtils.getRelativeTimeSpanString(data.getModifiedDate()));

        // 根据数据状态设置背景（区分笔记在列表中的位置：第一项、中间项、最后项或单独一项）
        setBackground(data);
    }

    /**
     * 根据笔记类型、背景颜色以及位置关系（是否为第一条、最后一条等）设置列表项背景
     * 以达到圆角卡片式列表的效果
     * @param data 当前项的数据
     */
    private void setBackground(NoteItemData data) {
        int id = data.getBgColorId();  // 笔记背景颜色资源ID（黄、蓝、红等）
        if (data.getType() == Notes.TYPE_NOTE) {
            // 笔记：根据是否独立一项、是否第一条、是否最后一条等选择对应的圆角背景
            if (data.isSingle() || data.isOneFollowingFolder()) {
                setBackgroundResource(NoteItemBgResources.getNoteBgSingleRes(id));
            } else if (data.isLast()) {
                setBackgroundResource(NoteItemBgResources.getNoteBgLastRes(id));
            } else if (data.isFirst() || data.isMultiFollowingFolder()) {
                setBackgroundResource(NoteItemBgResources.getNoteBgFirstRes(id));
            } else {
                setBackgroundResource(NoteItemBgResources.getNoteBgNormalRes(id));
            }
        } else {
            // 文件夹：使用固定的文件夹背景
            setBackgroundResource(NoteItemBgResources.getFolderBgRes());
        }
    }

    /**
     * 返回当前项的数据对象，供外部获取（例如长按时获取被点击项的信息）
     * @return NoteItemData 对象
     */
    public NoteItemData getItemData() {
        return mItemData;
    }
}