/*
 * 版权声明：属于 MiCode 开源社区，使用 Apache 2.0 开源协议
 * 简单说：可以自由使用、修改，但必须保留版权声明
 */
package net.micode.notes.tool;

import android.content.Context;
import android.database.Cursor;
import android.os.Environment;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.Log;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.DataConstants;
import net.micode.notes.data.Notes.NoteColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;

/**
 * 【核心类】BackupUtils：便签备份工具类
 * 功能：将 APP 内所有笔记、文件夹、通话记录笔记导出为 .txt 文件到 SD 卡
 * 设计模式：单例模式（整个APP只创建一个实例）
 */
public class BackupUtils {
    // 日志标签，用于调试打印
    private static final String TAG = "BackupUtils";

    // 单例实例（整个APP只使用一个BackupUtils对象）
    private static BackupUtils sInstance;

    /**
     * 获取单例实例（线程安全）
     */
    public static synchronized BackupUtils getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new BackupUtils(context);
        }
        return sInstance;
    }

    // ====================== 备份/恢复状态常量定义 ======================
    // SD 卡未挂载（不可用）
    public static final int STATE_SD_CARD_UNMOUONTED = 0;
    // 备份文件不存在
    public static final int STATE_BACKUP_FILE_NOT_EXIST = 1;
    // 数据格式损坏
    public static final int STATE_DATA_DESTROIED = 2;
    // 系统错误/运行时异常
    public static final int STATE_SYSTEM_ERROR = 3;
    // 备份/恢复成功
    public static final int STATE_SUCCESS = 4;

    // 文本导出工具对象（内部类，专门负责导出逻辑）
    private TextExport mTextExport;

    /**
     * 私有构造方法（单例模式，外部不能直接 new）
     */
    private BackupUtils(Context context) {
        mTextExport = new TextExport(context);
    }

    /**
     * 判断 SD 卡是否可用（已挂载）
     */
    private static boolean externalStorageAvailable() {
        return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
    }

    /**
     * 对外暴露的导出方法：调用内部类执行导出
     * @return 导出状态码（成功/失败）
     */
    public int exportToText() {
        return mTextExport.exportToText();
    }

    // 获取导出的文件名
    public String getExportedTextFileName() {
        return mTextExport.mFileName;
    }

    // 获取导出文件所在目录
    public String getExportedTextFileDir() {
        return mTextExport.mFileDirectory;
    }

    // ====================== 内部核心类：文本导出实现 ======================
    /**
     * TextExport：真正执行“把数据库数据写到txt文件”的类
     * 从数据库查询所有笔记 → 格式化 → 写入文件
     */
    private static class TextExport {
        // 数据库查询：需要查询的便签表字段（ID、修改时间、摘要、类型）
        private static final String[] NOTE_PROJECTION = {
                NoteColumns.ID,
                NoteColumns.MODIFIED_DATE,
                NoteColumns.SNIPPET,
                NoteColumns.TYPE
        };

        // 字段索引（方便代码读取，不用记数字0、1、2）
        private static final int NOTE_COLUMN_ID = 0;             // 便签ID
        private static final int NOTE_COLUMN_MODIFIED_DATE = 1;  // 修改时间
        private static final int NOTE_COLUMN_SNIPPET = 2;        // 摘要/文件夹名

        // 数据库查询：便签内容表字段（内容、类型、日期、号码等）
        private static final String[] DATA_PROJECTION = {
                DataColumns.CONTENT,
                DataColumns.MIME_TYPE,
                DataColumns.DATA1,
                DataColumns.DATA2,
                DataColumns.DATA3,
                DataColumns.DATA4,
        };

        // 内容表字段索引
        private static final int DATA_COLUMN_CONTENT = 0;        // 文本内容
        private static final int DATA_COLUMN_MIME_TYPE = 1;      // 类型（普通笔记/通话笔记）
        private static final int DATA_COLUMN_CALL_DATE = 2;      // 通话日期
        private static final int DATA_COLUMN_PHONE_NUMBER = 4;   // 通话号码

        // 导出文本的格式（从 strings.xml 读取：文件夹名、日期、内容格式）
        private final String [] TEXT_FORMAT;
        private static final int FORMAT_FOLDER_NAME = 0;     // 文件夹名格式
        private static final int FORMAT_NOTE_DATE = 1;       // 笔记日期格式
        private static final int FORMAT_NOTE_CONTENT = 2;    // 笔记内容格式

        private Context mContext;
        public String mFileName;       // 导出的文件名
        public String mFileDirectory;  // 导出文件目录

        // 构造：初始化格式、上下文
        public TextExport(Context context) {
            TEXT_FORMAT = context.getResources().getStringArray(R.array.format_for_exported_note);
            mContext = context;
            mFileName = "";
            mFileDirectory = "";
        }

        // 获取指定格式字符串
        private String getFormat(int id) {
            return TEXT_FORMAT[id];
        }

        /**
         * 导出【一个文件夹】下的所有笔记到文本
         * @param folderId 文件夹ID
         * @param ps 打印流（写入文件）
         */
        private void exportFolderToText(String folderId, PrintStream ps) {
            // 查询该文件夹下的所有笔记
            Cursor notesCursor = mContext.getContentResolver().query(Notes.CONTENT_NOTE_URI,
                    NOTE_PROJECTION, NoteColumns.PARENT_ID + "=?", new String[] { folderId }, null);

            if (notesCursor != null) {
                if (notesCursor.moveToFirst()) {
                    do {
                        // 打印笔记修改时间
                        ps.println(String.format(getFormat(FORMAT_NOTE_DATE), DateFormat.format(
                                mContext.getString(R.string.format_datetime_mdhm),
                                notesCursor.getLong(NOTE_COLUMN_MODIFIED_DATE))));

                        // 获取笔记ID，导出这条笔记的内容
                        String noteId = notesCursor.getString(NOTE_COLUMN_ID);
                        exportNoteToText(noteId, ps);

                    } while (notesCursor.moveToNext()); // 遍历所有笔记
                }
                notesCursor.close(); // 关闭游标，防止内存泄漏
            }
        }

        /**
         * 导出【单条笔记】的内容到文本
         * @param noteId 笔记ID
         * @param ps 写入流
         */
        private void exportNoteToText(String noteId, PrintStream ps) {
            // 查询这条笔记的所有内容数据
            Cursor dataCursor = mContext.getContentResolver().query(Notes.CONTENT_DATA_URI,
                    DATA_PROJECTION, DataColumns.NOTE_ID + "=?", new String[] { noteId }, null);

            if (dataCursor != null) {
                if (dataCursor.moveToFirst()) {
                    do {
                        String mimeType = dataCursor.getString(DATA_COLUMN_MIME_TYPE);

                        // ====================== 情况1：通话记录笔记 ======================
                        if (DataConstants.CALL_NOTE.equals(mimeType)) {
                            String phoneNumber = dataCursor.getString(DATA_COLUMN_PHONE_NUMBER);
                            long callDate = dataCursor.getLong(DATA_COLUMN_CALL_DATE);
                            String location = dataCursor.getString(DATA_COLUMN_CONTENT);

                            // 写入电话号码
                            if (!TextUtils.isEmpty(phoneNumber)) {
                                ps.println(String.format(getFormat(FORMAT_NOTE_CONTENT), phoneNumber));
                            }
                            // 写入通话时间
                            ps.println(String.format(getFormat(FORMAT_NOTE_CONTENT), DateFormat
                                    .format(mContext.getString(R.string.format_datetime_mdhm), callDate)));
                            // 写入通话地点
                            if (!TextUtils.isEmpty(location)) {
                                ps.println(String.format(getFormat(FORMAT_NOTE_CONTENT), location));
                            }
                        }
                        // ====================== 情况2：普通文本笔记 ======================
                        else if (DataConstants.NOTE.equals(mimeType)) {
                            String content = dataCursor.getString(DATA_COLUMN_CONTENT);
                            if (!TextUtils.isEmpty(content)) {
                                ps.println(String.format(getFormat(FORMAT_NOTE_CONTENT), content));
                            }
                        }
                    } while (dataCursor.moveToNext());
                }
                dataCursor.close();
            }

            // 笔记之间写入分隔符，方便阅读
            try {
                ps.write(new byte[] { Character.LINE_SEPARATOR });
            } catch (IOException e) {
                Log.e(TAG, e.toString());
            }
        }

        /**
         * 【核心导出方法】
         * 1.检查SD卡 → 2.创建文件 → 3.导出所有文件夹/笔记 → 4.返回状态
         */
        public int exportToText() {
            // 检查SD卡是否可用
            if (!externalStorageAvailable()) {
                Log.d(TAG, "Media was not mounted");
                return STATE_SD_CARD_UNMOUONTED;
            }

            // 获取文件输出流
            PrintStream ps = getExportToTextPrintStream();
            if (ps == null) {
                Log.e(TAG, "get print stream error");
                return STATE_SYSTEM_ERROR;
            }

            // ====================== 第一步：导出所有文件夹（不含回收站） ======================
            Cursor folderCursor = mContext.getContentResolver().query(
                    Notes.CONTENT_NOTE_URI,
                    NOTE_PROJECTION,
                    "(" + NoteColumns.TYPE + "=" + Notes.TYPE_FOLDER + " AND "
                            + NoteColumns.PARENT_ID + "<>" + Notes.ID_TRASH_FOLER + ") OR "
                            + NoteColumns.ID + "=" + Notes.ID_CALL_RECORD_FOLDER, null, null);

            if (folderCursor != null) {
                if (folderCursor.moveToFirst()) {
                    do {
                        // 获取文件夹名（通话记录文件夹特殊处理）
                        String folderName = "";
                        if(folderCursor.getLong(NOTE_COLUMN_ID) == Notes.ID_CALL_RECORD_FOLDER) {
                            folderName = mContext.getString(R.string.call_record_folder_name);
                        } else {
                            folderName = folderCursor.getString(NOTE_COLUMN_SNIPPET);
                        }

                        // 写入文件夹名
                        if (!TextUtils.isEmpty(folderName)) {
                            ps.println(String.format(getFormat(FORMAT_FOLDER_NAME), folderName));
                        }

                        // 导出这个文件夹下的所有笔记
                        String folderId = folderCursor.getString(NOTE_COLUMN_ID);
                        exportFolderToText(folderId, ps);

                    } while (folderCursor.moveToNext());
                }
                folderCursor.close();
            }

            // ====================== 第二步：导出【根目录】下的笔记（不属于任何文件夹） ======================
            Cursor noteCursor = mContext.getContentResolver().query(
                    Notes.CONTENT_NOTE_URI,
                    NOTE_PROJECTION,
                    NoteColumns.TYPE + "=" + Notes.TYPE_NOTE + " AND " + NoteColumns.PARENT_ID + "=0",
                    null, null);

            if (noteCursor != null) {
                if (noteCursor.moveToFirst()) {
                    do {
                        // 写入时间
                        ps.println(String.format(getFormat(FORMAT_NOTE_DATE), DateFormat.format(
                                mContext.getString(R.string.format_datetime_mdhm),
                                noteCursor.getLong(NOTE_COLUMN_MODIFIED_DATE))));
                        // 导出笔记内容
                        String noteId = noteCursor.getString(NOTE_COLUMN_ID);
                        exportNoteToText(noteId, ps);
                    } while (noteCursor.moveToNext());
                }
                noteCursor.close();
            }

            // 关闭流，完成导出
            ps.close();
            return STATE_SUCCESS;
        }

        /**
         * 创建导出文件，并返回文件打印流
         */
        private PrintStream getExportToTextPrintStream() {
            // 在SD卡创建备份文件
            File file = generateFileMountedOnSDcard(mContext, R.string.file_path, R.string.file_name_txt_format);
            if (file == null) {
                Log.e(TAG, "create file to exported failed");
                return null;
            }

            // 记录文件名和路径
            mFileName = file.getName();
            mFileDirectory = mContext.getString(R.string.file_path);

            PrintStream ps = null;
            try {
                FileOutputStream fos = new FileOutputStream(file);
                ps = new PrintStream(fos);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                return null;
            } catch (NullPointerException e) {
                e.printStackTrace();
                return null;
            }
            return ps;
        }
    }

    /**
     * 在 SD 卡生成备份文件
     * 路径：SD卡/notes/备份_20250101.txt
     */
    private static File generateFileMountedOnSDcard(Context context, int filePathResId, int fileNameFormatResId) {
        StringBuilder sb = new StringBuilder();
        // SD卡根目录
        sb.append(Environment.getExternalStorageDirectory());
        // 拼接文件路径（如 /notes/）
        sb.append(context.getString(filePathResId));

        File filedir = new File(sb.toString());

        // 拼接文件名（带日期）
        sb.append(context.getString(
                fileNameFormatResId,
                DateFormat.format(context.getString(R.string.format_date_ymd), System.currentTimeMillis())));

        File file = new File(sb.toString());

        try {
            // 目录不存在则创建
            if (!filedir.exists()) {
                filedir.mkdir();
            }
            // 文件不存在则创建
            if (!file.exists()) {
                file.createNewFile();
            }
            return file;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}