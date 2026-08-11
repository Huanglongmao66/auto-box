package com.github.catvod.crawler;

import android.content.Context;

import java.util.HashMap;
import java.util.List;

/**
 * Spider 空实现
 *
 * 迁移自 TVBoxOS com.github.catvod.crawler.SpiderNull
 * Jar/JS 爬虫加载失败时返回此实例，所有方法返回空值。
 */
public class SpiderNull extends Spider {

    @Override
    public void init(Context context, String extend) {
    }

    @Override
    public String homeContent(boolean filter) {
        return "";
    }

    @Override
    public String homeVideoContent() {
        return "";
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        return "";
    }

    @Override
    public String detailContent(List<String> ids) {
        return "";
    }

    @Override
    public String searchContent(String key, boolean quick) {
        return "";
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        return "";
    }

    @Override
    public boolean isVideoFormat(String url) {
        return false;
    }

    @Override
    public boolean manualVideoCheck() {
        return false;
    }

    @Override
    public String liveContent(String url) {
        return "";
    }

    @Override
    public void cancelByTag() {
    }

    @Override
    public void destroy() {
    }
}
