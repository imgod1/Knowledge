package com.dante.knowledge.mvp.model;


import com.dante.knowledge.mvp.interf.NewsModel;
import com.dante.knowledge.mvp.interf.OnLoadDataListener;
import com.dante.knowledge.mvp.interf.OnLoadDetailListener;
import com.dante.knowledge.net.API;
import com.dante.knowledge.net.DB;
import com.dante.knowledge.net.Json;
import com.dante.knowledge.net.Net;
import com.dante.knowledge.ui.BaseActivity;
import com.dante.knowledge.utils.Constants;
import com.dante.knowledge.utils.DateUtil;
import com.dante.knowledge.utils.SPUtil;
import com.zhy.http.okhttp.callback.Callback;

import java.util.Date;

import io.realm.Realm;
import io.realm.Sort;
import okhttp3.Call;
import okhttp3.Response;

/**
 * deals with the zhihu news' data work
 */
public class ZhihuModel implements NewsModel<ZhihuStory, ZhihuDetail> {

    private static final String TAG = "test";
    private BaseActivity mActivity;

    public ZhihuModel(BaseActivity activity) {
        mActivity = activity;
        realm = mActivity.mRealm;
    }

    private String date;
    private long lastGetTime;
    public static final int GET_DURATION = 2000;
    private int type;
    private Realm realm;

    @Override
    public void getNews(final int type, final OnLoadDataListener listener) {
        this.type = type;

        lastGetTime = System.currentTimeMillis();
        final Callback<ZhihuJson> callback = new Callback<ZhihuJson>() {
            @Override
            public ZhihuJson parseNetworkResponse(Response response) throws Exception {
                ZhihuJson zhihuJson = Json.parseZhihuNews(response.body().string());
                date = zhihuJson.getDate();
                if (type == API.TYPE_BEFORE) {
                    SPUtil.save(Constants.DATE, date);
                }
                return zhihuJson;
            }

            @Override
            public void onError(Call call, Exception e) {
                if (System.currentTimeMillis() - lastGetTime < GET_DURATION) {
                    getData(this);
                    return;
                }
                e.printStackTrace();
                listener.onFailure("load zhihu news failed");
            }

            @Override
            public void onResponse(ZhihuJson zhihuJson) {
                if (mActivity.isFinishing() ) {
                    return;
                }
                saveZhihu(zhihuJson);
                listener.onSuccess();
            }

        };

        getData(callback);
    }

    private void saveZhihu(final ZhihuJson zhihuJson) {
        if (null != zhihuJson) {
            realm.beginTransaction();
            if (type == API.TYPE_LATEST) {
                realm.where(ZhihuTop.class).findAll().clear();
            }
            realm.copyToRealmOrUpdate(zhihuJson);
            realm.commitTransaction();
            realm.where(ZhihuJson.class).findAllSorted(Constants.DATE, Sort.DESCENDING);

        }
    }

    private void getData(Callback callback) {
        if (type == API.TYPE_LATEST) {
            Net.get(API.NEWS_LATEST, callback, mActivity);

        } else if (type == API.TYPE_BEFORE) {
            date = SPUtil.get(Constants.DATE, DateUtil.parseStandardDate(new Date()));
            Net.get(API.NEWS_BEFORE + date, callback, mActivity);
        }
    }


    @Override
    public void getNewsDetail(final ZhihuStory newsItem, final OnLoadDetailListener<ZhihuDetail> listener) {

        requestData(newsItem, listener);
    }

    private void requestData(final ZhihuStory newsItem, final OnLoadDetailListener<ZhihuDetail> listener) {
        lastGetTime = System.currentTimeMillis();

        Callback<ZhihuDetail> callback = new Callback<ZhihuDetail>() {
            @Override
            public ZhihuDetail parseNetworkResponse(Response response) throws Exception {
                return Json.parseZhihuDetail(response.body().string());
            }

            @Override
            public void onError(Call call, Exception e) {
                if (System.currentTimeMillis() - lastGetTime < GET_DURATION) {
                    Net.get(API.BASE_URL + newsItem.getId(), this, API.TAG_ZHIHU);
                    return;
                }
                e.printStackTrace();
                listener.onFailure("load zhihu detail failed", e);
            }

            @Override
            public void onResponse(ZhihuDetail response) {
                DB.saveOrUpdate(mActivity.mRealm, response);
                listener.onDetailSuccess(response);
            }

        };
        Net.get(API.BASE_URL + newsItem.getId(), callback, mActivity);
    }


}
