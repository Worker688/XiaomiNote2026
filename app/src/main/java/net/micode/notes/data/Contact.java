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

package net.micode.notes.data;

import android.content.Context;
import android.database.Cursor;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Data;
import android.telephony.PhoneNumberUtils;
import android.util.Log;

import java.util.HashMap;

/**
 * 联系人工具类
 * 作用：根据手机号码查询系统通讯录中对应的联系人姓名，并做缓存优化
 * 当便签里插入一个电话号码时，自动把号码变成对应的联系人姓名显示。
 */
public class Contact {
    // 静态缓存Map：key=手机号，value=联系人姓名
    private static HashMap<String, String> sContactCache;
    // 日志TAG
    private static final String TAG = "Contact";

    /**
     * 数据库查询条件模板
     * 作用：匹配手机号 + 筛选电话数据类型 + 子查询优化匹配效率
     */
    private static final String CALLER_ID_SELECTION = "PHONE_NUMBERS_EQUAL(" + Phone.NUMBER
            + ",?) AND " + Data.MIMETYPE + "='" + Phone.CONTENT_ITEM_TYPE + "'"
            + " AND " + Data.RAW_CONTACT_ID + " IN "
            + "(SELECT raw_contact_id "
            + " FROM phone_lookup"
            + " WHERE min_match = '+')";

    /**
     * 根据手机号获取联系人姓名
     * @param context 上下文
     * @param phoneNumber 手机号码
     * @return 联系人姓名，找不到返回null
     */
    public static String getContact(Context context, String phoneNumber) {
        // 初始化缓存（懒加载）
        if(sContactCache == null) {
            sContactCache = new HashMap<String, String>();
        }

        // 先从缓存取，有就直接返回，不用查库
        if(sContactCache.containsKey(phoneNumber)) {
            return sContactCache.get(phoneNumber);
        }

        // 把查询模板中的占位符替换成手机号的最小匹配格式
        String selection = CALLER_ID_SELECTION.replace("+",
                PhoneNumberUtils.toCallerIDMinMatch(phoneNumber));

        // 查询系统联系人数据库
        Cursor cursor = context.getContentResolver().query(
                Data.CONTENT_URI,           // 联系人数据URI
                new String [] { Phone.DISPLAY_NAME },  // 只查询姓名列
                selection,                  // 查询条件
                new String[] { phoneNumber }, // 查询参数
                null);

        // 查询结果不为空，且能移动到第一条数据
        if (cursor != null && cursor.moveToFirst()) {
            try {
                // 获取联系人姓名
                String name = cursor.getString(0);
                // 存入缓存，下次直接用
                sContactCache.put(phoneNumber, name);
                return name;
            } catch (IndexOutOfBoundsException e) {
                // 捕获列索引越界异常
                Log.e(TAG, " Cursor get string error " + e.toString());
                return null;
            } finally {
                // 无论成功失败，必须关闭游标，防止内存泄漏
                cursor.close();
            }
        } else {
            // 没有匹配到联系人
            Log.d(TAG, "No contact matched with number:" + phoneNumber);
            return null;
        }
    }
}