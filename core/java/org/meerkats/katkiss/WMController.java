package org.meerkats.katkiss;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;
import java.util.Map.Entry;
import android.os.AsyncTask;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningTaskInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.content.res.Resources;
import android.provider.Settings;
import android.util.Log;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.view.IWindowManager;
import android.view.WindowManagerGlobal;
import com.android.internal.statusbar.IStatusBarService;

public class WMController 
{
	public static final boolean DEBUG = true;
	private static final String TAG = "WMController";
	private static float[] _previousAnimationScales;
	private IWindowManager _wm;
	Context _c;
	RunningTaskInfo _topTask;
	RunningTaskInfo _prevTask;
	boolean _isTopTaskSplitView = false;
	boolean _isPrevTaskSplitView = false;

	
	public WMController(Context c)
	{
		_c = c;
		_wm = (IWindowManager) WindowManagerGlobal.getWindowManagerService();
	}
	
	public synchronized float[] getAnimationScales()
	{
		final float[] anims = new float[3];
		for(int i=0; i<anims.length; i++)
			try {anims[i] = _wm.getAnimationScale(i);} catch(Exception e) {}
		return anims;
	}

	public synchronized void setAnimationScales(final float[] anims)
	{
		for(int i=0; i<anims.length; i++)
			try {_wm.setAnimationScale(i, anims[i]);} catch(Exception e) {Log.e(TAG, e.toString());}
	}

	public synchronized void saveAnimationScales()	{ if(_previousAnimationScales == null) _previousAnimationScales = getAnimationScales();	}
	public synchronized void restoreAnimationScales()	{ setAnimationScales(_previousAnimationScales);	}
	public synchronized void disableAnimationScales()	
	{ 
		final float[] noAnims = {0,0,0};
		saveAnimationScales();
		setAnimationScales(noAnims);
	}
	
	private String getDefaultLauncherPackage()
	{
		String defaultHomePackage = "com.android.launcher";
		final Intent intent = new Intent(Intent.ACTION_MAIN);
		intent.addCategory(Intent.CATEGORY_HOME);

		final ResolveInfo res = _c.getPackageManager().resolveActivity(intent, 0);
		if (res.activityInfo != null && !res.activityInfo.packageName.equals("android")) {
			defaultHomePackage = res.activityInfo.packageName;
		}
		return defaultHomePackage;
	}

	private boolean isDefaultLauncherOrSystemUI(String packageName)
	{
		return (packageName.equals(getDefaultLauncherPackage()) || packageName.equals("com.android.systemui"));
	}

	private RunningTaskInfo getTaskBeforeTop() { return getTask(1, false); }
	private RunningTaskInfo getTopTask() { return getTask(0, false); }

	private RunningTaskInfo getFirstSplitViewTaskBeforeTop() { return getTask(1, true); }
	private RunningTaskInfo getTopSplitViewTask() { return getTask(0, true); }

	private RunningTaskInfo getTask(int nTasksBeforeTop, boolean splitViewTaskOnly)
	{
		if(_c == null) return null; 

		RunningTaskInfo taskFound = null;;
		int current = nTasksBeforeTop;
		final ActivityManager am = (ActivityManager) _c.getSystemService(Activity.ACTIVITY_SERVICE);
		List <ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(10);

		try
		{
			while ((taskFound == null) && (current < tasks.size())) 
			{
				RunningTaskInfo currentTask = tasks.get(current);
				boolean isSplitViewTask = isTaskSplitView(currentTask.id);

				String packageName = currentTask.topActivity.getPackageName();
				if (!isDefaultLauncherOrSystemUI(packageName))
				{
					if(splitViewTaskOnly)
					{
						if(isSplitViewTask) taskFound = currentTask;	        	
					}
					else
						taskFound = tasks.get(current);
				}
				current++;
			}
		}catch (Exception e) {}
		Log.v(TAG, "getTask:"+(taskFound!=null ? taskFound.baseActivity:null));

		return taskFound;
	}

	public boolean isTaskSplitView(int taskID)
	{
		try { return _wm.isTaskSplitView(taskID); }
		catch(Exception e) {}
		return false;
	}

	private synchronized void switchTaskToSplitView(final int taskID, final boolean split, final int moveFlags,final int delayMS)
	{
		AsyncTask.execute(new Runnable() {
			public void run()
			{
				if(delayMS >0) try{Thread.sleep(delayMS);} catch(Exception e) {}
				switchTaskToSplitView(taskID, split, moveFlags);
			} });
	}

	private void setWMTaskFlagSplitView(int taskID, boolean split)
	{
		try { 	_wm.setTaskSplitView(taskID, split); }
		catch(Exception e) {}
		
	}
	public synchronized void switchTaskToSplitView(int taskID, boolean split, int moveFlags)
	{
			setWMTaskFlagSplitView(taskID, split);

			final ActivityManager am = (ActivityManager) _c.getSystemService(Context.ACTIVITY_SERVICE);
			//am.moveTaskToFront(taskID, ActivityManager.MOVE_TASK_WITH_HOME, null);
			am.moveTaskToFront(taskID, moveFlags, null);
	}

	private synchronized  void switchToPreviousTask(final int delayMS)
	{ 
		AsyncTask.execute(new Runnable() {
			public void run() 
			{
				if(delayMS >0) try{Thread.sleep(delayMS);} catch(Exception e) {}
				switchToPreviousTask();
			} });
	}

	public synchronized void switchToPreviousTask()
	{
		RunningTaskInfo task = getTaskBeforeTop();
		if(task == null) return;

		final ActivityManager am = (ActivityManager) _c.getSystemService(Context.ACTIVITY_SERVICE);
		am.moveTaskToFront(task.id, 0, null);
	}

	public synchronized void refreshTopAndPrevTasks()
	{
		_topTask = getTopTask();
		_prevTask = getTaskBeforeTop();
		if(_topTask != null) _isTopTaskSplitView = isTaskSplitView(_topTask.id);
		if(_prevTask != null) _isPrevTaskSplitView = isTaskSplitView(_prevTask.id);
		Log.v(TAG, "refreshTopAndPrevTasks:topTask="
			+ (_topTask!=null ? _topTask.baseActivity:null) 
			+ " prevTask="+ (_prevTask!=null ? _prevTask.baseActivity:null)
			+ " isTopTaskSplitView="+_isTopTaskSplitView 
			+ " isPrevTaskSplitView="+_isPrevTaskSplitView );
	}
	
	public synchronized void switchTopTaskToSplitView()
	{
		Log.d(TAG, "switchTopTaskToSplitView++");

		disableAnimationScales();
		refreshTopAndPrevTasks();
		if(_topTask == null) return;

		if(_prevTask != null)
		{	
			if(_isTopTaskSplitView) setWMTaskFlagSplitView(_prevTask.id, false);
			else switchTaskToSplitView(_prevTask.id, !_isTopTaskSplitView,  0);
		}
		if(_topTask != null) switchTaskToSplitView(_topTask.id, !_isTopTaskSplitView,  (_prevTask == null || _isTopTaskSplitView)? ActivityManager.MOVE_TASK_WITH_HOME :0);

		forceLayout2LastTasks();
		Log.d(TAG, "switchTopTaskToSplitView--");
	}

	private synchronized void forceLayout2LastTasks()
	{
		// Workaround to force relayout of apps that don't layout cleanly after switching    
		AsyncTask.execute(new Runnable() {
			public void run()
			{
				try{Thread.sleep(200);} catch(Exception e) {}
				switchToPreviousTask();
				try{Thread.sleep(100);} catch(Exception e) {}
				switchToPreviousTask();
				try{Thread.sleep(1000);} catch(Exception e) {}
				restoreAnimationScales();
			} });
	}
	
	public synchronized static void showRecentAppsSystemUI() 
	{
		try { IStatusBarService.Stub.asInterface(ServiceManager.getService("statusbar")).toggleRecentApps(); } 
		catch (Exception e) { }
	}
}
