## Android View Draw

### View Draw

[View Draw](./view_draw.png)

### Notes

Android只在UI主线程修改UI?

关键就是ViewRootImpl的checkThread方法,可以看到，它检查的并不是当前线程是否是UI线程，而是当前线程是否是操作线程。这个操作线程就是创建ViewRootImpl对象的线程.