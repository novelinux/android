## Handler Message Looper

[Looper Thread](./looper_thread.jpeg)

* 接收消息的“消息队列” —— MessageQueue
* 阻塞式地从消息队列中接收消息并进行处理的“线程” —— Thread + Looper
* 可发送的“消息的格式” —— Message
* 消息发送函数 —— Handler的post和sendMessage

一个Looper类似一个消息泵。它本身是一个死循环，不断地从MessageQueue中提取Message或者Runnable。而Handler可以看做是一个Looper的暴露接口，向外部暴露一些事件，并暴露sendMessage()和post()函数。

在安卓中，除了UI线程/主线程以外，普通的线程(先不提HandlerThread)是不自带Looper的。想要通过UI线程与子线程通信需要在子线程内自己实现一个Looper。开启Looper分三步走：

1.判定是否已有Looper并Looper.prepare()
2.做一些准备工作(如暴露handler等)
3.调用Looper.loop()，线程进入阻塞态

由于每一个线程内最多只可以有一个Looper，所以一定要在Looper.prepare()之前做好判定，否则会抛出java.lang.RuntimeException: Only one Looper may be created per thread。为了获取Looper的信息可以使用两个方法：

```
Looper.myLooper()
Looper.getMainLooper()
```

Looper.myLooper()获取当前线程绑定的Looper，如果没有返回null。Looper.getMainLooper()返回主线程的Looper,这样就可以方便的与主线程通信。

注意：在Thread的构造函数中调用Looper.myLooper只会得到主线程的Looper，因为此时新线程还未构造好

下面给一段代码，通过Thread，Looper和Handler实现线程通信：


```
public class MainActivity extends Activity {
    public static final String TAG = "Main Acticity";
    Button btn = null;
    Button btn2 = null;
    Handler handler = null;
    MyHandlerThread mHandlerThread = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        btn = (Button)findViewById(R.id.button);
        btn2 = (Button)findViewById(R.id.button2);
        Log.d("MainActivity.myLooper()", Looper.myLooper().toString());
        Log.d("MainActivity.MainLooper", Looper.getMainLooper().toString());


        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mHandlerThread = new MyHandlerThread("onStartHandlerThread");
                Log.d(TAG, "创建myHandlerThread对象");
                mHandlerThread.start();
                Log.d(TAG, "start一个Thread");
            }
        });

        btn2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(mHandlerThread.mHandler != null){
                    Message msg = new Message();
                    msg.what = 1;
                    mHandlerThread.mHandler.sendMessage(msg);
                }

            }
        });
    }
}

public class MyHandlerThread extends Thread {
    public static final String TAG = "MyHT";

    public Handler mHandler = null;

    @Override
    public void run() {
        Log.d(TAG, "进入Thread的run");
        Looper.prepare();
        Looper.prepare();
        mHandler = new Handler(Looper.myLooper()){
            @Override
            public void handleMessage(Message msg){
                Log.d(TAG, "获得了message");
                super.handleMessage(msg);
            }
        };
        Looper.loop();
    }
}
```

### 结论汇总

* Thread 若与 Looper 关联，将会是一一对应的关系，且关联后关系无法改变。
* Looper 与 MessageQueue 是一一对应的关系。
* Handler 与 Looper 是多对一的关系，创建 Handler 实例时要么提供一个 Looper 实例，要么当前线程有关联的 Looper。
* 在 Handler.sendMessage 时，会将 Message.target 设置为该 Handler 对象，这样从消息队列取出 Message 后，就能调用到该 Handler 的 dispatchMessage 方法来进行处理。
* Handler 会对应一个 Looper 和 MessageQueue，而 Looper 与线程又一一对应，所以通过 Handler.sendXXX 和 Hanler.postXXX 添加到 MessageQueue 的 Message，会在这个对应的线程的 Looper.loop() 里取出来，并就地执行 Handler.dispatchMessage，这就可以完成线程切换了。
* Runnable 被封装成 Message 之后添加到 MessageQueue。
* 可以从一个线程创建关联到另一个线程 Looper 的 Handler，只要能拿到对应线程的 Looper 实例。
* 消息可以插队，使用 Handler.xxxAtFrontOfQueue 方法。
* 尚未分发的消息是可以撤回的，处理过的就没法了。