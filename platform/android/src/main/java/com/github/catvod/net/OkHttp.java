package com.github.catvod.net;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;

import okhttp3.Call;
import okhttp3.FormBody;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * OkHttp 工具类
 *
 * 迁移自 TVBoxOS com.github.catvod.net.OkHttp
 * 为 Jar 加载的 Spider 爬虫提供统一的 HTTP 请求入口。
 * 信任所有 SSL 证书，支持自定义 DNS 和超时。
 */
public class OkHttp {

    private static final long TIMEOUT = TimeUnit.SECONDS.toMillis(30);
    private static OkDns dns;
    private static OkHttpClient client;

    public static synchronized OkDns dns() {
        if (dns == null) dns = new OkDns();
        return dns;
    }

    public static synchronized OkHttpClient client() {
        if (client != null) return client;
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .dns(dns())
                .connectTimeout(TIMEOUT, TimeUnit.MILLISECONDS)
                .readTimeout(TIMEOUT, TimeUnit.MILLISECONDS)
                .writeTimeout(TIMEOUT, TimeUnit.MILLISECONDS);
        setOkHttpSsl(builder);
        return client = builder.build();
    }

    public static OkHttpClient player() {
        return client();
    }

    public static OkHttpClient client(long timeout) {
        return client().newBuilder()
                .connectTimeout(timeout, TimeUnit.MILLISECONDS)
                .readTimeout(timeout, TimeUnit.MILLISECONDS)
                .writeTimeout(timeout, TimeUnit.MILLISECONDS)
                .build();
    }

    public static OkHttpClient noRedirect() {
        return noRedirect(TIMEOUT);
    }

    public static OkHttpClient noRedirect(long timeout) {
        return client().newBuilder()
                .dns(dns())
                .connectTimeout(timeout, TimeUnit.MILLISECONDS)
                .readTimeout(timeout, TimeUnit.MILLISECONDS)
                .writeTimeout(timeout, TimeUnit.MILLISECONDS)
                .followRedirects(false)
                .followSslRedirects(false)
                .build();
    }

    public static OkHttpClient client(boolean redirect, long timeout) {
        return redirect ? client(timeout) : noRedirect(timeout);
    }

    public static synchronized void reset() {
        client = null;
        dns = null;
    }

    public static synchronized void resetClient() {
        client = null;
    }

    // ============ 便捷请求方法 ============

    public static String string(String url) {
        if (url == null || !url.startsWith("http")) return "";
        try (Response res = newCall(url).execute()) {
            return res.body() != null ? res.body().string() : "";
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    public static String string(String url, long timeout) {
        if (url == null || !url.startsWith("http")) return "";
        try (Response res = newCall(client(timeout), url).execute()) {
            return res.body() != null ? res.body().string() : "";
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    public static String string(String url, Map<String, String> headers) {
        if (url == null || !url.startsWith("http")) return "";
        try (Response res = newCall(url, headers).execute()) {
            return res.body() != null ? res.body().string() : "";
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    public static byte[] bytes(String url) {
        if (url == null || !url.startsWith("http")) return new byte[0];
        try (Response res = newCall(url).execute()) {
            return res.body() != null ? res.body().bytes() : new byte[0];
        } catch (Exception e) {
            e.printStackTrace();
            return new byte[0];
        }
    }

    public static byte[] bytes(String url, Map<String, String> headers) {
        if (url == null || !url.startsWith("http")) return new byte[0];
        try (Response res = newCall(url, headers).execute()) {
            return res.body() != null ? res.body().bytes() : new byte[0];
        } catch (Exception e) {
            e.printStackTrace();
            return new byte[0];
        }
    }

    // ============ Call 构建 ============

    public static Call newCall(String url) {
        return client().newCall(new Request.Builder().url(url).build());
    }

    public static Call newCall(String url, String tag) {
        return client().newCall(new Request.Builder().url(url).tag(tag).build());
    }

    public static Call newCall(OkHttpClient client, String url) {
        return client.newCall(new Request.Builder().url(url).build());
    }

    public static Call newCall(OkHttpClient client, String url, String tag) {
        return client.newCall(new Request.Builder().url(url).tag(tag).build());
    }

    public static Call newCall(String url, Map<String, String> headers) {
        return client().newCall(new Request.Builder().url(url).headers(headers(headers)).build());
    }

    public static Call newCall(String url, Map<String, String> headers, RequestBody body) {
        return client().newCall(new Request.Builder().url(url).headers(headers(headers)).post(body).build());
    }

    public static Call newCall(String url, RequestBody body, String tag) {
        return client().newCall(new Request.Builder().url(url).post(body).tag(tag).build());
    }

    public static Call newCall(OkHttpClient client, String url, RequestBody body) {
        return client.newCall(new Request.Builder().url(url).post(body).build());
    }

    // ============ POST 便捷方法 ============

    public static String post(String url, Map<String, String> headers, Map<String, String> params) {
        FormBody.Builder body = new FormBody.Builder();
        if (params != null) {
            for (Map.Entry<String, String> entry : params.entrySet()) {
                body.add(entry.getKey(), entry.getValue());
            }
        }
        try (Response res = newCall(url, headers, body.build()).execute()) {
            return res.body() != null ? res.body().string() : "";
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    public static String postJson(String url, Map<String, String> headers, String json) {
        RequestBody body = RequestBody.create(json, MediaType.parse("application/json; charset=utf-8"));
        try (Response res = newCall(url, headers, body).execute()) {
            return res.body() != null ? res.body().string() : "";
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    // ============ 取消请求 ============

    public static void cancel(String tag) {
        cancel(client(), tag);
    }

    public static void cancel(OkHttpClient client, String tag) {
        if (client == null || tag == null) return;
        for (Call call : client.dispatcher().queuedCalls()) if (tag.equals(call.request().tag())) call.cancel();
        for (Call call : client.dispatcher().runningCalls()) if (tag.equals(call.request().tag())) call.cancel();
    }

    public static void cancelAll() {
        cancelAll(client());
    }

    public static void cancelAll(OkHttpClient client) {
        if (client != null) client.dispatcher().cancelAll();
    }

    // ============ 工具方法 ============

    public static FormBody toBody(Map<String, String> params) {
        FormBody.Builder body = new FormBody.Builder();
        if (params != null) for (Map.Entry<String, String> entry : params.entrySet()) body.add(entry.getKey(), entry.getValue());
        return body.build();
    }

    private static Headers headers(Map<String, String> headers) {
        return headers == null ? new Headers.Builder().build() : Headers.of(headers);
    }

    private static void setOkHttpSsl(OkHttpClient.Builder builder) {
        try {
            SSLSocketFactory sslSocketFactory = new TrustAllSslSocketFactory();
            builder.sslSocketFactory(sslSocketFactory, TRUST_ALL_CERT);
            builder.hostnameVerifier((hostname, session) -> true);
        } catch (Throwable ignored) {
        }
    }

    private static final X509TrustManager TRUST_ALL_CERT = new X509TrustManager() {
        @Override
        public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {
        }

        @Override
        public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {
        }

        @Override
        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
            return new java.security.cert.X509Certificate[]{};
        }
    };

    /**
     * 信任所有证书的 SSLSocketFactory
     */
    private static class TrustAllSslSocketFactory extends SSLSocketFactory {
        private final javax.net.ssl.SSLSocketFactory factory;

        TrustAllSslSocketFactory() throws Exception {
            javax.net.ssl.SSLContext sslContext = javax.net.ssl.SSLContext.getInstance("TLS");
            sslContext.init(null, new javax.net.ssl.TrustManager[]{TRUST_ALL_CERT}, new java.security.SecureRandom());
            this.factory = sslContext.getSocketFactory();
        }

        @Override
        public java.net.Socket createSocket(java.net.Socket s, String host, int port, boolean autoClose) throws java.io.IOException {
            return factory.createSocket(s, host, port, autoClose);
        }

        @Override
        public java.net.Socket createSocket(String host, int port) throws java.io.IOException {
            return factory.createSocket(host, port);
        }

        @Override
        public java.net.Socket createSocket(String host, int port, java.net.InetAddress localHost, int localPort) throws java.io.IOException {
            return factory.createSocket(host, port, localHost, localPort);
        }

        @Override
        public java.net.Socket createSocket(java.net.InetAddress host, int port) throws java.io.IOException {
            return factory.createSocket(host, port);
        }

        @Override
        public java.net.Socket createSocket(java.net.InetAddress address, int port, java.net.InetAddress localAddress, int localPort) throws java.io.IOException {
            return factory.createSocket(address, port, localAddress, localPort);
        }

        @Override
        public String[] getDefaultCipherSuites() {
            return factory.getDefaultCipherSuites();
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return factory.getSupportedCipherSuites();
        }
    }
}
