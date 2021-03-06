package razerdp.friendcircle.activity.circledemo;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.socks.library.KLog;

import java.util.ArrayList;
import java.util.List;

import cn.bmob.v3.exception.BmobException;
import razerdp.friendcircle.R;
import razerdp.friendcircle.activity.ActivityLauncher;
import razerdp.friendcircle.app.manager.UpdateInfoManager;
import razerdp.friendcircle.app.mvp.presenter.MomentPresenter;
import razerdp.friendcircle.app.mvp.view.IMomentView;
import razerdp.github.com.common.mvp.models.MomentsType;
import razerdp.friendcircle.ui.adapter.CircleMomentsAdapter;
import razerdp.friendcircle.ui.viewholder.EmptyMomentsVH;
import razerdp.friendcircle.ui.viewholder.MultiImageMomentsVH;
import razerdp.friendcircle.ui.viewholder.TextOnlyMomentsVH;
import razerdp.friendcircle.ui.viewholder.WebMomentsVH;
import razerdp.friendcircle.ui.widget.commentwidget.CommentBox;
import razerdp.friendcircle.ui.widget.commentwidget.CommentWidget;
import razerdp.friendcircle.ui.widget.popup.RegisterPopup;
import razerdp.github.com.baselibrary.helper.AppSetting;
import razerdp.github.com.baselibrary.imageloader.ImageLoadMnanger;
import razerdp.github.com.baselibrary.manager.KeyboardControlMnanager;
import razerdp.github.com.baselibrary.net.base.OnResponseListener;
import razerdp.github.com.baselibrary.utils.ToolUtil;
import razerdp.github.com.baselibrary.utils.ui.UIHelper;
import razerdp.github.com.baseuilib.base.BaseTitleBarActivity;
import razerdp.github.com.baseuilib.helper.PhotoHelper;
import razerdp.github.com.baseuilib.widget.common.TitleBar;
import razerdp.github.com.baseuilib.widget.popup.SelectPhotoMenuPopup;
import razerdp.github.com.baseuilib.widget.pullrecyclerview.CircleRecyclerView;
import razerdp.github.com.baseuilib.widget.pullrecyclerview.CircleRecyclerView.OnPreDispatchTouchListener;
import razerdp.github.com.baseuilib.widget.pullrecyclerview.interfaces.OnRefreshListener2;
import razerdp.github.com.common.manager.LocalHostManager;
import razerdp.github.com.common.mvp.models.entity.CommentInfo;
import razerdp.github.com.common.mvp.models.entity.LikesInfo;
import razerdp.github.com.common.mvp.models.entity.MomentsInfo;
import razerdp.github.com.common.mvp.models.entity.UserInfo;
import razerdp.github.com.common.mvp.models.localphotomanager.ImageInfo;
import razerdp.github.com.common.request.MomentsRequest;
import razerdp.github.com.common.router.RouterList;

/**
 * Created by 大灯泡 on 2016/10/26.
 * <p>
 * 朋友圈主界面
 */

public class FriendCircleDemoActivity extends BaseTitleBarActivity implements OnRefreshListener2, IMomentView, OnPreDispatchTouchListener {

    private static final int REQUEST_REFRESH = 0x10;
    private static final int REQUEST_LOADMORE = 0x11;


    private CircleRecyclerView circleRecyclerView;
    private CommentBox commentBox;
    private HostViewHolder hostViewHolder;
    private CircleMomentsAdapter adapter;
    private List<MomentsInfo> momentsInfoList;
    //request
    private MomentsRequest momentsRequest;
    private MomentPresenter presenter;

    private CircleViewHelper mViewHelper;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        UpdateInfoManager.INSTANCE.init(this);
        momentsInfoList = new ArrayList<>();
        momentsRequest = new MomentsRequest();
        initView();
        initKeyboardHeightObserver();
        UIHelper.ToastMessage("请尽量不要上传黄图，谢谢");

    }

    @Override
    public void onHandleIntent(Intent intent) {

    }


    private void initView() {
        if (mViewHelper == null) {
            mViewHelper = new CircleViewHelper(this);
        }
        setTitle("朋友圈");
        setTitleMode(TitleBar.MODE_BOTH);
        setTitleRightIcon(R.drawable.ic_camera);
        setTitleLeftText("发现");
        setTitleLeftIcon(R.drawable.back_left);
        presenter = new MomentPresenter(this);

        hostViewHolder = new HostViewHolder(this);
        circleRecyclerView = (CircleRecyclerView) findViewById(R.id.recycler);
        circleRecyclerView.setOnRefreshListener(this);
        circleRecyclerView.setOnPreDispatchTouchListener(this);
        circleRecyclerView.addHeaderView(hostViewHolder.getView());

        commentBox = (CommentBox) findViewById(R.id.widget_comment);
        commentBox.setOnCommentSendClickListener(onCommentSendClickListener);

        CircleMomentsAdapter.Builder<MomentsInfo> builder = new CircleMomentsAdapter.Builder<>(this);
        builder.addType(EmptyMomentsVH.class, MomentsType.EMPTY_CONTENT, R.layout.moments_empty_content)
               .addType(MultiImageMomentsVH.class, MomentsType.MULTI_IMAGES, R.layout.moments_multi_image)
               .addType(TextOnlyMomentsVH.class, MomentsType.TEXT_ONLY, R.layout.moments_only_text)
               .addType(WebMomentsVH.class, MomentsType.WEB, R.layout.moments_web)
               .setData(momentsInfoList)
               .setPresenter(presenter);
        adapter = builder.build();
        circleRecyclerView.setAdapter(adapter);
        circleRecyclerView.autoRefresh();

    }

    private void initKeyboardHeightObserver() {
        //观察键盘弹出与消退
        KeyboardControlMnanager.observerKeyboardVisibleChange(this, new KeyboardControlMnanager.OnKeyboardStateChangeListener() {
            View anchorView;

            @Override
            public void onKeyboardChange(int keyboardHeight, boolean isVisible) {
                int commentType = commentBox.getCommentType();
                if (isVisible) {
                    //定位评论框到view
                    anchorView = mViewHelper.alignCommentBoxToView(circleRecyclerView, commentBox, commentType);
                } else {
                    //定位到底部
                    commentBox.dismissCommentBox(false);
                    mViewHelper.alignCommentBoxToViewWhenDismiss(circleRecyclerView, commentBox, commentType, anchorView);
                }
            }
        });
    }


    @Override
    public void onRefresh() {
        momentsRequest.setOnResponseListener(momentsRequestCallBack);
        momentsRequest.setRequestType(REQUEST_REFRESH);
        momentsRequest.setCurPage(0);
        momentsRequest.execute();
    }

    @Override
    public void onLoadMore() {
        momentsRequest.setOnResponseListener(momentsRequestCallBack);
        momentsRequest.setRequestType(REQUEST_LOADMORE);
        momentsRequest.execute();
    }


    //titlebar click


    @Override
    public void onTitleDoubleClick() {
        super.onTitleDoubleClick();
        if (circleRecyclerView != null) {
            int firstVisibleItemPos = circleRecyclerView.findFirstVisibleItemPosition();
            circleRecyclerView.getRecyclerView().smoothScrollToPosition(0);
            if (firstVisibleItemPos > 1) {
                circleRecyclerView.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        circleRecyclerView.autoRefresh();
                    }
                }, 200);
            }
        }

    }

    @Override
    public void onTitleLeftClick() {
        if (System.currentTimeMillis() - lastClickBackTime > 2000) { // 后退阻断
            UIHelper.ToastMessage("这是朋友圈工程哦，不是整个微信哦~再点一次退出");
            lastClickBackTime = System.currentTimeMillis();
        } else { // 关掉app
            super.onBackPressed();
        }
    }

    @Override
    public void onTitleRightClick() {
        new SelectPhotoMenuPopup(this).setOnSelectPhotoMenuClickListener(new SelectPhotoMenuPopup.OnSelectPhotoMenuClickListener() {
            @Override
            public void onShootClick() {
                PhotoHelper.fromCamera(FriendCircleDemoActivity.this, false);
            }

            @Override
            public void onAlbumClick() {
                ActivityLauncher.startToPhotoSelectActivity(getActivity(), RouterList.PhotoSelectActivity.requestCode);
            }
        }).showPopupWindow();
    }

    @Override
    public boolean onTitleRightLongClick() {
        ActivityLauncher.startToPublishActivityWithResult(this, RouterList.PublishActivity.MODE_TEXT, null, RouterList.PublishActivity.requestCode);
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        PhotoHelper.handleActivityResult(this, requestCode, resultCode, data, new PhotoHelper.PhotoCallback() {
            @Override
            public void onFinish(String filePath) {
                List<ImageInfo> selectedPhotos = new ArrayList<ImageInfo>();
                selectedPhotos.add(new ImageInfo(filePath, null, null, 0, 0));
                ActivityLauncher.startToPublishActivityWithResult(FriendCircleDemoActivity.this,
                                                                  RouterList.PublishActivity.MODE_MULTI,
                                                                  selectedPhotos,
                                                                  RouterList.PublishActivity.requestCode);
            }

            @Override
            public void onError(String msg) {
                UIHelper.ToastMessage(msg);
            }
        });
        if (requestCode == RouterList.PhotoSelectActivity.requestCode && resultCode == RESULT_OK) {
            List<ImageInfo> selectedPhotos = data.getParcelableArrayListExtra(RouterList.PhotoSelectActivity.key_result);
            if (selectedPhotos != null) {
                ActivityLauncher.startToPublishActivityWithResult(this, RouterList.PublishActivity.MODE_MULTI, selectedPhotos, RouterList.PublishActivity.requestCode);
            }
        }

        if (requestCode == RouterList.PublishActivity.requestCode && resultCode == RESULT_OK) {
            circleRecyclerView.autoRefresh();
        }
    }

    //request
    //==============================================
    private OnResponseListener.SimpleResponseListener<List<MomentsInfo>> momentsRequestCallBack = new OnResponseListener.SimpleResponseListener<List<MomentsInfo>>() {
        @Override
        public void onSuccess(List<MomentsInfo> response, int requestType) {
            circleRecyclerView.compelete();
            switch (requestType) {
                case REQUEST_REFRESH:
                    if (!ToolUtil.isListEmpty(response)) {
                        KLog.i("firstMomentid", "第一条动态ID   >>>   " + response.get(0).getMomentid());
                        hostViewHolder.loadHostData(LocalHostManager.INSTANCE.getLocalHostUser());
                        adapter.updateData(response);
                    }
                    checkRegister();
                    break;
                case REQUEST_LOADMORE:
                    adapter.addMore(response);
                    break;
            }
        }

        @Override
        public void onError(BmobException e, int requestType) {
            super.onError(e, requestType);
            circleRecyclerView.compelete();
        }
    };


    //=============================================================View's method
    @Override
    public void onLikeChange(int itemPos, List<LikesInfo> likeUserList) {
        MomentsInfo momentsInfo = adapter.findData(itemPos);
        if (momentsInfo != null) {
            momentsInfo.setLikesList(likeUserList);
            adapter.notifyItemChanged(itemPos);
        }
    }

    @Override
    public void onCommentChange(int itemPos, List<CommentInfo> commentInfoList) {
        MomentsInfo momentsInfo = adapter.findData(itemPos);
        if (momentsInfo != null) {
            momentsInfo.setCommentList(commentInfoList);
            adapter.notifyItemChanged(itemPos);
        }
    }

    @Override
    public void showCommentBox(@Nullable View viewHolderRootView, int itemPos, String momentid, CommentWidget commentWidget) {
        if (viewHolderRootView != null) {
            mViewHelper.setCommentAnchorView(viewHolderRootView);
        } else if (commentWidget != null) {
            mViewHelper.setCommentAnchorView(commentWidget);
        }
        mViewHelper.setCommentItemDataPosition(itemPos);
        commentBox.toggleCommentBox(momentid, commentWidget == null ? null : commentWidget.getData(), false);
    }

    @Override
    public void onDeleteMomentsInfo(@NonNull MomentsInfo momentsInfo) {
        int pos = adapter.getDatas().indexOf(momentsInfo);
        if (pos < 0) return;
        adapter.deleteData(pos);
    }

    @Override
    public boolean onPreTouch(MotionEvent ev) {
        if (commentBox != null && commentBox.isShowing()) {
            commentBox.dismissCommentBox(false);
            return true;
        }
        return false;
    }

    //=============================================================tool method
    private void checkRegister() {
        boolean hasCheckRegister = (boolean) AppSetting.loadBooleanPreferenceByKey(AppSetting.CHECK_REGISTER, false);
        if (!hasCheckRegister) {
            RegisterPopup registerPopup = new RegisterPopup(FriendCircleDemoActivity.this);
            registerPopup.setOnRegisterSuccess(new RegisterPopup.onRegisterSuccess() {
                @Override
                public void onSuccess(UserInfo userInfo) {
                    hostViewHolder.loadHostData(userInfo);
                    UpdateInfoManager.INSTANCE.showUpdateInfo();
                }
            });
            registerPopup.showPopupWindow();
        } else {
            UpdateInfoManager.INSTANCE.showUpdateInfo();
        }
    }

    private long lastClickBackTime;

    @Override
    public void onBackPressed() {
        if (System.currentTimeMillis() - lastClickBackTime > 2000) { // 后退阻断
            UIHelper.ToastMessage("再点一次退出");
            lastClickBackTime = System.currentTimeMillis();
        } else { // 关掉app
            super.onBackPressed();
        }
    }

    //=============================================================call back
    private CommentBox.OnCommentSendClickListener onCommentSendClickListener = new CommentBox.OnCommentSendClickListener() {
        @Override
        public void onCommentSendClick(View v, String momentid, String commentAuthorId, String commentContent) {
            if (TextUtils.isEmpty(commentContent)) return;
            int itemPos = mViewHelper.getCommentItemDataPosition();
            if (itemPos < 0 || itemPos > adapter.getItemCount()) return;
            List<CommentInfo> commentInfos = adapter.findData(itemPos).getCommentList();
            presenter.addComment(itemPos, momentid, commentAuthorId, commentContent, commentInfos);
            commentBox.clearDraft();
            commentBox.dismissCommentBox(true);
        }
    };


    private static class HostViewHolder {
        private View rootView;
        private ImageView friend_wall_pic;
        private ImageView friend_avatar;
        private ImageView message_avatar;
        private TextView message_detail;
        private TextView hostid;

        public HostViewHolder(Context context) {
            this.rootView = LayoutInflater.from(context).inflate(R.layout.circle_host_header, null);
            this.hostid = (TextView) rootView.findViewById(R.id.host_id);
            this.friend_wall_pic = (ImageView) rootView.findViewById(R.id.friend_wall_pic);
            this.friend_avatar = (ImageView) rootView.findViewById(R.id.friend_avatar);
            this.message_avatar = (ImageView) rootView.findViewById(R.id.message_avatar);
            this.message_detail = (TextView) rootView.findViewById(R.id.message_detail);
        }

        public void loadHostData(UserInfo hostInfo) {
            if (hostInfo == null) return;
            ImageLoadMnanger.INSTANCE.loadImage(friend_wall_pic, hostInfo.getCover());
            ImageLoadMnanger.INSTANCE.loadImage(friend_avatar, hostInfo.getAvatar());
            hostid.setText("您的测试ID为: " + hostInfo.getUserid() + '\n' + "您的测试用户名为: " + hostInfo.getNick());
        }

        public View getView() {
            return rootView;
        }

    }
}
