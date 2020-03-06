package android.scrcpy.ui.activity;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import dongdong.util.Ln;
import drawthink.expandablerecyclerview.adapter.BaseRecyclerViewAdapter;
import drawthink.expandablerecyclerview.bean.RecyclerViewData;

import android.os.Bundle;
import android.scrcpy.R;
import android.scrcpy.manager.ServerManager;
import android.scrcpy.ui.adapter.PhoneAdapter;
import android.scrcpy.ui.bean.PhoneBean;
import android.scrcpy.ui.holder.PhoneViewHolder;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PhonesActivity extends AppCompatActivity {
    private BaseRecyclerViewAdapter<PhoneBean, PhoneBean, PhoneViewHolder> phoneAdapter;
    private ServerManager serverManager;
    private ByteBuffer byteBuffer;
    private List<RecyclerViewData> mDatas;
    private RecyclerView phoneInfoList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String ipAddress = getIntent().getStringExtra("ipAddress");
        Ln.e("onCreate: " + ipAddress);
        if (ipAddress == null) {
            finish();
        }
        serverManager = ServerManager.getInstance();
        serverManager.initAddress(ipAddress);
        setContentView(R.layout.activity_phones);
        byteBuffer = ByteBuffer.allocate(1024);
        mDatas = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            PhoneBean phoneBean = new PhoneBean();
            phoneBean.setPhone("杨安奇是猪");
            mDatas.add(new RecyclerViewData<>(phoneBean, Collections.singletonList(phoneBean), false));
        }
        phoneAdapter = new PhoneAdapter(this, mDatas);

        phoneInfoList = findViewById(R.id.phone_list);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        phoneInfoList.setLayoutManager(layoutManager);
        phoneInfoList.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
        phoneInfoList.setAdapter(phoneAdapter);


        new Thread(this::loadPhones).start();
    }

    private void loadPhones() {
        try {
            SocketChannel videoSc = serverManager.getVideoSc();
            Ln.d("connect over");
            byteBuffer.clear();
            byteBuffer.putInt(0);
            byteBuffer.flip();
            videoSc.write(byteBuffer);
            byteBuffer.position(0);
            byteBuffer.limit(4);
            Ln.d("read aaaaaaaaaaaaaaa");
            while (byteBuffer.hasRemaining()) {
                videoSc.read(byteBuffer);
            }
            byteBuffer.flip();
            int length = byteBuffer.getInt();
            byteBuffer.position(0);
            byteBuffer.limit(length);
            Ln.d("read ssssssssssssssssssssss");
            while (byteBuffer.hasRemaining()) {
                videoSc.read(byteBuffer);
            }
            byteBuffer.flip();
            String phonesStr = new String(byteBuffer.array(), 0, length);
            Ln.d("loadPhone: " + phonesStr);
            String[] phoneNums = phonesStr.split(",");
            for (String phoneNum : phoneNums) {
                PhoneBean phoneBean = new PhoneBean();
                phoneBean.setPhone(phoneNum);
                mDatas.add(new RecyclerViewData<>(phoneBean, Collections.singletonList(phoneBean), false));
            }
            runOnUiThread(phoneAdapter::notifyDataSetChanged);
            Ln.e("ok");
        } catch (IOException e) {
            Ln.e("errpr", e);
            finish();
        }
    }

}
