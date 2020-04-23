package android.scrcpy.ui.holder;

import android.content.Context;
import android.content.Intent;
import android.scrcpy.R;
import android.scrcpy.ui.activity.Main2Activity;
import android.scrcpy.ui.bean.PhoneBean;
import android.view.View;
import android.widget.TextView;
import drawthink.expandablerecyclerview.holder.BaseViewHolder;

public class PhoneViewHolder extends BaseViewHolder {

    public TextView tvPhoneName;
    public PhoneBean phoneBean;

    public PhoneViewHolder(Context ctx, View itemView, int viewType) {
        super(ctx, itemView, viewType);
        if (viewType == 1) {
            tvPhoneName = itemView.findViewById(R.id.phone_name);
        } else if (viewType == 2) {
            //重启应用
            itemView.findViewById(R.id.restart_app_btn).setOnClickListener(v -> {
            });

            //连接手机
            itemView.findViewById(R.id.connect_phone_btn).setOnClickListener(v -> {
                Intent intent = new Intent(ctx, Main2Activity.class);
                intent.putExtra("phone", phoneBean.getPhone());
                ctx.startActivity(intent);
            });

            //杀死清除数据并重启应用
            itemView.findViewById(R.id.reborn_app_btn).setOnClickListener(v -> {

            });
        }
    }

    @Override
    public int getChildViewResId() {
        return R.id.child;
    }

    @Override
    public int getGroupViewResId() {
        return R.id.group;
    }
}
