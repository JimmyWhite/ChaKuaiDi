package com.example.jimmywhite.kuaidi;

import android.content.Intent;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.TextView;
import android.os.Message;
import android.view.*;
import android.widget.Toast;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class MainActivity extends AppCompatActivity {
    public String[] historySearch = new String[50];
    public int historyNumber = 50;
    public ArrayAdapter<String> arrayAdapter;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout);
        //设置物流详情可以滑动。
        TextView content = (TextView)findViewById(R.id.textView);
        content.setMovementMethod(ScrollingMovementMethod.getInstance());

        //绑定输入框和适配器，完成单号历史查询记录的提取。
        for (int i=0;i<50;i++)
            historySearch[i] = "$";
        final AutoCompleteTextView autoCompleteTextView = (AutoCompleteTextView)findViewById(R.id.editText);
        arrayAdapter = new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,historySearch);
        autoCompleteTextView.setAdapter(arrayAdapter);

        //增加点击搜索记录的监听，自动提交点中单号进行查询。
        autoCompleteTextView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                OnClick(view);
            }
        });

        //增加获取搜索框焦点的监听，清除搜索框内容。
        autoCompleteTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                autoCompleteTextView.setText("");
            }
        });

        autoCompleteTextView.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH)
                    OnClick(v);
                return false;
            }
        });
    }

    private Handler handler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            /*
            获得从子线程发送的Message对象，然后在这里进行UI操作。
            在TextView上显示出物流详情。
             */
            TextView text = (TextView)findViewById(R.id.textView);
            String[] data = (String[])msg.obj;
            String show_data = "";

            for (int i=0; i<msg.arg1/2;i++)
                show_data = show_data + data[i] + "\n" + data[i+msg.arg1/2] + "\n";
            if (show_data =="")
                show_data = "暂无该单号的物流信息，请确认单号是否输入正确。";
            else
            {
                UpdateHistory(data[msg.arg1]);
            }
            text.setText(show_data);
        }
    };

    public void UpdateHistory(String number)
    {
        /*
        用户成功查询到一条单号后进行更新。
        判断该单号是否已经在搜索列表，如果不在则添加。
        重新绑定搜索框和搜索列表数组。
         */
        Boolean flag =false;
        for (int i=0;i<50;i++)
            if (historySearch[i].equals(number))
            {
                flag = true;
                break;
            }
        if (!flag)
        {
            historyNumber--;
            historySearch[historyNumber] = number;
            AutoCompleteTextView autoCompleteTextView = (AutoCompleteTextView)findViewById(R.id.editText);
            arrayAdapter = new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,historySearch);
            autoCompleteTextView.setAdapter(arrayAdapter);
        }
    }
    public String GetInformation(String url) throws IOException {
        /*
        通过HttpURLConnection访问传入的URL。
        对返回的数据进行处理，通过InputStream和ByteArrayOutputStream将byte转化成String。
        将最后得到的String作为函数的返回值。
         */
        String information;
        HttpURLConnection connection;
        InputStream inputStream;
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        int len;
        byte[] array = new byte[1024];
        connection = (HttpURLConnection)(new URL(url)).openConnection();
        inputStream = connection.getInputStream();
        while((len=inputStream.read(array))!= -1) {
            byteArrayOutputStream.write(array, 0, len);
            byteArrayOutputStream.flush();
        }
        byteArrayOutputStream.close();
        information = byteArrayOutputStream.toString();
        return information;
    }

    public String RegexToFindCompany(String data)
    {
        /*
        通过正则表达式处理第一次访问网页返回的数据。
        找出单号所对应的快递厂商名称并作为函数的返回值。
         */
        String company = "";
        Pattern pattern = Pattern.compile("\\[\\{\"comCode\":\"(.*?)\"");
        Matcher matcher = pattern.matcher(data);
        if (matcher.find())
        {
            company = matcher.group(1);
        }
        return company;
    }

    public String[] RegexToFindDetail(String data)
    {
          /*
        通过正则表达式处理第二次访问网页返回的数据。
        找出单号所对应物流的时间和地点详情。
        数据通过String数组的方式返回。
         */
        String[] detail = new String[50];
        int count = 0;
        Pattern pattern = Pattern.compile("ftime\":\"(.*?)\"");
        Matcher matcher = pattern.matcher(data);
        while (matcher.find())
        {
            detail[count] = matcher.group(1);
            count ++;
        }
        pattern = Pattern.compile("context\":\"(.*?)\"");
        matcher = pattern.matcher(data);
        while (matcher.find())
        {
            detail[count] = matcher.group(1);
            count ++;
        }
        return detail;
    }

    public void OnClick(View v)
    {
        /*
        查询按钮的响应函数。
        启动一个新的线程处理。
         */
        new Thread(network).start();
    }

    public void ClearText(View v)
    {
        //清除搜索框内容
        AutoCompleteTextView autoCompleteTextView = (AutoCompleteTextView)findViewById(R.id.editText);
        autoCompleteTextView.setText("");
    }

    public void OnCamera(View v)
    {
        /*
        开启相机扫描快递单条形码。
        扫描方式为横屏。
         */
        IntentIntegrator integrator = new IntentIntegrator(this);
        integrator.setDesiredBarcodeFormats(IntentIntegrator.ALL_CODE_TYPES);
        integrator.setPrompt("扫描条形码...");
        integrator.setCameraId(0);
        integrator.setBeepEnabled(false);
        integrator.setBarcodeImageEnabled(false);
        integrator.initiateScan();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        /*
        解析相机扫描快递单得到的单号。
        如果得到正确的单号则调用ScanSuccess查询该单号并更新UI。
        */
        super.onActivityResult(requestCode, resultCode, data);
        IntentResult intentResult = IntentIntegrator.parseActivityResult(requestCode,resultCode,data);
        if (intentResult != null)
        {
            if (intentResult.getContents()==null)
                Toast.makeText(this, "扫描失败！", Toast.LENGTH_SHORT).show();
            else {
                Toast.makeText(this, "扫描成功！", Toast.LENGTH_SHORT).show();
                ScanSuccess(intentResult.getContents());
            }
        }
        else
            super.onActivityResult(requestCode, resultCode, data);

    }

    public void ScanSuccess(String number)
    {
        /*
        处理通过Zxing得到的单号。
        更新搜索历史列表。
        触发查询按钮的点击事件。
        让按钮获取焦点，防止搜索框遮盖物流详情信息。
         */
        AutoCompleteTextView text = (AutoCompleteTextView)findViewById(R.id.editText);
        text.setText(number);
        UpdateHistory(number);
        Button button = (Button)findViewById(R.id.button);
        button.setFocusable(true);
        button.setFocusableInTouchMode(true);
        button.requestFocus();
        button.requestFocusFromTouch();
        button.performClick();
    }

    Runnable network = new Runnable() {
        @Override
        /*
        发送网络请求和处理响应的线程。
        通过两次请求获取物流的详情。
        发送消息给主线程进行处理，更新UI。
         */
        public void run() {
            /*
            第一次请求获取单号对应快递厂商名称。
            第二次请求获取单号的物流详情。
            调用正则处理函数对返回的数据包进行解析，获取数据。
            发送消息到handler进行UI的处理。
             */
            AutoCompleteTextView autoCompleteTextView = (AutoCompleteTextView)findViewById(R.id.editText);
            String number = autoCompleteTextView.getText().toString();
            Message message = new Message();
            String url1 = "http://www.kuaidi100.com/autonumber/autoComNum?text=" + number;
            String company = "";
            String[] detail = new String[50];
            try {
                 company = RegexToFindCompany(GetInformation(url1));
            } catch (IOException e) {
                e.printStackTrace();
            }
            String url2 = "http://www.kuaidi100.com/query?type=" + company + "&postid=" + number + "&id=1";
            try {
                detail = RegexToFindDetail(GetInformation(url2));
            } catch (IOException e) {
                e.printStackTrace();
            }
            message.obj = detail;
            for (int i=0; i<50;i++)
                if (detail[i]==null) {
                    message.arg1 = i;
                    detail[i] = number;
                    break;
                }
            handler.sendMessage(message);
        }
    };

}
