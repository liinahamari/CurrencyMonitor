package com.example.currencymonitor.ui;

import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import com.example.currencymonitor.R;
import com.example.currencymonitor.data.FixerAPI;
import com.example.currencymonitor.data.MetaCurr;
import com.example.currencymonitor.data.db.Flags;
import com.example.currencymonitor.di.components.CurrencyComponent;
import com.example.currencymonitor.di.components.DaggerCurrencyComponent;
import com.example.currencymonitor.di.modules.ContextModule;
import com.example.currencymonitor.utils.CustomSpinnerAdapter;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.XAxis.XAxisPosition;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.interfaces.datasets.IBarDataSet;
import com.jakewharton.rxbinding.widget.RxAdapterView;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.LinkedList;
import java.util.List;

import android.support.v4.app.Fragment;
import android.widget.Spinner;

import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;

import static rx.android.schedulers.AndroidSchedulers.mainThread;

/**
 * Created by l1maginaire on 1/6/18.
 */

public class MultiChartFragment extends Fragment {
    ArrayList<Float> floats;
    CompositeDisposable mCompositeDisposable;
    List<Flags> currencies = Arrays.asList(Flags.values());
    BarChart mChart;
    private static List <String> dates10 = new LinkedList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.chart, container, false);
 //       setRetainInstance(true); // to prevent hiding on changing orientation

        mChart = (BarChart) v.findViewById(R.id.chartView);
        mChart.getDescription().setEnabled(false);

        MyMarkerView mv = new MyMarkerView(getContext(), R.layout.marker_view);
        mv.setChartView(mChart);
        mChart.setMarker(mv);

        mChart.setDrawGridBackground(false); // todo: necessity
        mChart.setDrawBarShadow(false);

        Legend legend = mChart.getLegend();
        legend.setEnabled(false);

        YAxis leftAxis = mChart.getAxisLeft();
        leftAxis.setTextColor(getResources().getColor(R.color.textbright));
        YAxis rightAxis = mChart.getAxisRight();
        rightAxis.setEnabled(false);

        XAxis xAxis = mChart.getXAxis();
        xAxis.setPosition(XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setTextColor(getResources().getColor(R.color.textbright));
        xAxis.setValueFormatter((value, axis) -> dates10.get((int)value));

        Spinner spinnerF = (Spinner) v.findViewById(R.id.spinnerfrom);
        Spinner spinnerW = (Spinner) v.findViewById(R.id.spinnerto);
        ArrayAdapter<Flags> adapter = new CustomSpinnerAdapter(getContext(), R.layout.row, currencies);

        spinnerF.setAdapter(adapter);
        spinnerW.setAdapter(adapter);
        spinnerW.setSelection(1);

        rx.Observable<Integer> obs1 = RxAdapterView.itemSelections(spinnerF);
        rx.Observable<Integer> obs2 = RxAdapterView.itemSelections(spinnerW);
        rx.Observable.combineLatest(obs1, obs2, (s, s2) -> {
            com.example.currencymonitor.data.db.Pair pair = new com.example.currencymonitor.data.db.Pair(s, s2);
            return pair;
        })
                .subscribeOn(mainThread())
                .subscribe(pair -> {
                    Log.v("spinner", pair.getX().toString());
                    Log.v("spinner", pair.getY().toString());
                    daysSequence(pair.getX(), pair.getY());
                });

        return v;
    }

    private BarData generateData(ArrayList<Float> list) {
        ArrayList<BarEntry> entries = new ArrayList<>();

        for (int i = 0; i < list.size(); i++) {
            entries.add(new BarEntry(i, list.get(i)));
        }

        BarDataSet d = new BarDataSet(entries, "Dates");
        d.setColor(getResources().getColor(R.color.barcolor));
        d.setValueTextColor(getResources().getColor(R.color.textbright));
        d.setValueTextSize(15f);
        d.setValueFormatter((value, entry, dataSetIndex, viewPortHandler) -> new DecimalFormat("#.###").format(value));
        d.setBarShadowColor(Color.rgb(203, 203, 203));

        ArrayList<IBarDataSet> sets = new ArrayList<>();
        sets.add(d);

        BarData cd = new BarData(sets);
        cd.setBarWidth(0.9f);
        return cd;
    }

    private void daysSequence(final int x, final int y) {
        String base = currencies.get(x).toString();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        ArrayList<Single<MetaCurr>> dataList = new ArrayList();
        Calendar calendar = new GregorianCalendar();

        CurrencyComponent daggerRandomUserComponent = DaggerCurrencyComponent.builder()
                .contextModule(new ContextModule(getContext()))
                .build();
        FixerAPI fixerAPI = daggerRandomUserComponent.getCurrencyService();
        for (int i = 0; i < 10; i++) {
            Date result = calendar.getTime();
            dates10.add(new SimpleDateFormat("dd/MM").format(result));
            dataList.add(fixerAPI.statistics(sdf.format(result), base));
            calendar.add(Calendar.DATE, -1);
        }
        Collections.reverse(dates10);

        ArrayList <MetaCurr> emptyList = new ArrayList<>();

        Single<List<MetaCurr>> n = Single.merge(dataList).buffer(Integer.MAX_VALUE).single(emptyList);

        mCompositeDisposable = new CompositeDisposable();

        mCompositeDisposable.add(n
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(list -> {
                    floats = new ArrayList<>();

                    for (MetaCurr m:list) {
                        floats.add(adaptFunction(y, m));
                    }
                    mChart.setData(generateData(floats));
                    mChart.notifyDataSetChanged();
                    mChart.invalidate();
                })
        );
    }

    float adaptFunction(int y, MetaCurr m){
        switch (y){
            case 0:
                return m.getRates().getEUR();
            case 1:
                return m.getRates().getUSD();
            case 2:
                return m.getRates().getJPY();
            case 3:
                return m.getRates().getGBP();
            case 4:
                return m.getRates().getCHF();
            case 5:
                return m.getRates().getAUD();
            case 6:
                return m.getRates().getCAD();
            case 7:
                return m.getRates().getSEK();
            default:
                return 0f;
        }
    }
}
