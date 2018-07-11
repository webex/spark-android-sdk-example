package com.cisco.sparksdk.kitchensink.launcher.fragments;

import android.app.Activity;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.cisco.sparksdk.kitchensink.R;
import com.cisco.sparksdk.kitchensink.actions.SparkAgent;
import com.cisco.sparksdk.kitchensink.ui.BaseFragment;
import com.ciscospark.androidsdk.message.LocalFile;
import com.ciscospark.androidsdk.message.Mention;
import com.ciscospark.androidsdk.message.Message;
import com.ciscospark.androidsdk.message.MessageClient;
import com.ciscospark.androidsdk.message.MessageObserver;
import com.ciscospark.androidsdk.message.RemoteFile;
import com.github.benoitdion.ln.Ln;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

import static android.app.Activity.RESULT_OK;


public class MessageFragment extends BaseFragment {
    private static final String TARGET_ID = "target_id";
    private static final int FILE_SELECT_REQUEST = 1;

    @BindView(R.id.message_text)
    EditText text_message;

    @BindView(R.id.message_view)
    RecyclerView recyclerView;

    @BindView(R.id.message_mention)
    ImageButton btn_mention;

    @BindView(R.id.message_status)
    TextView text_status;

    @BindView(R.id.mention_text)
    TextView text_mention;

    MessageAdapter adapter;

    SparkAgent agent = SparkAgent.getInstance();

    MessageClient messageClient = agent.getMessageClient();

    ArrayList<File> selectedFile = new ArrayList<>();

    String targetId;

    public static MessageFragment newInstance(String id) {
        MessageFragment fragment = new MessageFragment();
        Bundle args = new Bundle();
        args.putInt(LAYOUT, R.layout.fragment_message);
        args.putString(TARGET_ID, id);
        fragment.setArguments(args);
        return fragment;
    }

    private String getTargetId() {
        Bundle bundle = getArguments();
        return bundle != null ? bundle.getString(TARGET_ID) : null;
    }

    public MessageFragment() {
        // Required empty public constructor
    }

    @Override
    public void onActivityCreated(Bundle saved) {
        super.onActivityCreated(saved);
        recyclerView.setLayoutManager(new LinearLayoutManager(this.getActivity()));
        adapter = new MessageAdapter(this.getActivity());
        recyclerView.setAdapter(adapter);
        messageClient.setMessageObserver(evt -> {
            if (evt instanceof MessageObserver.MessageArrived) {
                MessageObserver.MessageArrived event = (MessageObserver.MessageArrived) evt;
                Ln.e("message: " + event.getMessage().toString());
                adapter.mData.add(event.getMessage());
                adapter.notifyDataSetChanged();
                if (event.getMessage().getPersonEmail().equals("sparksdktestuser16@tropo.com")) {
                    text_status.setText("");
                }
            } else {
                MessageObserver.MessageDeleted event = (MessageObserver.MessageDeleted) evt;
                Ln.e("message deleted " + event.getMessageId());
            }
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        text_mention.setVisibility(View.GONE);
        targetId = getTargetId();
    }


    private LocalFile[] generateLocalFiles() {
        ArrayList<LocalFile> arrayList = new ArrayList<>();
        if (selectedFile != null) {
            for (File f : selectedFile) {
                if (f.exists()) {
                    LocalFile localFile = new LocalFile(f);
                    localFile.progressHandler = x -> {
                        text_status.setText("sending " + localFile.name + "...  " + x + "%");
                    };
                    /*
                    localFile.thumbnail = new LocalFile.Thumbnail();
                    localFile.thumbnail.path = thumbnail_file.getPath();
                    localFile.thumbnail.width = 622;
                    localFile.thumbnail.height = 492;
                    */
                    arrayList.add(localFile);
                }
            }
        }
        LocalFile[] localFile = new LocalFile[arrayList.size()];
        arrayList.toArray(localFile);
        return localFile;
    }

    private Mention[] generateMentions(String[] mentions) {
        Mention.MentionAll mentionAll = new Mention.MentionAll();
        ArrayList<Mention> mentionList = new ArrayList<>();
        for (String s : mentions) {
            if (s.toUpperCase().equals("ALL")) {
                mentionList.add(mentionAll);
            } else {
                Mention.MentionPerson person = new Mention.MentionPerson(s);
                mentionList.add(person);
            }
        }
        Mention[] mention_array = new Mention[mentionList.size()];
        mentionList.toArray(mention_array);
        return mention_array;
    }

    private void hideSoftKeyboard() {
        InputMethodManager inputMethodManager =
                (InputMethodManager) getActivity().getSystemService(
                        Activity.INPUT_METHOD_SERVICE);
        if (inputMethodManager != null) {
            inputMethodManager.hideSoftInputFromWindow(
                    getActivity().getCurrentFocus().getWindowToken(), 0);
        }
    }

    @OnClick(R.id.send_button)
    public void sendMessage(View btn) {
        if (!TextUtils.isEmpty(text_message.getText())) {
            String mention_string = text_mention.getText().toString();
            String[] strings = mention_string.split(" ");

            btn.setEnabled(false);
            agent.sendMessage(targetId,
                    text_message.getText().toString(),
                    generateMentions(strings),
                    generateLocalFiles(),
                    rst -> {
                        Ln.e("posted:" + rst.toString());
                        text_mention.setText("");
                        selectedFile.clear();
                        btn.setEnabled(true);
                    });
            text_status.setText("sending ...");
            text_message.setText("");
        }
        text_mention.setVisibility(View.GONE);
        text_message.clearFocus();
        hideSoftKeyboard();
    }


    @OnClick(R.id.message_upload_file)
    public void selectFile() {
        Intent intent = new Intent();
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent,
                "Select File"), FILE_SELECT_REQUEST);
    }


    @OnClick(R.id.message_mention)
    public void mentionPeople() {
        if (text_mention.getVisibility() == View.VISIBLE) {
            text_mention.setVisibility(View.GONE);
        } else {
            text_mention.setVisibility(View.VISIBLE);
        }
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Ln.e("onActivityResult");
        if (resultCode == RESULT_OK) {
            if (requestCode == FILE_SELECT_REQUEST) {

                Uri uri = data.getData();

                String path = getPath(getActivity(), uri);
                if (path != null) {
                    File selected = new File(path);
                    selectedFile.add(selected);
                    StringBuilder buffer = new StringBuilder();
                    for (File f : selectedFile) {
                        buffer.append(" ").append(f.getName());
                    }
                    text_status.setText(buffer.toString());
                }
            }
        }
    }


    public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.MessageViewHolder> {
        private final LayoutInflater mLayoutInflater;
        private final Context mContext;
        private ArrayList<Message> mData;

        MessageAdapter(Context context) {
            mContext = context;
            mData = new ArrayList<>();
            mLayoutInflater = LayoutInflater.from(mContext);
        }

        @Override
        public MessageViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new MessageViewHolder(mLayoutInflater.inflate(R.layout.listview_message, parent, false));
        }

        @Override
        public void onBindViewHolder(MessageViewHolder holder, int position) {
            Message message = mData.get(position);
            holder.message_date.setText(message.getCreated().toString());
            holder.message_text.setText(message.getText());
            try {
                JSONObject json = new JSONObject(message.toString());
                holder.message_payload.setText(json.toString(4));
            } catch (JSONException e) {
                Ln.e("JSONObject parse error");
                holder.message_payload.setText(message.toString());
            }
            if (message.isSelfMentioned) {
                holder.message_mention.setVisibility(View.VISIBLE);
            } else {
                holder.message_mention.setVisibility(View.GONE);
            }
            List<RemoteFile> list = message.getRemoteFiles();
            if (list != null && list.size() > 0) {
                holder.message_file.setVisibility(View.VISIBLE);
                holder.message_filename.setVisibility(View.VISIBLE);
                holder.message_filename.setText(list.get(0).displayName);

                if (message.getRemoteFiles().get(0).thumbnail != null) {
                    holder.progressBar.setVisibility(View.VISIBLE);
                    agent.downloadThumbnail(message.getRemoteFiles().get(0), null, null, (uri) -> {
                        holder.message_file.setImageURI(uri.getData());
                        holder.progressBar.setVisibility(View.GONE);
                    });
                }
            } else {
                holder.message_file.setVisibility(View.GONE);
                holder.message_filename.setVisibility(View.GONE);
                holder.message_file_process.setVisibility(View.GONE);
                holder.message_download_button.setVisibility(View.GONE);
            }
        }

        @Override
        public int getItemCount() {
            return mData == null ? 0 : mData.size();
        }

        class MessageViewHolder extends RecyclerView.ViewHolder {
            @BindView(R.id.message_item_text)
            TextView message_text;

            @BindView(R.id.message_item_date)
            TextView message_date;

            @BindView(R.id.message_item_mention)
            TextView message_mention;

            @BindView(R.id.message_item_file)
            ImageView message_file;

            @BindView(R.id.message_item_filename)
            TextView message_filename;

            @BindView(R.id.message_item_file_process)
            TextView message_file_process;

            @BindView(R.id.message_item_payload)
            TextView message_payload;

            @BindView(R.id.messageLayout)
            View message_layout;

            @BindView(R.id.payloadLayout)
            View message_payload_layout;

            @BindView(R.id.message_item_file_download)
            View message_download_button;

            @BindView(R.id.message_item_file_progressbar)
            ProgressBar progressBar;

            @OnClick(R.id.expand)
            public void expand() {
                if (message_payload_layout.getVisibility() != View.VISIBLE) {
                    message_payload_layout.setVisibility(View.VISIBLE);
                    message_layout.setVisibility(View.GONE);
                } else {
                    message_payload_layout.setVisibility(View.GONE);
                    message_layout.setVisibility(View.VISIBLE);
                }
            }

            @OnClick(R.id.message_item_file_download)
            public void download(View view) {
                view.setEnabled(false);
                message_file_process.setText("0");
                Message msg = mData.get(getAdapterPosition());
                Ln.e(msg.toString());
                if (msg.getRemoteFiles() != null) {
                    RemoteFile file = msg.getRemoteFiles().get(0);
                    agent.downloadFile(
                            file,
                            null,
                            progress -> {
                                message_file_process.setText(String.format("%s", Math.round(progress)));
                            },
                            uri -> {
                                message_file_process.setText("complete");
                                //message_file.setImageURI(uri.getData());
                            }
                    );
                }
            }

            MessageViewHolder(View view) {
                super(view);
                ButterKnife.bind(this, view);
            }
        }
    }

    /**
     * helper to retrieve the path of an image URI
     */
    public static String getPath(final Context context, final Uri uri) {

        // DocumentProvider
        if (DocumentsContract.isDocumentUri(context, uri)) {
            // ExternalStorageProvider
            if (isExternalStorageDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                if ("primary".equalsIgnoreCase(type)) {
                    return Environment.getExternalStorageDirectory() + "/" + split[1];
                }

                // TODO handle non-primary volumes
            }
            // DownloadsProvider
            else if (isDownloadsDocument(uri)) {

                final String id = DocumentsContract.getDocumentId(uri);
                try {
                    String encoded_id = URLEncoder.encode(id, "utf-8");
                    final Uri contentUri = ContentUris.withAppendedId(
                            Uri.parse("content://downloads/public_downloads"), Long.valueOf(encoded_id));
                    return getDataColumn(context, contentUri, null, null);
                } catch (Exception e) {
                    e.printStackTrace();
                    return "";
                }
            }
            // MediaProvider
            else if (isMediaDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                Uri contentUri = null;
                if ("image".equals(type)) {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                } else if ("video".equals(type)) {
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                } else if ("audio".equals(type)) {
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                }

                final String selection = "_id=?";
                final String[] selectionArgs = new String[]{
                        split[1]
                };

                return getDataColumn(context, contentUri, selection, selectionArgs);
            }
        }
        // MediaStore (and general)
        else if ("content".equalsIgnoreCase(uri.getScheme())) {
            return getDataColumn(context, uri, null, null);
        }
        // File
        else if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }

        return null;
    }

    /**
     * Get the value of the data column for this Uri. This is useful for
     * MediaStore Uris, and other file-based ContentProviders.
     *
     * @param context       The context.
     * @param uri           The Uri to query.
     * @param selection     (Optional) Filter used in the query.
     * @param selectionArgs (Optional) Selection arguments used in the query.
     * @return The value of the _data column, which is typically a file path.
     */
    public static String getDataColumn(Context context, Uri uri, String selection,
                                       String[] selectionArgs) {

        Cursor cursor = null;
        final String column = "_data";
        final String[] projection = {
                column
        };

        try {
            cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs,
                    null);
            if (cursor != null && cursor.moveToFirst()) {
                final int column_index = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(column_index);
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return null;
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is ExternalStorageProvider.
     */
    public static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is DownloadsProvider.
     */
    public static boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is MediaProvider.
     */
    public static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }

}
