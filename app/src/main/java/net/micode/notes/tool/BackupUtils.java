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

import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Font;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfWriter;


import com.itextpdf.text.DocumentException;
import java.io.IOException;

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
    private static TextExport mTextExport;

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

    /**
     * 对外暴露的导出PDF方法
     * @return 导出状态码（成功/失败）
     */
    public int exportToPdf() {
        return mTextExport.exportToPdf();
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
     * TextExport：真正执行"把数据库数据写到txt文件"的类
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
         * 导出 PDF 核心方法
         * 1.检查SD卡 → 2.创建PDF文档 → 3.导出所有笔记 → 4.返回状态
         */
        public int exportToPdf() {
            if (!externalStorageAvailable()) {
                Log.d(TAG, "Media was not mounted");
                return STATE_SD_CARD_UNMOUONTED;
            }

            File file = generateFileMountedOnSDcard(mContext, R.string.file_path, R.string.file_name_pdf_format);
            if (file == null) {
                Log.e(TAG, "create file to exported failed");
                return STATE_SYSTEM_ERROR;
            }
            mFileName = file.getName();
            mFileDirectory = mContext.getString(R.string.file_path);

            FileOutputStream fos = null;
            Document document = null;

            try {
                // 创建文件输出流
                fos = new FileOutputStream(file);

                // 创建PDF文档
                document = new Document();

                // 绑定PdfWriter到文档和输出流
                PdfWriter.getInstance(document, fos);

                // 打开文档开始写入
                document.open();

                // 创建字体 - 支持中文显示
                BaseFont baseFont = BaseFont.createFont("STSong-Light", "UniGB-UCS2-H", BaseFont.NOT_EMBEDDED);
                Font font = new Font(baseFont, 12, Font.NORMAL);

                // 添加标题
                String title = "笔记备份 - " + DateFormat.format("yyyy年MM月dd日 HH:mm", System.currentTimeMillis());
                Paragraph titleParagraph = new Paragraph(title, font);
                titleParagraph.setAlignment(Paragraph.ALIGN_CENTER);
                document.add(titleParagraph);
                document.add(new Paragraph(" "));
                document.add(new Paragraph(" "));

                // 导出文件夹笔记
                Cursor folderCursor = mContext.getContentResolver().query(
                        Notes.CONTENT_NOTE_URI,
                        NOTE_PROJECTION,
                        "(" + NoteColumns.TYPE + "=" + Notes.TYPE_FOLDER + " AND "
                                + NoteColumns.PARENT_ID + "<>" + Notes.ID_TRASH_FOLER + ") OR "
                                + NoteColumns.ID + "=" + Notes.ID_CALL_RECORD_FOLDER,
                        null, null
                );

                if (folderCursor != null) {
                    while (folderCursor.moveToNext()) {
                        // 获取文件夹名称
                        String folderName;
                        if (folderCursor.getLong(NOTE_COLUMN_ID) == Notes.ID_CALL_RECORD_FOLDER) {
                            folderName = mContext.getString(R.string.call_record_folder_name);
                        } else {
                            folderName = folderCursor.getString(NOTE_COLUMN_SNIPPET);
                        }

                        // 添加文件夹标题
                        if (!TextUtils.isEmpty(folderName)) {
                            Paragraph folderTitle = new Paragraph("【文件夹】" + folderName, font);
                            folderTitle.setAlignment(Paragraph.ALIGN_LEFT);
                            document.add(folderTitle);
                            document.add(new Paragraph(" "));
                        }

                        // 导出文件夹下的所有笔记
                        String folderId = folderCursor.getString(NOTE_COLUMN_ID);
                        exportNoteToPdf(folderId, document, font, true);
                    }
                    folderCursor.close();
                }

                // 导出根目录笔记（不属于任何文件夹的笔记）
                Cursor noteCursor = mContext.getContentResolver().query(
                        Notes.CONTENT_NOTE_URI,
                        NOTE_PROJECTION,
                        NoteColumns.TYPE + "=" + Notes.TYPE_NOTE + " AND " + NoteColumns.PARENT_ID + "=0",
                        null, null
                );

                if (noteCursor != null) {
                    if (noteCursor.getCount() > 0) {
                        // 添加根目录笔记标题
                        Paragraph rootTitle = new Paragraph("【根目录笔记】", font);
                        rootTitle.setAlignment(Paragraph.ALIGN_LEFT);
                        document.add(rootTitle);
                        document.add(new Paragraph(" "));

                        while (noteCursor.moveToNext()) {
                            String dateStr = DateFormat.format(
                                    mContext.getString(R.string.format_datetime_mdhm),
                                    noteCursor.getLong(NOTE_COLUMN_MODIFIED_DATE)).toString();

                            Paragraph dateParagraph = new Paragraph("创建时间：" + dateStr, font);
                            document.add(dateParagraph);

                            String noteId = noteCursor.getString(NOTE_COLUMN_ID);
                            exportNoteToPdf(noteId, document, font, false);

                            // 添加分隔线
                            document.add(new Paragraph("------------------------"));
                            document.add(new Paragraph(" "));
                        }
                    }
                    noteCursor.close();
                }

                // 添加结束标记
                Paragraph endParagraph = new Paragraph("--- 备份完成 ---", font);
                endParagraph.setAlignment(Paragraph.ALIGN_CENTER);
                document.add(endParagraph);

                document.close();
                Log.d(TAG, "PDF export completed: " + file.getAbsolutePath());
                return STATE_SUCCESS;

            } catch (FileNotFoundException e) {
                Log.e(TAG, "PDF file not found: " + e.getMessage(), e);
                return STATE_SYSTEM_ERROR;
            } catch (DocumentException e) {
                Log.e(TAG, "PDF document error: " + e.getMessage(), e);
                return STATE_SYSTEM_ERROR;
            } catch (IOException e) {
                Log.e(TAG, "PDF IO error or font error: " + e.getMessage(), e);
                return STATE_SYSTEM_ERROR;
            } catch (Exception e) {
                Log.e(TAG, "PDF export error: " + e.getMessage(), e);
                return STATE_SYSTEM_ERROR;
            } finally {
                // 确保关闭文档
                if (document != null && document.isOpen()) {
                    document.close();
                }

                // 确保关闭输出流
                if (fos != null) {
                    try {
                        fos.close();
                    } catch (IOException e) {
                        Log.e(TAG, "Error closing file output stream: " + e.getMessage(), e);
                    }
                }
            }
        }

        /**
         * 导出单条笔记到PDF
         * @param noteId 笔记ID
         * @param document PDF文档对象
         * @param font PDF字体
         * @param isInFolder 是否在文件夹中（控制格式）
         */
        private void exportNoteToPdf(String noteId, Document document, Font font, boolean isInFolder) {
            Cursor dataCursor = null;

            try {
                dataCursor = mContext.getContentResolver().query(
                        Notes.CONTENT_DATA_URI,
                        DATA_PROJECTION,
                        DataColumns.NOTE_ID + "=?",
                        new String[]{noteId},
                        null
                );

                if (dataCursor != null) {
                    while (dataCursor.moveToNext()) {
                        String mimeType = dataCursor.getString(DATA_COLUMN_MIME_TYPE);
                        String content = dataCursor.getString(DATA_COLUMN_CONTENT);

                        if (DataConstants.CALL_NOTE.equals(mimeType)) {
                            // 处理通话记录笔记
                            String phone = dataCursor.getString(DATA_COLUMN_PHONE_NUMBER);
                            long callDate = dataCursor.getLong(DATA_COLUMN_CALL_DATE);
                            String location = dataCursor.getString(DATA_COLUMN_CONTENT);

                            // 添加电话号码
                            if (!TextUtils.isEmpty(phone)) {
                                Paragraph phoneParagraph = new Paragraph("电话号码：" + phone, font);
                                document.add(phoneParagraph);
                            }

                            // 添加通话时间
                            String callDateStr = DateFormat.format(
                                    mContext.getString(R.string.format_datetime_mdhm),
                                    callDate).toString();
                            Paragraph dateParagraph = new Paragraph("通话时间：" + callDateStr, font);
                            document.add(dateParagraph);

                            // 添加通话地点
                            if (!TextUtils.isEmpty(location)) {
                                Paragraph locationParagraph = new Paragraph("通话地点：" + location, font);
                                document.add(locationParagraph);
                            }

                            document.add(new Paragraph(" "));

                        } else if (DataConstants.NOTE.equals(mimeType) && !TextUtils.isEmpty(content)) {
                            // 处理普通文本笔记
                            Paragraph contentParagraph = new Paragraph(content, font);
                            document.add(contentParagraph);
                            document.add(new Paragraph(" "));
                        }
                    }
                }

                // 如果是文件夹内的笔记，添加分隔线
                if (isInFolder && dataCursor != null && dataCursor.getCount() > 0) {
                    document.add(new Paragraph("------------------------"));
                    document.add(new Paragraph(" "));
                }

            } catch (DocumentException e) {
                Log.e(TAG, "Error adding paragraph to PDF for note ID: " + noteId, e);
            } finally {
                if (dataCursor != null) {
                    dataCursor.close();
                }
            }
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