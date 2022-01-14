package com.hmatalonga.greenhub.fragments;

import android.annotation.TargetApi;
import android.app.AppOpsManager;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.app.Fragment;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.app.sharejourny.Utils.UserPrefs;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.gson.Gson;
import com.google.protobuf.Any;
import com.hmatalonga.greenhub.Config;
import com.hmatalonga.greenhub.R;
import com.hmatalonga.greenhub.events.OpenTaskDetailsEvent;
import com.hmatalonga.greenhub.events.TaskRemovedEvent;
import com.hmatalonga.greenhub.managers.TaskController;
import com.hmatalonga.greenhub.models.Memory;
import com.hmatalonga.greenhub.models.ui.Task;
import com.hmatalonga.greenhub.ui.TaskListActivity;
import com.hmatalonga.greenhub.ui.WelcomeActivity;
import com.hmatalonga.greenhub.ui.adapters.TaskAdapter;
import com.hmatalonga.greenhub.util.SettingsUtils;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link AppsFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class AppsFragment extends Fragment {

    private static final String TAG = "AppsFragment";

    public static AppsFragment newInstance() {
        return new AppsFragment();
    }

    private ArrayList<Task> mTaskList;

    private RecyclerView mRecyclerView;

    private TaskAdapter mAdapter;

    /**
     * The {@link android.support.v4.widget.SwipeRefreshLayout} that detects swipe gestures and
     * triggers callbacks in the app.
     */
    private SwipeRefreshLayout mSwipeRefreshLayout;

    private ProgressBar mLoader;

    private Task mLastKilledApp;

    private long mLastKilledTimestamp;

    private boolean mIsUpdating;

    private int mSortOrderName;

    private int mSortOrderMemory;

    View view;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
    }

    @Override
    public void onStop() {
        EventBus.getDefault().unregister(this);
        super.onStop();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!mIsUpdating) initiateRefresh();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        view = inflater.inflate(R.layout.fragment_apps2, container, false);


        loadComponents(view);


        return  view;
    }


    private void loadComponents(View view) {
        mLoader = view.findViewById(R.id.loader);
        mLastKilledApp = null;
        mSortOrderName = 1;
        mSortOrderMemory = 1;

        FloatingActionButton fab = view.findViewById(R.id.fab);
        Button btnClean = view.findViewById(R.id.btnClean);

        btnClean.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {


                WifiManager wifiManager = (WifiManager) getActivity().getApplicationContext().getSystemService(Context.WIFI_SERVICE);
//                wifiManager.setWifiEnabled(true);
                wifiManager.setWifiEnabled(false);

                String isBluethoothEnable = "close";
                BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                if (mBluetoothAdapter.isEnabled()) {
                    mBluetoothAdapter.disable();
                    isBluethoothEnable = "open";
                }


                if (mTaskList.isEmpty()) {
                    Snackbar.make(
                            v,
                            getString(R.string.task_no_apps_running),
                            Snackbar.LENGTH_LONG
                    ).show();
                    return;
                }

                int apps = 0;
                double memory = 0;
                String message;

                TaskController controller = new TaskController(getActivity());

                for (Task task : mTaskList) {
                    if (!task.isChecked()) continue;
                    controller.killApp(task);
                    memory += task.getMemory();
                    apps++;
                }
                memory = Math.round(memory * 100.0) / 100.0;

                double availableMemory = Memory.getAvailableMemoryMB(getActivity());
                String mMemory = "";
                if (availableMemory > 1000) {
                    mMemory = (Math.round(availableMemory / 1000.0)) + " GB";
                } else {
                    mMemory = availableMemory + " MB";
                }

                UserPrefs userPrefs = new UserPrefs(getActivity());
                String level = userPrefs.getBatteryLevel();
                String health = userPrefs.getHealth();
                String temperature = userPrefs.getTemperature();

                CameraManager mCameraManager = (CameraManager) getActivity().getSystemService(Context.CAMERA_SERVICE);

                try {
                    String mCameraId = mCameraManager.getCameraIdList()[0];
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        mCameraManager.setTorchMode(mCameraId, false);
                    }
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }


                HashMap<String, String> uploadApp = new HashMap<>();
                uploadApp.put("id", userPrefs.getUserId());
                uploadApp.put("level", level);
                uploadApp.put("health", health);
                uploadApp.put("temperature", temperature);
                uploadApp.put("memory", ""+mMemory);
                uploadApp.put("apps", ""+apps);
                uploadApp.put("bluetooth", "turn off");
                uploadApp.put("tourch", "turn off");
                uploadApp.put("date", new Date().toString());
                uploadApp.put("list", new Gson().toJson(mTaskList));


                FirebaseFirestore firebase_db =  FirebaseFirestore.getInstance();


                firebase_db.collection("currentApps").document("" + new Date().toString())
                        .set(uploadApp).addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Toast.makeText(getActivity(), "State has been saved to db", Toast.LENGTH_SHORT).show();
                    }
                }).addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Toast.makeText(getActivity(), "Exception " + e.getMessage(), Toast.LENGTH_SHORT).show();

                    }
                });



                mRecyclerView.setVisibility(View.GONE);
                mLoader.setVisibility(View.VISIBLE);

                initiateRefresh();

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    message = (apps > 0) ?
                            makeMessage(apps) :
                            getString(R.string.task_no_apps_killed);
                } else {
                    message = (apps > 0) ?
                            makeMessage(apps, memory) :
                            getString(R.string.task_no_apps_killed);
                }

                Snackbar.make(
                        v,
                        message,
                        Snackbar.LENGTH_LONG
                ).show();

            }
        });

        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mTaskList.isEmpty()) {
                    Snackbar.make(
                            view,
                            getString(R.string.task_no_apps_running),
                            Snackbar.LENGTH_LONG
                    ).show();
                    return;
                }

                int apps = 0;
                double memory = 0;
                String message;

                TaskController controller = new TaskController(getActivity());

                for (Task task : mTaskList) {
                    if (!task.isChecked()) continue;
                    controller.killApp(task);
                    memory += task.getMemory();
                    apps++;
                }
                memory = Math.round(memory * 100.0) / 100.0;

                mRecyclerView.setVisibility(View.GONE);
                mLoader.setVisibility(View.VISIBLE);

                initiateRefresh();

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    message = (apps > 0) ?
                            makeMessage(apps) :
                            getString(R.string.task_no_apps_killed);
                } else {
                    message = (apps > 0) ?
                            makeMessage(apps, memory) :
                            getString(R.string.task_no_apps_killed);
                }

                Snackbar.make(
                        view,
                        message,
                        Snackbar.LENGTH_LONG
                ).show();
            }
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                !hasSpecialPermission(getActivity())) {
            showPermissionInfoDialog();
        }

        mTaskList = new ArrayList<>();
        mIsUpdating = false;

        setupRefreshLayout(view);

        setupRecyclerView(view);
    }
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onTaskRemovedEvent(TaskRemovedEvent event) {
        updateHeaderInfo();
        mLastKilledApp = event.task;
        mLastKilledTimestamp = System.currentTimeMillis();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onOpenTaskDetailsEvent(OpenTaskDetailsEvent event) {
        startActivity(new Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + event.task.getPackageInfo().packageName)
        ));
    }


    private void sortTasksBy(final int filter, final int order) {
        if (filter == Config.SORT_BY_MEMORY) {
            // Sort by memory
            Collections.sort(mTaskList, new Comparator<Task>() {
                @Override
                public int compare(Task t1, Task t2) {
                    int result;
                    if (t1.getMemory() < t2.getMemory()) {
                        result = -1;
                    } else if (t1.getMemory() == t2.getMemory()) {
                        result = 0;
                    } else {
                        result = 1;
                    }
                    return order * result;
                }
            });
        } else if (filter == Config.SORT_BY_NAME) {
            // Sort by name
            Collections.sort(mTaskList, new Comparator<Task>() {
                @Override
                public int compare(Task t1, Task t2) {
                    return order * t1.getLabel().compareTo(t2.getLabel());
                }
            });
        }
        mAdapter.notifyDataSetChanged();
    }

    private String makeMessage(int apps) {
        return getString(R.string.task_killed) + " " + apps + " apps!";
    }

    private String makeMessage(int apps, double memory) {
        return getString(R.string.task_killed) + " " + apps + " apps! " +
                getString(R.string.task_cleared) + " " + memory + " MB";
    }

    private void setupRecyclerView(View view) {
        mRecyclerView = view.findViewById(R.id.rv);

        // use this setting to improve performance if you know that changes
        // in content do not change the layout size of the RecyclerView
        mRecyclerView.setHasFixedSize(true);

        // use a linear layout manager
        mRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));

        mAdapter = new TaskAdapter(getActivity(), mTaskList);
        mRecyclerView.setAdapter(mAdapter);

        setUpItemTouchHelper();
        setUpAnimationDecoratorHelper();
    }

    private void setupRefreshLayout(View view) {
        mSwipeRefreshLayout = view.findViewById(R.id.swipe_layout);
        //noinspection ResourceAsColor
        if (Build.VERSION.SDK_INT >= 23) {
            mSwipeRefreshLayout.setColorSchemeColors(
                    getActivity().getColor(R.color.color_accent),
                    getActivity().getColor(R.color.color_primary_dark)
            );
        } else {
            final Context context = getActivity().getApplicationContext();
            mSwipeRefreshLayout.setColorSchemeColors(
                    ContextCompat.getColor(context, R.color.color_accent),
                    ContextCompat.getColor(context, R.color.color_primary_dark)
            );
        }
        mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                if (!mIsUpdating) initiateRefresh();
            }
        });
    }

    /**
     * This is the standard support library way of implementing "swipe to delete" feature.
     * You can do custom drawing in onChildDraw method but whatever you draw will
     * disappear once the swipe is over, and while the items are animating to their
     * new position the recycler view background will be visible.
     * That is rarely an desired effect.
     */
    private void setUpItemTouchHelper() {
        ItemTouchHelper.SimpleCallback simpleItemTouchCallback =
                new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {

                    // we want to cache these and not allocate anything repeatedly in the onChildDraw method
                    Drawable background;
                    Drawable xMark;
                    int xMarkMargin;
                    boolean initiated;

                    private void init() {
                        background = new ColorDrawable(Color.DKGRAY);
                        xMark = ContextCompat.getDrawable(
                                getActivity(), R.drawable.ic_delete_white_24dp
                        );
                        xMark.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_ATOP);
                        xMarkMargin = (int) getActivity().getResources()
                                .getDimension(R.dimen.fab_margin);
                        initiated = true;
                    }

                    // not important, we don't want drag & drop
                    @Override
                    public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder,
                                          RecyclerView.ViewHolder target) {
                        return false;
                    }

                    @Override
                    public int getSwipeDirs(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
                        int position = viewHolder.getAdapterPosition();
                        TaskAdapter testAdapter = (TaskAdapter) recyclerView.getAdapter();
                        if (testAdapter.isUndoOn() && testAdapter.isPendingRemoval(position)) {
                            return 0;
                        }
                        return super.getSwipeDirs(recyclerView, viewHolder);
                    }

                    @Override
                    public void onSwiped(RecyclerView.ViewHolder viewHolder, int swipeDir) {
                        int swipedPosition = viewHolder.getAdapterPosition();
                        TaskAdapter adapter = (TaskAdapter) mRecyclerView.getAdapter();
                        boolean undoOn = adapter.isUndoOn();
                        if (undoOn) {
                            adapter.pendingRemoval(swipedPosition);
                        } else {
                            adapter.remove(swipedPosition);
                        }
                    }

                    @Override
                    public void onChildDraw(Canvas canvas, RecyclerView recyclerView,
                                            RecyclerView.ViewHolder viewHolder, float dX, float dY,
                                            int actionState, boolean isCurrentlyActive) {

                        View itemView = viewHolder.itemView;

                        // not sure why, but this method get's called
                        // for viewholder that are already swiped away
                        if (viewHolder.getAdapterPosition() == -1) {
                            // not interested in those
                            return;
                        }

                        if (!initiated) {
                            init();
                        }

                        // draw background
                        background.setBounds(
                                itemView.getRight() + (int) dX,
                                itemView.getTop(),
                                itemView.getRight(),
                                itemView.getBottom()
                        );
                        background.draw(canvas);

                        // draw x mark
                        int itemHeight = itemView.getBottom() - itemView.getTop();
                        int intrinsicWidth = xMark.getIntrinsicWidth();
                        int intrinsicHeight = xMark.getIntrinsicWidth();

                        int xMarkLeft = itemView.getRight() - xMarkMargin - intrinsicWidth;
                        int xMarkRight = itemView.getRight() - xMarkMargin;
                        int xMarkTop = itemView.getTop() + (itemHeight - intrinsicHeight) / 2;
                        int xMarkBottom = xMarkTop + intrinsicHeight;
                        xMark.setBounds(xMarkLeft, xMarkTop, xMarkRight, xMarkBottom);

                        xMark.draw(canvas);

                        super.onChildDraw(canvas, recyclerView, viewHolder,
                                dX, dY, actionState, isCurrentlyActive);
                    }

                };
        ItemTouchHelper mItemTouchHelper = new ItemTouchHelper(simpleItemTouchCallback);
        mItemTouchHelper.attachToRecyclerView(mRecyclerView);
    }

    /**
     * We're gonna setup another ItemDecorator that will draw the red background
     * in the empty space while the items are animating to thier new positions
     * after an item is removed.
     */
    private void setUpAnimationDecoratorHelper() {
        mRecyclerView.addItemDecoration(new RecyclerView.ItemDecoration() {

            // we want to cache this and not allocate anything repeatedly in the onDraw method
            Drawable background;
            boolean initiated;

            private void init() {
                background = new ColorDrawable(Color.DKGRAY);
                initiated = true;
            }

            @Override
            public void onDraw(Canvas canvas, RecyclerView parent, RecyclerView.State state) {

                if (!initiated) {
                    init();
                }

                // only if animation is in progress
                if (parent.getItemAnimator().isRunning()) {

                    // some items might be animating down and some items might be
                    // animating up to close the gap left by the removed item
                    // this is not exclusive, both movement can be happening at the same time
                    // to reproduce this leave just enough items so the first one
                    // and the last one would be just a little off screen
                    // then remove one from the middle

                    // find first child with translationY > 0
                    // and last one with translationY < 0
                    // we're after a rect that is not covered in recycler-view views
                    // at this point in time
                    View lastViewComingDown = null;
                    View firstViewComingUp = null;

                    // this is fixed
                    int left = 0;
                    int right = parent.getWidth();

                    // this we need to find out
                    int top = 0;
                    int bottom = 0;

                    // find relevant translating views
                    int childCount = parent.getLayoutManager().getChildCount();
                    for (int i = 0; i < childCount; i++) {
                        View child = parent.getLayoutManager().getChildAt(i);
                        if (child.getTranslationY() < 0) {
                            // view is coming down
                            lastViewComingDown = child;
                        } else if (child.getTranslationY() > 0) {
                            // view is coming up
                            if (firstViewComingUp == null) {
                                firstViewComingUp = child;
                            }
                        }
                    }

                    if (lastViewComingDown != null && firstViewComingUp != null) {
                        // views are coming down AND going up to fill the void
                        top = lastViewComingDown.getBottom() +
                                (int) lastViewComingDown.getTranslationY();
                        bottom = firstViewComingUp.getTop() +
                                (int) firstViewComingUp.getTranslationY();
                    } else if (lastViewComingDown != null) {
                        // views are going down to fill the void
                        top = lastViewComingDown.getBottom() +
                                (int) lastViewComingDown.getTranslationY();
                        bottom = lastViewComingDown.getBottom();
                    } else if (firstViewComingUp != null) {
                        // views are coming up to fill the void
                        top = firstViewComingUp.getTop();
                        bottom = firstViewComingUp.getTop() +
                                (int) firstViewComingUp.getTranslationY();
                    }

                    background.setBounds(left, top, right, bottom);
                    background.draw(canvas);

                }
                super.onDraw(canvas, parent, state);
            }

        });
    }

    /**
     * By abstracting the refresh process to a single method, the app allows both the
     * SwipeGestureLayout onRefresh() method and the Refresh action item to refresh the content.
     */
    private void initiateRefresh() {
        mIsUpdating = true;
        setHeaderToRefresh();
        /**
         * Execute the background task, which uses {@link android.os.AsyncTask} to load the data.
         */
        new LoadRunningProcessesTask().execute(getActivity());
    }

    /**
     * When the AsyncTask finishes, it calls onRefreshComplete(), which updates the data in the
     * ListAdapter and turns off the progress bar.
     */
    private void onRefreshComplete(List<Task> result) {
        if (mLoader.getVisibility() == View.VISIBLE) {
            mLoader.setVisibility(View.GONE);
            mRecyclerView.setVisibility(View.VISIBLE);
        }

        // Remove all items from the ListAdapter, and then replace them with the new items
        mAdapter.swap(result);
        mIsUpdating = false;
        updateHeaderInfo();
        // Stop the refreshing indicator
        mSwipeRefreshLayout.setRefreshing(false);
    }

    private void updateHeaderInfo() {
        String text;
        TextView textView = view.findViewById(R.id.count);
        text = "Apps " + mTaskList.size();
        textView.setText(text);
        textView = view.findViewById(R.id.usage);
        double memory = Memory.getAvailableMemoryMB(getActivity());
        if (memory > 1000) {
            text = getString(R.string.task_free_ram) + " " +
                    (Math.round(memory / 1000.0)) + " GB";
        } else {
            text = getString(R.string.task_free_ram) + " " + memory + " MB";
        }
        textView.setText(text);
    }

    private void setHeaderToRefresh() {
        TextView textView = view.findViewById(R.id.count);
        textView.setText(getString(R.string.header_status_loading));
        textView = view.findViewById(R.id.usage);
        textView.setText("");
    }

    private double getTotalUsage(List<Task> list) {
        double usage = 0;
        for (Task task : list) {
            usage += task.getMemory();
        }
        return Math.round(usage * 100.0) / 100.0;
    }

    private boolean isKilledAppAlive(final String label) {
        long now = System.currentTimeMillis();
        if (mLastKilledTimestamp < (now - Config.KILL_APP_TIMEOUT)) {
            mLastKilledApp = null;
            return false;
        }
        for (Task task : mTaskList) {
            if (task.getLabel().equals(label)) {
                return true;
            }
        }
        return false;
    }

    private void checkIfLastAppIsKilled() {
        if (mLastKilledApp != null && isKilledAppAlive(mLastKilledApp.getLabel())) {
            final String packageName = mLastKilledApp.getPackageInfo().packageName;

            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

            builder.setMessage(getString(R.string.kill_app_dialog_text))
                    .setTitle(mLastKilledApp.getLabel());

            builder.setPositiveButton(R.string.force_close, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    // User clicked OK button
                    startActivity(new Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + packageName)
                    ));
                }
            });
            builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    // User cancelled the dialog
                    dialog.cancel();
                }
            });

            builder.create().show();
        }
        mLastKilledApp = null;
    }

    @TargetApi(21)
    private boolean hasSpecialPermission(final Context context) {
        AppOpsManager appOps = (AppOpsManager) context
                .getSystemService(Context.APP_OPS_SERVICE);
        int mode = appOps.checkOpNoThrow("android:get_usage_stats",
                android.os.Process.myUid(), context.getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    @TargetApi(21)
    private void showPermissionInfoDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        builder.setMessage(getString(R.string.package_usage_permission_text))
                .setTitle(getString(R.string.package_usage_permission_title));

        builder.setPositiveButton(R.string.open_settings, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                // User clicked OK button
                startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
            }
        });
        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                // User cancelled the dialog
                dialog.cancel();
            }
        });

        builder.create().show();
    }

    private class LoadRunningProcessesTask extends AsyncTask<Context, Void, List<Task>> {
        @Override
        protected List<Task> doInBackground(Context... params) {
            TaskController taskController = new TaskController(params[0]);
            return taskController.getRunningTasks();
        }

        @Override
        protected void onPostExecute(List<Task> result) {
            super.onPostExecute(result);
            onRefreshComplete(result);
            checkIfLastAppIsKilled();
        }

    }
}