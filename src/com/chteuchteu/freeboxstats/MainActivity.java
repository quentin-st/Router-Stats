package com.chteuchteu.freeboxstats;

import java.text.DecimalFormat;
import java.text.FieldPosition;
import java.text.Format;
import java.text.ParsePosition;

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.DrawerLayout;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.androidplot.ui.AnchorPosition;
import com.androidplot.ui.DynamicTableModel;
import com.androidplot.ui.SizeLayoutType;
import com.androidplot.ui.SizeMetrics;
import com.androidplot.ui.XLayoutStyle;
import com.androidplot.ui.YLayoutStyle;
import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.PointLabelFormatter;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.XValueMarker;
import com.androidplot.xy.XYPlot;
import com.androidplot.xy.XYStepMode;
import com.applovin.adview.AppLovinAdView;
import com.applovin.sdk.AppLovinAd;
import com.applovin.sdk.AppLovinAdLoadListener;
import com.applovin.sdk.AppLovinAdSize;
import com.applovin.sdk.AppLovinSdk;
import com.astuetz.PagerSlidingTabStrip;
import com.chteuchteu.freeboxstats.hlpr.Enums.AuthorizeStatus;
import com.chteuchteu.freeboxstats.hlpr.Enums.FieldType;
import com.chteuchteu.freeboxstats.hlpr.Enums.GraphPrecision;
import com.chteuchteu.freeboxstats.hlpr.Enums.Period;
import com.chteuchteu.freeboxstats.hlpr.Enums.Unit;
import com.chteuchteu.freeboxstats.hlpr.SettingsHelper;
import com.chteuchteu.freeboxstats.hlpr.Util;
import com.chteuchteu.freeboxstats.hlpr.Util.Fonts.CustomFont;
import com.chteuchteu.freeboxstats.net.AskForAppToken;
import com.chteuchteu.freeboxstats.net.BillingService;
import com.chteuchteu.freeboxstats.net.FreeboxDiscoverer;
import com.chteuchteu.freeboxstats.net.ManualGraphLoader;
import com.chteuchteu.freeboxstats.net.OutagesFetcher;
import com.chteuchteu.freeboxstats.net.SessionOpener;
import com.chteuchteu.freeboxstats.obj.DataSet;
import com.chteuchteu.freeboxstats.obj.Freebox;
import com.chteuchteu.freeboxstats.obj.GraphsContainer;
import com.crashlytics.android.Crashlytics;

public class MainActivity extends FragmentActivity {
	public static FragmentActivity activity;
	public static Context context;
	private static final int NB_TABS = 4;
	private static MainActivityPagerAdapter pagerAdapter;
	private static ViewPager viewPager;
	private ActionBarDrawerToggle drawerToggle;
	private DrawerLayout drawerLayout;
	
	private static Thread refreshThread;
	private static boolean justRefreshed;
	public static final int AUTOREFRESH_TIME = 20000;
	private static boolean graphsDisplayed;
	
	private static XYPlot plot1;
	private static XYPlot plot2;
	private static XYPlot plot3;
	private static XYPlot plot4;
	
	private static MenuItem refreshMenuItem;
	private static MenuItem periodMenuItem;
	private static MenuItem validerMenuItem;
	
	/**
	 * Only display the main activity when
	 * 	- we know if the user is premium or not
	 *  - we are connected to the freebox
	 */
	public static boolean appLoadingPrereq1 = false;
	public static boolean appLoadingPrereq2 = false;
	public static boolean appStarted = false;
	/**
	 * If the app should load ads the next time this boolean is checked
	 */
	private static boolean loadAds;
	
	private static AppLovinAd cachedAd;
	private static AppLovinAdView adView;
	
	public static boolean updating;
	
	@SuppressLint("InlinedApi")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Crashlytics.start(this);
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		setContentView(R.layout.activity_main);
		
		context = this;
		activity = this;
		graphsDisplayed = false;
		justRefreshed = false;
		updating = false;
		loadAds = false;
		
		plot1 = null;
		plot2 = null;
		plot3 = null;
		plot4 = null;
		
		initDrawer();
		
		ActionBar actionBar = getActionBar();
		// Some design
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			int id = getResources().getIdentifier("config_enableTranslucentDecor", "bool", "android");
			if (id != 0 && getResources().getBoolean(id)) { // Translucent available
				Window w = getWindow();
				w.setFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION, WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
				w.setFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS, WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
			}
		}
		actionBar.setBackgroundDrawable(new ColorDrawable(0xff3367D6));
		actionBar.setTitle("");
		
		// Set font
		Util.Fonts.setFont(this, (ViewGroup) findViewById(R.id.viewroot), CustomFont.RobotoCondensed_Light);
		
		FooBox.getInstance().init(this);
	}
	
	public static void displayLoadingScreen() {
		Util.Fonts.setFont(context, (TextView) ((Activity) context).findViewById(R.id.tv_loadingtxt), CustomFont.RobotoCondensed_Light);
		activity.findViewById(R.id.ll_loading).setVisibility(View.VISIBLE);
	}
	
	public static void hideLoadingScreen() {
		activity.findViewById(R.id.ll_loading).setVisibility(View.GONE);
	}
	
	private static void startRefreshThread() {
		if (FooBox.getInstance().getFreebox() == null)
			return;
		
		if (!SettingsHelper.getInstance().getAutoRefresh())
			return;
		
		if (refreshThread != null && refreshThread.isAlive())
			refreshThread.interrupt();
		
		refreshThread = new Thread(new Runnable() {
			@Override
			public void run() {
				while (true) {
					try {
						if (!Thread.interrupted()) {
							Thread.sleep(AUTOREFRESH_TIME);
							if (Util.isScreenOn(context)) {
								if (justRefreshed) {
									justRefreshed = false;
									continue;
								}
								activity.runOnUiThread(new Runnable() {
									@Override
									public void run() {
										refreshGraph();
									}
								});
							}
						} else return;
					} catch (InterruptedException ex) {
						ex.printStackTrace();
						Thread.currentThread().interrupt();
						return;
					}
				}
			}
		});
		refreshThread.start();
	}
	
	public static void stopRefreshThread() {
		if (refreshThread != null && refreshThread.isAlive())
			refreshThread.interrupt();
	}
	
	public static void displayGraphs() {
		if (graphsDisplayed)
			return;
		
		refreshMenuItem.setVisible(true);
		periodMenuItem.setVisible(true);
		
		pagerAdapter = new MainActivityPagerAdapter(activity.getSupportFragmentManager());
		viewPager = (ViewPager) activity.findViewById(R.id.pager);
		viewPager.setAdapter(pagerAdapter);
		
		// Let Android load all the tabs at once (= disable lazy load)
		viewPager.setOffscreenPageLimit(NB_TABS - 1);
		
		PagerSlidingTabStrip tabs = (PagerSlidingTabStrip) activity.findViewById(R.id.tabs);
		tabs.setViewPager(viewPager);
		tabs.setTextColor(Color.WHITE);
		
		graphsDisplayed = true;
	}
	
	public static class MainActivityPagerAdapter extends FragmentStatePagerAdapter {
		public MainActivityPagerAdapter(FragmentManager fm) {
			super(fm);
		}
		
		@Override
		public Fragment getItem(int i) {
			Fragment fragment = new GraphFragment();
			Bundle args = new Bundle();
			args.putInt(GraphFragment.ARG_OBJECT, i+1);
			fragment.setArguments(args);
			return fragment;
		}
		
		@Override
		public int getCount() {
			if (SettingsHelper.getInstance().getDisplayXdslTab())
				return 4;
			else
				return 3;
		}
		
		@Override
		public CharSequence getPageTitle(int position) {
			switch (position) {
				case 0: return activity.getString(R.string.tab1_name);
				case 1: return activity.getString(R.string.tab2_name);
				case 2: return activity.getString(R.string.tab3_name);
				case 3: return activity.getString(R.string.tab4_name);
				default: return "";
			}
		}
	}
	
	public static class GraphFragment extends Fragment {
		public static final String ARG_OBJECT = "object";
		
		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
			View rootView = inflater.inflate(R.layout.fragment_graph, container, false);
			
			Bundle args = getArguments();
			int index = args.getInt(ARG_OBJECT);
			XYPlot plot = (XYPlot) rootView.findViewById(R.id.xyPlot);
			switch (index) {
				case 1: plot1 = plot; break;
				case 2: plot2 = plot; break;
				case 3: plot3 = plot; break;
				case 4: plot4 = plot; break;
			}
			initPlot(plot, index);
			
			return rootView;
		}
	}
	
	private static void initPlot(final XYPlot plot, int plotIndex) {
		plot.setVisibility(View.GONE);
		
		// Styling
		plot.setBorderStyle(XYPlot.BorderStyle.NONE, null, null);
		plot.setPlotMargins(10, 0, 0, 10);
		plot.setPlotPadding(0, 0, 0, 0);
		plot.setGridPadding(4, 10, 15, 0);
		plot.getGraphWidget().setGridPaddingRight(15);
		plot.getGraphWidget().setGridPaddingTop(15);
		plot.getGraphWidget().setPaddingLeft(15);
		plot.getGraphWidget().getBackgroundPaint().setColor(Color.TRANSPARENT);
		plot.getGraphWidget().getGridBackgroundPaint().setColor(Color.TRANSPARENT);
		plot.getGraphWidget().getDomainLabelPaint().setColor(Color.GRAY);
		plot.getGraphWidget().getDomainGridLinePaint().setColor(Color.TRANSPARENT);
		plot.getGraphWidget().getDomainOriginLabelPaint().setColor(Color.GRAY);
		plot.getGraphWidget().getDomainOriginLinePaint().setColor(Color.GRAY);
		plot.getGraphWidget().getRangeLabelPaint().setColor(Color.GRAY);
		plot.getGraphWidget().getRangeOriginLabelPaint().setColor(Color.GRAY);
		plot.getGraphWidget().getRangeOriginLinePaint().setColor(Color.GRAY);
		
		if (plotIndex == 3) {
			plot.setRangeStep(XYStepMode.INCREMENT_BY_VAL, 10);
			plot.setRangeLowerBoundary(0, BoundaryMode.FIXED);
		} else if (plotIndex == 4) {
			plot.setRangeStep(XYStepMode.INCREMENT_BY_VAL, 1);
			plot.setRangeLowerBoundary(0, BoundaryMode.FIXED);
		}
		
		// Legend
		if (plotIndex == 1 || plotIndex == 2 || plotIndex == 4)
			plot.getLegendWidget().setTableModel(new DynamicTableModel(1, 2));
		else
			plot.getLegendWidget().setTableModel(new DynamicTableModel(2, 2));
		plot.getLegendWidget().setSize(new SizeMetrics(180, SizeLayoutType.ABSOLUTE, 460, SizeLayoutType.ABSOLUTE));
		Paint bgPaint = new Paint();
		bgPaint.setARGB(100, 0, 0, 0);
		bgPaint.setStyle(Paint.Style.FILL);
		plot.getLegendWidget().setBackgroundPaint(bgPaint);
		plot.getLegendWidget().setPadding(10, 1, 1, 3);
		plot.getLegendWidget().position(160, XLayoutStyle.ABSOLUTE_FROM_LEFT,
				40, YLayoutStyle.ABSOLUTE_FROM_TOP, AnchorPosition.LEFT_TOP);
		
		// Set range label
		if (plotIndex == 3)
			plot.setRangeLabel(activity.getString(R.string.temp));
		else if (plotIndex == 4)
			plot.setRangeLabel(activity.getString(R.string.noise));
		
		if (plotIndex == 3 || plotIndex == 4)
			plot.setRangeValueFormat(new DecimalFormat("#"));
	}
	
	@SuppressWarnings("serial")
	public static void loadGraph(int plotIndex, final GraphsContainer graphsContainer, final Period period, FieldType fieldType, Unit unit) {
		XYPlot plot = null;
		switch (plotIndex) {
			case 1: plot = plot1; break;
			case 2: plot = plot2; break;
			case 3: plot = plot3; break;
			case 4: plot = plot4; break;
		}
		// If the plot hasn't been inited correctly
		if (plot == null) {
			switch (plotIndex) {
				case 1: initPlot(plot1, 1); break;
				case 2: initPlot(plot2, 2); break;
				case 3: initPlot(plot3, 3); break;
				case 4: initPlot(plot4, 4); break;
			}
			switch (plotIndex) {
				case 1: plot = plot1; break;
				case 2: plot = plot2; break;
				case 3: plot = plot3; break;
				case 4: plot = plot4; break;
			}
			if (plot == null)
				return;
		}
		plot.setVisibility(View.VISIBLE);
		
		// Reset plot
		plot.clear();
		plot.getSeriesSet().clear();
		plot.removeMarkers();
		
		for (DataSet dSet : graphsContainer.getDataSets()) {
			SimpleXYSeries serie = new SimpleXYSeries(dSet.getValues(), SimpleXYSeries.ArrayFormat.Y_VALS_ONLY, dSet.getField().getDisplayName());
			
			LineAndPointFormatter serieFormat = new LineAndPointFormatter();
			serieFormat.setPointLabelFormatter(new PointLabelFormatter());
			
			int xmlRef = -1;
			switch (dSet.getField()) {
				case BW_DOWN:
				case BW_UP:		xmlRef = R.xml.serieformat_bw;		break;
				case RATE_DOWN:	xmlRef = R.xml.serieformat_rateup;	break;
				case RATE_UP: 	xmlRef = R.xml.serieformat_ratedown;break;
				case CPUM:		xmlRef = R.xml.serieformat_cpum;	break;
				case CPUB:		xmlRef = R.xml.serieformat_cpub;	break;
				case SW:		xmlRef = R.xml.serieformat_sw;		break;
				case HDD:		xmlRef = R.xml.serieformat_hdd;		break;
				case SNR_UP:	xmlRef = R.xml.serieformat_snr_up;	break;
				case SNR_DOWN:	xmlRef = R.xml.serieformat_snr_down;break; 
				default: break;
			}
			if (xmlRef != -1)
				serieFormat.configure(context, xmlRef);
			
			plot.addSeries(serie, serieFormat);
		}
		
		// Add markers (vertical lines)
		for (XValueMarker m : Util.Times.getMarkers(period, graphsContainer.getSerie())) {
			m.getLinePaint().setARGB(30, 255, 255, 255);
			plot.addMarker(m);
		}
		
		// Add labels
		plot.setDomainStep(XYStepMode.INCREMENT_BY_VAL, 1);
		plot.setDomainValueFormat(new Format() {
			@Override
			public StringBuffer format(Object obj, StringBuffer toAppendTo, FieldPosition pos) {
				int position = ((Number) obj).intValue();
				String label = Util.Times.getLabel(period, graphsContainer.getSerie().get(position), position, graphsContainer.getSerie());
				return new StringBuffer(label);
			}
			
			@Override public Object parseObject(String source, ParsePosition pos) { return null; }
		});
		
		// Set range label
		if (plotIndex == 1 || plotIndex == 2)
			plot.setRangeLabel(activity.getString(R.string.rate) + " (" + unit.name() + "/s)");
		
		plot.redraw();
		
		int plotIndexTreshold = 3;
		if (SettingsHelper.getInstance().getDisplayXdslTab())
			plotIndexTreshold = 4;
		
		if (plotIndex == plotIndexTreshold) {
			toggleSpinningMenuItem(false);
			
			// Load ads if needed
			if (loadAds)
				loadAds();
		}
	}
	
	public static void displayDebugMenuItem() {
		activity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				activity.findViewById(R.id.drawer_debug).setVisibility(View.VISIBLE);
				activity.findViewById(R.id.drawer_debug).setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View arg0) {
						LayoutInflater inflater = LayoutInflater.from(context);
						View dialog_layout = inflater.inflate(R.layout.debug_dialog, (ViewGroup) activity.findViewById(R.id.root_layout));
						
						final ListView lv = (ListView) dialog_layout.findViewById(R.id.debug_lv);
						ArrayAdapter<String> arrayAdapter = new ArrayAdapter<String>(context, android.R.layout.simple_list_item_1, FooBox.getInstance().getErrorsLogger().getErrors());
						lv.setAdapter(arrayAdapter);
						
						AlertDialog.Builder builder = new AlertDialog.Builder(context);
						builder.setPositiveButton(R.string.send_dev, new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
								dialog.dismiss();
								
								new AlertDialog.Builder(context)
								.setTitle(R.string.send_errors)
								.setMessage(R.string.send_errors_explanation)
								.setPositiveButton(R.string.send, new DialogInterface.OnClickListener() {
									public void onClick(DialogInterface dialog, int which) {
										String txt = "Version de l'application : " + FooBox.getInstance().getAppVersion() + "\r\n"
												+ "Explication de l'erreur rencontrée : \r\n\r\n\r\n\r\nListe des erreurs : \r\n"
												+ FooBox.getInstance().getErrorsLogger().getErrorsString();
										Intent send = new Intent(Intent.ACTION_SENDTO);
										String uriText = "mailto:" + Uri.encode("chteuchteu@gmail.com") + 
												"?subject=" + Uri.encode("Rapport de bug") + 
												"&body=" + Uri.encode(txt);
										Uri uri = Uri.parse(uriText);
										
										send.setData(uri);
										activity.startActivity(Intent.createChooser(send, context.getString(R.string.send_errors)));
									}
								})
								.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
									public void onClick(DialogInterface dialog, int which) { 
										dialog.dismiss();
									}
								})
								.setIcon(R.drawable.ic_action_error_light)
								.show();
							}
						});
						builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
								dialog.dismiss();
							}
						});
						builder.setTitle(R.string.debug);
						builder.setView(dialog_layout);
						// Avoid error when the app is closing or something
						if (!activity.isFinishing())
							builder.show();
					}
				});
			}
		});
	}
	
	private void displayOutagesDialog() {
		LayoutInflater inflater = LayoutInflater.from(context);
		View dialog_layout = inflater.inflate(R.layout.outages_dialog, (ViewGroup) activity.findViewById(R.id.root_layout));
		
		Util.Fonts.setFont(context, (TextView) dialog_layout.findViewById(R.id.outages_text), CustomFont.RobotoCondensed_Light);
		
		AlertDialog.Builder builder = new AlertDialog.Builder(context);
		builder.setPositiveButton(R.string.close, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				dialog.dismiss();
			}
		});
		builder.setView(dialog_layout);
		// Avoid error when the app is closing or something
		if (!activity.isFinishing()) {
			AlertDialog dialog = builder.show();
			
			new OutagesFetcher(this, FooBox.getInstance().getFreebox(), dialog, dialog_layout).execute();
		}
	}
	
	public static void toggleSpinningMenuItem(boolean visible) {
		activity.setProgressBarIndeterminateVisibility(visible);
	}
	
	public static void refreshGraph() { refreshGraph(false); }
	public static void refreshGraph(boolean manualRefresh) {
		if (FooBox.getInstance().getFreebox() == null)
			return;
		if (updating)
			return;
		
		updating = true;
		
		if (manualRefresh)
			justRefreshed = true;
		
		toggleSpinningMenuItem(true);
		
		new ManualGraphLoader(FooBox.getInstance().getFreebox(), FooBox.getInstance().getPeriod()).execute();
	}
	
	public static void displayLaunchPairingScreen() {
		Util.Fonts.setFont(context, (TextView) activity.findViewById(R.id.firstlaunch_text1), CustomFont.RobotoCondensed_Light);
		Util.Fonts.setFont(context, (TextView) activity.findViewById(R.id.firstlaunch_text2), CustomFont.RobotoCondensed_Light);
		
		activity.findViewById(R.id.firstlaunch).setVisibility(View.VISIBLE);
		activity.findViewById(R.id.screen1).setVisibility(View.VISIBLE);
		
		
		activity.findViewById(R.id.firstlaunch_ok).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				Util.Fonts.setFont(context, (TextView) activity.findViewById(R.id.firstlaunch_text3), CustomFont.RobotoCondensed_Light);
				activity.findViewById(R.id.screen1).setVisibility(View.GONE);
				activity.findViewById(R.id.screen2).setVisibility(View.VISIBLE);
				new AskForAppToken(FooBox.getInstance().getFreebox(), context).execute();
			}
		});
	}
	
	public static void pairingFinished(final AuthorizeStatus aStatus) {
		activity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if (aStatus == AuthorizeStatus.GRANTED) {
					activity.findViewById(R.id.screen2).setVisibility(View.GONE);
					displayGraphs();
				}
			}
		});
	}
	
	public static void sessionOpenFailed() {
		activity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				Toast.makeText(context, R.string.freebox_connection_fail, Toast.LENGTH_SHORT).show();
				if (activity.findViewById(R.id.ll_loading).getVisibility() == View.VISIBLE) {
					// App loading
					activity.findViewById(R.id.loadingprogressbar).setVisibility(View.GONE);
					activity.findViewById(R.id.retrybutton).setVisibility(View.VISIBLE);
					final TextView chargement = (TextView) activity.findViewById(R.id.tv_loadingtxt);
					chargement.setText(R.string.connection_failed);
					final TextView loadingFail = (TextView) activity.findViewById(R.id.sessionfailmessage);
					if (FooBox.getInstance().isPremium())
						loadingFail.setText(Html.fromHtml(activity.getText(R.string.sessionopening_error).toString()));
					else
						loadingFail.setText(Html.fromHtml(activity.getText(R.string.sessionopening_error_notpremium).toString()));
					
					loadingFail.setVisibility(View.VISIBLE);
					activity.findViewById(R.id.retrybutton).setOnClickListener(new OnClickListener() {
						@Override
						public void onClick(View v) {
							activity.findViewById(R.id.loadingprogressbar).setVisibility(View.VISIBLE);
							activity.findViewById(R.id.retrybutton).setVisibility(View.GONE);
							new SessionOpener(FooBox.getInstance().getFreebox(), context).execute();
							chargement.setText(R.string.loading);
							loadingFail.setVisibility(View.GONE);
						}
					});
				}
			}
		});
	}
	
	public static void displayFreeboxSearchFailedScreen() {
		activity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				activity.findViewById(R.id.ll_loading).setVisibility(View.VISIBLE);
				
				activity.findViewById(R.id.loadingprogressbar).setVisibility(View.GONE);
				activity.findViewById(R.id.retrybutton).setVisibility(View.VISIBLE);
				final TextView chargement = (TextView) activity.findViewById(R.id.tv_loadingtxt);
				chargement.setText(R.string.connection_failed);
				final TextView loadingFail = (TextView) activity.findViewById(R.id.freeboxsearchfailmessage);
				loadingFail.setVisibility(View.VISIBLE);
				activity.findViewById(R.id.retrybutton).setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View v) {
						activity.findViewById(R.id.loadingprogressbar).setVisibility(View.VISIBLE);
						activity.findViewById(R.id.retrybutton).setVisibility(View.GONE);
						new FreeboxDiscoverer(context).execute();
						chargement.setText(R.string.loading);
						loadingFail.setVisibility(View.GONE);
					}
				});
			}
		});
	}
	
	public static void displayFreeboxUpdateNeededScreen() {
		activity.findViewById(R.id.ll_loading).setVisibility(View.VISIBLE);
		
		activity.findViewById(R.id.loadingprogressbar).setVisibility(View.GONE);
		activity.findViewById(R.id.retrybutton).setVisibility(View.VISIBLE);
		final TextView chargement = (TextView) activity.findViewById(R.id.tv_loadingtxt);
		chargement.setText(R.string.connection_failed);
		final TextView loadingFail = (TextView) activity.findViewById(R.id.updatefreeboxmessage);
		loadingFail.setVisibility(View.VISIBLE);
		activity.findViewById(R.id.retrybutton).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				activity.findViewById(R.id.loadingprogressbar).setVisibility(View.VISIBLE);
				activity.findViewById(R.id.retrybutton).setVisibility(View.GONE);
				chargement.setText(R.string.loading);
				loadingFail.setVisibility(View.GONE);
				activity.findViewById(R.id.ll_loading).setVisibility(View.GONE);
				refreshGraph();
			}
		});
	}
	
	public static void displayFreeboxUpdateNeededScreenBeforePairing() {
		activity.findViewById(R.id.loadingprogressbar).setVisibility(View.GONE);
		activity.findViewById(R.id.retrybutton).setVisibility(View.VISIBLE);
		final TextView chargement = (TextView) activity.findViewById(R.id.tv_loadingtxt);
		chargement.setText(R.string.connection_failed);
		final TextView loadingFail = (TextView) activity.findViewById(R.id.updatefreeboxmessage);
		loadingFail.setVisibility(View.VISIBLE);
		
		activity.findViewById(R.id.retrybutton).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				activity.findViewById(R.id.loadingprogressbar).setVisibility(View.VISIBLE);
				activity.findViewById(R.id.retrybutton).setVisibility(View.GONE);
				new FreeboxDiscoverer(context).execute();
				chargement.setText(R.string.loading);
				loadingFail.setVisibility(View.GONE);
			}
		});
	}
	
	public static void graphLoadingFailed() {
		activity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				Toast.makeText(context, R.string.graphs_loading_fail, Toast.LENGTH_SHORT).show();
			}
		});
	}
	
	@SuppressLint("NewApi")
	private void initDrawer() {
		drawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
		drawerToggle = new ActionBarDrawerToggle(this, drawerLayout,
				R.drawable.ic_navigation_drawer, R.string.app_name, R.string.app_name) {
			public void onDrawerClosed(View view) {
				super.onDrawerClosed(view);
			}
			public void onDrawerOpened(View view) {
				super.onDrawerOpened(view);
			}
		};
		drawerLayout.setDrawerListener(drawerToggle);
		getActionBar().setDisplayHomeAsUpEnabled(true);
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH)
			getActionBar().setHomeButtonEnabled(true);
		
		findViewById(R.id.drawer_settings).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				drawerLayout.closeDrawers();
				LayoutInflater inflater = LayoutInflater.from(context);
				View dialog_layout = inflater.inflate(R.layout.settings_dialog, (ViewGroup) findViewById(R.id.root_layout));
				
				final CheckBox settings_autorefresh = (CheckBox) dialog_layout.findViewById(R.id.settings_autorefresh);
				settings_autorefresh.setChecked(SettingsHelper.getInstance().getAutoRefresh());
				final CheckBox settings_displayXdslTab = (CheckBox) dialog_layout.findViewById(R.id.settings_displayxdsltab);
				settings_displayXdslTab.setChecked(SettingsHelper.getInstance().getDisplayXdslTab());
				final Spinner settings_graphPrecision = (Spinner) dialog_layout.findViewById(R.id.settings_graphprecision);
				ArrayAdapter<String> adapter = new ArrayAdapter<String>(context, android.R.layout.simple_spinner_item, GraphPrecision.getStringArray());
				adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
				settings_graphPrecision.setAdapter(adapter);
				settings_graphPrecision.setSelection(SettingsHelper.getInstance().getGraphPrecision().getIndex());
				if (!FooBox.getInstance().isPremium()) {
					dialog_layout.findViewById(R.id.settings_graphprecisiondisabled).setVisibility(View.VISIBLE);
					settings_graphPrecision.setEnabled(false);
				}
				
				AlertDialog.Builder builder = new AlertDialog.Builder(context);
				builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						SettingsHelper.getInstance().setAutoRefresh(settings_autorefresh.isChecked());
						boolean displayXdslTabChanged = SettingsHelper.getInstance().getDisplayXdslTab()
								!= settings_displayXdslTab.isChecked();
						SettingsHelper.getInstance().setDisplayXdslTab(settings_displayXdslTab.isChecked());
						SettingsHelper.getInstance().setGraphPrecision(
								GraphPrecision.get(settings_graphPrecision.getSelectedItemPosition()));
						
						if (settings_autorefresh.isChecked())
							startRefreshThread();
						else
							stopRefreshThread();
						
						// Remove tab
						if (displayXdslTabChanged)
							activity.startActivity(new Intent(MainActivity.this, MainActivity.class));
					}
				});
				builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						settings_autorefresh.setChecked(SettingsHelper.getInstance().getAutoRefresh());
						settings_graphPrecision.setSelection(SettingsHelper.getInstance().getGraphPrecision().getIndex());
						dialog.dismiss();
					}
				});
				builder.setView(dialog_layout);
				builder.show();
			}
		});
		
		context = this;
		activity = this;
		findViewById(R.id.drawer_premium).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				drawerLayout.closeDrawers();
				LayoutInflater inflater = LayoutInflater.from(context);
				View dialog_layout = inflater.inflate(R.layout.premium_dialog, (ViewGroup) findViewById(R.id.root_layout));
				
				final TextView tv = (TextView) dialog_layout.findViewById(R.id.premium_tv);
				tv.setText(Html.fromHtml(context.getString(R.string.premium_text)));
				
				AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
				builder.setPositiveButton(R.string.buy, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						BillingService.getInstance().launchPurchase();
					}
				});
				builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						dialog.dismiss();
					}
				});
				builder.setTitle(R.string.freeboxstats_premium);
				builder.setView(dialog_layout);
				// Avoid error when the app is closing or something
				if (!activity.isFinishing())
					builder.show();
			}
		});
		
		findViewById(R.id.drawer_freebox).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				drawerLayout.closeDrawers();
				AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this)
				.setMessage(R.string.dissociate)
				.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						Freebox.delete(context);
						Util.restartApp(context);
					}
				})
				.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						dialog.dismiss();
					}
				}).setIcon(android.R.drawable.ic_dialog_alert);
				// Avoid error when the app is closing or something
				if (!MainActivity.this.isFinishing())
					builder.show();
			}
		});
		
		findViewById(R.id.drawer_outages).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				drawerLayout.closeDrawers();
				displayOutagesDialog();
			}
		});
	}
	
	public static void finishedLoading() {
		// Only finish if we know that is premium _and_
		//	the freebox configuration is ready and connected
		if (!appLoadingPrereq1 || !appLoadingPrereq2)
			return;
		
		TextView freeboxUri = (TextView) activity.findViewById(R.id.drawer_freebox_uri);
		freeboxUri.setText(FooBox.getInstance().getFreebox().getDisplayUrl());
		activity.findViewById(R.id.drawer_freebox).setVisibility(View.VISIBLE);
		
		appStarted = true;
		
		hideLoadingScreen();
		
		MainActivity.displayGraphs();
		MainActivity.refreshGraph();
		
		if (SettingsHelper.getInstance().getAutoRefresh())
			MainActivity.startRefreshThread();
		
		if (!FooBox.getInstance().isPremium()) {
			activity.findViewById(R.id.drawer_premium).setVisibility(View.VISIBLE);
			// Load the ads the next time this boolean will be checked
			loadAds = true;
		}
	}
	
	public static void displayNeedAuthScreen() {
		validerMenuItem.setVisible(true);
		refreshMenuItem.setVisible(false);
		periodMenuItem.setVisible(false);
		
		WebView wv = (WebView) activity.findViewById(R.id.firstlaunch_wv);
		wv.setVerticalScrollBarEnabled(true);
		wv.getSettings().setDefaultTextEncodingName("utf-8");
		wv.setLayerType(View.LAYER_TYPE_HARDWARE, null);
		wv.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
		wv.loadUrl("file:///android_asset/tuto/index.html");
		wv.setBackgroundColor(0x00000000);
		
		activity.findViewById(R.id.screen3).setVisibility(View.VISIBLE);
		activity.findViewById(R.id.firstlaunch).setVisibility(View.GONE);
	}
	
	/**
	 * Load the ads once we now that the user isn't premium
	 */
	public static void loadAds() {
		if (FooBox.getInstance().isPremium() || !loadAds)
			return;
		
		AppLovinSdk.initializeSdk(context);
		adView = (AppLovinAdView) activity.findViewById(R.id.ad);
		AppLovinSdk.getInstance(context).getAdService().loadNextAd(AppLovinAdSize.BANNER, new AppLovinAdLoadListener() {
			@Override
			public void adReceived(AppLovinAd ad) {
				cachedAd = ad;
				adView.renderAd(cachedAd);
				adView.setVisibility(View.VISIBLE);
			}
			
			@Override
			public void failedToReceiveAd(int errorCode) {
				adView.setVisibility(View.GONE);
			}
		});
	}
	
	public static void dismissAds() {
		adView.setVisibility(View.GONE);
	}
	
	@Override
	protected void onPostCreate(Bundle savedInstanceState) {
		super.onPostCreate(savedInstanceState);
		drawerToggle.syncState();
	}
	
	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		drawerToggle.onConfigurationChanged(newConfig);
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main, menu);
		refreshMenuItem = menu.findItem(R.id.action_refresh);
		refreshMenuItem.setVisible(false);
		periodMenuItem = menu.findItem(R.id.period);
		periodMenuItem.setVisible(false);
		validerMenuItem = menu.findItem(R.id.action_valider);
		validerMenuItem.setVisible(false);
		
		return true;
	}
	
	@Override
	public void onResume() {
		super.onResume();
		
		if (appStarted) {
			context = this;
			activity = this;
			
			refreshGraph();
			
			if (refreshThread != null)
				startRefreshThread();
		}
	}
	
	@Override
	public void onPause() {
		super.onPause();
		
		if (refreshThread != null)
			stopRefreshThread();
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		if (BillingService.isLoaded())
			BillingService.getInstance().unbind();
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) { 
		switch (requestCode) {
			case BillingService.REQUEST_CODE:
				if (resultCode == RESULT_OK) {
					Toast.makeText(context, R.string.thanks_bought_premium, Toast.LENGTH_LONG).show();
					
					FooBox.getInstance().setIsPremium(true);
					dismissAds();
					TextView freeboxUri = (TextView) activity.findViewById(R.id.drawer_freebox_uri);
					freeboxUri.setText(FooBox.getInstance().getFreebox().getDisplayUrl());
				} else
					Toast.makeText(context, R.string.buying_failed, Toast.LENGTH_SHORT).show();
				
				break;
		}
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (drawerToggle.onOptionsItemSelected(item))
			return true;
		
		switch (item.getItemId()) {
			case R.id.action_refresh:
				refreshGraph(true);
				break;
			case R.id.period:
				AlertDialog.Builder builderSingle = new AlertDialog.Builder(context);
				final ArrayAdapter<String> arrayAdapter = new ArrayAdapter<String>(
						context, android.R.layout.simple_list_item_1);
				for (Period period : Period.values())
					arrayAdapter.add(period.getLabel());
				
				builderSingle.setAdapter(arrayAdapter, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						FooBox.getInstance().setPeriod(Period.get(which));
						periodMenuItem.setTitle(Period.get(which).getLabel());
						refreshGraph();
					}
				});
				builderSingle.show();
				break;
			case R.id.action_valider:
				validerMenuItem.setVisible(false);
				refreshMenuItem.setVisible(true);
				periodMenuItem.setVisible(true);
				activity.findViewById(R.id.screen3).setVisibility(View.GONE);
				refreshGraph();
				break;
			default: super.onOptionsItemSelected(item); break;
		}
		return true;
	}
}