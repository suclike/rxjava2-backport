/**
 * Copyright 2015 David Karnok
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
package io.reactivex.internal.util;

import java.io.Serializable;

import org.reactivestreams.*;

import io.reactivex.NbpObservable.NbpSubscriber;
import io.reactivex.disposables.Disposable;

/**
 * Lightweight notification handling utility class.
 */
public enum NotificationLite {
    // No instances
    ;
    
    /**
     * Indicates a completion notification.
     */
    private enum Complete {
        INSTANCE;
        @Override
        public String toString() {
            return "NotificationLite.Complete";
        };
    }
    
    /**
     * Wraps a Throwable.
     */
    private static final class ErrorNotification implements Serializable {
        /** */
        private static final long serialVersionUID = -8759979445933046293L;
        final Throwable e;
        ErrorNotification(Throwable e) {
            this.e = e;
        }
        
        @Override
        public String toString() {
            return "NotificationLite.Error[" + e + "]";
        }
    }
    
    /**
     * Wraps a Subscription.
     */
    private static final class SubscriptionNotification implements Serializable {
        /** */
        private static final long serialVersionUID = -1322257508628817540L;
        final Subscription s;
        SubscriptionNotification(Subscription s) {
            this.s = s;
        }
        
        @Override
        public String toString() {
            return "NotificationLite.Subscription[" + s + "]";
        }
    }
    
    /**
     * Wraps a Disposable.
     */
    private static final class DisposableNotification implements Serializable {
        /** */
        private static final long serialVersionUID = -7482590109178395495L;
        final Disposable d;
        
        DisposableNotification(Disposable d) {
            this.d = d;
        }
        
        @Override
        public String toString() {
            return "NotificationLite.Disposable[" + d + "]";
        }
    }
    
    /**
     * Converts a value into a notification value.
     * @param value the value to convert
     * @return the notification representing the value
     */
    public static <T> Object next(T value) {
        return value;
    }
    
    /**
     * Returns a complete notification.
     * @return a complete notification
     */
    public static Object complete() {
        return Complete.INSTANCE;
    }
    
    /**
     * Converts a Throwable into a notification value.
     * @param e the Throwable to convert
     * @return the notification representing the Throwable
     */
    public static Object error(Throwable e) {
        return new ErrorNotification(e);
    }
    
    /**
     * Converts a Subscription into a notification value.
     * @param e the Subscription to convert
     * @return the notification representing the Subscription
     */
    public static Object subscription(Subscription s) {
        return new SubscriptionNotification(s);
    }
    
    public static Object disposable(Disposable d) {
        return new DisposableNotification(d);
    }
    
    /**
     * Checks if the given object represents a complete notification.
     * @param o the object to check
     * @return true if the object represents a complete notification
     */
    public static boolean isComplete(Object o) {
        return o == Complete.INSTANCE;
    }
    
    /**
     * Checks if the given object represents a error notification.
     * @param o the object to check
     * @return true if the object represents a error notification
     */
    public static boolean isError(Object o) {
        return o instanceof ErrorNotification;
    }
    
    /**
     * Checks if the given object represents a subscription notification.
     * @param o the object to check
     * @return true if the object represents a subscription notification
     */
    public static boolean isSubscription(Object o) {
        return o instanceof SubscriptionNotification;
    }
    
    public static boolean isDisposable(Object o) {
        return o instanceof DisposableNotification;
    }
    
    /**
     * Extracts the value from the notification object
     * @param o the notification object
     * @return the extracted value
     */
    @SuppressWarnings("unchecked")
    public static <T> T getValue(Object o) {
        return (T)o;
    }
    
    /**
     * Extracts the Throwable from the notification object
     * @param o the notification object
     * @return the extracted Throwable
     */
    public static Throwable getError(Object o) {
        return ((ErrorNotification)o).e;
    }
    
    /**
     * Extracts the Subscription from the notification object
     * @param o the notification object
     * @return the extracted Subscription
     */
    public static Subscription getSubscription(Object o) {
        return ((SubscriptionNotification)o).s;
    }
    
    public static Disposable getDisposable(Object o) {
        return ((DisposableNotification)o).d;
    }
    
    /**
     * Calls the appropriate Subscriber method based on the type of the notification.
     * <p>Does not check for a subscription notification, see {@link #acceptFull(Object, Subscriber)}.
     * @param o the notification object
     * @param s the subscriber to call methods on
     * @return true if the notification was a terminal event (i.e., complete or error)
     * @see #acceptFull(Object, Subscriber)
     */
    @SuppressWarnings("unchecked")
    public static <T> boolean accept(Object o, Subscriber<? super T> s) {
        if (o == Complete.INSTANCE) {
            s.onComplete();
            return true;
        } else
        if (o instanceof ErrorNotification) {
            s.onError(((ErrorNotification)o).e);
            return true;
        }
        s.onNext((T)o);
        return false;
    }

    /**
     * Calls the appropriate NbpSubscriber method based on the type of the notification.
     * <p>Does not check for a subscription notification.
     * @param o the notification object
     * @param s the NbpSubscriber to call methods on
     * @return true if the notification was a terminal event (i.e., complete or error)
     */
    @SuppressWarnings("unchecked")
    public static <T> boolean accept(Object o, NbpSubscriber<? super T> s) {
        if (o == Complete.INSTANCE) {
            s.onComplete();
            return true;
        } else
        if (o instanceof ErrorNotification) {
            s.onError(((ErrorNotification)o).e);
            return true;
        }
        s.onNext((T)o);
        return false;
    }

    /**
     * Calls the appropriate Subscriber method based on the type of the notification.
     * @param o the notification object
     * @param s the subscriber to call methods on
     * @return true if the notification was a terminal event (i.e., complete or error)
     * @see #accept(Object, Subscriber)
     */
    @SuppressWarnings("unchecked")
    public static <T> boolean acceptFull(Object o, Subscriber<? super T> s) {
        if (o == Complete.INSTANCE) {
            s.onComplete();
            return true;
        } else
        if (o instanceof ErrorNotification) {
            s.onError(((ErrorNotification)o).e);
            return true;
        } else
        if (o instanceof SubscriptionNotification) {
            s.onSubscribe(((SubscriptionNotification)o).s);
            return false;
        }
        s.onNext((T)o);
        return false;
    }
    
    /**
     * Calls the appropriate NbpSubscriber method based on the type of the notification.
     * @param o the notification object
     * @param s the subscriber to call methods on
     * @return true if the notification was a terminal event (i.e., complete or error)
     * @see #accept(Object, NbpSubscriber)
     */
    @SuppressWarnings("unchecked")
    public static <T> boolean acceptFull(Object o, NbpSubscriber<? super T> s) {
        if (o == Complete.INSTANCE) {
            s.onComplete();
            return true;
        } else
        if (o instanceof ErrorNotification) {
            s.onError(((ErrorNotification)o).e);
            return true;
        } else
        if (o instanceof DisposableNotification) {
            s.onSubscribe(((DisposableNotification)o).d);
            return false;
        }
        s.onNext((T)o);
        return false;
    }
}