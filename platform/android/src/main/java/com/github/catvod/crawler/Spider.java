package com.github.catvod.crawler;

import android.content.Context;

import com.github.catvod.net.OkHttp;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.Dns;
import okhttp3.OkHttpClient;

/**
 * Spider 爬虫基类
 *
 * 迁移自 TVBoxOS com.github.catvod.crawler.Spider
 * Jar 加载的爬虫类必须继承此类，运行时通过 DexClassLoader 加载后
 * 由 JarSpiderLoader 实例化并调用。
 */
public abstract class Spider {

    public static JSONObject empty = new JSONObject();
    public String siteKey;

    protected static Context mContext;

    public void init(Context context) {
        mContext = context;
    }

    public void init(Context context, String extend) {
        init(context);
    }

    public void initApi(SpiderApi api) {
    }

    public String homeContent(boolean filter) {
        return "";
    }

    public String homeVideoContent() {
        return "";
    }

    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        return "";
    }

    public String detailContent(List<String> ids) {
        return "";
    }

    public String searchContent(String key, boolean quick) {
        return "";
    }

    public String searchContent(String key, boolean quick, String pg) {
        return searchContent(key, quick);
    }

    public String playerContent(String flag, String id, List<String> vipFlags) {
        return "";
    }

    public boolean isVideoFormat(String url) {
        return false;
    }

    public boolean manualVideoCheck() {
        return false;
    }

    public String liveContent(String url) {
        return "";
    }

    public static Dns safeDns() {
        return OkHttp.dns();
    }

    public static OkHttpClient client() {
        return OkHttp.client();
    }

    public void cancelByTag() {
    }

    public void destroy() {}

    public Object[] proxyLocal(Map<String, String> params) {
        return null;
    }

    public Object[] proxy(Map<String, String> params) {
        return proxyLocal(params);
    }

    public String action(String action) {
        return null;
    }
}
