/**
 * Copyright 2015 David Karnok and Netflix, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See
 * the License for the specific language governing permissions and limitations under the License.
 */

package io.reactivex.subjects.nbp;

import java.lang.reflect.Array;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.*;

import io.reactivex.Scheduler;
import io.reactivex.disposables.Disposable;
import io.reactivex.internal.functions.Objects;
import io.reactivex.internal.util.NotificationLite;
import io.reactivex.plugins.RxJavaPlugins;
import io.reactivex.schedulers.Schedulers;

/**
 * Replays events to Subscribers.
 * 
 * <p>This Subject respects the backpressure behavior of its Subscribers (individually).
 * 
 * @param <T> the value type
 */
public final class NbpReplaySubject<T> extends NbpSubject<T, T> {

    public static <T> NbpReplaySubject<T> create() {
        return create(16);
    }

    public static <T> NbpReplaySubject<T> create(int capacityHint) {
        if (capacityHint <= 0) {
            throw new IllegalArgumentException("capacityHint > 0 required but it was " + capacityHint);
        }
        ReplayBuffer<T> buffer = new UnboundedReplayBuffer<T>(capacityHint);
        return createWithBuffer(buffer);
    }

    public static <T> NbpReplaySubject<T> createWithSize(int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("size > 0 required but it was " + size);
        }
        SizeBoundReplayBuffer<T> buffer = new SizeBoundReplayBuffer<T>(size);
        return createWithBuffer(buffer);
    }

    /* test */ static <T> NbpReplaySubject<T> createUnbounded() {
        SizeBoundReplayBuffer<T> buffer = new SizeBoundReplayBuffer<T>(Integer.MAX_VALUE);
        return createWithBuffer(buffer);
    }

    public static <T> NbpReplaySubject<T> createWithTime(long maxAge, TimeUnit unit) {
        return createWithTime(maxAge, unit, Schedulers.trampoline());
    }

    public static <T> NbpReplaySubject<T> createWithTime(long maxAge, TimeUnit unit, Scheduler scheduler) {
        return createWithTimeAndSize(maxAge, unit, scheduler, Integer.MAX_VALUE);
    }

    public static <T> NbpReplaySubject<T> createWithTimeAndSize(long maxAge, TimeUnit unit, Scheduler scheduler, int size) {
        Objects.requireNonNull(unit, "unit is null");
        Objects.requireNonNull(scheduler, "scheduler is null");
        if (size <= 0) {
            throw new IllegalArgumentException("size > 0 required but it was " + size);
        }
        SizeAndTimeBoundReplayBuffer<T> buffer = new SizeAndTimeBoundReplayBuffer<T>(size, maxAge, unit, scheduler);
        return createWithBuffer(buffer);
    }
    
    static <T> NbpReplaySubject<T> createWithBuffer(ReplayBuffer<T> buffer) {
        State<T> state = new State<T>(buffer);
        return new NbpReplaySubject<T>(state);
    }


    final State<T> state;
    
    protected NbpReplaySubject(State<T> state) {
        super(state);
        this.state = state;
    }
    
    @Override
    public void onSubscribe(Disposable s) {
        state.onSubscribe(s);
    }

    @Override
    public void onNext(T t) {
        if (t == null) {
            onError(new NullPointerException());
            return;
        }
        state.onNext(t);
    }

    @Override
    public void onError(Throwable t) {
        if (t == null) {
            t = new NullPointerException();
        }
        state.onError(t);
    }

    @Override
    public void onComplete() {
        state.onComplete();
    }

    @Override
    public boolean hasSubscribers() {
        return state.subscribers.length != 0;
    }

    /* test */ int subscriberCount() {
        return state.subscribers.length;
    }

    @Override
    public Throwable getThrowable() {
        Object o = state.get();
        if (NotificationLite.isError(o)) {
            return NotificationLite.getError(o);
        }
        return null;
    }
    
    @Override
    public T getValue() {
        return state.buffer.getValue();
    }
    
    @Override
    public T[] getValues(T[] array) {
        return state.buffer.getValues(array);
    }
    
    @Override
    public boolean hasComplete() {
        Object o = state.get();
        return NotificationLite.isComplete(o);
    }
    
    @Override
    public boolean hasThrowable() {
        Object o = state.get();
        return NotificationLite.isError(o);
    }
    
    @Override
    public boolean hasValue() {
        return state.buffer.size() != 0;
    }
    
    /* test*/ int size() {
        return state.buffer.size();
    }
    
    static final class State<T> extends AtomicReference<Object> implements NbpOnSubscribe<T>, NbpSubscriber<T> {
        /** */
        private static final long serialVersionUID = -4673197222000219014L;

        final ReplayBuffer<T> buffer;
        
        boolean done;
        
        volatile ReplayDisposable<T>[] subscribers;
        @SuppressWarnings("rawtypes")
        static final AtomicReferenceFieldUpdater<State, ReplayDisposable[]> SUBSCRIBERS =
                AtomicReferenceFieldUpdater.newUpdater(State.class, ReplayDisposable[].class, "subscribers");
        
        @SuppressWarnings("rawtypes")
        static final ReplayDisposable[] EMPTY = new ReplayDisposable[0];

        @SuppressWarnings("rawtypes")
        static final ReplayDisposable[] TERMINATED = new ReplayDisposable[0];
        
        public State(ReplayBuffer<T> buffer) {
            this.buffer = buffer;
            SUBSCRIBERS.lazySet(this, EMPTY);
        }
        
        @Override
        public void accept(NbpSubscriber<? super T> s) {
            ReplayDisposable<T> rs = new ReplayDisposable<T>(s, this);
            s.onSubscribe(rs);
            
            if (!rs.cancelled) {
                if (add(rs)) {
                    if (rs.cancelled) {
                        remove(rs);
                        return;
                    }
                }
                buffer.replay(rs);
            }
        }
        
        public boolean add(ReplayDisposable<T> rs) {
            for (;;) {
                ReplayDisposable<T>[] a = subscribers;
                if (a == TERMINATED) {
                    return false;
                }
                int len = a.length;
                @SuppressWarnings("unchecked")
                ReplayDisposable<T>[] b = new ReplayDisposable[len + 1];
                System.arraycopy(a, 0, b, 0, len);
                b[len] = rs;
                if (SUBSCRIBERS.compareAndSet(this, a, b)) {
                    return true;
                }
            }
        }
        
        @SuppressWarnings("unchecked")
        public void remove(ReplayDisposable<T> rs) {
            for (;;) {
                ReplayDisposable<T>[] a = subscribers;
                if (a == TERMINATED || a == EMPTY) {
                    return;
                }
                int len = a.length;
                int j = -1;
                for (int i = 0; i < len; i++) {
                    if (a[i] == rs) {
                        j = i;
                        break;
                    }
                }
                
                if (j < 0) {
                    return;
                }
                ReplayDisposable<T>[] b;
                if (len == 1) {
                    b = EMPTY;
                } else {
                    b = new ReplayDisposable[len - 1];
                    System.arraycopy(a, 0, b, 0, j);
                    System.arraycopy(a, j + 1, b, j, len - j - 1);
                }
                if (SUBSCRIBERS.compareAndSet(this, a, b)) {
                    return;
                }
            }
        }
        
        @SuppressWarnings("unchecked")
        public ReplayDisposable<T>[] terminate(Object terminalValue) {
            if (compareAndSet(null, terminalValue)) {
                return SUBSCRIBERS.getAndSet(this, TERMINATED);
            }
            return TERMINATED;
        }
        
        @Override
        public void onSubscribe(Disposable s) {
            if (done) {
                s.dispose();
            }
        }
        
        @Override
        public void onNext(T t) {
            if (done) {
                return;
            }

            ReplayBuffer<T> b = buffer;
            b.add(t);
            
            for (ReplayDisposable<T> rs : subscribers) {
                // FIXME there is a caught-up optimization possible here as is with 1.x
                b.replay(rs);
            }
        }
        
        @Override
        public void onError(Throwable t) {
            if (done) {
                RxJavaPlugins.onError(t);
                return;
            }
            done = true;

            Object o = NotificationLite.error(t);
            
            ReplayBuffer<T> b = buffer;
            
            b.addFinal(o);
            
            for (ReplayDisposable<T> rs : terminate(o)) {
                b.replay(rs);
            }
        }
        
        @Override
        public void onComplete() {
            if (done) {
                return;
            }
            done = true;

            Object o = NotificationLite.complete();
            
            ReplayBuffer<T> b = buffer;
            
            b.addFinal(o);
            
            for (ReplayDisposable<T> rs : terminate(o)) {
                b.replay(rs);
            }
        }
    }
    
    interface ReplayBuffer<T> {
        
        void add(T value);
        
        void addFinal(Object notificationLite);
        
        void replay(ReplayDisposable<T> rs);
        
        int size();
        
        T getValue();
        
        T[] getValues(T[] array);
    }
    
    static final class ReplayDisposable<T> extends AtomicInteger implements Disposable {
        /** */
        private static final long serialVersionUID = 466549804534799122L;
        final NbpSubscriber<? super T> actual;
        final State<T> state;
        
        Object index;
        
        volatile boolean cancelled;
        
        public ReplayDisposable(NbpSubscriber<? super T> actual, State<T> state) {
            this.actual = actual;
            this.state = state;
        }
        
        @Override
        public void dispose() {
            if (!cancelled) {
                cancelled = true;
                state.remove(this);
            }
        }
    }
    
    static final class UnboundedReplayBuffer<T> implements ReplayBuffer<T> {
        final List<Object> buffer;
        
        volatile boolean done;
        
        volatile int size;
        
        public UnboundedReplayBuffer(int capacityHint) {
            this.buffer = new ArrayList<Object>(capacityHint);
        }
        
        @Override
        public void add(T value) {
            buffer.add(value);
            size++;
        }
        
        @Override
        public void addFinal(Object notificationLite) {
            buffer.add(notificationLite);
            size++;
            done = true;
        }
        
        @Override
        @SuppressWarnings("unchecked")
        public T getValue() {
            int s = size;
            if (s != 0) {
                List<Object> b = buffer;
                Object o = b.get(s - 1);
                if (NotificationLite.isComplete(o) || NotificationLite.isError(o)) {
                    if (s == 1) {
                        return null;
                    }
                    return (T)b.get(s - 2);
                }
                return (T)o;
            }
            return null;
        }
        
        @Override
        @SuppressWarnings("unchecked")
        public T[] getValues(T[] array) {
            int s = size;
            if (s == 0) {
                if (array.length != 0) {
                    array[0] = null;
                }
                return array;
            }
            List<Object> b = buffer;
            Object o = b.get(s - 1);
            
            if (NotificationLite.isComplete(o) || NotificationLite.isError(o)) {
                s--;
                if (s == 0) {
                    if (array.length != 0) {
                        array[0] = null;
                    }
                    return array;
                }
            }
            
            
            if (array.length < s) {
                array = (T[])Array.newInstance(array.getClass().getComponentType(), s);
            }
            for (int i = 0; i < s; i++) {
                array[i] = (T)b.get(i);
            }
            if (array.length > s) {
                array[s] = null;
            }
            
            return array;
        }
        
        @Override
        @SuppressWarnings("unchecked")
        public void replay(ReplayDisposable<T> rs) {
            if (rs.getAndIncrement() != 0) {
                return;
            }
            
            int missed = 1;
            final List<Object> b = buffer;
            final NbpSubscriber<? super T> a = rs.actual;

            Integer indexObject = (Integer)rs.index;
            int index = 0;
            if (indexObject != null) {
                index = indexObject;
            } else {
                index = 0;
                rs.index = 0;
            }

            for (;;) {

                if (rs.cancelled) {
                    rs.index = null;
                    return;
                }

                int s = size;
                
                while (s != index) {
                    
                    if (rs.cancelled) {
                        rs.index = null;
                        return;
                    }
                    
                    Object o = b.get(index);
                    
                    if (done) {
                        if (index + 1 == s) {
                            s = size;
                            if (index + 1 == s) {
                                if (NotificationLite.isComplete(o)) {
                                    a.onComplete();
                                } else {
                                    a.onError(NotificationLite.getError(o));
                                }
                                rs.index = null;
                                rs.cancelled = true;
                                return;
                            }
                        }
                    }
                    
                    a.onNext((T)o);
                    index++;
                }
                
                if (index != size) {
                    continue;
                }
                
                rs.index = index;
                
                missed = rs.addAndGet(-missed);
                if (missed == 0) {
                    break;
                }
            }
        }
        
        @Override
        public int size() {
            int s = size;
            if (s != 0) {
                Object o = buffer.get(s - 1);
                if (NotificationLite.isComplete(o) || NotificationLite.isError(o)) {
                    return s - 1;
                }
                return s;
            }
            return 0;
        }
    }
    
    static final class Node<T> extends AtomicReference<Node<T>> {
        /** */
        private static final long serialVersionUID = 6404226426336033100L;
        
        final T value;
        
        public Node(T value) {
            this.value = value;
        }
    }
    
    static final class TimedNode<T> extends AtomicReference<TimedNode<T>> {
        /** */
        private static final long serialVersionUID = 6404226426336033100L;
        
        final T value;
        final long time;
        
        public TimedNode(T value, long time) {
            this.value = value;
            this.time = time;
        }
    }
    
    static final class SizeBoundReplayBuffer<T> implements ReplayBuffer<T> {
        final int maxSize;
        int size;
        
        volatile Node<Object> head;
        
        Node<Object> tail;
        
        volatile boolean done;
        
        public SizeBoundReplayBuffer(int maxSize) {
            this.maxSize = maxSize;
            Node<Object> h = new Node<Object>(null);
            this.tail = h;
            this.head = h;
        }

        void trim() {
            if (size > maxSize) {
                size--;
                Node<Object> h = head;
                head = h.get();
            }
        }
        
        @Override
        public void add(T value) {
            Object o = value;
            Node<Object> n = new Node<Object>(o);
            Node<Object> t = tail;

            tail = n;
            size++;
            /*
             *  FIXME not sure why lazySet doesn't work here
             *  (testReplaySubjectEmissionSubscriptionRace hangs) 
             *  must be the lack of StoreLoad barrier?
             */
            t.set(n); // releases both the tail and size
            
            trim();
        }
        
        @Override
        public void addFinal(Object notificationLite) {
            Object o = notificationLite;
            Node<Object> n = new Node<Object>(o);
            Node<Object> t = tail;

            tail = n;
            size++;
            t.lazySet(n); // releases both the tail and size
            
            done = true;
        }
        
        @Override
        @SuppressWarnings("unchecked")
        public T getValue() {
            Node<Object> prev = null;
            Node<Object> h = head;

            for (;;) {
                Node<Object> next = h.get();
                if (next == null) {
                    break;
                }
                prev = h;
                h = next;
            }
            
            Object v = h.value;
            if (v == null) {
                return null;
            }
            if (NotificationLite.isComplete(v) || NotificationLite.isError(v)) {
                return (T)prev.value;
            }
            
            return (T)v;
        }
        
        @Override
        @SuppressWarnings("unchecked")
        public T[] getValues(T[] array) {
            Node<Object> h = head;
            int s = size();
            
            if (s == 0) {
                if (array.length != 0) {
                    array[0] = null;
                }
            } else {
                if (array.length < s) {
                    array = (T[])Array.newInstance(array.getClass().getComponentType(), s);
                }

                int i = 0;
                while (i != s) {
                    Node<Object> next = h.get();
                    array[i] = (T)next.value;
                    i++;
                    h = next;
                }
                if (array.length > s) {
                    array[s] = null;
                }
            }
            
            return array;
        }
        
        @Override
        @SuppressWarnings("unchecked")
        public void replay(ReplayDisposable<T> rs) {
            if (rs.getAndIncrement() != 0) {
                return;
            }
            
            int missed = 1;
            final NbpSubscriber<? super T> a = rs.actual;

            Node<Object> index = (Node<Object>)rs.index;
            if (index == null) {
                index = head;
            }

            for (;;) {

                if (rs.cancelled) {
                    rs.index = null;
                    return;
                }

                for (;;) {
                    if (rs.cancelled) {
                        rs.index = null;
                        return;
                    }
                    
                    Node<Object> n = index.get();
                    
                    if (n == null) {
                        break;
                    }
                    
                    Object o = n.value;
                    
                    if (done) {
                        if (n.get() == null) {
                            
                            if (NotificationLite.isComplete(o)) {
                                a.onComplete();
                            } else {
                                a.onError(NotificationLite.getError(o));
                            }
                            rs.index = null;
                            rs.cancelled = true;
                            return;
                        }
                    }
                    
                    a.onNext((T)o);
                    
                    index = n;
                }
                
                if (index.get() != null) {
                    continue;
                }
                
                rs.index = index;
                
                missed = rs.addAndGet(-missed);
                if (missed == 0) {
                    break;
                }
            }
        }
        
        @Override
        public int size() {
            int s = 0;
            Node<Object> h = head;
            while (s != Integer.MAX_VALUE) {
                Node<Object> next = h.get();
                if (next == null) {
                    Object o = h.value;
                    if (NotificationLite.isComplete(o) || NotificationLite.isError(o)) {
                        s--;
                    }
                    break;
                }
                s++;
                h = next;
            }
            
            return s;
        }
    }
    
    static final class SizeAndTimeBoundReplayBuffer<T> implements ReplayBuffer<T> {
        final int maxSize;
        final long maxAge;
        final TimeUnit unit;
        final Scheduler scheduler;
        int size;
        
        volatile TimedNode<Object> head;
        
        TimedNode<Object> tail;
        
        volatile boolean done;
        
        
        public SizeAndTimeBoundReplayBuffer(int maxSize, long maxAge, TimeUnit unit, Scheduler scheduler) {
            this.maxSize = maxSize;
            this.maxAge = maxAge;
            this.unit = unit;
            this.scheduler = scheduler;
            TimedNode<Object> h = new TimedNode<Object>(null, 0L);
            this.tail = h;
            this.head = h;
        }

        void trim() {
            if (size > maxSize) {
                size--;
                TimedNode<Object> h = head;
                head = h.get();
            }
            long limit = scheduler.now(unit) - maxAge;
            
            TimedNode<Object> h = head;
            
            for (;;) {
                TimedNode<Object> next = h.get();
                if (next == null) {
                    head = h;
                    break;
                }
                
                if (next.time > limit) {
                    head = h;
                    break;
                }
                
                h = next;
            }
            
        }
        
        void trimFinal() {
            long limit = scheduler.now(unit) - maxAge;
            
            TimedNode<Object> h = head;
            
            for (;;) {
                TimedNode<Object> next = h.get();
                if (next.get() == null) {
                    head = h;
                    break;
                }
                
                if (next.time > limit) {
                    head = h;
                    break;
                }
                
                h = next;
            }
        }
        
        @Override
        public void add(T value) {
            Object o = value;
            TimedNode<Object> n = new TimedNode<Object>(o, scheduler.now(unit));
            TimedNode<Object> t = tail;

            tail = n;
            size++;
            /*
             *  FIXME not sure why lazySet doesn't work here
             *  (testReplaySubjectEmissionSubscriptionRace hangs) 
             *  must be the lack of StoreLoad barrier?
             */
            t.set(n); // releases both the tail and size
            
            trim();
        }
        
        @Override
        public void addFinal(Object notificationLite) {
            Object o = notificationLite;
            TimedNode<Object> n = new TimedNode<Object>(o, Long.MAX_VALUE);
            TimedNode<Object> t = tail;

            tail = n;
            size++;
            t.lazySet(n); // releases both the tail and size
            trimFinal();
            
            done = true;
        }
        
        @Override
        @SuppressWarnings("unchecked")
        public T getValue() {
            TimedNode<Object> prev = null;
            TimedNode<Object> h = head;

            for (;;) {
                TimedNode<Object> next = h.get();
                if (next == null) {
                    break;
                }
                prev = h;
                h = next;
            }
            
            Object v = h.value;
            if (v == null) {
                return null;
            }
            if (NotificationLite.isComplete(v) || NotificationLite.isError(v)) {
                return (T)prev.value;
            }
            
            return (T)v;
        }
        
        @Override
        @SuppressWarnings("unchecked")
        public T[] getValues(T[] array) {
            TimedNode<Object> h = head;
            int s = size();
            
            if (s == 0) {
                if (array.length != 0) {
                    array[0] = null;
                }
            } else {
                if (array.length < s) {
                    array = (T[])Array.newInstance(array.getClass().getComponentType(), s);
                }

                int i = 0;
                while (i != s) {
                    TimedNode<Object> next = h.get();
                    array[i] = (T)next.value;
                    i++;
                    h = next;
                }
                if (array.length > s) {
                    array[s] = null;
                }
            }
            
            return array;
        }
        
        @Override
        @SuppressWarnings("unchecked")
        public void replay(ReplayDisposable<T> rs) {
            if (rs.getAndIncrement() != 0) {
                return;
            }
            
            int missed = 1;
            final NbpSubscriber<? super T> a = rs.actual;

            TimedNode<Object> index = (TimedNode<Object>)rs.index;
            if (index == null) {
                index = head;
                if (!done) {
                    // skip old entries
                    long limit = scheduler.now(unit) - maxAge;
                    TimedNode<Object> next = index.get();
                    while (next != null) {
                        long ts = next.time;
                        if (ts > limit) {
                            break;
                        }
                        index = next;
                        next = index.get();
                    }
                }
            }

            for (;;) {

                if (rs.cancelled) {
                    rs.index = null;
                    return;
                }

                for (;;) {
                    if (rs.cancelled) {
                        rs.index = null;
                        return;
                    }
                    
                    TimedNode<Object> n = index.get();
                    
                    if (n == null) {
                        break;
                    }
                    
                    Object o = n.value;
                    
                    if (done) {
                        if (n.get() == null) {
                            
                            if (NotificationLite.isComplete(o)) {
                                a.onComplete();
                            } else {
                                a.onError(NotificationLite.getError(o));
                            }
                            rs.index = null;
                            rs.cancelled = true;
                            return;
                        }
                    }
                    
                    a.onNext((T)o);
                    
                    index = n;
                }
                
                if (index.get() != null) {
                    continue;
                }
                
                rs.index = index;
                
                missed = rs.addAndGet(-missed);
                if (missed == 0) {
                    break;
                }
            }
        }
        
        @Override
        public int size() {
            int s = 0;
            TimedNode<Object> h = head;
            while (s != Integer.MAX_VALUE) {
                TimedNode<Object> next = h.get();
                if (next == null) {
                    Object o = h.value;
                    if (NotificationLite.isComplete(o) || NotificationLite.isError(o)) {
                        s--;
                    }
                    break;
                }
                s++;
                h = next;
            }
            
            return s;
        }
    }
}
