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

package io.reactivex.internal.operators;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import io.reactivex.Observable;

public class OperatorElementAtTest {

    @Test
    public void testElementAt() {
        assertEquals(2, Observable.fromArray(1, 2).elementAt(1).toBlocking().single()
                .intValue());
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void testElementAtWithMinusIndex() {
        Observable.fromArray(1, 2).elementAt(-1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void testElementAtWithIndexOutOfBounds() {
        Observable.fromArray(1, 2).elementAt(2).toBlocking().single();
    }

    @Test
    public void testElementAtOrDefault() {
        assertEquals(2, Observable.fromArray(1, 2).elementAt(1, 0).toBlocking()
                .single().intValue());
    }

    @Test
    public void testElementAtOrDefaultWithIndexOutOfBounds() {
        assertEquals(0, Observable.fromArray(1, 2).elementAt(2, 0).toBlocking()
                .single().intValue());
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void testElementAtOrDefaultWithMinusIndex() {
        Observable.fromArray(1, 2).elementAt(-1, 0);
    }
}