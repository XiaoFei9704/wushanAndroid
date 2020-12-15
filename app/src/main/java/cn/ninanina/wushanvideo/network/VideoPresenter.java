package cn.ninanina.wushanvideo.network;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.RecyclerView;

import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.gms.common.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import cn.ninanina.wushanvideo.WushanApp;
import cn.ninanina.wushanvideo.adapter.CommentAdapter;
import cn.ninanina.wushanvideo.adapter.SearchResultAdapter;
import cn.ninanina.wushanvideo.adapter.SingleVideoListAdapter;
import cn.ninanina.wushanvideo.adapter.TagAdapter;
import cn.ninanina.wushanvideo.adapter.TagSuggestAdapter;
import cn.ninanina.wushanvideo.adapter.VideoListAdapter;
import cn.ninanina.wushanvideo.adapter.listener.DefaultDownloadClickListener;
import cn.ninanina.wushanvideo.adapter.listener.DefaultVideoClickListener;
import cn.ninanina.wushanvideo.adapter.listener.DefaultVideoOptionClickListener;
import cn.ninanina.wushanvideo.adapter.listener.ReplyCommentListener;
import cn.ninanina.wushanvideo.adapter.listener.TagClickListener;
import cn.ninanina.wushanvideo.model.DataHolder;
import cn.ninanina.wushanvideo.model.bean.common.Pair;
import cn.ninanina.wushanvideo.model.bean.common.ResultMsg;
import cn.ninanina.wushanvideo.model.bean.common.VideoSortBy;
import cn.ninanina.wushanvideo.model.bean.video.Comment;
import cn.ninanina.wushanvideo.model.bean.video.Playlist;
import cn.ninanina.wushanvideo.model.bean.video.Tag;
import cn.ninanina.wushanvideo.model.bean.video.ToWatch;
import cn.ninanina.wushanvideo.model.bean.video.VideoDetail;
import cn.ninanina.wushanvideo.model.bean.video.VideoUserViewed;
import cn.ninanina.wushanvideo.ui.MainActivity;
import cn.ninanina.wushanvideo.ui.home.HistoryActivity;
import cn.ninanina.wushanvideo.ui.home.LikeActivity;
import cn.ninanina.wushanvideo.ui.home.SearchActivity;
import cn.ninanina.wushanvideo.ui.home.VideoListFragment;
import cn.ninanina.wushanvideo.ui.home.WatchLaterActivity;
import cn.ninanina.wushanvideo.ui.instant.InstantFragment;
import cn.ninanina.wushanvideo.ui.me.MeFragment;
import cn.ninanina.wushanvideo.ui.tag.TagFragment;
import cn.ninanina.wushanvideo.ui.tag.TagVideoActivity;
import cn.ninanina.wushanvideo.ui.video.CommentFragment;
import cn.ninanina.wushanvideo.ui.video.DetailFragment;
import cn.ninanina.wushanvideo.ui.video.PlaylistActivity;
import cn.ninanina.wushanvideo.ui.video.VideoDetailActivity;
import cn.ninanina.wushanvideo.util.CommonUtils;
import cn.ninanina.wushanvideo.util.DialogManager;
import cn.ninanina.wushanvideo.util.MessageUtils;
import cn.ninanina.wushanvideo.util.ToastUtil;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

/**
 * 处理一切关于视频的网络请求，包括推荐视频、相关视频、搜索视频、获取视频链接、收藏视频等
 */
public class VideoPresenter extends BasePresenter {
    private static final VideoPresenter presenter = new VideoPresenter();

    private VideoPresenter() {
    }

    public static VideoPresenter getInstance() {
        return presenter;
    }

    public void getRecommendVideoList(VideoListFragment fragment, RecyclerViewOp recyclerViewOp) {
        fragment.setLoading(true);
        RecyclerView recyclerView = fragment.getRecyclerView();
        String type = fragment.getType();
        getVideoService().getRecommend(getAppKey(), type, fragment.size, getToken())
                .subscribeOn(Schedulers.io())
                .timeout(6, TimeUnit.SECONDS)
                .doOnError(throwable -> {
                    Looper.prepare();
                    ToastUtil.show("网络开小差了，请下拉刷新~");
                    fragment.setLoading(false);
                    fragment.setRefreshing(false);
                    Looper.loop();
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(listResult -> {
                    List<VideoDetail> videoDetails = listResult.getData();
                    if (videoDetails.isEmpty()) {
                        fragment.setLoadingFinished(true);
                        ToastUtil.show("没有更多了~");
                        return;
                    }
                    //1个广告+3个视频（开头也是广告）
                    List<Object> dataList = new ArrayList<>(videoDetails);
                    switch (recyclerViewOp) {
                        case INIT:
                            VideoListAdapter adapter = new VideoListAdapter(dataList,
                                    new DefaultVideoClickListener(fragment.getContext()), new DefaultVideoOptionClickListener(fragment.getActivity()));
                            recyclerView.setAdapter(adapter);
                            break;
                        case SWIPE:
                            adapter = (VideoListAdapter) recyclerView.getAdapter();
                            assert adapter != null;
                            adapter.insertToStart(dataList);
                            recyclerView.scrollToPosition(0);
                            break;
                        case APPEND:
                            adapter = (VideoListAdapter) recyclerView.getAdapter();
                            assert adapter != null;
                            adapter.append(dataList);
                            break;
                        default:
                    }
                    fragment.setRefreshing(false);
                    fragment.setLoading(false);
                });
    }

    public void getRelatedVideos(DetailFragment fragment, long videoId, boolean showLoading) {
        if (showLoading) DialogManager.getInstance().showPending(fragment.getActivity());
        String appKey = getAppKey();
        RecyclerView recyclerView = fragment.getRelatedRecyclerView();
        getVideoService().getRelatedVideos(appKey, videoId, fragment.page * fragment.size, fragment.size)
                .subscribeOn(Schedulers.io())
                .timeout(10, TimeUnit.SECONDS)
                .doOnError(throwable -> {
                    Looper.prepare();
                    ToastUtil.show("网络开小差了~");
                    DialogManager.getInstance().dismissPending();
                    Looper.loop();
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(listResult -> {
                    if (fragment.getContext() == null || CollectionUtils.isEmpty(listResult.getData()))
                        return;
                    List<VideoDetail> videoDetails = listResult.getData();
                    if (recyclerView.getAdapter() == null) {
                        recyclerView.setAdapter(new SingleVideoListAdapter(new ArrayList<>(videoDetails),
                                new DefaultVideoClickListener(fragment.getContext()), new DefaultVideoOptionClickListener(fragment.getActivity())));
                    } else {
                        SingleVideoListAdapter adapter = (SingleVideoListAdapter) recyclerView.getAdapter();
                        adapter.insert(new ArrayList<>(listResult.getData()));
                    }
                    DialogManager.getInstance().dismissPending();
                });
    }

    public void searchForVideo(SearchActivity searchActivity, boolean reset) {
        searchActivity.loading = true;
        searchActivity.swipe.setRefreshing(true);
        RecyclerView recyclerView = searchActivity.getRecyclerView();
        String appKey = getAppKey();
        int offset = searchActivity.page * searchActivity.size;
        int limit = searchActivity.size;
        String query = searchActivity.query;

        //todo:在未加密的情况下，搜索“色情”会导致程序端口被屏蔽，需要做一个过滤，待使用HTTPS后就可以取消
        if (query.equals("色情")) return;
        getVideoService().search(appKey, query, offset, limit, searchActivity.sortBy.getCode())
                .subscribeOn(Schedulers.io())
                .timeout(5, TimeUnit.SECONDS)
                .doOnError(throwable -> {
                    Looper.prepare();
                    ToastUtil.show("网络开小差了~");
                    searchActivity.loading = false;
                    searchActivity.swipe.setRefreshing(false);
                    Looper.loop();
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(listResult -> {
                    if (listResult.getData().size() <= 0) {
                        searchActivity.loadingFinished = true;
                        searchActivity.swipe.setRefreshing(false);
                        return;
                    }
                    SearchResultAdapter adapter = (SearchResultAdapter) recyclerView.getAdapter();
                    assert adapter != null;
                    if (!reset)
                        adapter.append(listResult.getData());
                    else adapter.reset(listResult.getData());
                    searchActivity.loading = false;
                    searchActivity.swipe.setRefreshing(false);
                });
    }

    /**
     * 视频详情页面初始化
     */
    public void getSrcForDetail(VideoDetailActivity videoDetailActivity, long id) {
        getVideoService().getVideoDetail(getAppKey(), id, getToken(), false, true)
                .subscribeOn(Schedulers.io())
                .timeout(5, TimeUnit.SECONDS)
                .doOnError(throwable -> {
                    Looper.prepare();
                    Looper.loop();
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(videoDetailResult -> {
                    VideoDetail detail = videoDetailResult.getData();
                    if (CommonUtils.isSrcValid(detail.getSrc())) {
                        videoDetailActivity.startPlaying(detail.getSrc());
                        loadAllHistory();
                    } else
                        ToastUtil.show("视频已经被原作者删除了，抱歉！");
                });
    }

    /**
     * 发现视频页面实时获取链接
     */
    public void getSrcForInstant(SimpleExoPlayer player, VideoDetail videoDetail) {
        getVideoService().getVideoDetail(getAppKey(), videoDetail.getId(), getToken(), false, false)
                .subscribeOn(Schedulers.io())
                .timeout(5, TimeUnit.SECONDS)
                .doOnError(throwable -> {
                    Looper.prepare();
                    Looper.loop();
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(videoDetailResult -> {
                    if (!CommonUtils.isSrcValid(videoDetailResult.getData().getSrc())) return;
                    MediaItem mediaItem = MediaItem.fromUri(videoDetailResult.getData().getSrc());
                    videoDetail.setSrc(videoDetailResult.getData().getSrc());
                    player.setMediaItem(mediaItem);
                    player.prepare();
                });
    }

    /**
     * 在未观看的情况下点击下载按钮，并且下载视频
     */
    public void getSrcForDownload(VideoDetail videoDetail) {
        getVideoService().getVideoDetail(getAppKey(), videoDetail.getId(), getToken(), false, false)
                .subscribeOn(Schedulers.io())
                .timeout(5, TimeUnit.SECONDS)
                .doOnError(throwable -> {
                    Looper.prepare();
                    Looper.loop();
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(videoDetailResult -> {
                    if (!CommonUtils.isSrcValid(videoDetailResult.getData().getSrc())) return;
                    videoDetail.setSrc(videoDetailResult.getData().getSrc());
                    DefaultDownloadClickListener listener = new DefaultDownloadClickListener(MainActivity.getInstance().downloadService);
                    listener.showMessage = false;
                    listener.onClick(videoDetail);
                });
    }

    /**
     * 点击离线视频进入详情页需要调用此方法,src指定本地视频文件路径
     */
    public void getSrcOfOffline(Context context, VideoDetail videoDetail, String src) {
        getVideoService().getVideoDetail(getAppKey(), videoDetail.getId(), getToken(), true, true)
                .subscribeOn(Schedulers.io())
                .timeout(5, TimeUnit.SECONDS)
                .doOnError(throwable -> {
                    Looper.prepare();
                    ToastUtil.show("网络开小差了~");
                    Looper.loop();
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(videoDetailResult -> {
                    VideoDetail video = videoDetailResult.getData();
                    video.setSrc(src);
                    DataHolder.getInstance().recordViewed(video.getId());
                    new DefaultVideoClickListener(context).onClick(video);
                    loadAllHistory();
                });
    }

    /**
     * 记录观看
     */
    public void recordViewed(VideoDetail videoDetail) {
        if (!WushanApp.loggedIn()) return;
        getVideoService().getVideoDetail(getAppKey(), videoDetail.getId(), getToken(), true, true)
                .subscribeOn(Schedulers.io())
                .timeout(5, TimeUnit.SECONDS)
                .doOnError(throwable -> {
                    Looper.prepare();
                    Looper.loop();
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(videoDetailResult -> DataHolder.getInstance().recordViewed(videoDetail.getId()));
    }

    /**
     * 新建收藏夹，创建完成后加入给定的recyclerView中
     */
    public void createPlaylist(Activity activity, String name) {
        DialogManager.getInstance().showPending(activity);
        getVideoService().createPlaylist(getAppKey(), name, getToken())
                .subscribeOn(Schedulers.io())
                .doOnError(throwable -> {
                    Looper.prepare();
                    ToastUtil.show("网络开小差了~");
                    DialogManager.getInstance().dismissPending();
                    Looper.loop();
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(playlistResult -> {
                    DataHolder.getInstance().getPlaylists().add(playlistResult.getData());
                    ToastUtil.show("创建成功");
                    MessageUtils.updatePlaylist();
                    DialogManager.getInstance().dismissPending();
                });
    }

    public void updatePlaylist(Activity activity, Playlist playlist) {
        DialogManager.getInstance().showPending(activity);
        getVideoService().updatePlaylist(getAppKey(), getToken(), playlist.getId(), playlist.getCover(), playlist.getName(), playlist.getIsPublic())
                .subscribeOn(Schedulers.io())
                .doOnError(throwable -> {
                    Looper.prepare();
                    ToastUtil.show("网络开小差了~");
                    DialogManager.getInstance().dismissPending();
                    Looper.loop();
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(playlistResult -> {
                    if (playlistResult.getRspCode().equals(ResultMsg.SUCCESS.getCode())) {
                        ToastUtil.show("操作成功");
                        DataHolder.getInstance().getPlaylists().remove(playlist);
                        DataHolder.getInstance().getPlaylists().add(playlist);
                        MessageUtils.updatePlaylist();
                        DialogManager.getInstance().dismissPending();
                    }
                });
    }

    public void deletePlaylist(Activity activity, Playlist playlist) {
        DialogManager.getInstance().showPending(activity);
        getVideoService().deletePlaylist(getAppKey(), playlist.getId(), getToken())
                .subscribeOn(Schedulers.io())
                .doOnError(throwable -> {
                    Looper.prepare();
                    ToastUtil.show("网络开小差了~");
                    DialogManager.getInstance().dismissPending();
                    Looper.loop();
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(playlistResult -> {
                    if (playlistResult.getRspCode().equals(ResultMsg.SUCCESS.getCode())) {
                        ToastUtil.show("删除成功");
                        DataHolder.getInstance().getPlaylists().remove(playlist);
                        MessageUtils.updatePlaylist();
                        DialogManager.getInstance().dismissPending();
                    }
                });
    }

    /**
     * 收藏视频
     */
    public void collectVideo(Activity activity, VideoDetail videoDetail, Playlist playlist) {
        DialogManager.getInstance().showPending(activity);
        getVideoService().collectVideo(getAppKey(), videoDetail.getId(), playlist.getId(), getToken())
                .subscribeOn(Schedulers.io())
                .doOnError(throwable -> {
                    Looper.prepare();
                    ToastUtil.show("网络开小差了~");
                    DialogManager.getInstance().dismissPending();
                    Looper.loop();
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> {
                    String code = result.getRspCode();
                    if (code.equals(ResultMsg.NOT_LOGIN.getCode())) {
                        ToastUtil.show("会话已过期，请刷新一下");
                    } else if (code.equals(ResultMsg.INVALID_VIDEO_ID.getCode()) || code.equals(ResultMsg.COLLECT_WRONG_DIR.getCode())) {
                        ToastUtil.show("出了点问题...");
                    } else if (code.equals(ResultMsg.COLLECT_ALREADY.getCode())) {
                        ToastUtil.show("已经收藏过了");
                    } else if (code.equals(ResultMsg.SUCCESS.getCode())) {
                        ToastUtil.show("收藏成功！");
                        videoDetail.setCollected(videoDetail.getCollected() + 1);
                        playlist.getVideoDetails().add(videoDetail);
                        playlist.setUpdateTime(System.currentTimeMillis());
                        if (!playlist.getUserSetCover())
                            playlist.setCover(videoDetail.getCoverUrl());
                        playlist.setCount(playlist.getCount() + 1);
                    }
                    DialogManager.getInstance().dismissPending();
                });
    }

    /**
     * 取消收藏
     */
    public void cancelCollect(Activity activity, VideoDetail videoDetail, Playlist playlist) {
        DialogManager.getInstance().showPending(activity);
        getVideoService().cancelCollect(getAppKey(), videoDetail.getId(), playlist.getId(), getToken())
                .subscribeOn(Schedulers.io())
                .doOnError(throwable -> {
                    Looper.prepare();
                    ToastUtil.show("网络开小差了~");
                    DialogManager.getInstance().dismissPending();
                    Looper.loop();
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> {
                    if (result.getRspCode().equals(ResultMsg.SUCCESS.getCode())) {
                        ToastUtil.show("操作成功");
                        videoDetail.setCollected(videoDetail.getCollected() - 1);
                        playlist.getVideoDetails().remove(videoDetail);
                        playlist.setCount(playlist.getCount() - 1);
                        playlist.setUpdateTime(System.currentTimeMillis());
                        DataHolder.getInstance().updatePlaylist(playlist);
                        Handler handler = PlaylistActivity.handler;
                        if (handler != null) {
                            Message message = new Message();
                            message.what = PlaylistActivity.deleteOne;
                            message.arg1 = Math.toIntExact(videoDetail.getId());
                            handler.sendMessage(message);
                        }
                        MessageUtils.updatePlaylist();
                    }
                    DialogManager.getInstance().dismissPending();
                });
    }

    /**
     * 获取用户的所有播单以及包含的视频，程序启动时就调用
     */
    public void loadPlaylists() {
        getVideoService().getPlaylist(getAppKey(), getToken())
                .subscribeOn(Schedulers.io())
                .doOnError(throwable -> {
                    Looper.prepare();
                    ToastUtil.show("网络开小差了~");
                    Looper.loop();
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(listResult -> {
                    if (listResult.getRspCode().equals(ResultMsg.SUCCESS.getCode())) {
                        DataHolder.getInstance().setPlaylists(listResult.getData());
                        loadPlaylistVideos();
                    }
                });
    }

    /**
     * 获取用户所有播单里面的所有视频
     */
    private void loadPlaylistVideos() {
        for (Playlist playlist : DataHolder.getInstance().getPlaylists()) {
            long playlistId = playlist.getId();
            getVideoService().getPlaylistVideos(getAppKey(), playlistId)
                    .subscribeOn(Schedulers.io())
                    .doOnError(throwable -> {
                        Looper.prepare();
                        ToastUtil.show("网络开小差了~");
                        Looper.loop();
                    })
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(listResult -> {
                        playlist.setVideoDetails(listResult.getData());
                        MainActivity.getInstance().playlistLoadingFinished++;
                        if (DataHolder.getInstance().getPlaylists().size() == MainActivity.getInstance().playlistLoadingFinished) {
                            MessageUtils.updatePlaylist();
                        }
                    });
        }
    }

    public void loadComments(CommentFragment commentFragment) {
        commentFragment.setLoading(true);
        getVideoService().getComments(getAppKey(), getToken(), commentFragment.getVideoDetail().getId(), commentFragment.getPage(), commentFragment.size, commentFragment.getSort().getCode())
                .subscribeOn(Schedulers.io())
                .doOnError(throwable -> {
                    Looper.prepare();
                    ToastUtil.show("网络开小差了~");
                    commentFragment.setLoading(false);
                    throwable.printStackTrace();
                    Looper.loop();
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> {
                    List<Comment> comments = result.getData();
                    if (CollectionUtils.isEmpty(comments)) {
                        if (commentFragment.getPage() == 0) {
                            //TODO:处理空评论
                        } else {
                            commentFragment.setLoadComplete(true);
                            ToastUtil.show("没有更多啦~");
                        }
                        return;
                    }
                    RecyclerView recyclerView = commentFragment.getRecyclerView();
                    if (recyclerView.getAdapter() == null) {
                        recyclerView.setAdapter(new CommentAdapter(comments, new ReplyCommentListener()));
                    } else {
                        CommentAdapter adapter = (CommentAdapter) recyclerView.getAdapter();
                        adapter.insert(comments);
                    }
                    commentFragment.setLoading(false);
                });
    }

    public void publishComment(CommentFragment commentFragment) {
        DialogManager.getInstance().showPending(commentFragment.getActivity());
        getVideoService().commentOn(getAppKey(), commentFragment.getVideoDetail().getId(), commentFragment.getInput().getText().toString(), getToken(), null)
                .subscribeOn(Schedulers.io())
                .doOnError(throwable -> {
                    Looper.prepare();
                    ToastUtil.show("网络开小差了~");
                    DialogManager.getInstance().dismissPending();
                    Looper.loop();
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> {
                    ToastUtil.show("评论成功！");
                    commentFragment.getInput().setText("");
                    Comment comment = result.getData();
                    RecyclerView recyclerView = commentFragment.getRecyclerView();
                    if (recyclerView.getAdapter() == null) {
                        recyclerView.setAdapter(new CommentAdapter(new ArrayList<Comment>() {{
                            add(comment);
                        }}, new ReplyCommentListener()));
                    } else {
                        CommentAdapter adapter = (CommentAdapter) recyclerView.getAdapter();
                        adapter.insert(comment);
                    }
                    DialogManager.getInstance().dismissPending();
                });
    }

    public void approveComment(CommentAdapter adapter, int position) {
        getVideoService().approveComment(getAppKey(), adapter.getCommentList().get(position).getId(), getToken())
                .subscribeOn(Schedulers.io())
                .doOnError(throwable -> {
                    Looper.prepare();
                    ToastUtil.show("网络开小差了~");
                    Looper.loop();
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> {
                    adapter.update(result.getData(), position);
                });
    }

    public void disapproveComment(CommentAdapter adapter, int position) {
        getVideoService().disapproveComment(getAppKey(), adapter.getCommentList().get(position).getId(), getToken())
                .subscribeOn(Schedulers.io())
                .doOnError(throwable -> {
                    Looper.prepare();
                    ToastUtil.show("网络开小差了~");
                    Looper.loop();
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> adapter.update(result.getData(), position));
    }

    public void getTags(TagFragment tagFragment) {
        tagFragment.setLoading(true);
        tagFragment.swipe.setRefreshing(true);
        char c = tagFragment.getC();
        getVideoService().getTags(getAppKey(), c, tagFragment.getPage(), tagFragment.size)
                .subscribeOn(Schedulers.io())
                .doOnError(throwable -> {
                    Looper.prepare();
                    ToastUtil.show("网络开小差了~");
                    tagFragment.setLoading(false);
                    tagFragment.swipe.setRefreshing(false);
                    Looper.loop();
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> {
                    tagFragment.setLoading(false);
                    tagFragment.swipe.setRefreshing(false);
                    if (CollectionUtils.isEmpty(result.getData())) {
                        tagFragment.setLoadingFinished(true);
                        ToastUtil.show("没有更多啦");
                        return;
                    }
                    TagAdapter tagAdapter = (TagAdapter) tagFragment.getContent().getAdapter();
                    assert tagAdapter != null;
                    tagAdapter.insert(result.getData());
                });
    }

    public void getTagSuggest(TagFragment tagFragment, String word) {
        getVideoService().suggestTags(getAppKey(), word)
                .subscribeOn(Schedulers.io())
                .doOnError(throwable -> {
                    Looper.prepare();
                    ToastUtil.show("网络开小差了~");
                    Looper.loop();
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> {
                    tagFragment.getSuggest().setAdapter(new TagSuggestAdapter(result.getData(),
                            new TagClickListener(tagFragment.getContext()), tagFragment.getSuggest()));
                });
    }

    public void getVideosForTag(TagVideoActivity activity, RecyclerViewOp op) {
        activity.setLoading(true);
        activity.swipe.setRefreshing(true);
        Tag tag = activity.getTag();
        VideoSortBy sortBy = activity.getSort();
        getVideoService().getTagVideos(getAppKey(), tag.getId(), activity.getPage() * activity.size, activity.size, sortBy.getCode())
                .subscribeOn(Schedulers.io())
                .doOnError(throwable -> {
                    Looper.prepare();
                    ToastUtil.show("网络开小差了~");
                    activity.setLoading(false);
                    activity.swipe.setRefreshing(false);
                    Looper.loop();
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> {
                    List<Object> dataList = new ArrayList<>(result.getData());
                    if (dataList.size() <= 0) {
                        activity.setLoadingFinished(true);
                        ToastUtil.show("没有更多啦");
                    }
                    SingleVideoListAdapter adapter = (SingleVideoListAdapter) activity.getContent().getAdapter();
                    if (op == RecyclerViewOp.INIT && adapter == null) {
                        activity.getContent().setAdapter(new SingleVideoListAdapter(dataList, new DefaultVideoClickListener(activity), new DefaultVideoOptionClickListener(activity)));
                    } else if (adapter != null && op == RecyclerViewOp.APPEND) {
                        adapter.insert(dataList);
                    } else if (adapter != null && op == RecyclerViewOp.SWIPE) {
                        adapter.insertToStart(dataList);

                    }
                    activity.setLoading(false);
                    activity.swipe.setRefreshing(false);
                });
    }

    public void getHistoryVideos(HistoryActivity activity) {
        activity.loading = true;
        getVideoService().getHistory(getAppKey(), getToken(), activity.page * activity.size, activity.size, null)
                .subscribeOn(Schedulers.io())
                .doOnError(throwable -> {
                    Looper.prepare();
                    ToastUtil.show("网络开小差了~");
                    activity.loading = false;
                    Looper.loop();
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> {
                    if (CollectionUtils.isEmpty(result.getData()) || result.getData().size() < activity.size) {
                        activity.loadingFinished = true;
                    }
                    activity.showData(result.getData());
                    activity.loading = false;
                });
    }

    public void deleteHistory(List<VideoUserViewed> histories, HistoryActivity activity, boolean all) {
        DialogManager.getInstance().showPending(activity);
        List<Long> historyIds = new ArrayList<>();
        for (VideoUserViewed history : histories)
            historyIds.add(history.getId());
        getVideoService().deleteHistory(getAppKey(), getToken(), historyIds)
                .subscribeOn(Schedulers.io())
                .doOnError(throwable -> {
                    Looper.prepare();
                    ToastUtil.show("网络开小差了~");
                    DialogManager.getInstance().dismissPending();
                    Looper.loop();
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> {
                    ToastUtil.show("操作成功");
                    if (all) activity.deleteAll();
                    else
                        activity.delete(histories);
                    DialogManager.getInstance().dismissPending();
                });
    }

    public void getInstantVideos(InstantFragment instantFragment) {
        getVideoService().getInstantVideos(getAppKey(), instantFragment.limit, getToken())
                .subscribeOn(Schedulers.io())
                .doOnError(throwable -> {
                    Looper.prepare();
                    ToastUtil.show("网络开小差了~");
                    instantFragment.loading = false;
                    Looper.loop();
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> instantFragment.showData(result.getData()));
    }

    public void downloadVideo(long videoId) {
        getVideoService().downloadVideo(getAppKey(), videoId, getToken())
                .subscribeOn(Schedulers.io())
                .doOnError(throwable -> {
                    Looper.prepare();
                    ToastUtil.show("网络开小差了~");
                    Looper.loop();
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> {
                });
    }

    public void likeVideo(Activity activity, VideoDetail videoDetail) {
        DialogManager.getInstance().showPending(activity);
        getVideoService().likeVideo(getAppKey(), getToken(), videoDetail.getId())
                .subscribeOn(Schedulers.io())
                .doOnError(throwable -> {
                    Looper.prepare();
                    ToastUtil.show("网络开小差了~");
                    DialogManager.getInstance().dismissPending();
                    Looper.loop();
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> {
                    if (result.getRspCode().equals(ResultMsg.NOT_LOGIN.getCode())) {
                        ToastUtil.show("请先登录");
                        return;
                    }
                    if (result.getData()) {
                        DataHolder.getInstance().getLikedVideos().add(videoDetail.getId());
                        DataHolder.getInstance().getDislikedVideos().remove(videoDetail.getId());
                        videoDetail.setLiked(videoDetail.getLiked() + 1);
                        ToastUtil.show("感谢推荐！");
                    } else {
                        DataHolder.getInstance().getLikedVideos().remove(videoDetail.getId());
                        videoDetail.setLiked(videoDetail.getLiked() - 1);
                        ToastUtil.show("取消喜欢");
                    }
                    DialogManager.getInstance().dismissPending();
                    MessageUtils.refreshVideoData(videoDetail);
                });
    }

    public void dislikeVideo(Activity activity, VideoDetail videoDetail) {
        DialogManager.getInstance().showPending(activity);
        getVideoService().dislikeVideo(getAppKey(), getToken(), videoDetail.getId())
                .subscribeOn(Schedulers.io())
                .doOnError(throwable -> {
                    Looper.prepare();
                    ToastUtil.show("网络开小差了~");
                    DialogManager.getInstance().dismissPending();
                    Looper.loop();
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> {
                    if (result.getRspCode().equals(ResultMsg.NOT_LOGIN.getCode())) {
                        ToastUtil.show("请先登录");
                        return;
                    }
                    if (result.getData()) {
                        DataHolder.getInstance().getDislikedVideos().add(videoDetail.getId());
                        DataHolder.getInstance().getLikedVideos().remove(videoDetail.getId());
                        videoDetail.setDisliked(videoDetail.getDisliked() + 1);
                        ToastUtil.show("感谢反馈！");
                    } else {
                        DataHolder.getInstance().getDislikedVideos().remove(videoDetail.getId());
                        videoDetail.setDisliked(videoDetail.getDisliked() - 1);
                        ToastUtil.show("取消不喜欢");
                    }
                    DialogManager.getInstance().dismissPending();
                    MessageUtils.refreshVideoData(videoDetail);
                });
    }

    //加载用户喜欢和不喜欢的所有videoId，需要在登录之后调用
    public void loadLikedAndDisliked() {
        getVideoService().likedVideo(getAppKey(), getToken())
                .subscribeOn(Schedulers.io())
                .doOnError(throwable -> {
                    Looper.prepare();
                    ToastUtil.show("网络开小差了~");
                    Looper.loop();
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> DataHolder.getInstance().setLikedVideos(result.getData()));
        getVideoService().dislikedVideo(getAppKey(), getToken())
                .subscribeOn(Schedulers.io())
                .doOnError(throwable -> {
                    Looper.prepare();
                    ToastUtil.show("网络开小差了~");
                    Looper.loop();
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> DataHolder.getInstance().setDislikedVideos(result.getData()));
    }

    public void getLikedVideos(LikeActivity likeActivity) {
        likeActivity.loading = true;
        likeActivity.swipe.setRefreshing(true);
        getVideoService().likedVideos(getAppKey(), getToken(), likeActivity.page * likeActivity.size, likeActivity.size)
                .subscribeOn(Schedulers.io())
                .doOnError(throwable -> {
                    Looper.prepare();
                    ToastUtil.show("网络开小差了~");
                    likeActivity.loading = false;
                    likeActivity.swipe.setRefreshing(false);
                    Looper.loop();
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> {
                    if (result.getData().size() <= 0)
                        likeActivity.loadingFinished = true;
                    likeActivity.showData(result.getData());
                    likeActivity.loading = false;
                    likeActivity.swipe.setRefreshing(false);
                });
    }

    //加载所有历史记录
    public void loadAllHistory() {
        getVideoService().getAllHistory(getAppKey(), getToken())
                .subscribeOn(Schedulers.io())
                .doOnError(throwable -> {
                    Looper.prepare();
                    Looper.loop();
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> DataHolder.getInstance().setAllViewed(result.getData()));
    }

    //增加稍后观看
    public void addToWatch(Activity activity, long videoId) {
        DialogManager.getInstance().showPending(activity);
        getVideoService().newToWatch(getAppKey(), getToken(), videoId)
                .subscribeOn(Schedulers.io())
                .doOnError(throwable -> {
                    Looper.prepare();
                    ToastUtil.show("网络开小差了~");
                    DialogManager.getInstance().dismissPending();
                    Looper.loop();
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> {
                    if (result.getRspCode().equals(ResultMsg.SUCCESS.getCode()))
                        ToastUtil.show("已添加到稍后观看列表");
                    DialogManager.getInstance().dismissPending();
                });
    }

    //删除稍后观看
    public void deleteWatchLater(WatchLaterActivity activity, List<Pair<ToWatch, VideoDetail>> pairs) {
        DialogManager.getInstance().showPending(activity);
        List<Long> ids = new ArrayList<>();
        for (Pair<ToWatch, VideoDetail> pair : pairs) ids.add(pair.getFirst().getId());
        getVideoService().deleteToWatch(getAppKey(), getToken(), ids)
                .subscribeOn(Schedulers.io())
                .doOnError(throwable -> {
                    Looper.prepare();
                    ToastUtil.show("网络开小差了~");
                    DialogManager.getInstance().dismissPending();
                    Looper.loop();
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> {
                    if (result.getRspCode().equals(ResultMsg.SUCCESS.getCode()))
                        ToastUtil.show("操作成功");
                    DialogManager.getInstance().dismissPending();
                    activity.deleteData(pairs);
                });
    }

    //获取稍后观看
    public void getWatchLater(WatchLaterActivity watchLaterActivity) {
        watchLaterActivity.loading = true;
        getVideoService().getToWatches(getAppKey(), getToken(), watchLaterActivity.page * watchLaterActivity.size, watchLaterActivity.size)
                .subscribeOn(Schedulers.io())
                .doOnError(throwable -> {
                    Looper.prepare();
                    ToastUtil.show("网络开小差了~");
                    watchLaterActivity.loading = false;
                    Looper.loop();
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> {
                    if (result.getData().size() < watchLaterActivity.size)
                        watchLaterActivity.loadingFinished = true;
                    watchLaterActivity.showData(result.getData());
                    watchLaterActivity.loading = false;
                });
    }

    public enum RecyclerViewOp {
        INIT, //第一次填充
        SWIPE, //下拉刷新
        APPEND, //下滑添加
    }

}
