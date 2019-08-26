// Copyright (c) 2019-present, iQIYI, Inc. All rights reserved.
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all
// copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.
//

// Created by caikelun on 2019-03-07.
package xcrash;

import android.content.Context;
import android.text.TextUtils;

/**
 * xCrash is a crash reporting library for Android APP.
 */
@SuppressWarnings("unused")
public final class XCrash {

    private static boolean initialized = false;
    private static String appId = null;
    private static String appVersion = null;
    private static String logDir = null;
    private static ILogger logger = new DefaultLogger();

    private XCrash() {
    }

    /**
     * Initialize xCrash with default parameters.
     *
     * <p>Note: This is a synchronous operation.
     *
     * @param ctx The context of the application object of the current process.
     * @return Return zero if successful, non-zero otherwise. The error code is defined in: {@link xcrash.Errno}.
     */
    @SuppressWarnings("unused")
    public static int init(Context ctx) {
        return init(ctx, null);
    }

    /**
     * Initialize xCrash with custom parameters.
     *
     * <p>Note: This is a synchronous operation.
     *
     * @param ctx The context of the application object of the current process.
     * @param params An initialization parameter set.
     * @return Return zero if successful, non-zero otherwise. The error code is defined in: {@link xcrash.Errno}.
     */
    @SuppressWarnings("unused")
    public static synchronized int init(Context ctx, InitParameters params) {
        if (XCrash.initialized) {
            return Errno.OK;
        }
        XCrash.initialized = true;

        if (ctx == null) {
            return Errno.CONTEXT_IS_NULL;
        }

        //make sure to get the instance of android.app.Application
        Context appContext = ctx.getApplicationContext();
        if (appContext != null) {
            ctx = appContext;
        }

        //use default parameters
        if (params == null) {
            params = new InitParameters();
        }

        //set logger
        if (params.logger != null) {
            XCrash.logger = params.logger;
        }

        //save app id
        XCrash.appId = ctx.getPackageName();
        if (TextUtils.isEmpty(XCrash.appId)) {
            XCrash.appId = "unknown";
        }

        //save app version
        if (TextUtils.isEmpty(params.appVersion)) {
            params.appVersion = Util.getAppVersion(ctx);
        }
        XCrash.appVersion = params.appVersion;

        //save log dir
        if (TextUtils.isEmpty(params.logDir)) {
            params.logDir = ctx.getFilesDir() + "/tombstones";
        }
        XCrash.logDir = params.logDir;

        //init file manager
        FileManager.getInstance().initialize(
            params.logDir,
            params.javaLogCountMax,
            params.nativeLogCountMax,
            params.placeholderCountMax,
            params.placeholderSizeKb,
            params.logFileMaintainDelayMs);

        //init java crash handler
        if (params.enableJavaCrashHandler) {
            JavaCrashHandler.getInstance().initialize(
                ctx,
                appId,
                params.appVersion,
                params.logDir,
                params.javaRethrow,
                params.javaLogcatSystemLines,
                params.javaLogcatEventsLines,
                params.javaLogcatMainLines,
                params.javaDumpAllThreads,
                params.javaDumpAllThreadsCountMax,
                params.javaDumpAllThreadsWhiteList,
                params.javaCallback);
        }

        //init native crash handler
        int r = Errno.OK;
        if (params.enableNativeCrashHandler) {
            r = NativeCrashHandler.getInstance().initialize(
                ctx,
                appId,
                params.appVersion,
                params.logDir,
                params.nativeRethrow,
                params.nativeLogcatSystemLines,
                params.nativeLogcatEventsLines,
                params.nativeLogcatMainLines,
                params.nativeDumpElfHash,
                params.nativeDumpMap,
                params.nativeDumpFds,
                params.nativeDumpAllThreads,
                params.nativeDumpAllThreadsCountMax,
                params.nativeDumpAllThreadsWhiteList,
                params.nativeCallback,
                params.libLoader);
        }

        //maintain tombstone and placeholder files in a background thread with some delay
        FileManager.getInstance().maintain();

        return r;
    }

    /**
     * An initialization parameter set.
     */
    public static class InitParameters {
        //common
        String     appVersion             = null;
        String     logDir                 = null;
        int        logFileMaintainDelayMs = 5000;
        ILogger    logger                 = null;
        ILibLoader libLoader              = null;

        /**
         * Set App version. You can use this method to set an internal test/gray version number.
         * (Default: {@link android.content.pm.PackageInfo#versionName})
         *
         * @param appVersion App version string.
         * @return The InitParameters object.
         */
        @SuppressWarnings("unused")
        public InitParameters setAppVersion(String appVersion) {
            this.appVersion = appVersion;
            return this;
        }

        /**
         * Set the directory to save crash log files.
         * (Default: {@link android.content.Context#getFilesDir()} + "/tombstones")
         *
         * @param dir Absolute path to the directory.
         * @return The InitParameters object.
         */
        @SuppressWarnings({"unused", "WeakerAccess"})
        public InitParameters setLogDir(String dir) {
            this.logDir = dir;
            return this;
        }

        /**
         * Set delay in milliseconds before the log file maintain task is to be executed. (Default: 5000)
         *
         * @param logFileMaintainDelayMs Delay in milliseconds before the log file maintain task is to be executed.
         * @return The InitParameters object.
         */
        @SuppressWarnings("unused")
        public InitParameters setLogFileMaintainDelayMs(int logFileMaintainDelayMs) {
            this.logFileMaintainDelayMs = (logFileMaintainDelayMs < 0 ? 0 : logFileMaintainDelayMs);
            return this;
        }

        /**
         * Set a logger implementation for xCrash to log message and exception.
         *
         * @param logger An instance of {@link xcrash.ILogger}.
         * @return The InitParameters object.
         */
        public InitParameters setLogger(ILogger logger) {
            this.logger = logger;
            return this;
        }

        /**
         * Set a libLoader implementation for xCrash to load native library.
         *
         * @param libLoader An instance of {@link xcrash.ILibLoader}.
         * @return The InitParameters object.
         */
        public InitParameters setLibLoader(ILibLoader libLoader) {
            this.libLoader = libLoader;
            return this;
        }

        //placeholder
        int placeholderCountMax = 0;
        int placeholderSizeKb   = 128;

        /**
         * Set the maximum number of placeholder files in the log directory. (Default: 0)
         *
         * <p>Note: Set this value to 0 means disable the placeholder feature.
         *
         * @param countMax The maximum number of placeholder files.
         * @return The InitParameters object.
         */
        @SuppressWarnings("unused")
        public InitParameters setPlaceholderCountMax(int countMax) {
            this.placeholderCountMax = (countMax < 0 ? 0 : countMax);
            return this;
        }

        /**
         * Set the KB of each placeholder files in the log directory. (Default: 128)
         *
         * @param sizeKb The KB of each placeholder files.
         * @return The InitParameters object.
         */
        @SuppressWarnings("unused")
        public InitParameters setPlaceholderSizeKb(int sizeKb) {
            this.placeholderSizeKb = (sizeKb < 0 ? 0 : sizeKb);
            return this;
        }

        //java
        boolean        enableJavaCrashHandler      = true;
        boolean        javaRethrow                 = true;
        int            javaLogCountMax             = 10;
        int            javaLogcatSystemLines       = 50;
        int            javaLogcatEventsLines       = 50;
        int            javaLogcatMainLines         = 200;
        boolean        javaDumpAllThreads          = true;
        int            javaDumpAllThreadsCountMax  = 0;
        String[]       javaDumpAllThreadsWhiteList = null;
        ICrashCallback javaCallback                = null;

        /**
         * Enable the Java exception capture feature. (Default: enable)
         *
         * @return The InitParameters object.
         */
        @SuppressWarnings("unused")
        public InitParameters enableJavaCrashHandler() {
            this.enableJavaCrashHandler = true;
            return this;
        }

        /**
         * Disable the Java exception capture feature. (Default: enable)
         *
         * @return The InitParameters object.
         */
        @SuppressWarnings("unused")
        public InitParameters disableJavaCrashHandler() {
            this.enableJavaCrashHandler = false;
            return this;
        }

        /**
         * Set whether xCrash should rethrow the Java exception to system
         * after it has been handled. (Default: true)
         *
         * @param rethrow If <code>true</code>, the Java exception will be rethrown to Android System.
         * @return The InitParameters object.
         */
        @SuppressWarnings("unused")
        public InitParameters setJavaRethrow(boolean rethrow) {
            this.javaRethrow = rethrow;
            return this;
        }

        /**
         * Set the maximum number of Java crash log files to save in the log directory. (Default: 10)
         *
         * @param countMax The maximum number of Java crash log files.
         * @return The InitParameters object.
         */
        @SuppressWarnings("unused")
        public InitParameters setJavaLogCountMax(int countMax) {
            this.javaLogCountMax = (countMax < 1 ? 1 : countMax);
            return this;
        }

        /**
         * Set the maximum number of rows to get from "logcat -b system" when a Java exception occurred. (Default: 50)
         *
         * @param logcatSystemLines The maximum number of rows.
         * @return The InitParameters object.
         */
        @SuppressWarnings("unused")
        public InitParameters setJavaLogcatSystemLines(int logcatSystemLines) {
            this.javaLogcatSystemLines = logcatSystemLines;
            return this;
        }

        /**
         * Set the maximum number of rows to get from "logcat -b events" when a Java exception occurred. (Default: 50)
         *
         * @param logcatEventsLines The maximum number of rows.
         * @return The InitParameters object.
         */
        @SuppressWarnings("unused")
        public InitParameters setJavaLogcatEventsLines(int logcatEventsLines) {
            this.javaLogcatEventsLines = logcatEventsLines;
            return this;
        }

        /**
         * Set the maximum number of rows to get from "logcat -b main" when a Java exception occurred. (Default: 200)
         *
         * @param logcatMainLines The maximum number of rows.
         * @return The InitParameters object.
         */
        @SuppressWarnings("unused")
        public InitParameters setJavaLogcatMainLines(int logcatMainLines) {
            this.javaLogcatMainLines = logcatMainLines;
            return this;
        }

        /**
         * Set if dumping threads info (stacktrace) for all threads (not just the thread that has crashed)
         * when a Java exception occurred. (Default: enable)
         *
         * @param flag True or false.
         * @return The InitParameters object.
         */
        @SuppressWarnings("unused")
        public InitParameters setJavaDumpAllThreads(boolean flag) {
            this.javaDumpAllThreads = flag;
            return this;
        }

        /**
         * Set the maximum number of other threads to dump when a Java exception occurred.
         * "0" means no limit. (Default: 0)
         *
         * <p>Note: This option is only useful when "JavaDumpAllThreads" is enabled by calling {@link InitParameters#setJavaDumpAllThreads(boolean)}.
         *
         * @param countMax The maximum number of other threads to dump.
         * @return The InitParameters object.
         */
        @SuppressWarnings("unused")
        public InitParameters setJavaDumpAllThreadsCountMax(int countMax) {
            this.javaDumpAllThreadsCountMax = (countMax < 0 ? 0 : countMax);
            return this;
        }

        /**
         * Set a thread name (regular expression) whitelist to filter which threads need to be dumped when a Java exception occurred.
         * "null" means no filtering. (Default: null)
         *
         * <p>Note: This option is only useful when "JavaDumpAllThreads" is enabled by calling {@link InitParameters#setJavaDumpAllThreads(boolean)}.
         *
         * @param whiteList A thread name (regular expression) whitelist.
         * @return The InitParameters object.
         */
        @SuppressWarnings("unused")
        public InitParameters setJavaDumpAllThreadsWhiteList(String[] whiteList) {
            this.javaDumpAllThreadsWhiteList = whiteList;
            return this;
        }

        /**
         * Set a callback to be executed when a Java exception occurred. (If not set, nothing will be happened.)
         *
         * @param callback An instance of {@link xcrash.ICrashCallback}.
         * @return The InitParameters object.
         */
        @SuppressWarnings("unused")
        public InitParameters setJavaCallback(ICrashCallback callback) {
            this.javaCallback = callback;
            return this;
        }

        //native
        boolean        enableNativeCrashHandler      = true;
        boolean        nativeRethrow                 = true;
        int            nativeLogCountMax             = 10;
        int            nativeLogcatSystemLines       = 50;
        int            nativeLogcatEventsLines       = 50;
        int            nativeLogcatMainLines         = 200;
        boolean        nativeDumpElfHash             = true;
        boolean        nativeDumpMap                 = true;
        boolean        nativeDumpFds                 = true;
        boolean        nativeDumpAllThreads          = true;
        int            nativeDumpAllThreadsCountMax  = 0;
        String[]       nativeDumpAllThreadsWhiteList = null;
        ICrashCallback nativeCallback                = null;

        /**
         * Enable the native crash capture feature. (Default: enable)
         *
         * @return The InitParameters object.
         */
        @SuppressWarnings("unused")
        public InitParameters enableNativeCrashHandler() {
            this.enableNativeCrashHandler = true;
            return this;
        }

        /**
         * Disable the native crash capture feature. (Default: enable)
         *
         * @return The InitParameters object.
         */
        @SuppressWarnings("unused")
        public InitParameters disableNativeCrashHandler() {
            this.enableNativeCrashHandler = false;
            return this;
        }

        /**
         * Set whether xCrash should rethrow the native signal to system
         * after it has been handled. (Default: true)
         *
         * @param rethrow If <code>true</code>, the native signal will be rethrown to Android System.
         * @return The InitParameters object.
         */
        @SuppressWarnings("unused")
        public InitParameters setNativeRethrow(boolean rethrow) {
            this.nativeRethrow = rethrow;
            return this;
        }

        /**
         * Set the maximum number of native crash log files to save in the log directory. (Default: 10)
         *
         * @param countMax The maximum number of native crash log files.
         * @return The InitParameters object.
         */
        @SuppressWarnings("unused")
        public InitParameters setNativeLogCountMax(int countMax) {
            this.nativeLogCountMax = (countMax < 1 ? 1 : countMax);
            return this;
        }

        /**
         * Set the maximum number of rows to get from "logcat -b system" when a native crash occurred. (Default: 50)
         *
         * @param logcatSystemLines The maximum number of rows.
         * @return The InitParameters object.
         */
        @SuppressWarnings("unused")
        public InitParameters setNativeLogcatSystemLines(int logcatSystemLines) {
            this.nativeLogcatSystemLines = logcatSystemLines;
            return this;
        }

        /**
         * Set the maximum number of rows to get from "logcat -b events" when a native crash occurred. (Default: 50)
         *
         * @param logcatEventsLines The maximum number of rows.
         * @return The InitParameters object.
         */
        @SuppressWarnings("unused")
        public InitParameters setNativeLogcatEventsLines(int logcatEventsLines) {
            this.nativeLogcatEventsLines = logcatEventsLines;
            return this;
        }

        /**
         * Set the maximum number of rows to get from "logcat -b main" when a native crash occurred. (Default: 200)
         *
         * @param logcatMainLines The maximum number of rows.
         * @return The InitParameters object.
         */
        @SuppressWarnings("unused")
        public InitParameters setNativeLogcatMainLines(int logcatMainLines) {
            this.nativeLogcatMainLines = logcatMainLines;
            return this;
        }

        /**
         * Set if dumping ELF file's MD5 hash in Build-Id section when a native crash occurred. (Default: enable)
         *
         * @param flag True or false.
         * @return The InitParameters object.
         */
        @SuppressWarnings("unused")
        public InitParameters setNativeDumpElfHash(boolean flag) {
            this.nativeDumpElfHash = flag;
            return this;
        }

        /**
         * Set if dumping memory map when a native crash occurred. (Default: enable)
         *
         * @param flag True or false.
         * @return The InitParameters object.
         */
        @SuppressWarnings("unused")
        public InitParameters setNativeDumpMap(boolean flag) {
            this.nativeDumpMap = flag;
            return this;
        }

        /**
         * Set if dumping FD list when a native crash occurred. (Default: enable)
         *
         * @param flag True or false.
         * @return The InitParameters object.
         */
        @SuppressWarnings("unused")
        public InitParameters setNativeDumpFds(boolean flag) {
            this.nativeDumpFds = flag;
            return this;
        }

        /**
         * Set if dumping threads info (registers, backtrace and stack) for all threads (not just the thread that has crashed)
         * when a native crash occurred. (Default: enable)
         *
         * @param flag True or false.
         * @return The InitParameters object.
         */
        @SuppressWarnings({"unused"})
        public InitParameters setNativeDumpAllThreads(boolean flag) {
            this.nativeDumpAllThreads = flag;
            return this;
        }

        /**
         * Set the maximum number of other threads to dump when a native crash occurred.
         * "0" means no limit. (Default: 0)
         *
         * <p>Note: This option is only useful when "NativeDumpAllThreads" is enabled by calling {@link InitParameters#setNativeDumpAllThreads(boolean)}.
         *
         * @param countMax The maximum number of other threads to dump.
         * @return The InitParameters object.
         */
        @SuppressWarnings("unused")
        public InitParameters setNativeDumpAllThreadsCountMax(int countMax) {
            this.nativeDumpAllThreadsCountMax = (countMax < 0 ? 0 : countMax);
            return this;
        }

        /**
         * Set a thread name (regular expression) whitelist to filter which threads need to be dumped when a native crash occurred.
         * "null" means no filtering. (Default: null)
         *
         * <p>Note: This option is only useful when "NativeDumpAllThreads" is enabled by calling {@link InitParameters#setNativeDumpAllThreads(boolean)}.
         *
         * <p>Warning: The regular expression used here only supports POSIX ERE (Extended Regular Expression).
         * Android bionic's regular expression is different from Linux libc's regular expression.
         * See: https://android.googlesource.com/platform/bionic/+/refs/heads/master/libc/include/regex.h .
         *
         * @param whiteList A thread name (regular expression) whitelist.
         * @return The InitParameters object.
         */
        @SuppressWarnings("unused")
        public InitParameters setNativeDumpAllThreadsWhiteList(String[] whiteList) {
            this.nativeDumpAllThreadsWhiteList = whiteList;
            return this;
        }

        /**
         * Set a callback to be executed when a native crash occurred. (If not set, nothing will be happened.)
         *
         * @param callback An instance of {@link xcrash.ICrashCallback}.
         * @return The InitParameters object.
         */
        @SuppressWarnings("unused")
        public InitParameters setNativeCallback(ICrashCallback callback) {
            this.nativeCallback = callback;
            return this;
        }
    }

    static String getAppId() {
        return appId;
    }

    static String getAppVersion() {
        return appVersion;
    }

    static String getLogDir() {
        return logDir;
    }

    static ILogger getLogger() {
        return logger;
    }

    /**
     * Force a java exception.
     *
     * <p>Warning: This method is for testing purposes only. Don't call it in a release version of your APP.
     *
     * @param runInNewThread Whether it is triggered in the current thread.
     * @throws RuntimeException This exception will terminate current process.
     */
    @SuppressWarnings("unused")
    public static void testJavaCrash(boolean runInNewThread) throws RuntimeException {
        if (runInNewThread) {
            Thread thread = new Thread() {
                @Override
                public void run() {
                    throw new RuntimeException("test java exception");
                }
            };
            thread.setName("xcrash_test_java_thread");
            thread.start();
        } else {
            throw new RuntimeException("test java exception");
        }
    }

    /**
     * Force a native crash.
     *
     * <p>Warning: This method is for testing purposes only. Don't call it in a release version of your APP.
     *
     * @param runInNewThread Whether it is triggered in the current thread.
     */
    @SuppressWarnings("unused")
    public static void testNativeCrash(boolean runInNewThread) {
        NativeCrashHandler.getInstance().testNativeCrash(runInNewThread);
    }
}
