package com.github.miao1007.animewallpaper.ui.activity;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.UiThread;
import android.support.annotation.WorkerThread;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import butterknife.Bind;
import butterknife.ButterKnife;
import com.github.miao1007.animewallpaper.R;
import com.github.miao1007.animewallpaper.support.api.konachan.DanbooruAPI;
import com.github.miao1007.animewallpaper.support.api.konachan.Tag;
import com.github.miao1007.animewallpaper.ui.widget.SearchBar;
import com.github.miao1007.animewallpaper.utils.LogUtils;
import com.github.miao1007.animewallpaper.utils.SquareUtils;
import com.github.miao1007.animewallpaper.utils.StatusbarUtils;
import com.jakewharton.rxbinding.widget.RxTextView;
import im.fir.sdk.FIR;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import rx.Observable;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.functions.Func2;
import rx.schedulers.Schedulers;

public class SearchActivity extends AppCompatActivity {

  static final String TAG = LogUtils.makeLogTag(SearchActivity.class);

  @Bind(R.id.search_bar)  SearchBar mSearchbar;
  @Bind(R.id.search_list)  ListView mSearchListView;
  private final DanbooruAPI repo = SquareUtils.getRetrofit(DanbooruAPI.KONACHAN).create(DanbooruAPI.class);
  @Bind(R.id.internal_search_progress)  ProgressBar progressBar;

  @Override protected void onCreate(final Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_search);
    ButterKnife.bind(this);
    StatusbarUtils.from(this)
        .setActionbarView(mSearchbar)
        .setTransparentStatusbar(true)
        .setLightStatusBar(true)
        .process();
    final ArrayList<Tag> arrayList = new ArrayList<>();
    final ResultAdapter adapter = new ResultAdapter(this, arrayList);
    mSearchListView.setAdapter(adapter);
    mSearchListView.post(new Runnable() {
      @Override public void run() {
        mSearchListView.setPadding(0,
            mSearchbar.getHeight() + StatusbarUtils.getStatusBarOffsetPx(getApplicationContext()),
            0, 0);
      }
    });
    mSearchListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
      @Override public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        MainActivity.startRefreshActivity(SearchActivity.this, arrayList.get(position).getName());
      }
    });
    //mSearchbar.toggle(true);
    mSearchbar.setOnButton(new Runnable() {
      @Override public void run() {
        finish();
      }
    });
    /**
     * Port from {@link https://github.com/ReactiveX/RxSwift}
     */
    RxTextView.textChanges(mSearchbar.getEditTextSearch())
        .subscribeOn(AndroidSchedulers.mainThread())
        //delay 500ms
        //debounce and throttle will use different thread after
        .throttleWithTimeout(300, TimeUnit.MILLISECONDS, AndroidSchedulers.mainThread())
        .distinct()
        .filter(new Func1<CharSequence, Boolean>() {
          @UiThread @Override public Boolean call(CharSequence charSequence) {
            //void unnecessary request
            return charSequence.length() != 0;
          }
        })
        .map(new Func1<CharSequence, String>() {
          @UiThread @Override public String call(CharSequence charSequence) {
            //fit network api doc require
            return charSequence + "*";
          }
        })
        .doOnNext(new Action1<CharSequence>() {
          @UiThread @Override public void call(CharSequence charSequence) {
            progressBar.setVisibility(View.VISIBLE);
            arrayList.clear();
            adapter.notifyDataSetChanged();
          }
        })
        .observeOn(Schedulers.io())
        .switchMap(new Func1<String, Observable<List<Tag>>>() {
          @WorkerThread @Override public Observable<List<Tag>> call(String s) {
            return repo.getTags(20, s);
          }
        })
        .retry(new Func2<Integer, Throwable, Boolean>() {
          //fix InterruptedIOException bugs on Retrofit
          // when stop old search
          @WorkerThread @Override public Boolean call(Integer integer, Throwable throwable) {
            return throwable instanceof InterruptedIOException;
          }
        })
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(new Subscriber<List<Tag>>() {
          @Override public void onCompleted() {

          }

          @Override public void onError(Throwable e) {
            FIR.sendCrashManually(e);
            progressBar.setVisibility(View.INVISIBLE);
            e.printStackTrace();
            Toast.makeText(SearchActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
          }

          @Override public void onNext(List<Tag> tags) {
            progressBar.setVisibility(View.INVISIBLE);

            arrayList.clear();
            arrayList.addAll(tags);
            adapter.notifyDataSetChanged();
          }
        });
  }

  @Override protected void onPause() {
    super.onPause();
    if (isFinishing()) {
      overridePendingTransition(0, 0);
    }
  }

  static class ResultAdapter extends BaseAdapter {

    final Context context;
    final List<Tag> tags;

    public ResultAdapter(Context context, List<Tag> tags) {
      this.context = context;
      this.tags = tags;
    }

    @Override public int getCount() {
      return tags.size();
    }

    @Override public Object getItem(int position) {
      return tags.get(position);
    }

    @Override public long getItemId(int position) {
      return position;
    }

    @Override public View getView(int position, View convertView, ViewGroup parent) {
      final ViewHolder holder;

      if (convertView == null) {
        holder = new ViewHolder();
        convertView =
            LayoutInflater.from(context).inflate(android.R.layout.simple_list_item_1, null, false);
        holder.textView = ((TextView) convertView.findViewById(android.R.id.text1));
        convertView.setTag(holder);
      } else {
        holder = (ViewHolder) convertView.getTag();
      }
      holder.textView.setText(tags.get(position).getName());
      return convertView;
    }

    static final class ViewHolder {

      TextView textView;
    }
  }
}
