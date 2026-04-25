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

package net.micode.notes.gtask.exception;

// 网络操作失败异常类
public class NetworkFailureException extends Exception {
    // 序列化版本唯一标识
    private static final long serialVersionUID = 2107610287180234136L;

    // 无参构造函数
    public NetworkFailureException() {
        super();
    }

    // 带异常信息的构造函数
    public NetworkFailureException(String paramString) {
        super(paramString);
    }

    // 带异常信息和异常原因的构造函数
    public NetworkFailureException(String paramString, Throwable paramThrowable) {
        super(paramString, paramThrowable);
    }
}