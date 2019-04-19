package com.danikula.videocache;

import android.text.TextUtils;
import android.util.Log;

import com.danikula.videocache.headers.EmptyHeadersInjector;
import com.danikula.videocache.headers.HeaderInjector;
import com.danikula.videocache.sourcestorage.SourceInfoStorage;
import com.danikula.videocache.sourcestorage.SourceInfoStorageFactory;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static com.danikula.videocache.Preconditions.checkNotNull;
import static com.danikula.videocache.ProxyCacheUtils.DEFAULT_BUFFER_SIZE;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.net.HttpURLConnection.HTTP_PARTIAL;

/**
 * Created by Administrator_ma on 2019/4/18.
 */
public class OkHttpSource implements Source {

    private final static String TAG = "okHttp";

    private static final int MAX_REDIRECTS = 5;
    private final SourceInfoStorage sourceInfoStorage;
    private final HeaderInjector headerInjector;
    private SourceInfo sourceInfo;
    private InputStream inputStream;

    private OkHttpClient okHttpClient;
    private Call requestCall;

    public OkHttpSource(OkHttpClient okHttpClient, String url) {
        this(okHttpClient, url, SourceInfoStorageFactory.newEmptySourceInfoStorage());
    }

    public OkHttpSource(OkHttpClient okHttpClient, String url, SourceInfoStorage sourceInfoStorage) {
        this(okHttpClient, url, sourceInfoStorage, new EmptyHeadersInjector());
    }

    public OkHttpSource(OkHttpClient okHttpClient, String url, SourceInfoStorage sourceInfoStorage, HeaderInjector headerInjector) {
        this.okHttpClient = okHttpClient;
        this.sourceInfoStorage = checkNotNull(sourceInfoStorage);
        this.headerInjector = checkNotNull(headerInjector);
        SourceInfo sourceInfo = sourceInfoStorage.get(url);
        this.sourceInfo = sourceInfo != null ? sourceInfo :
                new SourceInfo(url, Integer.MIN_VALUE, ProxyCacheUtils.getSupposablyMime(url));
    }

    public OkHttpSource(OkHttpSource source) {
        this.okHttpClient = source.okHttpClient;
        this.sourceInfo = source.sourceInfo;
        this.sourceInfoStorage = source.sourceInfoStorage;
        this.headerInjector = source.headerInjector;
    }

    @Override
    public void open(long offset) throws ProxyCacheException {
        try {
            Response response = openConnection(offset);
            String mime = response.header("Content-Type");
            long length = readSourceAvailableBytes(response, offset);
            ResponseBody body = response.body();
            if (body != null) {
                inputStream = new BufferedInputStream(body.byteStream(), DEFAULT_BUFFER_SIZE);
            }
            sourceInfo = new SourceInfo(sourceInfo.url, length, mime);
            sourceInfoStorage.put(sourceInfo.url, sourceInfo);
        } catch (IOException e) {
            throw new ProxyCacheException("Error opening okHttpClient for " + sourceInfo.url + " with offset " + offset, e);
        }
    }

    @Override
    public long length() throws ProxyCacheException {
        if (sourceInfo.length == Integer.MAX_VALUE) {
            fetchContentInfo();
        }
        return sourceInfo.length;
    }

    @Override
    public int read(byte[] buffer) throws ProxyCacheException {
        if (inputStream == null) {
            throw new ProxyCacheException("Error reading data from " + sourceInfo.url + ": okHttpClient is absent!");
        }
        try {
            return inputStream.read(buffer, 0, buffer.length);
        } catch (InterruptedIOException e) {
            throw new InterruptedProxyCacheException("Reading source " + sourceInfo.url + " is interrupted", e);
        } catch (IOException e) {
            throw new ProxyCacheException("Error reading data from " + sourceInfo.url, e);
        }
    }

    @Override
    public void close() throws ProxyCacheException {
        if (okHttpClient != null && inputStream != null && requestCall != null) {
            ProxyCacheUtils.close(inputStream);
            requestCall.cancel();
        }
    }

    private Response openConnection(long offset) throws ProxyCacheException, IOException {

        Response response;
        String newUrl = sourceInfo.url;
        boolean isRedirect;
        int redirectCount = 0;

        do {
            Request.Builder build = new Request.Builder().get().url(newUrl);
            if (offset > 0) {
                build.addHeader("Range", "bytes=" + offset + "-");
            }
            response = (requestCall = okHttpClient.newCall(build.build())).execute();
            if (isRedirect = response.isRedirect()) {
                newUrl = response.header("Location");
                redirectCount++;
            }
            if (redirectCount > MAX_REDIRECTS) {
                throw new ProxyCacheException("Too many redirects: " + redirectCount);
            }
        } while (isRedirect && redirectCount < MAX_REDIRECTS);
        return response;
    }

    private Response openConnectionForHeader() throws ProxyCacheException, IOException {

        Response response;
        String newUrl = sourceInfo.url;
        boolean isRedirect;
        int redirectCount = 0;

        do {
            Request.Builder build = new Request.Builder().head().get().url(newUrl);
            response = (requestCall = okHttpClient.newCall(build.build())).execute();
            if (isRedirect = response.isRedirect()) {
                newUrl = response.header("Location");
                redirectCount++;
                requestCall.cancel();
            }
            if (redirectCount > MAX_REDIRECTS) {
                throw new ProxyCacheException("Too many redirects: " + redirectCount);
            }
        } while (isRedirect && redirectCount < MAX_REDIRECTS);
        return response;
    }

    private long readSourceAvailableBytes(Response response, long offset) throws IOException {
        int responseCode = response.code();
        String contentLength = response.header("Content-Length");
        long length = contentLength == null ? -1 : Long.parseLong(contentLength);
        return responseCode == HTTP_OK ? length
                : responseCode == HTTP_PARTIAL ? length + offset : sourceInfo.length;
    }

    //获取头，更新视频的基本信息，如视频长度
    private void fetchContentInfo() throws ProxyCacheException {
        Response response = null;
//        InputStream inputStream = null;
        try {
            response = openConnectionForHeader();
            if (response == null || !response.isSuccessful()) {
                throw new ProxyCacheException("Fail to fetchContentInfo: " + sourceInfo.url);
            }
            String contentLength = response.header("Content-Length");
            long length = contentLength == null ? -1 : Long.parseLong(contentLength);
            String mime = response.header("Content-Type", "application/mp4");
//            inputStream = response.body().byteStream();
            this.sourceInfo = new SourceInfo(sourceInfo.url, length, mime);
            this.sourceInfoStorage.put(sourceInfo.url, sourceInfo);
            Log.i(TAG, "Content info for `" + sourceInfo.url + "`: mime: " + mime + ", content-length: " + length);
        } catch (IOException e) {
            Log.e(TAG, "Error fetching info from " + sourceInfo.url, e);
        } finally {
//            ProxyCacheUtils.close(inputStream);
            if (response != null && requestCall != null) {
                requestCall.cancel();
            }
        }
    }

    @Override
    public synchronized String getMime() throws ProxyCacheException {
        if (TextUtils.isEmpty(sourceInfo.mime)) {
            fetchContentInfo();
        }
        return sourceInfo.mime;
    }

    @Override
    public String getUrl() {
        return sourceInfo.url;
    }

    @Override
    public String toString() {
        return "OkHttpSource{sourceInfo='" + sourceInfo + "}";
    }

    @Override
    public Source copy() {
        return new OkHttpSource(this);
    }
}
