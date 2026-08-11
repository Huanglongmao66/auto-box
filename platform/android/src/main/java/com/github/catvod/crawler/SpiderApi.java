package com.github.catvod.crawler;

/**
 * Spider API 辅助类
 *
 * 迁移自 TVBoxOS com.github.catvod.crawler.SpiderApi
 * 为 Jar 加载的爬虫提供内部 API 回调入口。
 */
public class SpiderApi {

    private Object api;

    public void setApi(Object api) {
        this.api = api;
    }

    public Object getApi() {
        return api;
    }
}
