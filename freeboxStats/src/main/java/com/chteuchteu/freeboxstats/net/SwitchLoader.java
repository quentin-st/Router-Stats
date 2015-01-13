package com.chteuchteu.freeboxstats.net;

import android.os.AsyncTask;

import com.chteuchteu.freeboxstats.hlpr.Enums;
import com.chteuchteu.freeboxstats.obj.Freebox;
import com.chteuchteu.freeboxstats.obj.GraphsContainer;
import com.chteuchteu.freeboxstats.obj.NetResponse;
import com.chteuchteu.freeboxstats.ui.MainActivity;
import com.crashlytics.android.Crashlytics;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;

public class SwitchLoader extends AsyncTask<Void, Void, Void> {
	private Freebox freebox;
	private boolean switchLoadingFailed;
	private Enums.Period period;

	private GraphsContainer switch1;
	private GraphsContainer switch2;
	private GraphsContainer switch3;
	private GraphsContainer switch4;

	public SwitchLoader(Freebox freebox, Enums.Period period) {
		this.freebox = freebox;
		this.switchLoadingFailed = false;
		this.period = period;
		this.switch1 = null;
		this.switch2 = null;
		this.switch3 = null;
		this.switch4 = null;
	}

	@Override
	protected Void doInBackground(Void... params) {
		this.switch1 = loadData(1);

		if (!switchLoadingFailed) {
			this.switch2 = loadData(2);
			this.switch3 = loadData(3);
			this.switch4 = loadData(4);
		}

		return null;
	}

	@Override
	protected void onPostExecute(Void res) {
		MainActivity.updating = false;

		if (!switchLoadingFailed) {
			if (this.switch1 != null)
				MainActivity.loadGraph(6, switch1, period, switch1.getValuesUnit());
			if (this.switch2 != null)
				MainActivity.loadGraph(7, switch2, period, switch2.getValuesUnit());
			if (this.switch3 != null)
				MainActivity.loadGraph(8, switch3, period, switch3.getValuesUnit());
			if (this.switch4 != null)
				MainActivity.loadGraph(9, switch4, period, switch4.getValuesUnit());
		}
	}

	/**
	 * Load the graph and display the result on MainActivity
	 */
	private GraphsContainer loadData(int switchIndex) {
		ArrayList<Enums.Field> fields = new ArrayList<>();
		Enums.FieldType fieldType = Enums.FieldType.DATA;

		switch (switchIndex) {
			case 1:
				fields.add(Enums.Field.RX_1);
				fields.add(Enums.Field.TX_1);
				break;
			case 2:
				fields.add(Enums.Field.RX_2);
				fields.add(Enums.Field.TX_2);
				break;
			case 3:
				fields.add(Enums.Field.RX_3);
				fields.add(Enums.Field.TX_3);
				break;
			case 4:
				fields.add(Enums.Field.RX_4);
				fields.add(Enums.Field.TX_4);
				break;
		}

		NetResponse netResponse = NetHelper.loadGraph(freebox, period, fields, false);

		if (netResponse != null && netResponse.hasSucceeded()) {
			try {
				if (netResponse.getJsonObject().get("data") instanceof JSONArray)
					return new GraphsContainer(fields, netResponse.getJsonObject().getJSONArray("data"), fieldType, period);
				else
					return null;
			} catch (JSONException ex) {
				ex.printStackTrace();
				Crashlytics.logException(ex);
				return null;
			}
		} else
			switchLoadingFailed = true;

		return null;
	}
}
