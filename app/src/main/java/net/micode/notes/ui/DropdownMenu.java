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
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.PopupMenu;
import android.widget.PopupMenu.OnMenuItemClickListener;

import net.micode.notes.R;

/**
 * 下拉菜单封装类：基于PopupMenu实现，关联指定Button控件展示下拉菜单功能
 * 核心能力：绑定按钮、加载菜单布局、处理菜单点击、修改按钮标题、查找菜单项等
 */
public class DropdownMenu {
    // 下拉菜单关联的按钮控件
    private Button mButton;
    // 弹出式菜单核心对象
    private PopupMenu mPopupMenu;
    // PopupMenu对应的菜单数据对象
    private Menu mMenu;

    /**
     * 构造方法：初始化下拉菜单，绑定按钮并加载菜单布局
     * @param context 上下文对象
     * @param button 关联的按钮控件（点击该按钮展示下拉菜单）
     * @param menuId 菜单布局资源ID（定义下拉菜单的菜单项）
     */
    public DropdownMenu(Context context, Button button, int menuId) {
        mButton = button;
        // 设置按钮背景为下拉图标样式
        mButton.setBackgroundResource(R.drawable.dropdown_icon);
        // 创建PopupMenu对象，关联指定按钮作为锚点
        mPopupMenu = new PopupMenu(context, mButton);
        // 获取PopupMenu的菜单对象
        mMenu = mPopupMenu.getMenu();
        // 加载菜单布局资源到PopupMenu中
        mPopupMenu.getMenuInflater().inflate(menuId, mMenu);
        // 给按钮设置点击事件：点击时展示下拉菜单
        mButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                mPopupMenu.show();
            }
        });
    }

    /**
     * 设置下拉菜单项的点击监听器
     * @param listener 菜单项点击回调接口（处理具体菜单项的点击逻辑）
     */
    public void setOnDropdownMenuItemClickListener(OnMenuItemClickListener listener) {
        if (mPopupMenu != null) {
            mPopupMenu.setOnMenuItemClickListener(listener);
        }
    }

    /**
     * 根据菜单项ID查找对应的MenuItem对象
     * @param id 菜单项资源ID
     * @return 匹配的MenuItem对象（无匹配则返回null）
     */
    public MenuItem findItem(int id) {
        return mMenu.findItem(id);
    }

    /**
     * 设置下拉菜单关联按钮的显示标题
     * @param title 按钮要展示的文字内容
     */
    public void setTitle(CharSequence title) {
        mButton.setText(title);
    }
}