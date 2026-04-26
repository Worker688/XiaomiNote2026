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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.appwidget.AppWidgetManager;
import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.ActionMode;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Display;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnCreateContextMenuListener;
import android.view.View.OnTouchListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.gtask.remote.GTaskSyncService;
import net.micode.notes.model.WorkingNote;
import net.micode.notes.tool.BackupUtils;
import net.micode.notes.tool.DataUtils;
import net.micode.notes.tool.ResourceParser;
import net.micode.notes.ui.NotesListAdapter.AppWidgetAttribute;
import net.micode.notes.widget.NoteWidgetProvider_2x;
import net.micode.notes.widget.NoteWidgetProvider_4x;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashSet;

/**
 * 笔记列表Activity，显示所有笔记或特定文件夹下的笔记。
 * 支持创建、删除、移动笔记/文件夹，导出文本/PDF，同步Google Tasks，以及处理桌面小部件。
 */
public class NotesListActivity extends Activity implements OnClickListener, OnItemLongClickListener {
    // 异步查询标识：查询当前文件夹下的笔记列表
    private static final int FOLDER_NOTE_LIST_QUERY_TOKEN = 0;
    // 异步查询标识：查询所有文件夹（用于移动笔记时的目标文件夹选择）
    private static final int FOLDER_LIST_QUERY_TOKEN      = 1;

    // 上下文菜单项：删除文件夹
    private static final int MENU_FOLDER_DELETE = 0;
    // 上下文菜单项：查看文件夹内容
    private static final int MENU_FOLDER_VIEW = 1;
    // 上下文菜单项：重命名文件夹
    private static final int MENU_FOLDER_CHANGE_NAME = 2;

    // SharedPreferences中记录是否已添加过应用介绍笔记的键名
    private static final String PREFERENCE_ADD_INTRODUCTION = "net.micode.notes.introduction";

    /**
     * 列表编辑状态枚举：当前Activity处于哪种视图模式
     * NOTE_LIST       - 根目录（所有笔记/文件夹）
     * SUB_FOLDER      - 某个普通文件夹内部
     * CALL_RECORD_FOLDER - 通话记录文件夹（特殊）
     */
    private enum ListEditState {
        NOTE_LIST, SUB_FOLDER, CALL_RECORD_FOLDER
    };

    private ListEditState mState;                       // 当前视图状态
    private BackgroundQueryHandler mBackgroundQueryHandler; // 异步查询处理器
    private NotesListAdapter mNotesListAdapter;         // 列表适配器
    private ListView mNotesListView;                    // 笔记列表视图
    private Button mAddNewNote;                         // “新建笔记”按钮
    private boolean mDispatch;                          // 是否正在分发触摸事件（用于穿透按钮透明区域）
    private int mOriginY;                               // 触摸起始Y坐标
    private int mDispatchY;                             // 分发触摸事件时转换后的Y坐标
    private TextView mTitleBar;                         // 标题栏（显示当前文件夹名称）
    private long mCurrentFolderId;                      // 当前打开的文件夹ID
    private ContentResolver mContentResolver;           // 内容解析器
    private ModeCallback mModeCallBack;                 // 多选模式回调（ActionMode）
    private static final String TAG = "NotesListActivity"; // 日志标签
    public static final int NOTES_LISTVIEW_SCROLL_RATE = 30; // 列表滚动速率（未使用）
    private NoteItemData mFocusNoteDataItem;            // 长按时获取的笔记/文件夹数据对象

    // 普通文件夹的查询条件：父ID等于指定值
    private static final String NORMAL_SELECTION = NoteColumns.PARENT_ID + "=?";
    // 根目录的查询条件：显示非系统类型且父ID为根文件夹，或者通话记录文件夹（如果有记录）
    private static final String ROOT_FOLDER_SELECTION = "(" + NoteColumns.TYPE + "<>"
            + Notes.TYPE_SYSTEM + " AND " + NoteColumns.PARENT_ID + "=?)" + " OR ("
            + NoteColumns.ID + "=" + Notes.ID_CALL_RECORD_FOLDER + " AND "
            + NoteColumns.NOTES_COUNT + ">0)";

    // 打开已有笔记的请求码
    private final static int REQUEST_CODE_OPEN_NODE = 102;
    // 新建笔记的请求码
    private final static int REQUEST_CODE_NEW_NODE  = 103;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.note_list);
        initResources();        // 初始化界面控件、适配器等

        /**
         * 首次使用应用时，从raw资源中读取介绍文本并创建一条笔记
         */
        setAppInfoFromRawRes();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // 当从笔记编辑界面返回（打开或新建笔记）且结果为成功时，刷新当前列表
        if (resultCode == RESULT_OK
                && (requestCode == REQUEST_CODE_OPEN_NODE || requestCode == REQUEST_CODE_NEW_NODE)) {
            mNotesListAdapter.changeCursor(null);
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    /**
     * 从raw/introduction文件中读取内容，创建一条介绍笔记（仅首次运行）
     */
    private void setAppInfoFromRawRes() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        if (!sp.getBoolean(PREFERENCE_ADD_INTRODUCTION, false)) {
            StringBuilder sb = new StringBuilder();
            InputStream in = null;
            try {
                in = getResources().openRawResource(R.raw.introduction);
                if (in != null) {
                    InputStreamReader isr = new InputStreamReader(in);
                    BufferedReader br = new BufferedReader(isr);
                    char [] buf = new char[1024];
                    int len = 0;
                    while ((len = br.read(buf)) > 0) {
                        sb.append(buf, 0, len);
                    }
                } else {
                    Log.e(TAG, "Read introduction file error");
                    return;
                }
            } catch (IOException e) {
                e.printStackTrace();
                return;
            } finally {
                if(in != null) {
                    try {
                        in.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

            // 创建一条新笔记，内容为介绍文本，保存在根文件夹下
            WorkingNote note = WorkingNote.createEmptyNote(this, Notes.ID_ROOT_FOLDER,
                    AppWidgetManager.INVALID_APPWIDGET_ID, Notes.TYPE_WIDGET_INVALIDE,
                    ResourceParser.RED);
            note.setWorkingText(sb.toString());
            if (note.saveNote()) {
                sp.edit().putBoolean(PREFERENCE_ADD_INTRODUCTION, true).commit();
            } else {
                Log.e(TAG, "Save introduction note error");
                return;
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        // 启动异步查询，加载笔记列表
        startAsyncNotesListQuery();
    }

    /**
     * 初始化界面资源：获取ContentResolver、设置ListView、适配器、按钮监听等
     */
    private void initResources() {
        mContentResolver = this.getContentResolver();
        mBackgroundQueryHandler = new BackgroundQueryHandler(this.getContentResolver());
        mCurrentFolderId = Notes.ID_ROOT_FOLDER;
        mNotesListView = (ListView) findViewById(R.id.notes_list);
        // 添加列表底部视图
        mNotesListView.addFooterView(LayoutInflater.from(this).inflate(R.layout.note_list_footer, null),
                null, false);
        mNotesListView.setOnItemClickListener(new OnListItemClickListener());
        mNotesListView.setOnItemLongClickListener(this);
        mNotesListAdapter = new NotesListAdapter(this);
        mNotesListView.setAdapter(mNotesListAdapter);
        mAddNewNote = (Button) findViewById(R.id.btn_new_note);
        mAddNewNote.setOnClickListener(this);
        mAddNewNote.setOnTouchListener(new NewNoteOnTouchListener()); // 处理按钮透明区域触摸穿透
        mDispatch = false;
        mDispatchY = 0;
        mOriginY = 0;
        mTitleBar = (TextView) findViewById(R.id.tv_title_bar);
        mState = ListEditState.NOTE_LIST;
        mModeCallBack = new ModeCallback();
    }

    /**
     * 多选模式（ActionMode）的回调类，处理批量删除、移动等操作的选择菜单。
     * 实现 ListView.MultiChoiceModeListener 和 OnMenuItemClickListener。
     */
    private class ModeCallback implements ListView.MultiChoiceModeListener, OnMenuItemClickListener {
        private DropdownMenu mDropDownMenu;      // 下拉菜单（全选/取消全选）
        private ActionMode mActionMode;          // 当前ActionMode对象
        private MenuItem mMoveMenu;              // 移动菜单项

        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            getMenuInflater().inflate(R.menu.note_list_options, menu);
            menu.findItem(R.id.delete).setOnMenuItemClickListener(this);
            mMoveMenu = menu.findItem(R.id.move);
            // 根据当前焦点项是否为通话记录文件夹，或是否有用户文件夹，决定是否显示“移动”菜单
            if (mFocusNoteDataItem.getParentId() == Notes.ID_CALL_RECORD_FOLDER
                    || DataUtils.getUserFolderCount(mContentResolver) == 0) {
                mMoveMenu.setVisible(false);
            } else {
                mMoveMenu.setVisible(true);
                mMoveMenu.setOnMenuItemClickListener(this);
            }
            mActionMode = mode;
            mNotesListAdapter.setChoiceMode(true);
            mNotesListView.setLongClickable(false);
            mAddNewNote.setVisibility(View.GONE);   // 多选模式隐藏新建按钮

            // 自定义ActionMode的标题栏，显示选择数量和全选/取消按钮
            View customView = LayoutInflater.from(NotesListActivity.this).inflate(
                    R.layout.note_list_dropdown_menu, null);
            mode.setCustomView(customView);
            mDropDownMenu = new DropdownMenu(NotesListActivity.this,
                    (Button) customView.findViewById(R.id.selection_menu),
                    R.menu.note_list_dropdown);
            mDropDownMenu.setOnDropdownMenuItemClickListener(new PopupMenu.OnMenuItemClickListener(){
                public boolean onMenuItemClick(MenuItem item) {
                    mNotesListAdapter.selectAll(!mNotesListAdapter.isAllSelected());
                    updateMenu();
                    return true;
                }

            });
            return true;
        }

        /**
         * 更新下拉菜单的文本（显示当前选中数量），以及全选/取消全选的文字
         */
        private void updateMenu() {
            int selectedCount = mNotesListAdapter.getSelectedCount();
            String format = getResources().getString(R.string.menu_select_title, selectedCount);
            mDropDownMenu.setTitle(format);
            MenuItem item = mDropDownMenu.findItem(R.id.action_select_all);
            if (item != null) {
                if (mNotesListAdapter.isAllSelected()) {
                    item.setChecked(true);
                    item.setTitle(R.string.menu_deselect_all);
                } else {
                    item.setChecked(false);
                    item.setTitle(R.string.menu_select_all);
                }
            }
        }

        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            return false;
        }

        public void onDestroyActionMode(ActionMode mode) {
            mNotesListAdapter.setChoiceMode(false);
            mNotesListView.setLongClickable(true);
            mAddNewNote.setVisibility(View.VISIBLE);
        }

        public void finishActionMode() {
            mActionMode.finish();
        }

        public void onItemCheckedStateChanged(ActionMode mode, int position, long id,
                                              boolean checked) {
            mNotesListAdapter.setCheckedItem(position, checked);
            updateMenu();
        }

        /**
         * 处理多选模式下的菜单项点击：删除、移动、导出PDF
         */
        public boolean onMenuItemClick(MenuItem item) {
            if (mNotesListAdapter.getSelectedCount() == 0) {
                Toast.makeText(NotesListActivity.this, getString(R.string.menu_select_none),
                        Toast.LENGTH_SHORT).show();
                return true;
            }

            int itemId = item.getItemId();
            if (itemId == R.id.delete) {
                // 弹出确认删除对话框
                AlertDialog.Builder builder = new AlertDialog.Builder(NotesListActivity.this);
                builder.setTitle(getString(R.string.alert_title_delete));
                builder.setIcon(android.R.drawable.ic_dialog_alert);
                builder.setMessage(getString(R.string.alert_message_delete_notes,
                        mNotesListAdapter.getSelectedCount()));
                builder.setPositiveButton(android.R.string.ok,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog,
                                                int which) {
                                batchDelete();
                            }
                        });
                builder.setNegativeButton(android.R.string.cancel, null);
                builder.show();
            } else if (itemId == R.id.move) {
                // 移动笔记到其他文件夹
                startQueryDestinationFolders();
            } else if(itemId == R.id.action_export_pdf){
                exportPdf();
            }
            return true;
        }
    }

    /**
     * “新建笔记”按钮的触摸监听器。
     * 由于UI设计按钮上半部分透明，需要将触摸事件穿透给下方的ListView，以实现点击透明区域时滚动列表。
     * 根据坐标计算是否处于透明区域，并分派事件。
     */
    private class NewNoteOnTouchListener implements OnTouchListener {

        public boolean onTouch(View v, MotionEvent ev) {
            switch (ev.getAction()) {
                case MotionEvent.ACTION_DOWN: {
                    Display display = getWindowManager().getDefaultDisplay();
                    int screenHeight = display.getHeight();
                    int newNoteViewHeight = mAddNewNote.getHeight();
                    int start = screenHeight - newNoteViewHeight;
                    int eventY = start + (int) ev.getY();
                    // 如果当前在子文件夹视图，需要减去标题栏高度
                    if (mState == ListEditState.SUB_FOLDER) {
                        eventY -= mTitleBar.getHeight();
                        start -= mTitleBar.getHeight();
                    }
                    /**
                     * 检查触摸点是否在按钮的透明区域（近似公式 y=-0.12x+94）
                     * 如果是则将事件分发给ListView
                     */
                    if (ev.getY() < (ev.getX() * (-0.12) + 94)) {
                        View view = mNotesListView.getChildAt(mNotesListView.getChildCount() - 1
                                - mNotesListView.getFooterViewsCount());
                        if (view != null && view.getBottom() > start
                                && (view.getTop() < (start + 94))) {
                            mOriginY = (int) ev.getY();
                            mDispatchY = eventY;
                            ev.setLocation(ev.getX(), mDispatchY);
                            mDispatch = true;
                            return mNotesListView.dispatchTouchEvent(ev);
                        }
                    }
                    break;
                }
                case MotionEvent.ACTION_MOVE: {
                    if (mDispatch) {
                        mDispatchY += (int) ev.getY() - mOriginY;
                        ev.setLocation(ev.getX(), mDispatchY);
                        return mNotesListView.dispatchTouchEvent(ev);
                    }
                    break;
                }
                default: {
                    if (mDispatch) {
                        ev.setLocation(ev.getX(), mDispatchY);
                        mDispatch = false;
                        return mNotesListView.dispatchTouchEvent(ev);
                    }
                    break;
                }
            }
            return false;
        }

    };

    /**
     * 启动异步查询，根据当前文件夹ID加载笔记/文件夹列表
     */
    private void startAsyncNotesListQuery() {
        String selection = (mCurrentFolderId == Notes.ID_ROOT_FOLDER) ? ROOT_FOLDER_SELECTION
                : NORMAL_SELECTION;
        mBackgroundQueryHandler.startQuery(FOLDER_NOTE_LIST_QUERY_TOKEN, null,
                Notes.CONTENT_NOTE_URI, NoteItemData.PROJECTION, selection, new String[] {
                        String.valueOf(mCurrentFolderId)
                }, NoteColumns.TYPE + " DESC," + NoteColumns.MODIFIED_DATE + " DESC");
    }

    /**
     * 异步查询处理器，处理查询完成后的UI更新
     */
    private final class BackgroundQueryHandler extends AsyncQueryHandler {
        public BackgroundQueryHandler(ContentResolver contentResolver) {
            super(contentResolver);
        }

        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
            switch (token) {
                case FOLDER_NOTE_LIST_QUERY_TOKEN:
                    mNotesListAdapter.changeCursor(cursor);
                    break;
                case FOLDER_LIST_QUERY_TOKEN:
                    if (cursor != null && cursor.getCount() > 0) {
                        showFolderListMenu(cursor);
                    } else {
                        Log.e(TAG, "Query folder failed");
                    }
                    break;
                default:
                    return;
            }
        }
    }

    /**
     * 显示选择目标文件夹的对话框（用于移动笔记）
     * @param cursor 包含所有可用文件夹的游标
     */
    private void showFolderListMenu(Cursor cursor) {
        AlertDialog.Builder builder = new AlertDialog.Builder(NotesListActivity.this);
        builder.setTitle(R.string.menu_title_select_folder);
        final FoldersListAdapter adapter = new FoldersListAdapter(this, cursor);
        builder.setAdapter(adapter, new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int which) {
                DataUtils.batchMoveToFolder(mContentResolver,
                        mNotesListAdapter.getSelectedItemIds(), adapter.getItemId(which));
                Toast.makeText(
                        NotesListActivity.this,
                        getString(R.string.format_move_notes_to_folder,
                                mNotesListAdapter.getSelectedCount(),
                                adapter.getFolderName(NotesListActivity.this, which)),
                        Toast.LENGTH_SHORT).show();
                mModeCallBack.finishActionMode();
            }
        });
        builder.show();
    }

    /**
     * 创建新笔记，跳转到NoteEditActivity
     */
    private void createNewNote() {
        Intent intent = new Intent(this, NoteEditActivity.class);
        intent.setAction(Intent.ACTION_INSERT_OR_EDIT);
        intent.putExtra(Notes.INTENT_EXTRA_FOLDER_ID, mCurrentFolderId);
        this.startActivityForResult(intent, REQUEST_CODE_NEW_NODE);
    }

    /**
     * 批量删除选中的笔记（或移动到废纸篓，取决于是否同步模式）
     * 使用AsyncTask在后台执行，完成后更新对应的桌面小部件
     */
    private void batchDelete() {
        new AsyncTask<Void, Void, HashSet<AppWidgetAttribute>>() {
            protected HashSet<AppWidgetAttribute> doInBackground(Void... unused) {
                HashSet<AppWidgetAttribute> widgets = mNotesListAdapter.getSelectedWidget();
                if (!isSyncMode()) {
                    // 非同步模式：直接删除
                    if (DataUtils.batchDeleteNotes(mContentResolver, mNotesListAdapter
                            .getSelectedItemIds())) {
                    } else {
                        Log.e(TAG, "Delete notes error, should not happens");
                    }
                } else {
                    // 同步模式：移动到废纸篓文件夹
                    if (!DataUtils.batchMoveToFolder(mContentResolver, mNotesListAdapter
                            .getSelectedItemIds(), Notes.ID_TRASH_FOLER)) {
                        Log.e(TAG, "Move notes to trash folder error, should not happens");
                    }
                }
                return widgets;
            }

            @Override
            protected void onPostExecute(HashSet<AppWidgetAttribute> widgets) {
                if (widgets != null) {
                    for (AppWidgetAttribute widget : widgets) {
                        if (widget.widgetId != AppWidgetManager.INVALID_APPWIDGET_ID
                                && widget.widgetType != Notes.TYPE_WIDGET_INVALIDE) {
                            updateWidget(widget.widgetId, widget.widgetType);
                        }
                    }
                }
                mModeCallBack.finishActionMode();
            }
        }.execute();
    }

    /**
     * 删除文件夹（或移动到废纸篓）
     * @param folderId 要删除的文件夹ID
     */
    private void deleteFolder(long folderId) {
        if (folderId == Notes.ID_ROOT_FOLDER) {
            Log.e(TAG, "Wrong folder id, should not happen " + folderId);
            return;
        }

        HashSet<Long> ids = new HashSet<Long>();
        ids.add(folderId);
        HashSet<AppWidgetAttribute> widgets = DataUtils.getFolderNoteWidget(mContentResolver,
                folderId);
        if (!isSyncMode()) {
            DataUtils.batchDeleteNotes(mContentResolver, ids);
        } else {
            DataUtils.batchMoveToFolder(mContentResolver, ids, Notes.ID_TRASH_FOLER);
        }
        if (widgets != null) {
            for (AppWidgetAttribute widget : widgets) {
                if (widget.widgetId != AppWidgetManager.INVALID_APPWIDGET_ID
                        && widget.widgetType != Notes.TYPE_WIDGET_INVALIDE) {
                    updateWidget(widget.widgetId, widget.widgetType);
                }
            }
        }
    }

    /**
     * 打开一条笔记进行编辑
     * @param data 要打开的笔记数据项
     */
    private void openNode(NoteItemData data) {
        Intent intent = new Intent(this, NoteEditActivity.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.putExtra(Intent.EXTRA_UID, data.getId());
        this.startActivityForResult(intent, REQUEST_CODE_OPEN_NODE);
    }

    /**
     * 打开一个文件夹（进入子目录）
     * @param data 要打开的文件夹数据项
     */
    private void openFolder(NoteItemData data) {
        mCurrentFolderId = data.getId();
        startAsyncNotesListQuery();
        if (data.getId() == Notes.ID_CALL_RECORD_FOLDER) {
            mState = ListEditState.CALL_RECORD_FOLDER;
            mAddNewNote.setVisibility(View.GONE);
        } else {
            mState = ListEditState.SUB_FOLDER;
        }
        if (data.getId() == Notes.ID_CALL_RECORD_FOLDER) {
            mTitleBar.setText(R.string.call_record_folder_name);
        } else {
            mTitleBar.setText(data.getSnippet());
        }
        mTitleBar.setVisibility(View.VISIBLE);
    }

    // OnClickListener 实现：点击新建笔记按钮
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.btn_new_note) {
            createNewNote();
        }
    }

    /**
     * 显示软键盘
     */
    private void showSoftInput() {
        InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (inputMethodManager != null) {
            inputMethodManager.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
        }
    }

    /**
     * 隐藏软键盘
     * @param view 当前焦点视图
     */
    private void hideSoftInput(View view) {
        InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    /**
     * 显示创建或修改文件夹的对话框
     * @param create true表示新建文件夹，false表示重命名当前选中的文件夹
     */
    private void showCreateOrModifyFolderDialog(final boolean create) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_text, null);
        final EditText etName = (EditText) view.findViewById(R.id.et_foler_name);
        showSoftInput();
        if (!create) {
            if (mFocusNoteDataItem != null) {
                etName.setText(mFocusNoteDataItem.getSnippet());
                builder.setTitle(getString(R.string.menu_folder_change_name));
            } else {
                Log.e(TAG, "The long click data item is null");
                return;
            }
        } else {
            etName.setText("");
            builder.setTitle(this.getString(R.string.menu_create_folder));
        }

        builder.setPositiveButton(android.R.string.ok, null);
        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                hideSoftInput(etName);
            }
        });

        final Dialog dialog = builder.setView(view).show();
        final Button positive = (Button)dialog.findViewById(android.R.id.button1);
        positive.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                hideSoftInput(etName);
                String name = etName.getText().toString();
                // 检查文件夹名是否已存在
                if (DataUtils.checkVisibleFolderName(mContentResolver, name)) {
                    Toast.makeText(NotesListActivity.this, getString(R.string.folder_exist, name),
                            Toast.LENGTH_LONG).show();
                    etName.setSelection(0, etName.length());
                    return;
                }
                if (!create) {
                    if (!TextUtils.isEmpty(name)) {
                        ContentValues values = new ContentValues();
                        values.put(NoteColumns.SNIPPET, name);
                        values.put(NoteColumns.TYPE, Notes.TYPE_FOLDER);
                        values.put(NoteColumns.LOCAL_MODIFIED, 1);
                        mContentResolver.update(Notes.CONTENT_NOTE_URI, values, NoteColumns.ID
                                + "=?", new String[] {
                                String.valueOf(mFocusNoteDataItem.getId())
                        });
                    }
                } else if (!TextUtils.isEmpty(name)) {
                    ContentValues values = new ContentValues();
                    values.put(NoteColumns.SNIPPET, name);
                    values.put(NoteColumns.TYPE, Notes.TYPE_FOLDER);
                    mContentResolver.insert(Notes.CONTENT_NOTE_URI, values);
                }
                dialog.dismiss();
            }
        });

        // 初始时如果输入框为空，禁用确定按钮
        if (TextUtils.isEmpty(etName.getText())) {
            positive.setEnabled(false);
        }
        // 实时监听输入框变化，控制确定按钮的启用状态
        etName.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (TextUtils.isEmpty(etName.getText())) {
                    positive.setEnabled(false);
                } else {
                    positive.setEnabled(true);
                }
            }

            public void afterTextChanged(Editable s) {
            }
        });
    }

    @Override
    public void onBackPressed() {
        switch (mState) {
            case SUB_FOLDER:
                // 从子文件夹返回根目录
                mCurrentFolderId = Notes.ID_ROOT_FOLDER;
                mState = ListEditState.NOTE_LIST;
                startAsyncNotesListQuery();
                mTitleBar.setVisibility(View.GONE);
                break;
            case CALL_RECORD_FOLDER:
                // 从通话记录文件夹返回根目录
                mCurrentFolderId = Notes.ID_ROOT_FOLDER;
                mState = ListEditState.NOTE_LIST;
                mAddNewNote.setVisibility(View.VISIBLE);
                mTitleBar.setVisibility(View.GONE);
                startAsyncNotesListQuery();
                break;
            case NOTE_LIST:
                super.onBackPressed();
                break;
            default:
                break;
        }
    }

    /**
     * 更新桌面小部件
     * @param appWidgetId 小部件ID
     * @param appWidgetType 小部件类型（2x 或 4x）
     */
    private void updateWidget(int appWidgetId, int appWidgetType) {
        Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        if (appWidgetType == Notes.TYPE_WIDGET_2X) {
            intent.setClass(this, NoteWidgetProvider_2x.class);
        } else if (appWidgetType == Notes.TYPE_WIDGET_4X) {
            intent.setClass(this, NoteWidgetProvider_4x.class);
        } else {
            Log.e(TAG, "Unspported widget type");
            return;
        }

        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[] {
                appWidgetId
        });

        sendBroadcast(intent);
        setResult(RESULT_OK, intent);
    }

    // 文件夹长按时显示的上下文菜单创建监听器
    private final OnCreateContextMenuListener mFolderOnCreateContextMenuListener = new OnCreateContextMenuListener() {
        public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
            if (mFocusNoteDataItem != null) {
                menu.setHeaderTitle(mFocusNoteDataItem.getSnippet());
                menu.add(0, MENU_FOLDER_VIEW, 0, R.string.menu_folder_view);
                menu.add(0, MENU_FOLDER_DELETE, 0, R.string.menu_folder_delete);
                menu.add(0, MENU_FOLDER_CHANGE_NAME, 0, R.string.menu_folder_change_name);
            }
        }
    };

    @Override
    public void onContextMenuClosed(Menu menu) {
        if (mNotesListView != null) {
            mNotesListView.setOnCreateContextMenuListener(null);
        }
        super.onContextMenuClosed(menu);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (mFocusNoteDataItem == null) {
            Log.e(TAG, "The long click data item is null");
            return false;
        }
        int itemId = item.getItemId();
        if (itemId == MENU_FOLDER_VIEW) {
            openFolder(mFocusNoteDataItem);
        } else if (itemId == MENU_FOLDER_DELETE) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(getString(R.string.alert_title_delete));
            builder.setIcon(android.R.drawable.ic_dialog_alert);
            builder.setMessage(getString(R.string.alert_message_delete_folder));
            builder.setPositiveButton(android.R.string.ok,
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            deleteFolder(mFocusNoteDataItem.getId());
                        }
                    });
            builder.setNegativeButton(android.R.string.cancel, null);
            builder.show();
        } else if (itemId == MENU_FOLDER_CHANGE_NAME) {
            showCreateOrModifyFolderDialog(false);
        }
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.clear();
        // 根据当前状态加载不同的菜单资源
        if (mState == ListEditState.NOTE_LIST) {
            getMenuInflater().inflate(R.menu.note_list, menu);
            // 同步菜单项文本根据同步状态动态改变
            menu.findItem(R.id.menu_sync).setTitle(
                    GTaskSyncService.isSyncing() ? R.string.menu_sync_cancel : R.string.menu_sync);
        } else if (mState == ListEditState.SUB_FOLDER) {
            getMenuInflater().inflate(R.menu.sub_folder, menu);
        } else if (mState == ListEditState.CALL_RECORD_FOLDER) {
            getMenuInflater().inflate(R.menu.call_record_folder, menu);
        } else {
            Log.e(TAG, "Wrong state:" + mState);
        }
        return true;
    }

    // 选项菜单点击处理
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.menu_new_folder) {
            showCreateOrModifyFolderDialog(true);
        } else if (itemId == R.id.menu_export_text) {
            exportNoteToText();
        } else if (itemId == R.id.menu_sync) {
            if (isSyncMode()) {
                if (TextUtils.equals(item.getTitle(), getString(R.string.menu_sync))) {
                    GTaskSyncService.startSync(this);
                } else {
                    GTaskSyncService.cancelSync(this);
                }
            } else {
                startPreferenceActivity();
            }
        } else if (itemId == R.id.menu_setting) {
            startPreferenceActivity();
        } else if (itemId == R.id.menu_new_note) {
            createNewNote();
        } else if (itemId == R.id.menu_search) {
            onSearchRequested();
        }
        return true;
    }

    @Override
    public boolean onSearchRequested() {
        startSearch(null, false, null, false);
        return true;
    }

    /**
     * 导出所有笔记为文本文件（调用BackupUtils）
     */
    private void exportNoteToText() {
        final BackupUtils backup = BackupUtils.getInstance(NotesListActivity.this);
        new AsyncTask<Void, Void, Integer>() {

            @Override
            protected Integer doInBackground(Void... unused) {
                return backup.exportToText();
            }

            @Override
            protected void onPostExecute(Integer result) {
                if (result == BackupUtils.STATE_SD_CARD_UNMOUONTED) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(NotesListActivity.this);
                    builder.setTitle(NotesListActivity.this
                            .getString(R.string.failed_sdcard_export));
                    builder.setMessage(NotesListActivity.this
                            .getString(R.string.error_sdcard_unmounted));
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.show();
                } else if (result == BackupUtils.STATE_SUCCESS) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(NotesListActivity.this);
                    builder.setTitle(NotesListActivity.this
                            .getString(R.string.success_sdcard_export));
                    builder.setMessage(NotesListActivity.this.getString(
                            R.string.format_exported_file_location, backup
                                    .getExportedTextFileName(), backup.getExportedTextFileDir()));
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.show();
                } else if (result == BackupUtils.STATE_SYSTEM_ERROR) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(NotesListActivity.this);
                    builder.setTitle(NotesListActivity.this
                            .getString(R.string.failed_sdcard_export));
                    builder.setMessage(NotesListActivity.this
                            .getString(R.string.error_sdcard_export));
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.show();
                }
            }

        }.execute();
    }

    /**
     * 判断是否处于同步模式（已配置Google账号）
     */
    private boolean isSyncMode() {
        return NotesPreferenceActivity.getSyncAccountName(this).trim().length() > 0;
    }

    /**
     * 启动设置界面
     */
    private void startPreferenceActivity() {
        Activity from = getParent() != null ? getParent() : this;
        Intent intent = new Intent(from, NotesPreferenceActivity.class);
        from.startActivityIfNeeded(intent, -1);
    }

    /**
     * 列表项单击监听器
     * 根据当前状态（根目录/子文件夹/通话记录文件夹）处理打开笔记或文件夹
     */
    private class OnListItemClickListener implements OnItemClickListener {

        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            if (view instanceof NotesListItem) {
                NoteItemData item = ((NotesListItem) view).getItemData();
                // 如果正处于多选模式，则仅切换选中状态，不打开
                if (mNotesListAdapter.isInChoiceMode()) {
                    if (item.getType() == Notes.TYPE_NOTE) {
                        position = position - mNotesListView.getHeaderViewsCount();
                        mModeCallBack.onItemCheckedStateChanged(null, position, id,
                                !mNotesListAdapter.isSelectedItem(position));
                    }
                    return;
                }

                switch (mState) {
                    case NOTE_LIST:
                        if (item.getType() == Notes.TYPE_FOLDER
                                || item.getType() == Notes.TYPE_SYSTEM) {
                            openFolder(item);
                        } else if (item.getType() == Notes.TYPE_NOTE) {
                            openNode(item);
                        } else {
                            Log.e(TAG, "Wrong note type in NOTE_LIST");
                        }
                        break;
                    case SUB_FOLDER:
                    case CALL_RECORD_FOLDER:
                        if (item.getType() == Notes.TYPE_NOTE) {
                            openNode(item);
                        } else {
                            Log.e(TAG, "Wrong note type in SUB_FOLDER");
                        }
                        break;
                    default:
                        break;
                }
            }
        }

    }

    /**
     * 启动查询可用文件夹（用于移动笔记时选择目标文件夹）
     */
    private void startQueryDestinationFolders() {
        String selection = NoteColumns.TYPE + "=? AND " + NoteColumns.PARENT_ID + "<>? AND " + NoteColumns.ID + "<>?";
        selection = (mState == ListEditState.NOTE_LIST) ? selection:
                "(" + selection + ") OR (" + NoteColumns.ID + "=" + Notes.ID_ROOT_FOLDER + ")";

        mBackgroundQueryHandler.startQuery(FOLDER_LIST_QUERY_TOKEN,
                null,
                Notes.CONTENT_NOTE_URI,
                FoldersListAdapter.PROJECTION,
                selection,
                new String[] {
                        String.valueOf(Notes.TYPE_FOLDER),
                        String.valueOf(Notes.ID_TRASH_FOLER),
                        String.valueOf(mCurrentFolderId)
                },
                NoteColumns.MODIFIED_DATE + " DESC");
    }

    // OnItemLongClickListener 实现：处理长按事件
    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        if (view instanceof NotesListItem) {
            mFocusNoteDataItem = ((NotesListItem) view).getItemData();
            if (mFocusNoteDataItem.getType() == Notes.TYPE_NOTE && !mNotesListAdapter.isInChoiceMode()) {
                // 长按笔记：启动多选模式
                if (mNotesListView.startActionMode(mModeCallBack) != null) {
                    mModeCallBack.onItemCheckedStateChanged(null, position, id, true);
                    mNotesListView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                } else {
                    Log.e(TAG, "startActionMode fails");
                }
            } else if (mFocusNoteDataItem.getType() == Notes.TYPE_FOLDER) {
                // 长按文件夹：显示上下文菜单
                mNotesListView.setOnCreateContextMenuListener(mFolderOnCreateContextMenuListener);
            }
        }
        return false;
    }

    // 创建选项菜单（初始页面菜单）
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.note_list, menu);
        return true;
    }

    /**
     * 导出当前选中的笔记为PDF文件
     * 调用 BackupUtils.exportToPdf()
     */
    private void exportPdf() {
        BackupUtils backupUtils = BackupUtils.getInstance(this);
        int result = backupUtils.exportToPdf();

        switch (result) {
            case BackupUtils.STATE_SUCCESS:
                Toast.makeText(this, "PDF导出成功！\n文件保存在 /sdcard/notes/ 目录", Toast.LENGTH_LONG).show();
                break;
            case BackupUtils.STATE_SD_CARD_UNMOUONTED:
                Toast.makeText(this, "错误：SD卡未挂载", Toast.LENGTH_SHORT).show();
                break;
            case BackupUtils.STATE_SYSTEM_ERROR:
                Toast.makeText(this, "错误：系统异常，导出失败", Toast.LENGTH_SHORT).show();
                break;
            default:
                Toast.makeText(this, "导出失败", Toast.LENGTH_SHORT).show();
                break;
        }
    }
}