package doext.implement;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;

import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.media.MediaRecorder.OnErrorListener;
import android.media.MediaRecorder.OnInfoListener;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import core.DoServiceContainer;
import core.helper.DoJsonHelper;
import core.helper.DoTextHelper;
import core.helper.DoUIModuleHelper;
import core.interfaces.DoBaseActivityListener;
import core.interfaces.DoIPageView;
import core.interfaces.DoIScriptEngine;
import core.interfaces.DoIUIModuleView;
import core.object.DoInvokeResult;
import core.object.DoUIModule;
import doext.define.do_VideoRecord_IMethod;
import doext.define.do_VideoRecord_MAbstract;

/**
 * 自定义扩展UIView组件实现类，此类必须继承相应VIEW类，并实现DoIUIModuleView,do_VideoRecord_IMethod接口；
 * #如何调用组件自定义事件？可以通过如下方法触发事件：
 * this.model.getEventCenter().fireEvent(_messageName, jsonResult);
 * 参数解释：@_messageName字符串事件名称，@jsonResult传递事件参数对象； 获取DoInvokeResult对象方式new
 * DoInvokeResult(this.model.getUniqueKey());
 */
public class do_VideoRecord_View extends SurfaceView implements DoIUIModuleView, do_VideoRecord_IMethod, SurfaceHolder.Callback, DoBaseActivityListener {

	private boolean mStartedFlg = false;
	private MediaRecorder mRecorder;
	private SurfaceHolder mSurfaceHolder;
	private String fileFullName;
	private String fileName;
	/**
	 * 每个UIview都会引用一个具体的model实例；
	 */
	private do_VideoRecord_MAbstract model;

	/** 最大帧率 */
	private int MAX_FRAME_RATE = 25;
	/** 最小帧率 */
	private int MIN_FRAME_RATE = 15;

	/** 摄像头参数 */
	private Camera.Parameters mParameters = null;

	/** 帧率 */
	protected int mFrameRate = MIN_FRAME_RATE;

	public do_VideoRecord_View(Context context) {
		super(context);
		SurfaceHolder holder = this.getHolder();// 取得holder
		holder.addCallback(this); // holder加入回调接口
		// setType必须设置，要不出错.
		holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
	}

	private void fireFinish() {
		try {
			File _file = new File(fileFullName);
			long _size = 0;
			if (_file.exists()) {
				_size = _file.length() / 1024;
			}
			DoInvokeResult _result = new DoInvokeResult(model.getUniqueKey());
			JSONObject _obj = new JSONObject();
			_obj.put("path", "data://" + fileName);
			_obj.put("size", _size);
			_result.setResultNode(_obj);
			model.getEventCenter().fireEvent("finish", _result);

		} catch (Exception e) {
			DoServiceContainer.getLogEngine().writeError("do_VideoRecord finish", e);
		}
	}

	/**
	 * 初始化加载view准备,_doUIModule是对应当前UIView的model实例
	 */
	@Override
	public void loadView(DoUIModule _doUIModule) throws Exception {
		this.model = (do_VideoRecord_MAbstract) _doUIModule;
		((DoIPageView) DoServiceContainer.getPageViewFactory().getAppContext()).setBaseActivityListener(this);
		startPreview();
	}

	/**
	 * 动态修改属性值时会被调用，方法返回值为true表示赋值有效，并执行onPropertiesChanged，否则不进行赋值；
	 * 
	 * @_changedValues<key,value>属性集（key名称、value值）；
	 */
	@Override
	public boolean onPropertiesChanging(Map<String, String> _changedValues) {
		return true;
	}

	/**
	 * 属性赋值成功后被调用，可以根据组件定义相关属性值修改UIView可视化操作；
	 * 
	 * @_changedValues<key,value>属性集（key名称、value值）；
	 */
	@Override
	public void onPropertiesChanged(Map<String, String> _changedValues) {
		DoUIModuleHelper.handleBasicViewProperChanged(this.model, _changedValues);
	}

	/**
	 * 同步方法，JS脚本调用该组件对象方法时会被调用，可以根据_methodName调用相应的接口实现方法；
	 * 
	 * @_methodName 方法名称
	 * @_dictParas 参数（K,V），获取参数值使用API提供DoJsonHelper类；
	 * @_scriptEngine 当前Page JS上下文环境对象
	 * @_invokeResult 用于返回方法结果对象
	 */
	@Override
	public boolean invokeSyncMethod(String _methodName, JSONObject _dictParas, DoIScriptEngine _scriptEngine, DoInvokeResult _invokeResult) throws Exception {

		if ("start".equals(_methodName)) {
			this.start(_dictParas, _scriptEngine, _invokeResult);
			return true;
		}
		if ("stop".equals(_methodName)) {
			this.stop(_dictParas, _scriptEngine, _invokeResult);
			return true;
		}
		return false;
	}

	/**
	 * 异步方法（通常都处理些耗时操作，避免UI线程阻塞），JS脚本调用该组件对象方法时会被调用， 可以根据_methodName调用相应的接口实现方法；
	 * 
	 * @_methodName 方法名称
	 * @_dictParas 参数（K,V），获取参数值使用API提供DoJsonHelper类；
	 * @_scriptEngine 当前page JS上下文环境
	 * @_callbackFuncName 回调函数名 #如何执行异步方法回调？可以通过如下方法：
	 *                    _scriptEngine.callback(_callbackFuncName,
	 *                    _invokeResult);
	 *                    参数解释：@_callbackFuncName回调函数名，@_invokeResult传递回调函数参数对象；
	 *                    获取DoInvokeResult对象方式new
	 *                    DoInvokeResult(this.model.getUniqueKey());
	 */
	@Override
	public boolean invokeAsyncMethod(String _methodName, JSONObject _dictParas, DoIScriptEngine _scriptEngine, String _callbackFuncName) {
		return false;
	}

	/**
	 * 释放资源处理，前端JS脚本调用closePage或执行removeui时会被调用；
	 */
	@Override
	public void onDispose() {
		mSurfaceHolder = null;
		stopRecord();
	}

	/**
	 * 重绘组件，构造组件时由系统框架自动调用；
	 * 或者由前端JS脚本调用组件onRedraw方法时被调用（注：通常是需要动态改变组件（X、Y、Width、Height）属性时手动调用）
	 */
	@Override
	public void onRedraw() {
		this.setLayoutParams(DoUIModuleHelper.getLayoutParams(this.model));
	}

	/**
	 * 获取当前model实例
	 */
	@Override
	public DoUIModule getModel() {
		return model;
	}

	Camera camera;

	/**
	 * 开始录制视频；
	 * 
	 * @_dictParas 参数（K,V），可以通过此对象提供相关方法来获取参数值（Key：为参数名称）；
	 * @_scriptEngine 当前Page JS上下文环境对象
	 * @_invokeResult 用于返回方法结果对象
	 */
	@SuppressWarnings("deprecation")
	@Override
	public void start(JSONObject _dictParas, DoIScriptEngine _scriptEngine, DoInvokeResult _invokeResult) throws Exception {
		if (mStartedFlg) {
			return;
		}
		// 支持high(1920*1080)、normal(1280*720)、low(640*480)
		String _quality = DoJsonHelper.getString(_dictParas, "quality", "normal");
		int _limit = DoJsonHelper.getInt(_dictParas, "limit", -1);

		if (mRecorder == null) {
			mRecorder = new MediaRecorder(); // Create MediaRecorder
			mRecorder.setOnErrorListener(new OnErrorListener() {
				@Override
				public void onError(MediaRecorder mr, int what, int extra) {
					model.getEventCenter().fireEvent("error", new DoInvokeResult(model.getUniqueKey()));
					stopRecord();
				}
			});
			mRecorder.setOnInfoListener(new OnInfoListener() {
				@Override
				public void onInfo(MediaRecorder mr, int what, int extra) {
					if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
						stopRecord();
						fireFinish();
					}
				}
			});
		}

		if (camera == null) {
			camera = Camera.open(Camera.CameraInfo.CAMERA_FACING_BACK);
		}

		if (camera != null) {
			camera.setDisplayOrientation(90);// 摄像图旋转90度
			camera.unlock();
			mRecorder.setCamera(camera);
		}
		// 这两项需要放在setOutputFormat之前
		mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
		mRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);

		CamcorderProfile profile = CamcorderProfile.get(CamcorderProfile.QUALITY_LOW);
		profile.videoFrameWidth = 640;
		profile.videoFrameHeight = 480;
		profile.fileFormat = MediaRecorder.OutputFormat.MPEG_4;
		profile.videoCodec = MediaRecorder.VideoEncoder.H264;
		profile.audioCodec = MediaRecorder.AudioEncoder.AAC;
		profile.videoFrameRate = 15;
		mRecorder.setProfile(profile);
		mRecorder.setOrientationHint(90);// 视频旋转90度
		// Set output file format
//		mRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
//		// 这两项需要放在setOutputFormat之后
//		mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
//		mRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H263);
		mRecorder.setVideoSize(640, 480);// 设置分辨率
		if ("high".equals(_quality)) {

			mRecorder.setVideoEncodingBitRate(8 * 1024 * 1024);// 清晰度
		} else if ("normal".equals(_quality)) {

			mRecorder.setVideoEncodingBitRate(5 * 1024 * 1024);
		} else {

			mRecorder.setVideoEncodingBitRate(2 * 1024 * 1024);
		}

		// 设置录制的视频帧率。必须放在设置编码和格式的后面，否则报错
		mRecorder.setVideoFrameRate(30);

		if (_limit > 0) {
			mRecorder.setMaxDuration(_limit);
		}

		mRecorder.setPreviewDisplay(mSurfaceHolder.getSurface());
		fileName = "temp/do_VideoRecord/" + DoTextHelper.getTimestampStr() + ".mp4";
		fileFullName = _scriptEngine.getCurrentApp().getDataFS().getRootPath() + File.separator + fileName;
		File _parentFile = new File(fileFullName).getParentFile();
		if (!_parentFile.exists()) {
			_parentFile.mkdirs();
		}
		mRecorder.setOutputFile(fileFullName);

		mRecorder.prepare();
		mRecorder.start(); // Recording is now started
		mStartedFlg = true;
	}

	/**
	 * 停止录制视频；
	 * 
	 * @_dictParas 参数（K,V），可以通过此对象提供相关方法来获取参数值（Key：为参数名称）；
	 * @_scriptEngine 当前Page JS上下文环境对象
	 * @_invokeResult 用于返回方法结果对象
	 */
	@Override
	public void stop(JSONObject _dictParas, DoIScriptEngine _scriptEngine, DoInvokeResult _invokeResult) throws Exception {
		stopRecord();
		fireFinish();
		startPreview();
	}

	public void stopRecord() {
		if (!mStartedFlg) {
			return;
		}
		if (mRecorder != null) {
			mRecorder.stop();
			// 释放资源
			mRecorder.release();
			mRecorder = null;
		}

		if (camera != null) {
			camera.release();
			camera = null;
		}
		mStartedFlg = false; // Set button status flag
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		// 将holder，这个holder为开始在onCreate里面取得的holder，将它赋给mSurfaceHolder
		mSurfaceHolder = holder;
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		// 将holder，这个holder为开始在onCreate里面取得的holder，将它赋给mSurfaceHolder
		mSurfaceHolder = holder;
		startPreview();
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		// surfaceDestroyed的时候同时对象设置为null
		destroyed();

	}

	/**
	 * 设置预览输出SurfaceHolder
	 * 
	 * @param sh
	 */
//	@SuppressWarnings("deprecation")
//	public void setSurfaceHolder(SurfaceHolder sh) {
//		if (sh != null) {
//			sh.addCallback(this);
//			if (!DeviceUtils.hasHoneycomb()) {
//				sh.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
//			}
//		}
//	}

	/** 开始预览 */
	private void startPreview() {

		try {
			if (camera == null) {
				camera = Camera.open(Camera.CameraInfo.CAMERA_FACING_BACK);
			}
			camera.setDisplayOrientation(90);
			try {
				camera.setPreviewDisplay(mSurfaceHolder);
			} catch (IOException e) {
				Log.e("do_VideoRecord_View", "setPreviewDisplay fail " + e.getMessage());
			}

			// 设置摄像头参数
			mParameters = camera.getParameters();
			prepareCameraParaments();
			camera.setParameters(mParameters);
			camera.startPreview();
		} catch (Exception e) {
			e.printStackTrace();
			Log.e("do_VideoRecord_View", "startPreview fail :" + e.getMessage());
		}
	}

	/**
	 * 预处理一些拍摄参数
	 * 
	 */
	@SuppressWarnings("deprecation")
	private void prepareCameraParaments() {
		if (mParameters == null)
			return;

		List<Integer> rates = mParameters.getSupportedPreviewFrameRates();
		if (rates != null) {
			if (rates.contains(MAX_FRAME_RATE)) {
				mFrameRate = MAX_FRAME_RATE;
			} else {
				Collections.sort(rates);
				for (int i = rates.size() - 1; i >= 0; i--) {
					if (rates.get(i) <= MAX_FRAME_RATE) {
						mFrameRate = rates.get(i);
						break;
					}
				}
			}
		}
		mParameters.setPreviewFrameRate(mFrameRate);
		mParameters.setPreviewSize(640, 480);// 3:2
		// 设置输出视频流尺寸，采样率
		mParameters.setPreviewFormat(ImageFormat.NV21);

	}

	@Override
	public void onResume() {
	}

	@Override
	public void onPause() {
		destroyed();
	}

	@Override
	public void onRestart() {
	}

	@Override
	public void onStop() {
	}

	private void destroyed() {
		mSurfaceHolder = null;
		stopRecord();
		if (camera != null) {
			camera.stopPreview();
			camera.release();
			camera = null;
		}
	}
}