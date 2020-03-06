package android.scrcpy.ui.adapter;

import android.content.Context;
import android.scrcpy.R;
import android.scrcpy.ui.bean.PhoneBean;
import android.scrcpy.ui.holder.PhoneViewHolder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;


import java.util.List;

import drawthink.expandablerecyclerview.adapter.BaseRecyclerViewAdapter;
import drawthink.expandablerecyclerview.bean.RecyclerViewData;


public class PhoneAdapter extends BaseRecyclerViewAdapter<PhoneBean, PhoneBean, PhoneViewHolder> {

    private LayoutInflater mInflater;

    public PhoneAdapter(Context ctx, List<RecyclerViewData> datas) {
        super(ctx, datas);
        mInflater = LayoutInflater.from(ctx);
    }

    @Override
    public void onBindGroupHolder(PhoneViewHolder holder, int groupPos, int position, PhoneBean groupData) {
        holder.tvPhoneName.setText(groupData.getPhone());
    }

    @Override
    public void onBindChildpHolder(PhoneViewHolder holder, int groupPos, int childPos, int position, PhoneBean childData) {
        holder.phoneBean = childData;
    }

    @Override
    public View getGroupView(ViewGroup parent) {
        return mInflater.inflate(R.layout.phone_item, parent, false);
    }

    @Override
    public View getChildView(ViewGroup parent) {
        return mInflater.inflate(R.layout.phone_item_child, parent, false);
    }

    @Override
    public PhoneViewHolder createRealViewHolder(Context ctx, View view, int viewType) {
        return new PhoneViewHolder(ctx, view, viewType);
    }

    @Override
    public boolean canExpandAll() {
        return false;
    }

}
