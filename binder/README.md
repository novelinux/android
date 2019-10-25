## Binder

### struct binder_proc

```
/**
 * struct binder_proc - binder process bookkeeping
 * @proc_node:            element for binder_procs list
 * @threads:              rbtree of binder_threads in this proc
 *                        (protected by @inner_lock)
 * @nodes:                rbtree of binder nodes associated with
 *                        this proc ordered by node->ptr
 *                        (protected by @inner_lock)
 * @refs_by_desc:         rbtree of refs ordered by ref->desc
 *                        (protected by @outer_lock)
 * @refs_by_node:         rbtree of refs ordered by ref->node
 *                        (protected by @outer_lock)
 * @waiting_threads:      threads currently waiting for proc work
 *                        (protected by @inner_lock)
 * @pid                   PID of group_leader of process
 *                        (invariant after initialized)
 * @tsk                   task_struct for group_leader of process
 *                        (invariant after initialized)
 * @deferred_work_node:   element for binder_deferred_list
 *                        (protected by binder_deferred_lock)
 * @deferred_work:        bitmap of deferred work to perform
 *                        (protected by binder_deferred_lock)
 * @is_dead:              process is dead and awaiting free
 *                        when outstanding transactions are cleaned up
 *                        (protected by @inner_lock)
 * @todo:                 list of work for this process
 *                        (protected by @inner_lock)
 * @stats:                per-process binder statistics
 *                        (atomics, no lock needed)
 * @delivered_death:      list of delivered death notification
 *                        (protected by @inner_lock)
 * @max_threads:          cap on number of binder threads
 *                        (protected by @inner_lock)
 * @requested_threads:    number of binder threads requested but not
 *                        yet started. In current implementation, can
 *                        only be 0 or 1.
 *                        (protected by @inner_lock)
 * @requested_threads_started: number binder threads started
 *                        (protected by @inner_lock)
 * @tmp_ref:              temporary reference to indicate proc is in use
 *                        (protected by @inner_lock)
 * @default_priority:     default scheduler priority
 *                        (invariant after initialized)
 * @debugfs_entry:        debugfs node
 * @alloc:                binder allocator bookkeeping
 * @context:              binder_context for this proc
 *                        (invariant after initialized)
 * @inner_lock:           can nest under outer_lock and/or node lock
 * @outer_lock:           no nesting under innor or node lock
 *                        Lock order: 1) outer, 2) node, 3) inner
 *
 * Bookkeeping structure for binder processes
 */
struct binder_proc {
	struct hlist_node proc_node;
	struct rb_root threads;
	struct rb_root nodes;
	struct rb_root refs_by_desc;
	struct rb_root refs_by_node;
	struct list_head waiting_threads;
	int pid;
	struct task_struct *tsk;
	struct hlist_node deferred_work_node;
	int deferred_work;
	bool is_dead;

	struct list_head todo;
	struct binder_stats stats;
	struct list_head delivered_death;
	int max_threads;
	int requested_threads;
	int requested_threads_started;
	int tmp_ref;
	long default_priority;
	struct dentry *debugfs_entry;
	struct binder_alloc alloc;
	struct binder_context *context;
	spinlock_t inner_lock;
	spinlock_t outer_lock;
};
```

binder_proc中存在两棵binder_ref红黑树，其实两棵红黑树中的节点是复用的，只是查询方式不同，一个通过handle句柄，一个通过node节点查找。

refs_by_node红黑树主要是为了binder驱动往用户空间写数据所使用的，而refs_by_desc是用户空间向Binder驱动写数据使用的，只是方向问题。比如在服务addService的时候，binder驱动会在在ServiceManager进程的binder_proc中查找binder_ref结构体，如果没有就会新建binder_ref结构体，再比如在Client端getService的时候，binder驱动会在Client进程中通过 binder_get_ref_for_node为Client创建binder_ref结构体，并分配句柄，同时插入到refs_by_desc红黑树中，可见refs_by_node红黑树，主要是给binder驱动往用户空间写数据使用的。相对的refs_by_desc主要是为了用户空间往binder驱动写数据使用的，当用户空间已经获得Binder驱动为其创建的binder_ref引用句柄后，就可以通过binder_get_ref从refs_by_desc找到响应binder_ref，进而找到目标binder_node。

现在假设存在一堆Client与Service，Client如何才能访问Service呢？首先Service会通过addService将binder实体注册到ServiceManager中去，Client如果想要使用Servcie，就需要通过getService向ServiceManager请求该服务。在Service通过addService向ServiceManager注册的时候，ServiceManager会将服务相关的信息存储到自己进程的Service列表中去，同时在ServiceManager进程的binder_ref红黑树中为Service添加binder_ref节点，这样ServiceManager就能获取Service的Binder实体信息。而当Client通过getService向ServiceManager请求该Service服务的时候，ServiceManager会在注册的Service列表中查找该服务，如果找到就将该服务返回给Client，在这个过程中，ServiceManager会在Client进程的binder_ref红黑树中添加binder_ref节点.
**本进程中的binder_ref红黑树节点都不是本进程自己创建的，要么是Service进程将binder_ref插入到ServiceManager中去，要么是ServiceManager进程将binder_ref插入到Client中去**。
之后，Client就能通过Handle句柄获取binder_ref，进而访问Service服务。

### struct binder_ref

```
/**
 * struct binder_ref - struct to track references on nodes
 * @data:        binder_ref_data containing id, handle, and current refcounts
 * @rb_node_desc: node for lookup by @data.desc in proc's rb_tree
 * @rb_node_node: node for lookup by @node in proc's rb_tree
 * @node_entry:  list entry for node->refs list in target node
 *               (protected by @node->lock)
 * @proc:        binder_proc containing ref
 * @node:        binder_node of target node. When cleaning up a
 *               ref for deletion in binder_cleanup_ref, a non-NULL
 *               @node indicates the node must be freed
 * @death:       pointer to death notification (ref_death) if requested
 *               (protected by @node->lock)
 *
 * Structure to track references from procA to target node (on procB). This
 * structure is unsafe to access without holding @proc->outer_lock.
 */
struct binder_ref {
	/* Lookups needed: */
	/*   node + proc => ref (transaction) */
	/*   desc + proc => ref (transaction, inc/dec ref) */
	/*   node => refs + procs (proc exit) */
	struct binder_ref_data data;
	struct rb_node rb_node_desc;
	struct rb_node rb_node_node;
	struct hlist_node node_entry;
	struct binder_proc *proc;
	struct binder_node *node;
	struct binder_ref_death *death;
};
```

getService之后，便可以获取binder_ref引用，进而获取到binder_proc与binder_node信息，之后Client便可有目的的将binder_transaction事务插入到binder_proc的待处理列表，并且，如果进程正在睡眠，就唤起进程，其实这里到底是唤起进程还是线程也有讲究，对于Client向Service发送请求的状况，一般都是唤醒binder_proc上睡眠的线程：

### struct binder_node

```
/**
 * struct binder_node - binder node bookkeeping
 * @debug_id:             unique ID for debugging
 *                        (invariant after initialized)
 * @lock:                 lock for node fields
 * @work:                 worklist element for node work
 *                        (protected by @proc->inner_lock)
 * @rb_node:              element for proc->nodes tree
 *                        (protected by @proc->inner_lock)
 * @dead_node:            element for binder_dead_nodes list
 *                        (protected by binder_dead_nodes_lock)
 * @proc:                 binder_proc that owns this node
 *                        (invariant after initialized)
 * @refs:                 list of references on this node
 *                        (protected by @lock)
 * @internal_strong_refs: used to take strong references when
 *                        initiating a transaction
 *                        (protected by @proc->inner_lock if @proc
 *                        and by @lock)
 * @local_weak_refs:      weak user refs from local process
 *                        (protected by @proc->inner_lock if @proc
 *                        and by @lock)
 * @local_strong_refs:    strong user refs from local process
 *                        (protected by @proc->inner_lock if @proc
 *                        and by @lock)
 * @tmp_refs:             temporary kernel refs
 *                        (protected by @proc->inner_lock while @proc
 *                        is valid, and by binder_dead_nodes_lock
 *                        if @proc is NULL. During inc/dec and node release
 *                        it is also protected by @lock to provide safety
 *                        as the node dies and @proc becomes NULL)
 * @ptr:                  userspace pointer for node
 *                        (invariant, no lock needed)
 * @cookie:               userspace cookie for node
 *                        (invariant, no lock needed)
 * @has_strong_ref:       userspace notified of strong ref
 *                        (protected by @proc->inner_lock if @proc
 *                        and by @lock)
 * @pending_strong_ref:   userspace has acked notification of strong ref
 *                        (protected by @proc->inner_lock if @proc
 *                        and by @lock)
 * @has_weak_ref:         userspace notified of weak ref
 *                        (protected by @proc->inner_lock if @proc
 *                        and by @lock)
 * @pending_weak_ref:     userspace has acked notification of weak ref
 *                        (protected by @proc->inner_lock if @proc
 *                        and by @lock)
 * @has_async_transaction: async transaction to node in progress
 *                        (protected by @lock)
 * @accept_fds:           file descriptor operations supported for node
 *                        (invariant after initialized)
 * @min_priority:         minimum scheduling priority
 *                        (invariant after initialized)
 * @async_todo:           list of async work items
 *                        (protected by @proc->inner_lock)
 *
 * Bookkeeping structure for binder nodes.
 */
struct binder_node {
	int debug_id;
	spinlock_t lock;
	struct binder_work work;
	union {
		struct rb_node rb_node;
		struct hlist_node dead_node;
	};
	struct binder_proc *proc;
	struct hlist_head refs;
	int internal_strong_refs;
	int local_weak_refs;
	int local_strong_refs;
	int tmp_refs;
	binder_uintptr_t ptr;
	binder_uintptr_t cookie;
	struct {
		/*
		 * bitfield elements protected by
		 * proc inner_lock
		 */
		u8 has_strong_ref:1;
		u8 pending_strong_ref:1;
		u8 has_weak_ref:1;
		u8 pending_weak_ref:1;
	};
	struct {
		/*
		 * invariant after initialization
		 */
		u8 accept_fds:1;
		u8 min_priority;
	};
	bool has_async_transaction;
	struct list_head async_todo;
};
```

### Binder一次拷贝原理

* Binder的mmap函数，会将内核空间直接与用户空间对应，用户空间可以直接访问内核空间的数据
* A进程的数据会被直接拷贝到B进程的内核空间（一次拷贝）.

当数据从用户空间拷贝到内核空间的时候，是直从当前进程的用户空间接拷贝到目标进程的内核空间，这个过程是在请求端线程中处理的，操作对象是目标进程的内核空间, 内核空间的数据映射到用户空间其实就是添加一个偏移地址，并且将数据的首地址、数据的大小都复制到一个用户空间的Parcel结构体.

Binder线程、Binder主线程、Client请求线程的概念与区别:

Binder线程是执行Binder服务的载体，只对于服务端才有意义，对请求端来说，是不需要考虑Binder线程的，但Android系统的处理机制其实大部分是互为C/S的。比如APP与AMS进行交互的时候，都互为对方的C与S，这里先不讨论这个问题，先看Binder线程的概念。Binder线程就是执行Binder实体业务的线程，一个普通线程如何才能成为Binder线程呢？很简单，只要开启一个监听Binder字符设备的Loop线程即可，在Android中有很多种方法，不过归根到底都是监听Binder，换成代码就是通过ioctl来进行监听。

ServerManager进程来说，其主线就是Binder线程，其做法是通过binder_loop实现不死线程SystemSever主线程便是Binder线程，同时一个Binder主线程，Binder线程与Binder主线程的区别是：线程是否可以终止Loop，不过目前启动的Binder线程都是无法退出的，其实可以全部看做是Binder主线程，其实现原理是，在SystemServer主线程执行到最后的时候，Loop监听Binder设备，变身死循环线程

普通Client的binder请求线程，比如我们APP的主线程，在startActivity请求AMS的时候，APP的主线程成其实就是Binder请求线程，在进行Binder通信的过程中，Client的Binder请求线程会一直阻塞Service处理完毕返回处理结果.

Android APP进程都是由Zygote进程孵化出来的。常见场景：点击桌面icon启动APP，或者startActivity启动一个新进程里面的Activity，最终都会由AMS去调用Process.start()方法去向Zygote进程发送请求，让Zygote去fork一个新进程，Zygote收到请求后会调用Zygote.forkAndSpecialize()来fork出新进程,之后会通过RuntimeInit.nativeZygoteInit来初始化Andriod APP运行需要的一些环境，而binder线程就是在这个时候新建启动的,ProcessState::self()函数会调用open()打开/dev/binder设备，这个时候Client就能通过Binder进行远程通信；其次，proc->startThreadPool()负责新建一个binder线程，监听Binder设备，这样进程就具备了作为Binder服务端的资格。每个APP的进程都会通过onZygoteInit打开Binder，既能作为Client，也能作为Server，这就是Android进程天然支持Binder通信的原因。

APP端UI线程都是Looper线程，每个Looper线程中维护一个消息队列，其他线程比如Binder线程或者自定义线程，都能通过Handler对象向Handler所依附消息队列线程发送消息，比如点击事件，都是通过InputManagerService处理后，通过binder通信，发送到App端Binder线程，再由Binder线程向UI线程发送送Message，其实就是通过Handler向UI的MessageQueue插入消息，与此同时，其他线程也能通过Handler向UI线程发送消息，显然这里就需要同步
