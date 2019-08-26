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

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.os.Debug;
import android.text.TextUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static android.content.Context.ACTIVITY_SERVICE;

class Util {

    private Util() {
    }

    private static final String memInfoFmt = "%21s %8s\n";
    private static final String memInfoFmt2 = "%21s %8s %21s %8s\n";

    static final String TAG = "xcrash_" + Version.version;

    static final String sepHead = "*** *** *** *** *** *** *** *** *** *** *** *** *** *** *** ***";
    static final String sepOtherThreads = "--- --- --- --- --- --- --- --- --- --- --- --- --- --- --- ---";
    static final String sepOtherThreadsEnding = "+++ +++ +++ +++ +++ +++ +++ +++ +++ +++ +++ +++ +++ +++ +++ +++";

    static final String timeFormatterStr = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";

    static final String javaCrashType = "java";
    static final String nativeCrashType = "native";

    static final String logPrefix = "tombstone";
    static final String javaLogSuffix = ".java.xcrash";
    static final String nativeLogSuffix = ".native.xcrash";

    static String readFileLine(String path) {
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(path), 1024);
            return br.readLine().trim();
        } catch (IOException ignored) {
            return "unknown";
        } finally {
            try {
                if (br != null) br.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static final Pattern patMemTotal = Pattern.compile("^MemTotal:\\s+(\\d*)\\s+kB$");
    private static final Pattern patMemFree = Pattern.compile("^MemFree:\\s+(\\d*)\\s+kB$");
    private static final Pattern patBuffers = Pattern.compile("^Buffers:\\s+(\\d*)\\s+kB$");
    private static final Pattern patCached = Pattern.compile("^Cached:\\s+(\\d*)\\s+kB$");

    static class SystemMemoryInfo {
        SystemMemoryInfo() {
            totalKb = 0;
            usedKb = 0;
        }
        long totalKb;
        long usedKb;
    }

    static SystemMemoryInfo getSystemMemoryInfo(Context ctx) {
        Util.SystemMemoryInfo memoryInfo = new Util.SystemMemoryInfo();

        //get system total and used memory
        if (android.os.Build.VERSION.SDK_INT >= 16) {
            try {
                ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                ActivityManager activityManager = (ActivityManager) ctx.getSystemService(ACTIVITY_SERVICE);
                if (activityManager != null) {
                    activityManager.getMemoryInfo(mi);
                    memoryInfo.totalKb = mi.totalMem / 1024;
                    memoryInfo.usedKb = (mi.totalMem - mi.availMem) / 1024;
                }
            } catch (Exception ignored) {
            }
        }
        if (memoryInfo.totalKb == 0 || memoryInfo.usedKb == 0) {
            BufferedReader br = null;
            String line;
            Matcher matcher;
            long memTotal = 0;
            long memFree = 0;
            long memBuffers = 0;
            long memCached = 0;
            try {
                br = new BufferedReader(new FileReader("/proc/meminfo"));
                while (null != (line = br.readLine())) {
                    matcher = patMemTotal.matcher(line);
                    if (matcher.find() && matcher.groupCount() == 1) {
                        memTotal = Long.parseLong(matcher.group(1), 10);
                        continue;
                    }
                    matcher = patMemFree.matcher(line);
                    if (matcher.find() && matcher.groupCount() == 1) {
                        memFree = Long.parseLong(matcher.group(1), 10);
                        continue;
                    }
                    matcher = patBuffers.matcher(line);
                    if (matcher.find() && matcher.groupCount() == 1) {
                        memBuffers = Long.parseLong(matcher.group(1), 10);
                        continue;
                    }
                    matcher = patCached.matcher(line);
                    if (matcher.find() && matcher.groupCount() == 1) {
                        memCached = Long.parseLong(matcher.group(1), 10);
                    }
                }
                memoryInfo.totalKb = memTotal;
                memoryInfo.usedKb = memTotal - memFree - memBuffers - memCached;
            } catch (Exception e) {
                memoryInfo.totalKb = 0;
                memoryInfo.usedKb = 0;
                XCrash.getLogger().i(Util.TAG, "Util getSystemMemoryInfo failed", e);
            } finally {
                if (br != null) {
                    try {
                        br.close();
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        return memoryInfo;
    }

    static int getNumberOfThreads(int pid) {
        try {
            File dir = new File("/proc/" + pid + "/task");
            File[] files = dir.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return android.text.TextUtils.isDigitsOnly(name);
                }
            });
            return files == null ? 0 : files.length;
        } catch (Exception ignored) {
            return 0;
        }
    }

    static String getProcessName(Context ctx, int pid) {
        try {
            ActivityManager manager = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            if (manager != null) {
                for (ActivityManager.RunningAppProcessInfo processInfo : manager.getRunningAppProcesses()) {
                    if (processInfo.pid == pid) {
                        return processInfo.processName;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "unknown";
    }

    private static final String[] suPathname = {
        "/data/local/su",
        "/data/local/bin/su",
        "/data/local/xbin/su",
        "/system/xbin/su",
        "/system/bin/su",
        "/system/bin/.ext/su",
        "/system/bin/failsafe/su",
        "/system/sd/xbin/su",
        "/system/usr/we-need-root/su",
        "/sbin/su",
        "/su/bin/su"};

    static boolean isRoot() {
        try {
            for (String path : suPathname) {
                File file = new File(path);
                if (file.exists()) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    @SuppressWarnings("deprecation")
    static String getAbiList() {
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            return android.text.TextUtils.join(",", Build.SUPPORTED_ABIS);
        } else {
            String abi = Build.CPU_ABI;
            String abi2 = Build.CPU_ABI2;
            if (TextUtils.isEmpty(abi2)) {
                return abi;
            } else {
                return abi + "," + abi2;
            }
        }
    }

    static String getAppVersion(Context ctx) {
        String appVersion = null;

        try {
            appVersion = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionName;
        } catch (Exception ignored) {
        }

        if (TextUtils.isEmpty(appVersion)) {
            appVersion = "unknown";
        }

        return appVersion;
    }

    static String getProcessMemoryInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append(" Process Summary (From: android.os.Debug.MemoryInfo)\n");
        sb.append(String.format(Locale.US, memInfoFmt, "", "Pss(KB)"));
        sb.append(String.format(Locale.US, memInfoFmt, "", "------"));

        try {
            Debug.MemoryInfo mi = new Debug.MemoryInfo();
            Debug.getMemoryInfo(mi);

            if (Build.VERSION.SDK_INT >= 23) {
                sb.append(String.format(Locale.US, memInfoFmt, "Java Heap:", mi.getMemoryStat("summary.java-heap")));
                sb.append(String.format(Locale.US, memInfoFmt, "Native Heap:", mi.getMemoryStat("summary.native-heap")));
                sb.append(String.format(Locale.US, memInfoFmt, "Code:", mi.getMemoryStat("summary.code")));
                sb.append(String.format(Locale.US, memInfoFmt, "Stack:", mi.getMemoryStat("summary.stack")));
                sb.append(String.format(Locale.US, memInfoFmt, "Graphics:", mi.getMemoryStat("summary.graphics")));
                sb.append(String.format(Locale.US, memInfoFmt, "Private Other:", mi.getMemoryStat("summary.private-other")));
                sb.append(String.format(Locale.US, memInfoFmt, "System:", mi.getMemoryStat("summary.system")));
                sb.append(String.format(Locale.US, memInfoFmt2, "TOTAL:", mi.getMemoryStat("summary.total-pss"), "TOTAL SWAP:", mi.getMemoryStat("summary.total-swap")));
            } else {
                sb.append(String.format(Locale.US, memInfoFmt, "Java Heap:", "~ " + mi.dalvikPrivateDirty));
                sb.append(String.format(Locale.US, memInfoFmt, "Native Heap:", String.valueOf(mi.nativePrivateDirty)));
                sb.append(String.format(Locale.US, memInfoFmt, "Private Other:", "~ " + mi.otherPrivateDirty));
                if (Build.VERSION.SDK_INT >= 19) {
                    sb.append(String.format(Locale.US, memInfoFmt, "System:", String.valueOf(mi.getTotalPss() - mi.getTotalPrivateDirty() - mi.getTotalPrivateClean())));
                } else {
                    sb.append(String.format(Locale.US, memInfoFmt, "System:", "~ " + (mi.getTotalPss() - mi.getTotalPrivateDirty())));
                }
                sb.append(String.format(Locale.US, memInfoFmt, "TOTAL:", String.valueOf(mi.getTotalPss())));
            }
        } catch (Exception e) {
            XCrash.getLogger().i(Util.TAG, "Util getProcessMemoryInfo failed", e);
        }

        return sb.toString();
    }

    @SuppressWarnings({"ResultOfMethodCallIgnored", "BooleanMethodIsAlwaysInverted"})
    static boolean checkAndCreateDir(String path) {
        File dir = new File(path);
        try {
            if (!dir.exists()) {
                dir.mkdirs();
                return dir.exists() && dir.isDirectory();
            } else {
                return dir.isDirectory();
            }
        } catch (Exception ignored) {
            return false;
        }
    }
}
