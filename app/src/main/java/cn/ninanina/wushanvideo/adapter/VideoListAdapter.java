package cn.ninanina.wushanvideo.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.facebook.drawee.view.SimpleDraweeView;
import com.google.android.gms.ads.AdView;

import org.apache.commons.lang3.StringUtils;

import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import cn.ninanina.wushanvideo.R;
import cn.ninanina.wushanvideo.model.bean.video.Tag;
import cn.ninanina.wushanvideo.model.bean.video.VideoDetail;

public class VideoListAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private List<Object> dataList;
    private ItemClickListener listener;

    public VideoListAdapter(List<Object> dataList, ItemClickListener listener) {
        this.dataList = dataList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VideoCardHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_video_card, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (dataList.get(position) instanceof VideoDetail) {
            VideoCardHolder videoCardHolder = (VideoCardHolder) holder;
            VideoDetail videoDetail = (VideoDetail) dataList.get(position);
            videoCardHolder.cover.setAspectRatio(1.78f);
            videoCardHolder.cover.setImageURI(videoDetail.getCoverUrl());
            if (StringUtils.isEmpty(videoDetail.getTitleZh()))
                videoCardHolder.videoTitle.setText(videoDetail.getTitle());
            else videoCardHolder.videoTitle.setText(videoDetail.getTitleZh());
            int viewed = videoDetail.getViewed();
            viewed = viewed / 100;
            StringBuilder strViewed = new StringBuilder();
            if (viewed >= 10000) {
                int wan = viewed / 10000;
                int qian = viewed % 10000 / 1000;
                strViewed.append(wan);
                if (qian != 0) strViewed.append('.').append(qian);
                strViewed.append("万");
            } else strViewed.append(viewed);
            videoCardHolder.videoViews.setText(strViewed.toString());
            String duration = videoDetail.getDuration().replace(" ", "\0").replace("min", "分钟").replace("h", "小时").replace("sec", "秒");
            videoCardHolder.videoDuration.setText(duration);
            StringBuilder strTag = new StringBuilder();
            if (isSrcValid(videoDetail.getSrc())) {
                videoCardHolder.label1.setText("不限次");
                videoCardHolder.label1.setVisibility(View.VISIBLE);
                LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) videoCardHolder.label3.getLayoutParams();
                params.leftMargin = 5;
                videoCardHolder.label3.setLayoutParams(params);
            }
            for (Tag tag : videoDetail.getTags()) {
                if (!StringUtils.isEmpty(tag.getTagZh())) {
                    strTag.append(tag.getTagZh()).append("·");
                }
                if (strTag.length() >= 12) break;
            }
            if (strTag.toString().isEmpty() && videoDetail.getTags().size() > 0)
                strTag.append(videoDetail.getTags().get(0).getTag());
            String sTag = strTag.toString();
            if (sTag.endsWith("·")) sTag = sTag.substring(0, sTag.length() - 1);
            videoCardHolder.label3.setText(sTag);
            videoCardHolder.cover.setOnClickListener(v -> listener.onItemClicked((VideoDetail) dataList.get(holder.getLayoutPosition())));
            videoCardHolder.videoTitle.setOnClickListener(v -> listener.onItemClicked((VideoDetail) dataList.get(holder.getLayoutPosition())));
        }
    }

    @Override
    public int getItemCount() {
        return dataList.size();
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    public static final class VideoCardHolder extends RecyclerView.ViewHolder {
        @BindView(R.id.video_cover)
        SimpleDraweeView cover;
        @BindView(R.id.video_title)
        TextView videoTitle;
        @BindView(R.id.video_views)
        TextView videoViews;
        @BindView(R.id.video_duration)
        TextView videoDuration;
        @BindView(R.id.tag_group)
        LinearLayout tagGroup;
        @BindView(R.id.video_label1)
        TextView label1;
        @BindView(R.id.video_label3)
        TextView label3;

        private VideoCardHolder(View itemView) {
            super(itemView);
            if (!(itemView instanceof AdView))
                ButterKnife.bind(this, itemView);
        }
    }

    public interface ItemClickListener {
        void onItemClicked(VideoDetail videoDetail);
    }

    public void append(List<Object> newData) {
        int index = dataList.size();
        dataList.addAll(newData);
        notifyItemRangeInserted(index, newData.size());
    }

    public void insertToStart(List<Object> newData) {
        dataList.addAll(0, newData);
        notifyItemRangeInserted(0, newData.size());
    }

    public static boolean isSrcValid(String src) {
        if (StringUtils.isEmpty(src)) return false;
        long currentSeconds = System.currentTimeMillis() / 1000;
        long urlSeconds = Long.parseLong(src.substring(src.indexOf("?e=") + 3, src.indexOf("&h="))) - 1800;
        return currentSeconds < urlSeconds;
    }
}
